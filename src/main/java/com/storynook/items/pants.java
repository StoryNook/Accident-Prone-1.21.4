package com.storynook.items;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;


public class pants {
    private JavaPlugin plugin;
    public static ItemStack cleanPants;
    public static ItemStack wetPants;
    public static ItemStack dirtyPants;
    public static ItemStack wetAndDirtyPants;
    public pants(JavaPlugin plugin){this.plugin = plugin;}
    public static void init(JavaPlugin plugin){}
    
    public static ItemStack createPants(Material woolMaterial) {
        ItemStack item = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setDisplayName("Pants");
        meta.setColor(getColorFromWool(woolMaterial));
        meta.setCustomModelData(626015);
        item.setItemMeta(meta);
        meta.setUnbreakable(true);
        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), "generic.armor", 0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS);
        meta.addAttributeModifier(Attribute.GENERIC_ARMOR, modifier);
        item.setItemMeta(meta);
        cleanPants = item;
        
        return item;
    }

    public static Color getColorFromWool(Material wool) {
        switch (wool) {
            case WHITE_WOOL: return Color.WHITE;
            case LIGHT_GRAY_WOOL: return Color.fromRGB(211, 211, 211);
            case GRAY_WOOL: return Color.GRAY; 
            case BLACK_WOOL: return Color.BLACK;
            case RED_WOOL: return Color.RED;
            case ORANGE_WOOL: return Color.ORANGE;
            case YELLOW_WOOL: return Color.YELLOW;
            case LIME_WOOL: return Color.LIME;
            case GREEN_WOOL: return Color.GREEN;
            case LIGHT_BLUE_WOOL : return Color.fromRGB(173, 216, 230);
            case CYAN_WOOL: return Color.fromRGB(0, 255, 255);
            case BLUE_WOOL: return Color.BLUE;
            case PURPLE_WOOL: return Color.PURPLE;
            case MAGENTA_WOOL : return Color.fromRGB(255, 0, 255);
            case PINK_WOOL: return Color.fromRGB(243, 139, 170);
            case BROWN_WOOL: return Color.fromRGB(131, 84, 50);

            // Add more cases as needed for other colors
            default: return null;
        }
    }

    public void createCleanPantsRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "CleanPants"), createPants(Material.WHITE_WOOL));
        recipe.shape("WWW", "W W", "W W");
        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(Material.WHITE_WOOL, Material.GRAY_WOOL, Material.RED_WOOL, 
        Material.BLACK_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL, 
        Material.CYAN_WOOL, Material.GREEN_WOOL, Material.LIGHT_BLUE_WOOL, 
        Material.LIGHT_GRAY_WOOL, Material.LIME_WOOL, Material.MAGENTA_WOOL, 
        Material.ORANGE_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL, 
        Material.YELLOW_WOOL));
        Bukkit.addRecipe(recipe);
    }

    public void WashedPants(){
        NamespacedKey key = new NamespacedKey(plugin, "WashedPants");
        ItemStack result = new ItemStack(Material.LEATHER_LEGGINGS);
        FurnaceRecipe recipe = new FurnaceRecipe(key, result, Material.LEATHER_LEGGINGS, 0.0f, 200);
        Bukkit.addRecipe(recipe);
    }
}

