package com.storynook.furniture.highchair;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/**
 * Registers the highchair shapeless recipe and rewrites its result based on
 * the carpet colour at {@link PrepareItemCraftEvent}. The recipe itself is
 * shaped: 4 sticks + 1 wooden slab + 1 carpet → SLIME_BALL placeholder. The
 * actual result is computed here from the matrix.
 */
public class HighchairCraftingListener implements Listener {

    private final Plugin plugin;
    private final NamespacedKey recipeKey;

    public HighchairCraftingListener(Plugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "highchair");
    }

    /** Register the placeholder recipe. Caller decides whether to call this (kept off when Nursery_Items is off). */
    public void register() {
        // Placeholder result — overwritten in onPrepareCraft.
        ItemStack placeholder = HighchairItem.make(0);
        if (placeholder == null) {
            plugin.getLogger().warning("Highchair recipe not registered — colour 0 not in registry.");
            return;
        }

        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, placeholder);
        recipe.addIngredient(4, Material.STICK);

        // Any wooden slab.
        List<Material> woodenSlabs = new ArrayList<>(Tag.WOODEN_SLABS.getValues());
        recipe.addIngredient(new RecipeChoice.MaterialChoice(woodenSlabs));

        // Any carpet.
        List<Material> carpets = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().endsWith("_CARPET")) carpets.add(m);
        }
        recipe.addIngredient(new RecipeChoice.MaterialChoice(carpets));

        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        if (!(event.getRecipe() instanceof ShapelessRecipe sr)) return;
        if (!recipeKey.equals(sr.getKey())) return;

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        String[] names = new String[9];
        for (int i = 0; i < 9 && i < matrix.length; i++) {
            ItemStack s = matrix[i];
            names[i] = (s == null || s.getType() == Material.AIR) ? null : s.getType().name();
        }

        int colorIndex = RecipeMatrixValidator.colorIndexFor(
            names,
            mat -> {
                Material m = Material.matchMaterial(mat);
                return m != null && Tag.WOODEN_SLABS.isTagged(m);
            },
            HighchairRegistry::carpetMaterialNameToColorIndex
        );

        if (colorIndex < 0) {
            inv.setResult(new ItemStack(Material.AIR));
            return;
        }
        ItemStack result = HighchairItem.make(colorIndex);
        inv.setResult(result == null ? new ItemStack(Material.AIR) : result);
    }
}
