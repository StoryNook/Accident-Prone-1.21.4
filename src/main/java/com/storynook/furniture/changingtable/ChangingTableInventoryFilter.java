package com.storynook.furniture.changingtable;

import com.storynook.DesignRegistry;
import com.storynook.items.CustomItemCheck;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Predicate deciding whether an ItemStack may live in a changing-table inventory.
 * Allowed: clean pull-up / diaper / thick diaper, plus DesignRegistry variants
 * with category 1/2/3. Blocked: clean underwear (category 0), any used variant,
 * any non-diaper custom item, any vanilla item.
 */
public final class ChangingTableInventoryFilter {

    private ChangingTableInventoryFilter() {}

    public static boolean isStorable(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return isStorableByFields(true, false, 0, false, null);
        }
        boolean hasMeta = stack.hasItemMeta();
        ItemMeta meta = hasMeta ? stack.getItemMeta() : null;
        boolean hasCmd = meta != null && meta.hasCustomModelData();
        int cmd = hasCmd ? meta.getCustomModelData() : 0;
        boolean used = CustomItemCheck.isUsed(stack);
        DesignRegistry.DesignDef def = hasCmd ? DesignRegistry.findByCleanCmd(cmd) : null;
        return isStorableByFields(false, hasCmd, cmd, used, def);
    }

    /**
     * Field-broken overload for unit-testing without Bukkit. Inputs are the
     * already-extracted values from an ItemStack.
     */
    static boolean isStorableByFields(boolean isAir, boolean hasCmd, int cmd,
                                      boolean isUsed, DesignRegistry.DesignDef def) {
        if (isAir) return true;
        if (!hasCmd) return false;
        if (isUsed) return false;
        if (def != null) {
            return def.category == 1 || def.category == 2 || def.category == 3;
        }
        return cmd == 626001 || cmd == 626003 || cmd == 626009;
    }
}
