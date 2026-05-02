package com.storynook.menus;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;

import net.md_5.bungee.api.ChatColor;

public class IncontinenceMenu implements Listener{
    private Plugin plugin;
    public IncontinenceMenu (Plugin plugin) {
        this.plugin = plugin;
    }
    public static void IncontinenceSettings(Player player, Plugin plugin) {
        UUID playerUUID = player.getUniqueId();
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        Inventory menu = Bukkit.createInventory(player, 9, "Incontinence Settings");

        ItemStack Back = new ItemStack(Material.RED_STAINED_GLASS_PANE); // Custom button
        ItemMeta BackMeta = Back.getItemMeta();
        if (BackMeta != null) {
            BackMeta.setDisplayName("Back to Settings");
            Back.setItemMeta(BackMeta);   
        }
        ItemStack BladderIncon = new ItemStack(Material.WATER_BUCKET);
        ItemMeta BladderInconMeta = BladderIncon.getItemMeta();
        if (BladderInconMeta != null) {
            List<String> lore = Arrays.asList(
                "Current setting: " + ChatColor.BLUE + Math.round(stats.getBladderIncontinence()),
                stats.getHardcore() ? ChatColor.RED + "LOCKED" : ""
            );
            BladderInconMeta.setLore(lore);
            BladderInconMeta.setDisplayName("Bladder Incontinence");
            BladderIncon.setItemMeta(BladderInconMeta);
        }
        if (stats.getMessing()) {
            ItemStack BowelIncon = new ItemStack(Material.COCOA_BEANS); // Custom button
            ItemMeta BowelInconMeta = BowelIncon.getItemMeta();
            if (BowelInconMeta != null) {
                List<String> lore = Arrays.asList(
                    "Current setting: " + ChatColor.BLUE + Math.round(stats.getBowelIncontinence()),
                    stats.getHardcore() ? ChatColor.RED + "LOCKED" : ""
                );
                BowelInconMeta.setLore(lore);
                BowelInconMeta.setDisplayName("Bowel Incontinence");
                BowelIncon.setItemMeta(BowelInconMeta);   
            }
            menu.setItem(2, BowelIncon);
            ItemStack BowelInconLock = new ItemStack(Material.MAGMA_BLOCK); // Custom button
            ItemMeta BowelInconLcokMeta = BowelInconLock.getItemMeta();
            if (BowelInconMeta != null) {
                List<String> lore = Arrays.asList(
                    " Locked? " + (stats.getBowelLockIncon() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                    stats.getHardcore() ? ChatColor.RED + "SETTING LOCKED" : ""
                );
                BowelInconLcokMeta.setLore(lore);
                BowelInconLcokMeta.setDisplayName("Bowel Incontinence Lock");
                BowelInconLock.setItemMeta(BowelInconLcokMeta);   
            }
            menu.setItem(4, BowelInconLock);
        }

        ItemStack BladderInconLock = new ItemStack(Material.SPONGE); // Custom button
        ItemMeta BladderInconLockMeta = BladderInconLock.getItemMeta();
        if (BladderInconLockMeta != null) {
            List<String> lore = Arrays.asList(
                "Locked?: " + (stats.getBladderLockIncon() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                stats.getHardcore() ? ChatColor.RED + "SETTING LOCKED" : ""
            );
            BladderInconLockMeta.setLore(lore);
            BladderInconLockMeta.setDisplayName("Bladder Incontinence Lock");
            BladderInconLock.setItemMeta(BladderInconLockMeta);   
        }
        
        menu.setItem(0, Back);
        menu.setItem(1, BladderIncon);
        if(!stats.getMessing()){menu.setItem(2, BladderInconLock);}else{menu.setItem(3, BladderInconLock);}
        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Incontinence Settings")) {
            return;
        }

        event.setCancelled(true); // Prevent default behavior

        if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        PlayerStats stats = plugin.getPlayerStats(playerUUID);

        if (stats == null) {
            return;
        }
        else if (event.getView().getTitle().equals("Incontinence Settings")) {
            if (event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE) {
                SettingsMenu.OpenSettings(player, plugin);
            }
            else if (stats.getHardcore()) {
                player.sendMessage("You are in " + ChatColor.RED + "Hardcore Mode" + ChatColor.WHITE + " ask a caregiver for help.");
                return;
            }
            else if (event.getCurrentItem().getType() == Material.SPONGE) {
                stats.setBladderLockIncon(!stats.getBladderLockIncon());
                IncontinenceSettings(player, plugin);
            }
            else if (event.getCurrentItem().getType() == Material.MAGMA_BLOCK) {
                stats.setBowelLockIncon(!stats.getBowelLockIncon());
                IncontinenceSettings(player, plugin);
            }
            else if (event.getCurrentItem().getType() == Material.WATER_BUCKET) {
                plugin.setAwaitingInput(player.getUniqueId(), "bladderincon");
                player.closeInventory(); // Optionally close the inventory
                player.sendMessage(ChatColor.YELLOW + "Please enter a new bladder Incontinence level:");
            }
            else if (event.getCurrentItem().getType() == Material.COCOA_BEANS) {
                plugin.setAwaitingInput(player.getUniqueId(), "bowelincon");
                player.closeInventory(); // Optionally close the inventory
                player.sendMessage(ChatColor.YELLOW + "Please enter a new bowel Incontinence level:");
            }
        }
    }
}
