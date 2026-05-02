package com.storynook.Event_Listeners;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCheck;

public class BindingDiaper implements Listener {

    private final Plugin plugin;

    public BindingDiaper(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        // Check if the result is intended to be a cursed diaper
        if (event.getRecipe() == null || event.getInventory() == null) return;

        ItemStack result = event.getRecipe().getResult();
        ItemMeta resultMeta = result.getItemMeta();

        if (resultMeta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "bound_underwear"), 
            PersistentDataType.BYTE)) {
            
            // We are crafting a diaper, now validate ingredients
            int underwearCount = 0;
            int bindingCount = 0;
            ItemStack underwearItem = null;

            for (ItemStack item : matrix) {
                if (item == null) continue;

                // Check if item is valid underwear
                if (CustomItemCheck.VailidUnderwear(item)) {
                    if (underwearItem == null) {
                        underwearItem = item;
                    }
                    else if (underwearItem != null) {
                        inventory.setResult(null);
                        return;
                    }
                    underwearCount += item.getAmount();
                    if (underwearCount > 1) {
                        inventory.setResult(null);
                        return;
                    }
                }
                // Check if item is a binding enchantment (custom model data 626011)
                else if (item.getType() == Material.ENCHANTED_BOOK) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        // Check if the book has Curse of Binding enchantment
                        if (meta instanceof EnchantmentStorageMeta) {
                            EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
                            // Get all enchantments and check for BINDING_CURSE
                            Map<Enchantment, Integer> storedEnchants = storageMeta.getStoredEnchants();
                            if (storedEnchants.containsKey(Enchantment.BINDING_CURSE)) {
                                bindingCount += item.getAmount();
                                if (bindingCount > 1) {
                                    inventory.setResult(null);
                                    return;
                                }
                            } 
                        } 
                    } 
                }
                // Invalid item
                else {
                    inventory.setResult(null);
                    return;
                }
            }

            // Require at least one of each
            if (underwearCount == 1 && bindingCount == 1 && underwearItem != null) {
                
                result = new ItemStack(underwearItem);
                ItemMeta meta = result.getItemMeta();

                if(meta != null){
                    // Add persistent data: who crafted it
                    NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");
                    String crafterName = ((Player) inventory.getViewers().get(0)).getName();
                    meta.getPersistentDataContainer().set(craftedByKey, PersistentDataType.STRING, crafterName);

                    // Add persistent data: player UUID
                    NamespacedKey uuidKey = new NamespacedKey(plugin, "crafted_by_uuid");
                    UUID crafterUUID = ((Player) inventory.getViewers().get(0)).getUniqueId();
                    meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, crafterUUID.toString());

                    // Add curse tag
                    NamespacedKey curseKey = new NamespacedKey(plugin, "cursed");
                    meta.getPersistentDataContainer().set(curseKey, PersistentDataType.BYTE, (byte) 1);

                    // Set lore
                    meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment"));

                    result.setItemMeta(meta);
                }
                
                inventory.setResult(result);
            } else {
                inventory.setResult(null);
                return;
            }
            
        } else {
            // If result isn't a diaper, prevent crafting if any ingredient is cursed or invalid
            for (ItemStack item : matrix) {
                if (item == null) continue;

                // Prevent crafting if any ingredient is a cursed item
                if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(
                        new NamespacedKey(plugin, "cursed"), PersistentDataType.BYTE)) {
                    inventory.setResult(null);
                    return;
                }
            }
        }
    }

    // Optional: Add event to update lore when player opens inventory, picks up, or closes inventory
    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            NamespacedKey curseKey = new NamespacedKey(plugin, "cursed");
            NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

            if (meta.getPersistentDataContainer().has(curseKey, PersistentDataType.BYTE) &&
                meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {

                String crafterName = meta.getPersistentDataContainer().get(craftedByKey, PersistentDataType.STRING);
                PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

                if (crafterName != null && crafterName.equals(player.getName()) && !stats.getHardcore()) {
                    meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment (Crafted by you)"));
                } else {
                    meta.setLore(null);
                }

                item.setItemMeta(meta);
                inventory.setItem(i, item);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            NamespacedKey curseKey = new NamespacedKey(plugin, "cursed");
            NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

            if (meta.getPersistentDataContainer().has(curseKey, PersistentDataType.BYTE) &&
                meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {

                String crafterName = meta.getPersistentDataContainer().get(craftedByKey, PersistentDataType.STRING);
                PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

                if (crafterName != null && crafterName.equals(player.getName()) && !stats.getHardcore()) {
                    meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment (Crafted by you)"));
                } else {
                    meta.setLore(null);
                }

                item.setItemMeta(meta);
                inventory.setItem(i, item);
            }
        }
    }

    @EventHandler
    public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey curseKey = new NamespacedKey(plugin, "cursed");
        NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

        if (meta.getPersistentDataContainer().has(curseKey, PersistentDataType.BYTE) &&
            meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {

            String crafterName = meta.getPersistentDataContainer().get(craftedByKey, PersistentDataType.STRING);
            PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

            if (crafterName != null && crafterName.equals(player.getName()) && !stats.getHardcore()) {
                meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment (Crafted by you)"));
            } else {
                meta.setLore(null);
            }

            item.setItemMeta(meta);
            event.getItem().setItemStack(item);
        }
    }
}