package com.storynook.nanny;

import java.util.UUID;

/**
 * Pluggable check for whether a player has unlocked the AI chat tier.
 *
 * <p>Phase 4 ships {@link AlwaysLockedProvider} (always returns false).
 * Future Patreon/Subscribestar implementations live in a separate spec.
 */
public interface MembershipProvider {

    /** @return true if {@code playerUUID} has an active membership granting AI chat. */
    boolean isUnlocked(UUID playerUUID);

    /** Optional refresh hint (called on login + every 30 min by future providers). */
    void refresh(UUID playerUUID);
}
