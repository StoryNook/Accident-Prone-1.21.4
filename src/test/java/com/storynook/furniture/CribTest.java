package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CribTest {

    @Test
    public void recordExposesAllFields() {
        UUID cribId = UUID.randomUUID();
        UUID displayUuid = UUID.randomUUID();
        UUID interactionUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();

        Crib c = new Crib(
            cribId,
            "world", 10, 64, -20,
            90.0f,
            3,
            10, 64, -20,
            10, 65, -19,
            displayUuid, interactionUuid, ownerUuid
        );

        assertEquals(cribId, c.id());
        assertEquals("world", c.worldName());
        assertEquals(10, c.originX());
        assertEquals(64, c.originY());
        assertEquals(-20, c.originZ());
        assertEquals(90.0f, c.yaw(), 0.0001f);
        assertEquals(3, c.woodVariant());
        assertEquals(10, c.floorBlockX());
        assertEquals(64, c.floorBlockY());
        assertEquals(-20, c.floorBlockZ());
        assertEquals(10, c.bedHeadX());
        assertEquals(65, c.bedHeadY());
        assertEquals(-19, c.bedHeadZ());
        assertEquals(displayUuid, c.displayUuid());
        assertEquals(interactionUuid, c.interactionUuid());
        assertEquals(ownerUuid, c.ownerUuid());
    }

    @Test
    public void bedFootDerivedFromYawSouth() {
        // yaw 0 = facing south; bed head/foot extends along +Z
        Crib c = new Crib(
            UUID.randomUUID(),
            "world", 10, 64, -20,
            0.0f, 0,
            10, 64, -20,
            10, 65, -20,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        assertEquals(10, c.bedFootX());
        assertEquals(65, c.bedFootY());
        assertEquals(-19, c.bedFootZ());
    }

    @Test
    public void bedFootDerivedFromYawWest() {
        // yaw 90 = facing west; bed head/foot extends along -X
        Crib c = new Crib(
            UUID.randomUUID(),
            "world", 10, 64, -20,
            90.0f, 0,
            10, 64, -20,
            10, 65, -20,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        assertEquals(9, c.bedFootX());
        assertEquals(65, c.bedFootY());
        assertEquals(-20, c.bedFootZ());
    }

    @Test
    public void containmentBoxNonNull() {
        Crib c = new Crib(
            UUID.randomUUID(),
            "world", 10, 64, -20,
            0.0f, 0,
            10, 64, -20,
            10, 65, -20,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
        assertNotNull(c.containmentBox());
    }
}
