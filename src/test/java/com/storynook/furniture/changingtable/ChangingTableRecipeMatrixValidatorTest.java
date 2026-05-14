package com.storynook.furniture.changingtable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class ChangingTableRecipeMatrixValidatorTest {

    private static final Predicate<String> IS_SLAB = m ->
            Set.of("OAK_SLAB","BIRCH_SLAB","DARK_OAK_SLAB").contains(m);

    private static final Function<String, Integer> COLOR_LOOKUP = m -> {
        if ("WHITE_CARPET".equals(m)) return 0;
        if ("PINK_CARPET".equals(m))  return 15;
        return -1;
    };

    private static String[] m(String... s) {
        String[] out = new String[9];
        System.arraycopy(s, 0, out, 0, Math.min(9, s.length));
        return out;
    }

    @Test
    public void validMatrixSixSticksTwoSlabsOneCarpetReturnsColorIndex() {
        assertEquals(0, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","OAK_SLAB","WHITE_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
        assertEquals(15, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","DARK_OAK_SLAB","BIRCH_SLAB","PINK_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void fiveSticksFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","OAK_SLAB","OAK_SLAB","WHITE_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void oneSlabFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","WHITE_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void nonWoodSlabFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","COBBLESTONE_SLAB","COBBLESTONE_SLAB","WHITE_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void twoCarpetsFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","WHITE_CARPET","PINK_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void zeroCarpetsFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","OAK_SLAB"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void unregisteredCarpetColorFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","OAK_SLAB","LIME_CARPET"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void extraJunkItemFails() {
        // 10 entries — only first 9 will be used by m(), so junk DIRT would fall outside.
        // Use a junk slot inside the 9: replace one carpet with DIRT + extra carpet.
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            m("STICK","STICK","STICK","STICK","STICK","STICK","OAK_SLAB","WHITE_CARPET","DIRT"),
            IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void emptyMatrixFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            new String[9], IS_SLAB, COLOR_LOOKUP));
    }

    @Test
    public void nullMatrixFails() {
        assertEquals(-1, ChangingTableRecipeMatrixValidator.colorIndexFor(
            null, IS_SLAB, COLOR_LOOKUP));
    }
}
