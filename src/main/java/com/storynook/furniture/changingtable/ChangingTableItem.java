package com.storynook.furniture.changingtable;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds the changing-table item stack. The visual is the resource pack's
 * {@code changingtable_<color>} model, served via SLIME_BALL + CustomModelData.
 */
public final class ChangingTableItem {

    /**
     * Builds a changing-table item for the given registered colorIndex.
     * Returns null if the colorIndex isn't registered.
     */
    public static ItemStack make(int colorIndex) {
        String displayKey = ChangingTableRegistry.displayKeyFor(colorIndex);
        String colorKey = ChangingTableRegistry.colorKeyFor(colorIndex);
        if (displayKey == null || colorKey == null) return null;

        ItemStack stack = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        meta.setCustomModelData(ChangingTableRegistry.cmdFor(colorIndex));
        meta.setDisplayName(prettyName(colorKey) + " Changing Table");
        stack.setItemMeta(meta);
        return stack;
    }

    private static String prettyName(String colorKey) {
        String[] parts = colorKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private ChangingTableItem() {}
}
