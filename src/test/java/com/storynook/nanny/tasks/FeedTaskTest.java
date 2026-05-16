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
 * Evaluate-only tests for {@link FeedTask}. Default {@code feedThreshold} is 14
 * per {@link com.storynook.nanny.NannyData}.
 */
public class FeedTaskTest {

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
    public void evaluate_returnsCandidateWhenFoodBelowThreshold() {
        PlayerMock ward = server.addPlayer();
        ward.setFoodLevel(10);
        statsMap.put(ward.getUniqueId(), new PlayerStats());

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        FeedTask task = new FeedTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(75, c.priority());
    }

    @Test
    public void evaluate_returnsCandidateWithSeverityBonusWhenFoodAt4OrBelow() {
        PlayerMock ward = server.addPlayer();
        ward.setFoodLevel(4);
        statsMap.put(ward.getUniqueId(), new PlayerStats());

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        FeedTask task = new FeedTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(85, c.priority()); // 75 + 10 severity
    }

    @Test
    public void evaluate_returnsNullWhenFoodAtOrAboveThreshold() {
        PlayerMock ward = server.addPlayer();
        ward.setFoodLevel(20);
        statsMap.put(ward.getUniqueId(), new PlayerStats());

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        FeedTask task = new FeedTask(statsMap::get, null, null);

        assertNull(task.evaluate(null, data, ward));
    }
}
