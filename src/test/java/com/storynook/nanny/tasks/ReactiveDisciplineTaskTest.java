package com.storynook.nanny.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyData.MoodTier;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Evaluate-only tests for {@link ReactiveDisciplineTask}. Verifies the
 * mood-modifier priority math and the boundWardUUID filter.
 */
public class ReactiveDisciplineTaskTest {

    private ServerMock server;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void evaluate_returnsCandidateForBoundWard() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "Nanny", null);
        ReactiveDisciplineTask task = new ReactiveDisciplineTask(
                null, null, ward.getUniqueId(),
                ReactiveDisciplineTask.Severity.MODERATE, "punched");

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        // CARING default mood → 100 - 10 = 90
        assertEquals(90, c.priority());
        assertEquals(ward.getUniqueId(), c.ward().getUniqueId());
        assertEquals("punched", c.reasonTag());
    }

    @Test
    public void evaluate_returnsNullForOtherWard() {
        PlayerMock bound = server.addPlayer();
        PlayerMock other = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), bound.getUniqueId(), "Nanny", null);
        ReactiveDisciplineTask task = new ReactiveDisciplineTask(
                null, null, bound.getUniqueId(),
                ReactiveDisciplineTask.Severity.SEVERE, "cursed");

        assertNull(task.evaluate(null, data, other));
    }

    @Test
    public void moodModifier_sweetSubtracts15() {
        NannyData data = new NannyData(UUID.randomUUID(), UUID.randomUUID(), "Nanny", null);
        data.setMoodTier(MoodTier.SWEET);
        assertEquals(-15, ReactiveDisciplineTask.moodModifier(data));
    }

    @Test
    public void moodModifier_caringSubtracts10() {
        NannyData data = new NannyData(UUID.randomUUID(), UUID.randomUUID(), "Nanny", null);
        data.setMoodTier(MoodTier.CARING);
        assertEquals(-10, ReactiveDisciplineTask.moodModifier(data));
    }

    @Test
    public void moodModifier_strictReturnsZero() {
        NannyData data = new NannyData(UUID.randomUUID(), UUID.randomUUID(), "Nanny", null);
        data.setMoodTier(MoodTier.STRICT);
        assertEquals(0, ReactiveDisciplineTask.moodModifier(data));
    }

    @Test
    public void moodModifier_wardenAdds10() {
        NannyData data = new NannyData(UUID.randomUUID(), UUID.randomUUID(), "Nanny", null);
        data.setMoodTier(MoodTier.WARDEN);
        assertEquals(10, ReactiveDisciplineTask.moodModifier(data));
    }

    @Test
    public void moodModifier_nullDataReturnsZero() {
        assertEquals(0, ReactiveDisciplineTask.moodModifier(null));
    }

    @Test
    public void evaluate_wardenWardCanReach110() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "Nanny", null);
        data.setMoodTier(MoodTier.WARDEN);
        ReactiveDisciplineTask task = new ReactiveDisciplineTask(
                null, null, ward.getUniqueId(),
                ReactiveDisciplineTask.Severity.SEVERE, "punched");

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(110, c.priority());
    }
}
