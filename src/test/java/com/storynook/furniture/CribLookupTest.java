package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CribLookupTest {

    private CribRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new CribRegistry();
    }

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

    @Test
    public void findNearestNewCribReturnsClosest() {
        Crib far = makeCrib(100, 64, 0);
        Crib near = makeCrib(2, 64, 0);
        registry.register(far);
        registry.register(near);

        CribLookupResult result = registry.findNearestNewCrib("world", 0, 64, 0, 50.0);
        assertTrue(result instanceof CribLookupResult.NewCribResult);
        assertEquals(near, ((CribLookupResult.NewCribResult) result).crib());
    }

    @Test
    public void findNearestNewCribOutOfRangeReturnsNone() {
        Crib c = makeCrib(100, 64, 0);
        registry.register(c);
        CribLookupResult result = registry.findNearestNewCrib("world", 0, 64, 0, 10.0);
        assertTrue(result instanceof CribLookupResult.None);
    }

    @Test
    public void findNearestNewCribEmptyRegistryReturnsNone() {
        CribLookupResult result = registry.findNearestNewCrib("world", 0, 64, 0, 50.0);
        assertTrue(result instanceof CribLookupResult.None);
    }

    @Test
    public void findNearestNewCribDifferentWorldReturnsNone() {
        Crib c = makeCrib(0, 64, 0);
        registry.register(c);
        CribLookupResult result = registry.findNearestNewCrib("nether", 0, 64, 0, 50.0);
        assertTrue(result instanceof CribLookupResult.None);
    }
}
