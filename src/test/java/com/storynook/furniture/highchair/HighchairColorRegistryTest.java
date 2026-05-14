package com.storynook.furniture.highchair;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HighchairColorRegistryTest {

    @AfterEach
    public void resetRegistry() {
        HighchairRegistry.resetForTesting();
    }

    @Test
    public void register_assignsSequentialIndexes() {
        int idx0 = HighchairRegistry.register("light_blue", "light_blue_highchair");
        int idx1 = HighchairRegistry.register("pink", "pink_highchair");
        assertEquals(0, idx0);
        assertEquals(1, idx1);
    }

    @Test
    public void colorIndex_returnsRegisteredIndex() {
        HighchairRegistry.register("light_blue", "light_blue_highchair");
        HighchairRegistry.register("pink", "pink_highchair");
        assertEquals(0, HighchairRegistry.colorIndex("light_blue"));
        assertEquals(1, HighchairRegistry.colorIndex("pink"));
    }

    @Test
    public void colorIndex_returnsMinusOneForUnknown() {
        HighchairRegistry.register("light_blue", "light_blue_highchair");
        assertEquals(-1, HighchairRegistry.colorIndex("lime"));
    }

    @Test
    public void duplicateRegister_isIdempotent() {
        int first = HighchairRegistry.register("light_blue", "light_blue_highchair");
        int second = HighchairRegistry.register("light_blue", "light_blue_highchair");
        assertEquals(first, second);
        assertEquals(1, HighchairRegistry.registeredCount());
    }

    @Test
    public void cmdFor_returns630000PlusIndex() {
        HighchairRegistry.register("light_blue", "light_blue_highchair");
        HighchairRegistry.register("pink", "pink_highchair");
        assertEquals(630000, HighchairRegistry.cmdFor(0));
        assertEquals(630001, HighchairRegistry.cmdFor(1));
    }

    @Test
    public void displayKeyFor_returnsRegisteredModelKey() {
        HighchairRegistry.register("pink", "pink_highchair");
        int idx = HighchairRegistry.colorIndex("pink");
        assertEquals("pink_highchair", HighchairRegistry.displayKeyFor(idx));
    }

    @Test
    public void colorKeyFor_returnsRegisteredKey() {
        HighchairRegistry.register("light_blue", "light_blue_highchair");
        assertEquals("light_blue", HighchairRegistry.colorKeyFor(0));
    }

    @Test
    public void carpetMaterialNameToColorIndex_mapsBukkitNames() {
        HighchairRegistry.register("light_blue", "light_blue_highchair");
        HighchairRegistry.register("pink", "pink_highchair");
        assertEquals(0, HighchairRegistry.carpetMaterialNameToColorIndex("LIGHT_BLUE_CARPET"));
        assertEquals(1, HighchairRegistry.carpetMaterialNameToColorIndex("PINK_CARPET"));
        assertEquals(-1, HighchairRegistry.carpetMaterialNameToColorIndex("LIME_CARPET"));
        assertEquals(-1, HighchairRegistry.carpetMaterialNameToColorIndex("OAK_SLAB"));
    }
}
