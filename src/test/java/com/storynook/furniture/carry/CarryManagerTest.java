package com.storynook.furniture.carry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CarryManagerTest {

    private CarryManager carry;

    @BeforeEach
    public void setUp() {
        carry = new CarryManager();
    }

    @Test
    public void notCarryingByDefault() {
        UUID c = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        assertFalse(carry.isCarrying(c));
        assertFalse(carry.isBeingCarried(w));
        assertNull(carry.wardOf(c));
        assertNull(carry.caregiverOf(w));
    }

    @Test
    public void recordCarryAndQuery() {
        UUID c = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        carry.recordCarry(c, w);
        assertTrue(carry.isCarrying(c));
        assertTrue(carry.isBeingCarried(w));
        assertEquals(w, carry.wardOf(c));
        assertEquals(c, carry.caregiverOf(w));
    }

    @Test
    public void clearCarryRemovesBoth() {
        UUID c = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        carry.recordCarry(c, w);
        carry.clearCarry(c);
        assertFalse(carry.isCarrying(c));
        assertFalse(carry.isBeingCarried(w));
    }

    @Test
    public void clearByWard() {
        UUID c = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        carry.recordCarry(c, w);
        carry.clearCarryByWard(w);
        assertFalse(carry.isCarrying(c));
        assertFalse(carry.isBeingCarried(w));
    }

    @Test
    public void cooldownNotSetByDefault() {
        UUID c = UUID.randomUUID();
        assertFalse(carry.isOnCooldown(c, 100L));
    }

    @Test
    public void cooldownExpiresAtTick() {
        UUID c = UUID.randomUUID();
        carry.setCooldown(c, 200L);
        assertTrue(carry.isOnCooldown(c, 100L));
        assertTrue(carry.isOnCooldown(c, 199L));
        assertFalse(carry.isOnCooldown(c, 200L));
        assertFalse(carry.isOnCooldown(c, 201L));
    }
}
