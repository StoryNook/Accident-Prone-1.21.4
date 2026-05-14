package com.storynook.furniture.highchair;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HighchairRegistryTest {

    @AfterEach
    public void reset() {
        HighchairRegistry.resetForTesting();
    }

    private static Highchair sample(int x, int y, int z) {
        return new Highchair(
            UUID.randomUUID(),
            "world",
            x, y, z,
            0.0f, 0,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
    }

    @Test
    public void registerThenFindById_returnsSame() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair h = sample(10, 64, 20);
        reg.register(h);
        assertSame(h, reg.findById(h.id()));
    }

    @Test
    public void findByOriginBlock_returnsRegisteredHighchair() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair h = sample(10, 64, 20);
        reg.register(h);
        assertSame(h, reg.findByOriginBlock("world", 10, 64, 20));
    }

    @Test
    public void findByOriginBlock_returnsNullForUnregistered() {
        HighchairRegistry reg = new HighchairRegistry();
        assertNull(reg.findByOriginBlock("world", 0, 0, 0));
    }

    @Test
    public void unregister_removesIt() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair h = sample(10, 64, 20);
        reg.register(h);
        reg.unregister(h.id());
        assertNull(reg.findById(h.id()));
        assertNull(reg.findByOriginBlock("world", 10, 64, 20));
    }

    @Test
    public void findByChunk_returnsAllInChunk() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair a = sample(10, 64, 20);
        Highchair b = sample(11, 64, 21);
        Highchair c = sample(50, 64, 50);
        reg.register(a);
        reg.register(b);
        reg.register(c);
        assertEquals(2, reg.findByChunk("world", 0, 1).size());
    }

    @Test
    public void unregisterChunk_clearsAllInChunk() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair a = sample(10, 64, 20);
        Highchair b = sample(11, 64, 21);
        reg.register(a);
        reg.register(b);
        reg.unregisterChunk("world", 0, 1);
        assertNull(reg.findById(a.id()));
        assertNull(reg.findById(b.id()));
    }

    @Test
    public void seatThenReleaseWard_clearsOccupancy() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair h = sample(0, 64, 0);
        reg.register(h);
        UUID ward = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        reg.recordSeating(ward, h.id(), HighchairRegistry.LockMode.LOCKED, placer);

        assertEquals(h.id(), reg.highchairIdForWard(ward));
        assertEquals(HighchairRegistry.LockMode.LOCKED, reg.lockModeForWard(ward));
        assertEquals(placer, reg.placerForWard(ward));
        assertTrue(reg.containedWards().contains(ward));

        reg.clearSeating(ward);

        assertNull(reg.highchairIdForWard(ward));
        assertNull(reg.lockModeForWard(ward));
        assertNull(reg.placerForWard(ward));
        assertFalse(reg.containedWards().contains(ward));
    }

    @Test
    public void unregisterHighchair_clearsOccupancyOfWardsInIt() {
        HighchairRegistry reg = new HighchairRegistry();
        Highchair h = sample(0, 64, 0);
        reg.register(h);
        UUID ward = UUID.randomUUID();
        reg.recordSeating(ward, h.id(), HighchairRegistry.LockMode.SELF, null);

        reg.unregister(h.id());

        assertNull(reg.highchairIdForWard(ward));
    }
}
