package com.storynook.furniture.highchair;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HighchairPlacementServiceTest {

    @Test
    public void validate_acceptsWhenAllConditionsMet() {
        assertEquals(HighchairPlacementService.Result.ACCEPT,
            HighchairPlacementService.validate(true, true, false));
    }

    @Test
    public void validate_rejectsWhenBaseNotSolid() {
        assertEquals(HighchairPlacementService.Result.REJECT_NEEDS_SOLID_BASE,
            HighchairPlacementService.validate(false, true, false));
    }

    @Test
    public void validate_rejectsWhenOriginBlocked() {
        assertEquals(HighchairPlacementService.Result.REJECT_ORIGIN_BLOCKED,
            HighchairPlacementService.validate(true, false, false));
    }

    @Test
    public void validate_rejectsWhenPlayerInOrigin() {
        assertEquals(HighchairPlacementService.Result.REJECT_PLAYER_IN_ORIGIN,
            HighchairPlacementService.validate(true, true, true));
    }

    @Test
    public void validate_baseFailureBeatsOriginFailure() {
        assertEquals(HighchairPlacementService.Result.REJECT_NEEDS_SOLID_BASE,
            HighchairPlacementService.validate(false, false, true));
    }

    @Test
    public void snapYaw_snapsToNearest90() {
        assertEquals(0.0f,   HighchairPlacementService.snapYaw(0.0f),   0.001f);
        assertEquals(90.0f,  HighchairPlacementService.snapYaw(89.0f),  0.001f);
        assertEquals(90.0f,  HighchairPlacementService.snapYaw(135.0f - 1f), 0.001f);
        assertEquals(180.0f, HighchairPlacementService.snapYaw(180.0f), 0.001f);
        assertEquals(270.0f, HighchairPlacementService.snapYaw(269.0f), 0.001f);
        assertEquals(0.0f,   HighchairPlacementService.snapYaw(360.0f), 0.001f);
    }

    @Test
    public void snapYaw_normalisesNegative() {
        assertEquals(270.0f, HighchairPlacementService.snapYaw(-90.0f), 0.001f);
        assertEquals(180.0f, HighchairPlacementService.snapYaw(-180.0f), 0.001f);
    }

    @Test
    public void message_acceptIsEmpty() {
        assertEquals("", HighchairPlacementService.message(HighchairPlacementService.Result.ACCEPT));
    }

    @Test
    public void message_rejectionsAreNonEmpty() {
        assertFalse(HighchairPlacementService.message(HighchairPlacementService.Result.REJECT_NEEDS_SOLID_BASE).isEmpty());
        assertFalse(HighchairPlacementService.message(HighchairPlacementService.Result.REJECT_ORIGIN_BLOCKED).isEmpty());
        assertFalse(HighchairPlacementService.message(HighchairPlacementService.Result.REJECT_PLAYER_IN_ORIGIN).isEmpty());
    }
}
