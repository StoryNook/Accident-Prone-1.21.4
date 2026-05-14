package com.storynook.items;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.storynook.DesignRegistry;

public class CustomItemCheck {

    public static boolean VailidUnderwear (ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) {
            return false;
        }

        int customModelData = meta.getCustomModelData();
        // Any registered design's clean CMD counts as a wearable underwear item.
        // This covers legacy CMDs (626001/2/3/9) and every custom design via DesignRegistry.
        return DesignRegistry.isAnyCleanCmd(customModelData);
    }

    public static boolean isCustomItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            return false;
        }
        int customModelData = item.getItemMeta().getCustomModelData();
        List<Integer> CustomItemIDs = Arrays.asList(626001, 626002, 626003, 626004, 626005, 626009, 626011, 626010);
        
        return CustomItemIDs.contains(customModelData);
    }

    public static boolean isCustomPants(ItemStack item) {
        if (item == null || item.getType() != Material.LEATHER_LEGGINGS) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if(meta.hasCustomModelData() && (
                meta.getCustomModelData() == 626015 ||  // Pants
                meta.getCustomModelData() == 626016 ||  // Pants Wet
                meta.getCustomModelData() == 626017 ||  // Pants Dirt
                meta.getCustomModelData() == 626018     // Pants Wet & Dirty
        )) return true;
        return false;
    }
    public static boolean isDiaper(ItemStack item) {
        if (item == null || item.getType() != Material.LEATHER_LEGGINGS) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) {
            return false;
        }
        int modelData = meta.getCustomModelData();
        // Match any registered design's clean/wet/dirty/wet+dirty CMD. Covers
        // legacy CMDs (626001/2/3/9 + 626022..626033) and every custom design.
        return DesignRegistry.isAnyDesignCmd(modelData);
    }

    public static boolean isPaci(ItemStack item) {
        if (item == null || item.getType() != Material.LEATHER_HELMET) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) {
            return false;
        }
        return com.storynook.PaciRegistry.isPaciCmd(meta.getCustomModelData());
    }

    //Used for Cleaning Pants
    public static boolean isValidSmeltingItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) {
            return false;
        }

        int modelData = meta.getCustomModelData();
        //Checks the item ID to ensure it's the custom pants
        if (item.getType() == Material.SLIME_BALL && (modelData == 626019 || 
        modelData == 626020 || 
        modelData == 626021)) {
            return true;
        }
        
        return item.getType() == Material.LEATHER_LEGGINGS;
    }

    public static boolean isUsed (ItemStack item){
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasCustomModelData()) {
                return meta.getCustomModelData() == 626005 || 
                meta.getCustomModelData() == 626004 ||
                meta.getCustomModelData() == 626011 ||  
                meta.getCustomModelData() == 626010;
            }
        }
        return false;
    }

    public static boolean isCrib (ItemMeta meta){
        return meta.getCustomModelData() == 627000 || 
        meta.getCustomModelData() == 627001 ||
        meta.getCustomModelData() == 627002 ||
        meta.getCustomModelData() == 627003 ||
        meta.getCustomModelData() == 627004 ||
        meta.getCustomModelData() == 627005 ||
        meta.getCustomModelData() == 627006 ||
        meta.getCustomModelData() == 627007 ||
        meta.getCustomModelData() == 627008 ||
        meta.getCustomModelData() == 627009 ||
        meta.getCustomModelData() == 627010;
    }
}
