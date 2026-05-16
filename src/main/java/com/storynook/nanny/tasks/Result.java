package com.storynook.nanny.tasks;

/**
 * Outcome of a task's act() call. The arbiter uses this to decide whether to
 * keep the task active, retry, or apply a failure cooldown.
 */
public enum Result {
    /** Task complete this tick. Arbiter clears activeTask. */
    DONE,
    /** Still working (e.g. one of N items deposited). Arbiter keeps activeTask. */
    CONTINUE,
    /** Could not complete; arbiter keeps active and will retry next tick. No cooldown. */
    FAIL_RETRY,
    /** Bail. Arbiter clears active and sets failureCooldown[(taskId, target)]. */
    FAIL_GIVEUP
}
