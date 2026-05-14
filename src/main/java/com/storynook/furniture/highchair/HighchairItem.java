package com.storynook.furniture.highchair;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds the highchair item stack. The visual is the resource pack's
 * {@code <color>_highchair} model, served via SLIME_BALL + CustomModelData.
 */
public final class HighchairItem {

    /**
     * Builds a highchair item for the given registered colorIndex.
     * Returns null if the colorIndex isn't registered.
     */
    public static ItemStack make(int colorIndex) {
        String displayKey = HighchairRegistry.displayKeyFor(colorIndex);
        String colorKey = HighchairRegistry.colorKeyFor(colorIndex);
        if (displayKey == null || colorKey == null) return null;

        ItemStack stack = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        meta.setCustomModelData(HighchairRegistry.cmdFor(colorIndex));
        meta.setDisplayName(prettyName(colorKey) + " Highchair");
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

    private HighchairItem() {}
}
