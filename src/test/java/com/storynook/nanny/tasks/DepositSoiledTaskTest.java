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
 * Evaluate-only tests for {@link DepositSoiledTask}. Like
 * {@link RefillBottleTaskTest}, the pail-scan branch requires a real
 * {@link com.storynook.nanny.NannyEntity} in a Bukkit world and is exercised
 * live. These tests cover the cheap null-guard and the priority/range
 * constants documented by the spec.
 */
public class DepositSoiledTaskTest {

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
    public void evaluate_returnsNullWhenNannyIsNull() {
        PlayerMock ward = server.addPlayer();
        NannyData data = new NannyData(UUID.randomUUID(), ward.getUniqueId(), "TN", null);
        DepositSoiledTask task = new DepositSoiledTask(null, null);

        Candidate c = task.evaluate(null, data, ward);
        assertNull(c, "evaluate must return null when nanny entity unavailable");
    }

    @Test
    public void id_isDeposit() {
        DepositSoiledTask task = new DepositSoiledTask(null, null);
        assertEquals("deposit", task.id());
    }

    @Test
    public void actionRangeBlocks_isTwo() {
        // pailDistSq > 4.0 in legacy code → sqrt(4) = 2.0
        DepositSoiledTask task = new DepositSoiledTask(null, null);
        assertEquals(2.0, task.actionRangeBlocks(), 0.0001);
    }
}
