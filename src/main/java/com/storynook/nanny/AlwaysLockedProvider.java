package com.storynook.nanny;

import java.util.UUID;

/**
 * Default {@link MembershipProvider} that locks every player out of AI chat.
 *
 * <p>This is what ships in Phase 4 — AI tier wiring exists but never fires.
 */
public class AlwaysLockedProvider implements MembershipProvider {

    @Override
    public boolean isUnlocked(UUID playerUUID) {
        return false;
    }

    @Override
    public void refresh(UUID playerUUID) {
        // No-op
    }
}
