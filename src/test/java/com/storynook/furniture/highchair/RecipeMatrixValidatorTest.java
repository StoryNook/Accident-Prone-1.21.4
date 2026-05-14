package com.storynook.furniture.highchair;

import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RecipeMatrixValidatorTest {

    private static final Predicate<String> IS_WOODEN_SLAB = mat ->
        Set.of("OAK_SLAB", "BIRCH_SLAB", "DARK_OAK_SLAB", "SPRUCE_SLAB",
               "ACACIA_SLAB", "JUNGLE_SLAB", "MANGROVE_SLAB", "CHERRY_SLAB",
               "BAMBOO_SLAB", "CRIMSON_SLAB", "WARPED_SLAB").contains(mat);

    private static final Function<String, Integer> COLOR_INDEX = mat -> {
        Map<String, Integer> map = Map.of(
            "LIGHT_BLUE_CARPET", 0,
            "PINK_CARPET", 1
        );
        Integer v = map.get(mat);
        return v == null ? -1 : v;
    };

    private int validate(String[] matrix) {
        return RecipeMatrixValidator.colorIndexFor(matrix, IS_WOODEN_SLAB, COLOR_INDEX);
    }

    @Test
    public void fourSticksOakSlabLightBlueCarpet_returnsZero() {
        String[] m = {
            "STICK", "STICK", null,
            "STICK", "STICK", "OAK_SLAB",
            "LIGHT_BLUE_CARPET", null, null
        };
        assertEquals(0, validate(m));
    }

    @Test
    public void fourSticksDarkOakSlabPinkCarpet_returnsOne() {
        String[] m = {
            "STICK", "STICK", "STICK",
            "STICK", "DARK_OAK_SLAB", "PINK_CARPET",
            null, null, null
        };
        assertEquals(1, validate(m));
    }

    @Test
    public void unregisteredCarpetColor_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "OAK_SLAB", "LIME_CARPET",
            null, null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void threeSticks_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK",
            "OAK_SLAB", "LIGHT_BLUE_CARPET",
            null, null, null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void nonWoodenSlab_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "COBBLESTONE_SLAB", "LIGHT_BLUE_CARPET",
            null, null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void twoSlabs_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "OAK_SLAB", "OAK_SLAB", "LIGHT_BLUE_CARPET",
            null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void noCarpet_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "OAK_SLAB", null, null, null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void twoCarpets_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "OAK_SLAB", "LIGHT_BLUE_CARPET", "PINK_CARPET",
            null, null
        };
        assertEquals(-1, validate(m));
    }

    @Test
    public void emptyMatrix_returnsMinusOne() {
        String[] m = new String[9];
        assertEquals(-1, validate(m));
    }

    @Test
    public void extraJunkItem_returnsMinusOne() {
        String[] m = {
            "STICK", "STICK", "STICK", "STICK",
            "OAK_SLAB", "LIGHT_BLUE_CARPET", "DIAMOND",
            null, null
        };
        assertEquals(-1, validate(m));
    }
}
