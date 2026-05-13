package com.storynook.furniture.carry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StruggleStateTest {

    private StruggleState state;

    @BeforeEach
    public void setUp() {
        state = new StruggleState();
    }

    @Test
    public void noBurstAtStart() {
        assertFalse(state.recordEvent(0L));
    }

    @Test
    public void fourEventsIn40TicksFiresBurst() {
        assertFalse(state.recordEvent(0L));
        assertFalse(state.recordEvent(10L));
        assertFalse(state.recordEvent(20L));
        assertTrue(state.recordEvent(35L));
    }

    @Test
    public void threeEventsIn40TicksDoesNotFire() {
        state.recordEvent(0L);
        state.recordEvent(10L);
        assertFalse(state.recordEvent(20L));
    }

    @Test
    public void fourEventsIn41TicksDoesNotFire() {
        state.recordEvent(0L);
        state.recordEvent(10L);
        state.recordEvent(20L);
        assertFalse(state.recordEvent(41L));
    }

    @Test
    public void cooldownBlocksSecondBurst() {
        state.recordEvent(0L);
        state.recordEvent(10L);
        state.recordEvent(20L);
        assertTrue(state.recordEvent(35L));
        // Burst lasts until 35+100=135; cooldown until 135+100=235
        assertFalse(state.recordEvent(140L));
        assertFalse(state.recordEvent(150L));
        assertFalse(state.recordEvent(160L));
        assertFalse(state.recordEvent(170L));
    }

    @Test
    public void newBurstAfterCooldown() {
        state.recordEvent(0L);
        state.recordEvent(10L);
        state.recordEvent(20L);
        assertTrue(state.recordEvent(35L));
        assertFalse(state.recordEvent(240L));
        assertFalse(state.recordEvent(245L));
        assertFalse(state.recordEvent(250L));
        assertTrue(state.recordEvent(255L));
    }
}
