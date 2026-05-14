package com.storynook.furniture.changingtable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.storynook.DesignRegistry;

public class ChangingTableInventoryFilterTest {

    @Test
    public void airIsStorable() {
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(true, false, 0, false, null));
    }

    @Test
    public void itemWithoutMetaOrCmdIsBlocked() {
        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, false, 0, false, null));
        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, true, 0, false, null));
    }

    @Test
    public void usedVariantBlocked() {
        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, true, 626022, true, null));
    }

    @Test
    public void legacyCleanCmdsAllowedExceptUnderwear() {
        // 626001 = thick diaper, 626003 = pull-up, 626009 = diaper → allow
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(false, true, 626001, false, null));
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(false, true, 626003, false, null));
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(false, true, 626009, false, null));
        // 626002 = underwear → block
        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, true, 626002, false, null));
    }

    @Test
    public void designRegistryCategoryGatesAccess() {
        // Register temporary test designs in categories 0 (blocked) and 1 (allowed).
        // CMDs 999001-999004 chosen to avoid collision with real designs.
        DesignRegistry.register(0, 90, 999001, 999011, 999021, 999031, "test-cat0", "Test Cat0");
        DesignRegistry.register(1, 90, 999002, 999012, 999022, 999032, "test-cat1", "Test Cat1");
        DesignRegistry.register(3, 90, 999003, 999013, 999023, 999033, "test-cat3", "Test Cat3");

        DesignRegistry.DesignDef defCat0 = DesignRegistry.findByCleanCmd(999001);
        DesignRegistry.DesignDef defCat1 = DesignRegistry.findByCleanCmd(999002);
        DesignRegistry.DesignDef defCat3 = DesignRegistry.findByCleanCmd(999003);
        assertNotNull(defCat0, "test setup: cat0 design should be registered");
        assertNotNull(defCat1, "test setup: cat1 design should be registered");
        assertNotNull(defCat3, "test setup: cat3 design should be registered");

        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, true, 999001, false, defCat0));
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(false, true, 999002, false, defCat1));
        assertTrue(ChangingTableInventoryFilter.isStorableByFields(false, true, 999003, false, defCat3));
    }

    @Test
    public void unknownCmdBlockedWhenNoDesignRegistryMatch() {
        // CMD 628000 = stuffie. Pass null for def since the filter's by-fields
        // overload doesn't do its own DesignRegistry lookup.
        assertFalse(ChangingTableInventoryFilter.isStorableByFields(false, true, 628000, false, null));
    }
}
