package com.storynook.furniture.changingtable;

import com.storynook.Plugin;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Listens for PrepareItemCraftEvent and rewrites the recipe result to a coloured
 * changing-table item when the matrix matches: 6 sticks + 2 wooden slabs (any
 * wood) + 1 carpet (registered colour). Mismatched matrices clear our own
 * placeholder result without blanking valid vanilla recipes.
 */
public final class ChangingTableCraftingListener implements Listener {

    private final Plugin plugin;

    public ChangingTableCraftingListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Object flag = plugin.getGlobalConfig().get("Nursery_Items");
        if (!Boolean.TRUE.equals(flag)) return;
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (matrix == null || matrix.length != 9) return;
        String[] names = new String[9];
        for (int i = 0; i < 9; i++) {
            ItemStack s = matrix[i];
            names[i] = (s == null || s.getType() == Material.AIR) ? null : s.getType().name();
        }
        int colorIndex = ChangingTableRecipeMatrixValidator.colorIndexFor(
            names,
            n -> {
                try { return Tag.WOODEN_SLABS.isTagged(Material.valueOf(n)); }
                catch (IllegalArgumentException e) { return false; }
            },
            n -> {
                try { return ChangingTableRegistry.colorIndexFromCarpet(Material.valueOf(n)); }
                catch (IllegalArgumentException e) { return -1; }
            }
        );
        if (colorIndex < 0) {
            // Don't blank result for valid vanilla recipes; only blank our own placeholder.
            ItemStack current = event.getInventory().getResult();
            if (current != null && isOurTableItem(current)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
            }
            return;
        }
        event.getInventory().setResult(ChangingTableItem.make(colorIndex));
    }

    private static boolean isOurTableItem(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return false;
        ItemMeta meta = s.getItemMeta();
        if (!meta.hasCustomModelData()) return false;
        int cmd = meta.getCustomModelData();
        return cmd >= ChangingTableRegistry.CMD_BASE && cmd <= ChangingTableRegistry.CMD_MAX;
    }
}
