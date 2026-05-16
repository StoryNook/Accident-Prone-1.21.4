package com.storynook.nanny.tasks;

import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import org.bukkit.entity.Player;

/**
 * One autonomous behavior the Nanny can perform. Tasks are stateless; all
 * state lives on world blocks, NannyData, or the arbiter's per-Nanny activeTask
 * reference.
 *
 * <p>evaluate() is called every tick × every ward × every registered task —
 * it MUST be cheap and side-effect-free. act() is called only when the arbiter
 * has selected this task as winner AND the Nanny is within action range.
 */
public interface NannyTask {

    /** Stable identifier for logging, cooldown keys, debug. e.g. "change", "laundry_load". */
    String id();

    /**
     * Cheap eligibility + targeting check. Returns null if not applicable
     * this tick. MUST be side-effect-free. Early-return on the cheapest
     * negative check first.
     */
    Candidate evaluate(NannyEntity nanny, NannyData data, Player ward);

    /**
     * Run the task. Only called when the arbiter has selected this task AND the
     * Nanny is within action range of getTargetLocation() (or target is null).
     */
    Result act(NannyEntity nanny, NannyData data, Player ward);

    /** Action range in blocks. Default 3.0 matches the existing isWithinActionRange check. */
    default double actionRangeBlocks() { return 3.0; }

    /** Cooldown after FAIL_GIVEUP, in ms. Default 30 seconds. */
    default long failureCooldownMs() { return 30_000L; }
}
