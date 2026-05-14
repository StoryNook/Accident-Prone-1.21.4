package com.storynook.items;


import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

public class ItemManager {
    private JavaPlugin plugin;
    // public static ItemStack diaperpail;
    public static ItemStack lax;
    // public static ItemStack dur;
    
    public ItemManager(JavaPlugin plugin){this.plugin = plugin;}

    //Recipes for crafting custom items

    public boolean isHypnoEnabled(){
        try {
            Object config = plugin.getConfig(); // Using standard getConfig() method
            if (config != null) {
                Object hypnoValue = plugin.getConfig().get("Hyponsis");
                if (hypnoValue instanceof Boolean) {
                    return (Boolean) hypnoValue;
                }
            }
        } catch (Exception e) {
            // Handle any errors gracefully
            return false;
        }
        return false;
    }

    public void createAllRecipes() {
        createToiletRecipe();
        createDiaperPailRecipe();
        createWasherRecipe();
        createlaxedItem();
        // if(isHypnoEnabled()){createHypnoRecipe();}
        createHypnoRecipe();
        if (isCursedPaciEnabled()) {
            createCursedPaciRecipe();
        }
    }

    private boolean isCursedPaciEnabled() {
        try {
            com.storynook.Plugin storynookPlugin = (com.storynook.Plugin) plugin;
            Object v = storynookPlugin.getGlobalConfig().get("Binding_Diapers");
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    public void createCursedPaciRecipe() {
        // Sentinel result: a placeholder LEATHER_HELMET that the PaciBinding listener
        // overwrites with the real cursed paci before the player ever receives it.
        // The PDC marker `bound_paci` tells the listener "this recipe is mine."
        ItemStack placeholder = new ItemStack(Material.LEATHER_HELMET, 1);
        ItemMeta meta = placeholder.getItemMeta();
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "bound_paci"),
            PersistentDataType.BYTE, (byte) 1);
        placeholder.setItemMeta(meta);

        ShapelessRecipe recipe = new ShapelessRecipe(
            new NamespacedKey(plugin, "CursedPaci"), placeholder);
        recipe.addIngredient(Material.LEATHER_HELMET);
        recipe.addIngredient(Material.ENCHANTED_BOOK);
        Bukkit.addRecipe(recipe);
    }

