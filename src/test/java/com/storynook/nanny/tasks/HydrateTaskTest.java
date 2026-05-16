package com.storynook.nanny.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.nanny.NannyData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Evaluate-only tests for {@link HydrateTask}. Default {@code hydrationThreshold}
 * is 30 per {@link com.storynook.nanny.NannyData}.
 */
public class HydrateTaskTest {

    private ServerMock server;
    private Map<UUID, PlayerStats> statsMap;

    @BeforeEach
    public void setup() {
        server = MockBukkit.mock();
        statsMap = new HashMap<>();
    }

    @AfterEach
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void evaluate_returnsCandidateWhenHydrationLow() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setHydration(25);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        HydrateTask task = new HydrateTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(70, c.priority());
    }

    @Test
    public void evaluate_returnsCandidateWithSeverityBonusWhenHydrationVeryLow() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setHydration(5);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        HydrateTask task = new HydrateTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(75, c.priority()); // 70 + 5 severity
    }

    @Test
    public void evaluate_returnsNullWhenHydrated() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setHydration(90);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        HydrateTask task = new HydrateTask(statsMap::get, null, null);

        assertNull(task.evaluate(null, data, ward));
    }
}
