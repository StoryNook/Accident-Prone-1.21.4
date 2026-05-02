package com.storynook.nanny;

/**
 * Capability keys for the mood-tier action matrix. {@link NannyPolicy}
 * translates a {@code (NannyData, Capability)} pair into a yes/no decision
 * by consulting the Nanny's mood tier (or {@code customSettings} for the
 * CUSTOM tier).
 *
 * <p>Phase 5a wires the first four (potty reminders, armor lock, crib,
 * caregiver block). Phase 5b adds the Warden capabilities.
 */
public enum Capability {
    BASIC_CARE,
    POTTY_REMINDERS,
    ARMOR_LOCK,
    CRIB_PLACEMENT,
    BLOCK_CAREGIVERS,
    FORCE_FEED_LAXATIVE,
    BINDING_LEGGINGS,
    LEASH_WARD,
    HYPNOSIS_USE,
    ROOM_LOCKDOWN,
    EVIL_CRAFTING
}
