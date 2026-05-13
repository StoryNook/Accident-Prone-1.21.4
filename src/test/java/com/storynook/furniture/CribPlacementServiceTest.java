package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class CribPlacementServiceTest {

    @Test
    public void rejectsAirBase() {
        CribPlacementService.Result r = CribPlacementService.validate(false, true, false);
        assertEquals(CribPlacementService.Result.REJECT_NEEDS_SOLID_BASE, r);
    }

    @Test
    public void rejectsBlockedFootprint() {
        CribPlacementService.Result r = CribPlacementService.validate(true, false, false);
        assertEquals(CribPlacementService.Result.REJECT_FOOTPRINT_BLOCKED, r);
    }

    @Test
    public void rejectsPlayerInsideFootprint() {
        CribPlacementService.Result r = CribPlacementService.validate(true, true, true);
        assertEquals(CribPlacementService.Result.REJECT_PLAYER_IN_FOOTPRINT, r);
    }

    @Test
    public void acceptsValidPlacement() {
        CribPlacementService.Result r = CribPlacementService.validate(true, true, false);
        assertEquals(CribPlacementService.Result.ACCEPT, r);
    }

    @Test
    public void yawSnap0() {
        assertEquals(0.0f, CribPlacementService.snapYaw(10.0f), 0.0001f);
    }

    @Test
    public void yawSnap90() {
        assertEquals(90.0f, CribPlacementService.snapYaw(80.0f), 0.0001f);
        assertEquals(90.0f, CribPlacementService.snapYaw(110.0f), 0.0001f);
    }

    @Test
    public void yawSnap180() {
        assertEquals(180.0f, CribPlacementService.snapYaw(170.0f), 0.0001f);
        assertEquals(180.0f, CribPlacementService.snapYaw(-179.0f), 0.0001f);
    }

    @Test
    public void yawSnap270() {
        assertEquals(270.0f, CribPlacementService.snapYaw(-90.0f), 0.0001f);
        assertEquals(270.0f, CribPlacementService.snapYaw(260.0f), 0.0001f);
    }
}