    public static ItemStack Washer(){
        ItemStack Washer = new ItemStack(Material.FURNACE);
        ItemMeta meta = Washer.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Washing Machine");
        meta.setCustomModelData(626014);// Custom Model Data for texture
        Washer.setItemMeta(meta);
        return Washer;
    }
    public static ItemStack Diaperpail(){
        ItemStack diaperPail = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = diaperPail.getItemMeta();
        meta.setDisplayName("Diaper Pail");
        meta.setCustomModelData(628000); // Custom Model Data for texture
        diaperPail.setItemMeta(meta);
        return diaperPail;
    }
    public static ItemStack Toilet(){
        ItemStack toilet = new ItemStack(Material.CAULDRON);
        ItemMeta meta = toilet.getItemMeta();
        meta.setDisplayName("Toilet");
        meta.setCustomModelData(626006); // Custom Model Data for texture
        toilet.setItemMeta(meta);
        return toilet;
    }
    public void createToiletRecipe() {       
        // Create the recipe
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "Toilet"), Toilet());
        recipe.shape(" T ", " C ", "   "); // Crafting grid: T = Trapdoor, C = Cauldron
        recipe.setIngredient('T', Material.IRON_TRAPDOOR); 
        recipe.setIngredient('C', Material.CAULDRON);

        // Register the recipe
        Bukkit.addRecipe(recipe);
    }
    public void createDiaperPailRecipe() {
        // Create the recipe
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "DiaperPail"), Diaperpail());
        recipe.shape(" T ", " B ", "   "); // Crafting grid: T = Trapdoor, C = Cauldron
        recipe.setIngredient('T', Material.IRON_TRAPDOOR); 
        recipe.setIngredient('B', Material.BARREL);

        // Register the recipe
        Bukkit.addRecipe(recipe);
    }



    public void createWasherRecipe() {
            // Create the recipe
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "Washer"), Washer());
        recipe.addIngredient(Material.FURNACE);
        recipe.addIngredient(Material.WATER_BUCKET);

        // Register the recipe
        Bukkit.addRecipe(recipe);
    }

    // public void createLaxRecipe() {
    //     // Define the custom item
    //     ItemStack item = new ItemStack(Material.GLOWSTONE_DUST, 1);
    //     ItemMeta meta = item.getItemMeta();
    //     meta.setDisplayName("Laxative");
    //     meta.setCustomModelData(626012);
    //     item.setItemMeta(meta);
    //     lax = item;

    //     // Create the recipe
    //     ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "Laxative"), lax);
    //     recipe.addIngredient(Material.ROTTEN_FLESH);

    //     // Register the recipe
    //     Bukkit.addRecipe(recipe);
    // }

    public static ItemStack Laxative(){
        ItemStack Laxitem = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta Laxmeta = Laxitem.getItemMeta();
        Laxmeta.setDisplayName("Laxative");
        Laxmeta.setCustomModelData(626012);// Custom Model Data for texture
        Laxitem.setItemMeta(Laxmeta);
        return Laxitem;
    }
    
    public void createlaxedItem(){
        ItemStack Laxitem = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta Laxmeta = Laxitem.getItemMeta();
        Laxmeta.setDisplayName("Laxative");
        Laxmeta.setCustomModelData(626012);// Custom Model Data for texture
        Laxitem.setItemMeta(Laxmeta);
        lax = Laxitem;
        List<Material> foodMaterials = Arrays.asList(
            Material.SUSPICIOUS_STEW, Material.APPLE, Material.BAKED_POTATO, Material.COOKED_BEEF, Material.COOKED_CHICKEN, 
            Material.COOKED_COD, Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_RABBIT, Material.COOKED_SALMON,
            Material.GOLDEN_CARROT, Material.COOKIE, Material.GOLDEN_APPLE, Material.PUMPKIN_PIE,  Material.RABBIT_STEW,  Material.MUSHROOM_STEW,
            Material.BREAD, Material.BEETROOT_SOUP, Material.ENCHANTED_GOLDEN_APPLE
        );

        RecipeChoice.MaterialChoice foodChoice = new RecipeChoice.MaterialChoice(foodMaterials);

        ShapelessRecipe recipe = new ShapelessRecipe(
        new NamespacedKey(plugin, "LaxedItem"), createLaxedItem(foodMaterials.get(0)));
        

        // Create a custom recipe choice to check for slimeball with specific model data
        recipe.addIngredient(foodChoice);
        recipe.addIngredient(new RecipeChoice.ExactChoice(lax));
        ItemStack result = new ItemStack(foodMaterials.get(0)); // Use any food material as placeholder
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "laxative_effect"), PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);
        
        Bukkit.addRecipe(recipe);
    }
    
    public ItemStack createLaxedItem(Material Food) {
        ItemStack item = new ItemStack(Food, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "laxative_effect"), PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }
    // public void createLDurRecipe() {
    //     // Define the custom item
    //     ItemStack item = new ItemStack(Material.SLIME_BALL, 1);
    //     ItemMeta meta = item.getItemMeta();
    //     meta.setDisplayName("Diuretic");
    //     meta.setCustomModelData(626013);
    //     item.setItemMeta(meta);
    //     dur = item;

    //     // Create the recipe
    //     ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "Diuretic"), dur);

    //     recipe.addIngredient('W', Material.MELON_SLICE);

    //     // Register the recipe
    //     Bukkit.addRecipe(recipe);
    // }

    public void createHypnoRecipe() {
        ItemStack item = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hypnosis"), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, "Hypnosis"), item);
        recipe.addIngredient(Material.CLOCK);
        recipe.addIngredient(Material.ENCHANTED_BOOK);
        
        Bukkit.addRecipe(recipe);
    }
}
