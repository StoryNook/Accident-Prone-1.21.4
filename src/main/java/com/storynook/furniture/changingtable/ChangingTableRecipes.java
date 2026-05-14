package com.storynook.furniture.changingtable;

import com.storynook.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers / unregisters the shapeless recipe for changing tables. The recipe's
 * result is the white changing table as a placeholder; the actual rewrite to
 * the correct colour happens in {@link ChangingTableCraftingListener}.
 */
public final class ChangingTableRecipes {

    private ChangingTableRecipes() {}

    public static void register(Plugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "changing_table_recipe");
        Bukkit.removeRecipe(key);   // idempotent — removes prior registration first
        ShapelessRecipe recipe = new ShapelessRecipe(key, ChangingTableItem.make(0));
        recipe.addIngredient(6, Material.STICK);
        recipe.addIngredient(new RecipeChoice.MaterialChoice(woodenSlabs()));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(woodenSlabs()));
        recipe.addIngredient(new RecipeChoice.MaterialChoice(allCarpets()));
        Bukkit.addRecipe(recipe);
    }

    public static void unregister(Plugin plugin) {
        Bukkit.removeRecipe(new NamespacedKey(plugin, "changing_table_recipe"));
    }

    private static List<Material> woodenSlabs() {
        List<Material> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (Tag.WOODEN_SLABS.isTagged(m)) out.add(m);
        }
        return out;
    }

    private static List<Material> allCarpets() {
        List<Material> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.name().endsWith("_CARPET")) out.add(m);
        }
        return out;
    }
}
