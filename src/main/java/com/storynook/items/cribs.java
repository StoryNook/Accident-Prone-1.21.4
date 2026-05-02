package com.storynook.items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class cribs {
    private JavaPlugin plugin;
    public static ItemStack Crib;
    public cribs(JavaPlugin plugin){this.plugin = plugin;}
    public static void init(JavaPlugin plugin){}

    public static ItemStack createCrib(Material woodtype) {
        ItemStack item = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Crib");
        meta.setCustomModelData(getCribModelNumber(woodtype));
        item.setItemMeta(meta);
        Crib = item;
        
        return item;
    }

    public static int getCribModelNumber (Material woodtype)
    {
        switch (woodtype) {
            case ACACIA_SLAB:
                return 627000;
            case BIRCH_SLAB:
                return 627001;
            case DARK_OAK_SLAB:
                return 627002;
            case JUNGLE_SLAB:
                return 627003;
            case MANGROVE_SLAB:
                return 627004;
            case OAK_SLAB:
                return 627005;
            case SPRUCE_SLAB:
                return 627006;
            case WARPED_SLAB:
                return 627007;
            case QUARTZ_SLAB:
                return 627009;
            default:

                try {
                    // Check if the material exists and it's CHERRY_SLAB (1.20+)
                    if (woodtype != null && woodtype.name().equals("CHERRY_SLAB")) {
                        return 627008;
                    }
                } catch (NoSuchFieldError ignored) { 
                    // Ignore if CHERRY_SLAB doesn't exist in this version
                }
                return 0;
        }
    }

    public void createCribRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "Crib"), createCrib(Material.DARK_OAK_SLAB));
        recipe.shape("WWW", "SSS", "WWW");
        List<Material> woodMaterials = new ArrayList<>(Arrays.asList(
            Material.BIRCH_SLAB, Material.SPRUCE_SLAB, Material.OAK_SLAB, 
            Material.ACACIA_SLAB, Material.JUNGLE_SLAB, Material.QUARTZ_SLAB,
            Material.WARPED_SLAB, Material.DARK_OAK_SLAB, Material.MANGROVE_SLAB
        ));

        try {
            Class<Material> materialClass = Material.class;
            if (materialClass.getField("CHERRY_SLAB").get(null) != null) {
                woodMaterials.add(Material.valueOf("CHERRY_SLAB"));
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // CHERRY_SLAB doesn't exist in this version
        }

        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(woodMaterials));
        recipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(recipe);
    }
}
