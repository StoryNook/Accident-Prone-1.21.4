package com.storynook.nanny;

import com.storynook.nanny.tasks.Candidate;
import com.storynook.nanny.tasks.NannyTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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

    /**
     * Visible for tests. Iterates wards × registered tasks, calls evaluate,
     * collects non-null candidates, sorts by priority desc.
     *
     * Owner tie-break and distance tie-break are added in later tasks; for
     * now only the primary priority sort runs.
     */
    public List<ScoredCandidate> buildAndSortCandidates(
            NannyEntity nanny, NannyData data, List<Player> wards) {
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
                .thenComparing((ScoredCandidate sc) -> {
                    if (ownerUUID == null || sc.ward() == null) return 1;
                    return sc.ward().getUniqueId().equals(ownerUUID) ? 0 : 1;
                }));
        return out;
    }
}
