package com.storynook.items;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


public class Stuffies {

    public static ItemStack Scruffy(){
        ItemStack item = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Scruffy");
        meta.setCustomModelData(628000);
        List<String> lore = Arrays.asList("Created By SkullDoge");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
