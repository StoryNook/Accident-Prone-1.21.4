package com.storynook.furniture.changingtable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ChangingTablePlacementServiceTest {

    @Test
    public void snapYawClampsToCardinalAngles() {
        assertEquals(0f, ChangingTablePlacementService.snapYaw(-22.5f), 0.001f);
        assertEquals(90f, ChangingTablePlacementService.snapYaw(80f), 0.001f);
        assertEquals(180f, ChangingTablePlacementService.snapYaw(170f), 0.001f);
        assertEquals(270f, ChangingTablePlacementService.snapYaw(-100f), 0.001f);
        assertEquals(0f, ChangingTablePlacementService.snapYaw(360f), 0.001f);
        assertEquals(180f, ChangingTablePlacementService.snapYaw(180.49f), 0.001f);
    }

    @Test
    public void footOffsetMatchesChestStyleRightExtension() {
        // yaw 0 = facing south → foot extends +X (east)
        assertArrayEquals(new int[]{1, 0}, ChangingTablePlacementService.footOffset(0f));
        // yaw 90 = facing west → foot extends -Z (north)
        assertArrayEquals(new int[]{0, -1}, ChangingTablePlacementService.footOffset(90f));
        // yaw 180 = facing north → foot extends -X (west)
        assertArrayEquals(new int[]{-1, 0}, ChangingTablePlacementService.footOffset(180f));
        // yaw 270 = facing east → foot extends +Z (south)
        assertArrayEquals(new int[]{0, 1}, ChangingTablePlacementService.footOffset(270f));
    }

    @Test
    public void validateAcceptsHappyPath() {
        assertEquals(ChangingTablePlacementService.Result.OK,
            ChangingTablePlacementService.validate(true, true, true, true, false, false, true));
    }

    @Test
    public void validateRejectsNonSolidBase() {
        assertEquals(ChangingTablePlacementService.Result.BASE_NOT_SOLID,
            ChangingTablePlacementService.validate(false, true, true, true, false, false, true));
        assertEquals(ChangingTablePlacementService.Result.BASE_NOT_SOLID,
            ChangingTablePlacementService.validate(true, false, true, true, false, false, true));
    }

    @Test
    public void validateRejectsNonAirOrigin() {
        assertEquals(ChangingTablePlacementService.Result.ORIGIN_NOT_AIR,
            ChangingTablePlacementService.validate(true, true, false, true, false, false, true));
        assertEquals(ChangingTablePlacementService.Result.ORIGIN_NOT_AIR,
            ChangingTablePlacementService.validate(true, true, true, false, false, false, true));
    }

    @Test
    public void validateRejectsPlayerInOrigin() {
        assertEquals(ChangingTablePlacementService.Result.PLAYER_IN_ORIGIN,
            ChangingTablePlacementService.validate(true, true, true, true, true, false, true));
        assertEquals(ChangingTablePlacementService.Result.PLAYER_IN_ORIGIN,
            ChangingTablePlacementService.validate(true, true, true, true, false, true, true));
    }

    @Test
    public void validateRejectsFootOutOfWorld() {
        assertEquals(ChangingTablePlacementService.Result.FOOT_OUT_OF_WORLD,
            ChangingTablePlacementService.validate(true, true, true, true, false, false, false));
    }
}
