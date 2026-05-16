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
        return true;  // placeholder until activeTask is added in a later task
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
