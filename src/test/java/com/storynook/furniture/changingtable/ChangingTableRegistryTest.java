package com.storynook.furniture.changingtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChangingTableRegistryTest {

    @BeforeEach
    public void resetRegistry() {
        ChangingTableRegistry.resetForTest();
    }

    @Test
    public void initRegistersSixteenColorsInOrder() {
        ChangingTableRegistry.init();
        assertEquals(0,  ChangingTableRegistry.colorIndex("white"));
        assertEquals(15, ChangingTableRegistry.colorIndex("pink"));
        assertEquals("changingtable_white", ChangingTableRegistry.displayKeyFor(0));
        assertEquals("changingtable_pink",  ChangingTableRegistry.displayKeyFor(15));
    }

    @Test
    public void cmdForReturnsBaseOffsetSums() {
        ChangingTableRegistry.init();
        assertEquals(630100, ChangingTableRegistry.cmdFor(0));
        assertEquals(630115, ChangingTableRegistry.cmdFor(15));
    }

    @Test
    public void colorIndexReturnsNegativeOneForUnregistered() {
        ChangingTableRegistry.init();
        assertEquals(-1, ChangingTableRegistry.colorIndex("dark_oak"));
        assertEquals(-1, ChangingTableRegistry.colorIndex("bogus"));
    }

    @Test
    public void registerIsIdempotent() {
        int first  = ChangingTableRegistry.register("teal", "changingtable_teal");
        int second = ChangingTableRegistry.register("teal", "changingtable_teal");
        assertEquals(first, second);
    }

    @Test
    public void colorIndexFromCarpetParsesMaterialName() {
        ChangingTableRegistry.init();
        assertEquals(10, ChangingTableRegistry.colorIndexFromCarpet(Material.LIGHT_BLUE_CARPET));
        assertEquals(15, ChangingTableRegistry.colorIndexFromCarpet(Material.PINK_CARPET));
        assertEquals(-1, ChangingTableRegistry.colorIndexFromCarpet(Material.STICK));
    }

    @Test
    public void colorKeysListIsReadOnlyOrderedView() {
        ChangingTableRegistry.init();
        assertEquals(16, ChangingTableRegistry.colorKeys().size());
        assertEquals("white", ChangingTableRegistry.colorKeys().get(0));
        assertThrows(UnsupportedOperationException.class, () -> {
            ChangingTableRegistry.colorKeys().add("rogue");
        });
    }
}
