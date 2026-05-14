package com.storynook.furniture.changingtable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure-Java validator for the changing-table shapeless recipe. The matrix is
 * a 9-cell String[] of material names (null for empty). Returns the registered
 * colorIndex iff the matrix contains exactly: 6 STICK + 2 wooden slabs (per
 * the predicate) + 1 carpet whose colour is registered (per the lookup), and
 * zero other items. Returns -1 in any other case.
 *
 * <p>Mirrors com.storynook.furniture.highchair.RecipeMatrixValidator but with
 * different ingredient counts (highchair: 4 sticks + 1 slab + 1 carpet).
 */
public final class ChangingTableRecipeMatrixValidator {

    private ChangingTableRecipeMatrixValidator() {}

    public static int colorIndexFor(
            String[] matrix,
            Predicate<String> isWoodenSlab,
            Function<String, Integer> colorIndexLookup) {
        if (matrix == null) return -1;
        int sticks = 0, slabs = 0, carpets = 0, junk = 0;
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
        if (sticks != 6 || slabs != 2 || carpets != 1 || junk != 0) return -1;
        return colorIndex;
    }
}
