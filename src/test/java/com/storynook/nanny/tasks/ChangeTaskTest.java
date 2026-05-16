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
 * Evaluate-only tests for {@link ChangeTask}. The act() body requires live world
 * state (NannyEntity + NannyInventoryManager) and is covered by manual end-to-end
 * verification post-merge.
 *
 * <p>We avoid {@code MockBukkit.load(Plugin.class)} (it triggers Plugin.onEnable's
 * recipe registration which hits MockBukkit's UnsafeValues limitations and
 * silently skips the test). Instead we use the test-only ChangeTask constructor
 * that accepts an explicit {@code Function<UUID, PlayerStats>} stats resolver.
 */
public class ChangeTaskTest {

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
    public void evaluate_returnsCandidateWhenWardWetnessAboveThreshold() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setDiaperWetness(85); // above default changeThreshold of 70
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        ChangeTask task = new ChangeTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(ward.getUniqueId(), c.ward().getUniqueId());
        // Priority 90 base; no severity bonus because wetness < 100
        assertEquals(90, c.priority());
    }

    @Test
    public void evaluate_returnsNullWhenWardBelowThreshold() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setDiaperWetness(20);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        ChangeTask task = new ChangeTask(statsMap::get, null, null);

        assertNull(task.evaluate(null, data, ward));
    }

    @Test
    public void evaluate_emergencyPriorityWhenWetnessAt100() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setDiaperWetness(100);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        ChangeTask task = new ChangeTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(110, c.priority()); // 90 + 20 severity
    }
}
