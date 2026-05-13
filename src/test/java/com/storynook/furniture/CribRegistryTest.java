package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CribRegistryTest {

    private CribRegistry registry;

    private static Crib makeCrib(int x, int y, int z) {
        return new Crib(
            UUID.randomUUID(),
            "world", x, y, z,
            0.0f, 0,
            x, y - 1, z,
            x, y, z + 1,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );
    }

    @BeforeEach
    public void setUp() {
        registry = new CribRegistry();
    }

    @Test
    public void registerAndFindById() {
        Crib c = makeCrib(0, 64, 0);
        registry.register(c);
        assertEquals(c, registry.findById(c.id()));
    }

    @Test
    public void findByIdMissingReturnsNull() {
        assertNull(registry.findById(UUID.randomUUID()));
    }

    @Test
    public void findByFloorBlockKey() {
        Crib c = makeCrib(10, 64, -20);
        registry.register(c);
        assertEquals(c, registry.findByFloorBlock("world", 10, 63, -20));
    }

    @Test
    public void findByFloorBlockMissReturnsNull() {
        Crib c = makeCrib(10, 64, -20);
        registry.register(c);
        assertNull(registry.findByFloorBlock("world", 999, 63, -20));
    }

    @Test
    public void unregisterRemovesFromAllIndices() {
        Crib c = makeCrib(10, 64, -20);
        registry.register(c);
        assertNotNull(registry.findById(c.id()));

        registry.unregister(c.id());
        assertNull(registry.findById(c.id()));
        assertNull(registry.findByFloorBlock("world", 10, 63, -20));
        assertEquals(0, registry.findByChunk("world", 0, -2).size());
    }

    @Test
    public void findByChunkGroupsByCoordinates() {
        // chunk (0, 0) covers x=0..15, z=0..15
        Crib a = makeCrib(2, 64, 5);
        Crib b = makeCrib(15, 64, 15);
        Crib c = makeCrib(16, 64, 0);   // different chunk
        registry.register(a);
        registry.register(b);
        registry.register(c);

        assertEquals(2, registry.findByChunk("world", 0, 0).size());
        assertEquals(1, registry.findByChunk("world", 1, 0).size());
    }

    @Test
    public void wardContainmentReverseLookup() {
        Crib c = makeCrib(0, 64, 0);
        registry.register(c);
        UUID ward = UUID.randomUUID();

        registry.containWard(ward, c.id());
        assertEquals(c.id(), registry.cribIdForWard(ward));

        registry.releaseWard(ward);
        assertNull(registry.cribIdForWard(ward));
    }

    @Test
    public void containedWardsIterable() {
        Crib c = makeCrib(0, 64, 0);
        registry.register(c);
        UUID ward1 = UUID.randomUUID();
        UUID ward2 = UUID.randomUUID();

        registry.containWard(ward1, c.id());
        registry.containWard(ward2, c.id());
        assertEquals(2, registry.containedWards().size());
        assertTrue(registry.containedWards().contains(ward1));
        assertTrue(registry.containedWards().contains(ward2));
    }
}
