package com.storynook.nanny.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.storynook.nanny.NannyData;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Evaluate-only tests for {@link CribPlacementTask}. The crib-lookup branch
 * requires a real {@link com.storynook.furniture.CribRegistry} (resolved via
 * {@link com.storynook.Plugin}) and is exercised live. These tests cover the
 * cheap gates: foodLevel threshold + null-guards.
 */
public class CribPlacementTaskTest {

    private ServerMock server;

    @BeforeEach
    public void setup() {
        server = MockBukkit.mock();
    }

    @AfterEach
    public void teardown() {
        MockBukkit.unmock();
    }

    @Test
    public void evaluate_returnsNullWhenFoodLevelAboveThreshold() {
        PlayerMock ward = server.addPlayer();
        ward.setFoodLevel(15);
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        CribPlacementTask task = new CribPlacementTask(null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNull(c, "crib placement should not trigger when ward is awake (food>6)");
    }

    @Test
    public void evaluate_returnsNullWhenEngineUnavailable() {
        PlayerMock ward = server.addPlayer();
        ward.setFoodLevel(4);
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        CribPlacementTask task = new CribPlacementTask(null, null);

        // Engine is null so we can't dereference the registry; evaluate must
        // gracefully return null instead of throwing NPE.
        Candidate c = task.evaluate(null, data, ward);
        assertNull(c);
    }

    @Test
    public void id_isCrib() {
        CribPlacementTask task = new CribPlacementTask(null, null);
        assertEquals("crib", task.id());
    }
}
