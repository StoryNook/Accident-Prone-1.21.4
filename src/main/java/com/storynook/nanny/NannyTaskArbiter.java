package com.storynook.nanny;

import com.storynook.nanny.tasks.Candidate;
import com.storynook.nanny.tasks.NannyTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Owns task selection and navigation gating for one Nanny. Built incrementally —
 * this skeleton just holds the registered-tasks list and reports idle state. See
 * docs/superpowers/specs/2026-05-16-nanny-task-dispatch-design.md.
 */
public class NannyTaskArbiter {

    private final List<NannyTask> registered = new ArrayList<>();

    public void register(NannyTask task) {
        registered.add(task);
    }

    public List<NannyTask> registered() {
        return List.copyOf(registered);
    }

    /** True when no activeTask is set. The next tickFor will rebuild candidates from scratch. */
    public boolean isIdle() {
        return activeTask == null;
    }

    /**
     * Per-Nanny active-task reference. In-memory only — re-evaluated on server
     * restart, which is correct (don't try to resume a half-walked path that
     * may no longer be reachable).
     */
    public record ActiveTaskRef(NannyTask task, Player ward, Location target, int priority) {}

    private ActiveTaskRef activeTask;

    public String activeTaskId() { return activeTask == null ? null : activeTask.task().id(); }

    /**
     * Apply the hybrid latch rule against a sorted candidate list. Mutates activeTask.
     *
     * Rules in order:
     *   1. No activeTask → switch to winner.
     *   2. No winner → clear activeTask.
     *   3. winner.priority > activeTask.priority → PREEMPT.
     *   4. winner is same task + same target as activeTask → stay (refresh).
     *   5. activeTask still in candidates (same priority tier or below) → stay.
     *   6. activeTask no longer in candidates → switch.
     */
    public void applyLatch(List<ScoredCandidate> sorted) {
        if (sorted.isEmpty()) { activeTask = null; return; }
        ScoredCandidate winner = sorted.get(0);

        if (activeTask == null) {
            activeTask = toActive(winner);
            return;
        }
        if (winner.priority() > activeTask.priority()) {
            activeTask = toActive(winner);
            return;
        }
        // Same task & same target as activeTask → stay (refresh target, ward may have moved)
        if (winner.task().id().equals(activeTask.task().id())
                && sameTarget(winner.candidate().target(), activeTask.target())) {
            activeTask = toActive(winner);
            return;
        }
        // Incumbent still present in the sorted list → stay
        for (ScoredCandidate sc : sorted) {
            if (sc.task().id().equals(activeTask.task().id())
                    && (activeTask.ward() == null
                        || (sc.ward() != null && sc.ward().getUniqueId().equals(activeTask.ward().getUniqueId())))) {
                return;  // incumbent eligible, stay
            }
        }
        // Incumbent gone → take winner
        activeTask = toActive(winner);
    }

    private ActiveTaskRef toActive(ScoredCandidate sc) {
        return new ActiveTaskRef(sc.task(), sc.ward(), sc.candidate().target(), sc.priority());
    }

    private static boolean sameTarget(Location a, Location b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return a.distanceSquared(b) < 1.0;
    }

    /**
     * Snapshot of a task's evaluate() result bound to the task itself.
     * Used internally by tickFor.
     */
    public record ScoredCandidate(NannyTask task, Candidate candidate) {
        public int priority() { return candidate.priority(); }
        public Player ward() { return candidate.ward(); }
    }

    /** Caller-provided origin (Nanny location) for distance tie-break. */
    public List<ScoredCandidate> buildAndSortCandidatesAt(
            Location nannyLoc, NannyEntity nanny, NannyData data, List<Player> wards) {
        List<ScoredCandidate> out = new ArrayList<>();
        for (Player ward : wards) {
            for (NannyTask task : registered) {
                Candidate c = task.evaluate(nanny, data, ward);
                if (c != null) out.add(new ScoredCandidate(task, c));
            }
        }
        UUID ownerUUID = data == null ? null : data.getOwnerUUID();
        out.sort(Comparator
                .comparingInt(ScoredCandidate::priority).reversed()
                .thenComparingInt((ScoredCandidate sc) -> {
                    if (ownerUUID == null || sc.ward() == null) return 1;
                    return sc.ward().getUniqueId().equals(ownerUUID) ? 0 : 1;
                })
                .thenComparingDouble((ScoredCandidate sc) -> {
                    Location t = sc.candidate().target();
                    if (nannyLoc == null || t == null) return Double.MAX_VALUE;
                    if (t.getWorld() != nannyLoc.getWorld()) return Double.MAX_VALUE;
                    return t.distanceSquared(nannyLoc);
                }));
        return out;
    }

    /** Convenience overload for tests that don't need distance tie-break. Delegates with null origin. */
    public List<ScoredCandidate> buildAndSortCandidates(
            NannyEntity nanny, NannyData data, List<Player> wards) {
        return buildAndSortCandidatesAt(null, nanny, data, wards);
    }
}
