package com.storynook.furniture.highchair;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure-Java validator for the highchair shapeless recipe. The matrix is
 * a 9-cell {@code String[]} of material names (or null for empty). Returns
 * the registered colorIndex of the carpet if and only if the matrix contains:
 * exactly 4 sticks, exactly 1 wooden slab (per the predicate), exactly 1
 * carpet whose color is registered (per the lookup), and zero other items.
 * Returns -1 in any other case.
 *
 * <p>Extracted from {@code HighchairCraftingListener.onPrepareCraft} so it's
 * testable without spinning up Bukkit's recipe system.
 */
public final class RecipeMatrixValidator {

    public static int colorIndexFor(
            String[] matrix,
            Predicate<String> isWoodenSlab,
            Function<String, Integer> colorIndexLookup) {
        if (matrix == null) return -1;
        int sticks = 0;
        int slabs = 0;
        int carpets = 0;
        int junk = 0;
        int colorIndex = -1;
        for (String mat : matrix) {
            if (mat == null) continue;
            if ("STICK".equals(mat)) {
                sticks++;
            } else if (isWoodenSlab.test(mat)) {
                slabs++;
            } else if (mat.endsWith("_CARPET")) {
                carpets++;
                int idx = colorIndexLookup.apply(mat);
                if (idx < 0) return -1;
                colorIndex = idx;
            } else {
                junk++;
            }
        }
        if (sticks != 4) return -1;
        if (slabs != 1) return -1;
        if (carpets != 1) return -1;
        if (junk != 0) return -1;
        return colorIndex;
    }

    private RecipeMatrixValidator() {}
}
