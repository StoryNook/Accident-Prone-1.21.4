package com.storynook.nanny;

import com.storynook.nanny.tasks.NannyTask;
import java.util.ArrayList;
import java.util.List;

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
}
