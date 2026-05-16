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
 * Evaluate-only tests for {@link EquipDiaperTask}. See {@link ChangeTaskTest}
 * for the rationale behind the test seam (no MockBukkit.load(Plugin.class)).
 */
public class EquipDiaperTaskTest {

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
    public void evaluate_returnsCandidateWhenUnderwearTypeZero() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setUnderwearType(0);
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        EquipDiaperTask task = new EquipDiaperTask(statsMap::get, null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNotNull(c);
        assertEquals(65, c.priority());
    }

    @Test
    public void evaluate_returnsNullWhenAlreadyInDiaper() {
        PlayerMock ward = server.addPlayer();
        PlayerStats stats = new PlayerStats();
        stats.setUnderwearType(2); // pull-up
        statsMap.put(ward.getUniqueId(), stats);

        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        EquipDiaperTask task = new EquipDiaperTask(statsMap::get, null, null);

        assertNull(task.evaluate(null, data, ward));
    }
}
