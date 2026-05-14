package com.storynook.furniture.changingtable;

/**
 * Pure-Java placement validation + footprint geometry. No Bukkit imports so
 * this is unit-testable without a server.
 *
 * <p>Footprint: 1×2, chest-style — the foot cell extends to the player's
 * right based on their facing direction.
 */
public final class ChangingTablePlacementService {

    public enum Result {
        OK,
        BASE_NOT_SOLID,
        ORIGIN_NOT_AIR,
        PLAYER_IN_ORIGIN,
        FOOT_OUT_OF_WORLD
    }

    private ChangingTablePlacementService() {}

    /** Snap any yaw to {0, 90, 180, 270}. Mirrors CribPlacementService.snapYaw. */
    public static float snapYaw(float rawYaw) {
        float y = ((rawYaw % 360f) + 360f) % 360f;
        if (y < 45f || y >= 315f) return 0f;
        if (y < 135f) return 90f;
        if (y < 225f) return 180f;
        return 270f;
    }

    /**
     * Foot-cell offset from the anchor cell, chest-style (foot extends to the
     * player's right). Returns int[]{dx, dz}.
     */
    public static int[] footOffset(float snappedYaw) {
        if (snappedYaw == 0f)   return new int[]{ 1,  0};   // facing south → foot east
        if (snappedYaw == 90f)  return new int[]{ 0, -1};   // facing west  → foot north
        if (snappedYaw == 180f) return new int[]{-1,  0};   // facing north → foot west
        if (snappedYaw == 270f) return new int[]{ 0,  1};   // facing east  → foot south
        return new int[]{1, 0};
    }

    /**
     * Validation predicate. All inputs are pre-resolved booleans so this stays
     * pure-Java. Order of checks matches the spec's preferred error precedence.
     */
    public static Result validate(
            boolean anchorBaseSolid,
            boolean footBaseSolid,
            boolean anchorOriginAir,
            boolean footOriginAir,
            boolean playerInAnchorOrigin,
            boolean playerInFootOrigin,
            boolean footInWorld) {
        if (!footInWorld) return Result.FOOT_OUT_OF_WORLD;
        if (!anchorBaseSolid || !footBaseSolid) return Result.BASE_NOT_SOLID;
        if (!anchorOriginAir || !footOriginAir) return Result.ORIGIN_NOT_AIR;
        if (playerInAnchorOrigin || playerInFootOrigin) return Result.PLAYER_IN_ORIGIN;
        return Result.OK;
    }
}
