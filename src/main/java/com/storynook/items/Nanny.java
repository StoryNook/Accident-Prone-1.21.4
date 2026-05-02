package com.storynook.items;

import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

public class Nanny {
    private static JavaPlugin plugin;
    public Nanny(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Creates a Nanny Egg. Right-click to summon a Nanny NPC. The optional
     * displayName overrides the default — when set on the egg, it becomes
     * the Nanny's spawn name.
     */
    public static ItemStack createNannyEgg(String displayName) {
        ItemStack item = new ItemStack(Material.ZOMBIE_SPAWN_EGG, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + (displayName == null || displayName.isEmpty() ? "Nanny Egg" : displayName));
            meta.setCustomModelData(629001);
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click to summon your Nanny.",
                ChatColor.GRAY + "Use a name tag on this egg to set her name."
            ));
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "nanny_egg"),
                PersistentDataType.BYTE,
                (byte) 1
            );
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Backwards-compatible no-arg form: returns the default named egg. */
    public static ItemStack createNannyEgg() {
        return createNannyEgg(null);
    }
}
