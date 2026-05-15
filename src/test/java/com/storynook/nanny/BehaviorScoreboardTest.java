package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class BehaviorScoreboardTest {

    private AtomicLong fakeNow;
    private NannyData data;
    private UUID nanny;
    private UUID ward;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        fakeNow = new AtomicLong(1_000_000_000L);
        nanny = UUID.randomUUID();
        ward = UUID.randomUUID();
        data = new NannyData(nanny, UUID.randomUUID(), "Test", null);
    }

    @AfterEach
    public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void recordAddsDelta() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        sb.record(data, ward, "test", -15);
        assertEquals(-15, sb.getScore(data, ward));
    }

    @Test
    public void scoreClampsAtNegativeFloor() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        for (int i = 0; i < 10; i++) sb.record(data, ward, "test", -15);
        assertEquals(-100, sb.getScore(data, ward));
    }

    @Test
    public void scoreClampsAtPositiveCeiling() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        for (int i = 0; i < 10; i++) sb.record(data, ward, "test", 15);
        assertEquals(100, sb.getScore(data, ward));
    }

    @Test
    public void streakIsBidirectional() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        sb.record(data, ward, "test", -5);
        sb.record(data, ward, "test", -5);
        sb.record(data, ward, "test", -3);
        assertEquals(-13, sb.getStreak(data, ward));
    }

    @Test
    public void streakHalvesEveryRealMinute() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        sb.record(data, ward, "test", 20);
        assertEquals(20, sb.getStreak(data, ward));

        // Advance 1 real minute — streak should halve
        fakeNow.addAndGet(60_000L);
        sb.record(data, ward, "test", 0); // a 0-delta record forces decay
        assertEquals(10, sb.getStreak(data, ward));

        // Another minute — halve again
        fakeNow.addAndGet(60_000L);
        sb.record(data, ward, "test", 0);
        assertEquals(5, sb.getStreak(data, ward));
    }

    @Test
    public void sycophancyDampensPositiveWhenScoreNegativeAndStreakPositive() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        // Drop score deep negative
        for (int i = 0; i < 4; i++) sb.record(data, ward, "test", -10);  // score -40
        // Then a positive streak builds
        for (int i = 0; i < 5; i++) sb.record(data, ward, "test", 5);    // streak +25, sycophancy gate trips
        int beforeScore = sb.getScore(data, ward);
        // Next positive should be halved (sycophancy gate: score < 0 AND streak > +20 → dampen 50%)
        sb.record(data, ward, "test", 10);
        // delta 10 → applied as 5 → score moves +5 not +10
        assertEquals(beforeScore + 5, sb.getScore(data, ward));
    }

    @Test
    public void sycophancyDoesNotDampenNegative() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        for (int i = 0; i < 4; i++) sb.record(data, ward, "test", -10);
        for (int i = 0; i < 5; i++) sb.record(data, ward, "test", 5);
        int beforeScore = sb.getScore(data, ward);
        sb.record(data, ward, "test", -10);
        assertEquals(beforeScore - 10, sb.getScore(data, ward));
    }

    @Test
    public void unrecordedWardReadsZero() {
        BehaviorScoreboard sb = new BehaviorScoreboard(fakeNow::get);
        assertEquals(0, sb.getScore(data, ward));
        assertEquals(0, sb.getStreak(data, ward));
    }
}
