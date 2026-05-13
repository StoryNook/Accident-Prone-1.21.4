package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KnockbackTrackerTest {

    private KnockbackTracker tracker;

    @BeforeEach
    public void setUp() {
        tracker = new KnockbackTracker();
    }

    @Test
    public void mobAttackCountsAsKnockback() {
        assertTrue(KnockbackTracker.isKnockbackCause(DamageCause.ENTITY_ATTACK));
    }

    @Test
    public void projectileCountsAsKnockback() {
        assertTrue(KnockbackTracker.isKnockbackCause(DamageCause.PROJECTILE));
    }

    @Test
    public void explosionCountsAsKnockback() {
        assertTrue(KnockbackTracker.isKnockbackCause(DamageCause.BLOCK_EXPLOSION));
        assertTrue(KnockbackTracker.isKnockbackCause(DamageCause.ENTITY_EXPLOSION));
    }

    @Test
    public void fallDoesNotCountAsKnockback() {
        assertFalse(KnockbackTracker.isKnockbackCause(DamageCause.FALL));
    }

    @Test
    public void burningDoesNotCountAsKnockback() {
        assertFalse(KnockbackTracker.isKnockbackCause(DamageCause.FIRE));
        assertFalse(KnockbackTracker.isKnockbackCause(DamageCause.LAVA));
    }

    @Test
    public void poisonDoesNotCountAsKnockback() {
        assertFalse(KnockbackTracker.isKnockbackCause(DamageCause.POISON));
    }

    @Test
    public void stampAndQuery() {
        UUID p = UUID.randomUUID();
        tracker.stamp(p, 100L);
        assertEquals(100L, tracker.lastDamageTick(p));
    }

    @Test
    public void unknownPlayerReturnsLongMin() {
        assertEquals(Long.MIN_VALUE, tracker.lastDamageTick(UUID.randomUUID()));
    }

    @Test
    public void pruneRemovesOldEntries() {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        tracker.stamp(old, 50L);
        tracker.stamp(recent, 200L);
        tracker.prune(220L, 30L);
        assertEquals(Long.MIN_VALUE, tracker.lastDamageTick(old));
        assertEquals(200L, tracker.lastDamageTick(recent));
    }
}
