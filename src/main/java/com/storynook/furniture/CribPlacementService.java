package com.storynook.furniture;

/**
 * Pure-Java placement-validation logic for new-system cribs. Lives
 * separate from {@code CribListener} so it's testable without Bukkit.
 *
 * <p>The listener does the I/O (read blocks, spawn entities, send chat
 * messages); this service decides yes/no/why.
 */
public final class CribPlacementService {

    public enum Result {
        ACCEPT,
        REJECT_NEEDS_SOLID_BASE,
        REJECT_FOOTPRINT_BLOCKED,
        REJECT_PLAYER_IN_FOOTPRINT
    }

    public static Result validate(boolean baseSolid, boolean footprintAllAir, boolean playerInFootprint) {
        if (!baseSolid) return Result.REJECT_NEEDS_SOLID_BASE;
        if (!footprintAllAir) return Result.REJECT_FOOTPRINT_BLOCKED;
        if (playerInFootprint) return Result.REJECT_PLAYER_IN_FOOTPRINT;
        return Result.ACCEPT;
    }

    /** Snaps a yaw to one of {0, 90, 180, 270}, normalising to [0, 360). */
    public static float snapYaw(float yaw) {
        float n = ((yaw % 360.0f) + 360.0f) % 360.0f;
        return Math.round(n / 90.0f) * 90.0f % 360.0f;
    }

    /** User-facing message for a rejection result. */
    public static String message(Result r) {
        return switch (r) {
            case REJECT_NEEDS_SOLID_BASE -> "Crib needs a solid surface.";
            case REJECT_FOOTPRINT_BLOCKED -> "Not enough room for a crib here.";
            case REJECT_PLAYER_IN_FOOTPRINT -> "Step out of the way first.";
            case ACCEPT -> "";
        };
    }

    private CribPlacementService() {}
}
