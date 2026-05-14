package com.storynook.furniture.highchair;

/**
 * Pure-Java placement-validation logic for highchairs. Mirrors
 * {@link com.storynook.furniture.CribPlacementService} so it's testable
 * without Bukkit. The listener does the I/O (read blocks, spawn entities,
 * send chat messages); this service decides yes/no/why.
 */
public final class HighchairPlacementService {

    public enum Result {
        ACCEPT,
        REJECT_NEEDS_SOLID_BASE,
        REJECT_ORIGIN_BLOCKED,
        REJECT_PLAYER_IN_ORIGIN
    }

    public static Result validate(boolean baseSolid, boolean originAir, boolean playerInOrigin) {
        if (!baseSolid) return Result.REJECT_NEEDS_SOLID_BASE;
        if (!originAir) return Result.REJECT_ORIGIN_BLOCKED;
        if (playerInOrigin) return Result.REJECT_PLAYER_IN_ORIGIN;
        return Result.ACCEPT;
    }

    /** Snaps a yaw to one of {0, 90, 180, 270}, normalising to [0, 360). */
    public static float snapYaw(float yaw) {
        float n = ((yaw % 360.0f) + 360.0f) % 360.0f;
        return Math.round(n / 90.0f) * 90.0f % 360.0f;
    }

    /** User-facing action-bar message for a rejection result; "" for ACCEPT. */
    public static String message(Result r) {
        return switch (r) {
            case REJECT_NEEDS_SOLID_BASE -> "Highchair needs a solid surface.";
            case REJECT_ORIGIN_BLOCKED   -> "Not enough room for a highchair here.";
            case REJECT_PLAYER_IN_ORIGIN -> "Step out of the way first.";
            case ACCEPT                  -> "";
        };
    }

    private HighchairPlacementService() {}
}
