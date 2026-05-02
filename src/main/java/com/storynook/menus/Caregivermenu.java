package com.storynook.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.storynook.Plugin;
// import com.storynook.menus.CustomInventory;
import com.storynook.PlayerStatsManagement.PlayerStats;

import net.md_5.bungee.api.ChatColor;

public class Caregivermenu implements Listener{
    private Plugin plugin;

    public Caregivermenu (Plugin plugin) {
        this.plugin = plugin;
    }
    private static Map<String, UUID> playerHeadMap = new HashMap<>();
    private static Map<UUID, Integer> currentPage = new HashMap<>();
    public static void OpenCareGiverSettings(Player player, Plugin plugin, int page) {
        UUID playerUUID = player.getUniqueId();
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        Inventory menu = Bukkit.createInventory(player, 9 * 6, "CareGiver Settings");
        int start = page *35;
        int end = page + 35;

        if (page > 0) {
            ItemStack Home = new ItemStack(Material.BARRIER); // Custom button
            ItemMeta HomeMeta = Home.getItemMeta();
            if (HomeMeta != null) {
                HomeMeta.setDisplayName("Back to Settings");
                Home.setItemMeta(HomeMeta);   
            }
            menu.setItem(0, Home);
        }

        ItemStack Back = new ItemStack(Material.RED_STAINED_GLASS_PANE); // Custom button
        ItemMeta BackMeta = Back.getItemMeta();
        if (BackMeta != null) {
            if (page > 0) {
                BackMeta.setDisplayName("Previous Page");
            }
            else{
                BackMeta.setDisplayName("Back to Settings");
            }
            Back.setItemMeta(BackMeta);   
        }
        ItemStack EveryoneCG = new ItemStack(Material.ENDER_PEARL);
        if (stats.getAllCaregiver()) {
            EveryoneCG = new ItemStack(Material.ENDER_EYE); // Change to eye of ender if true
        } else {
            EveryoneCG = new ItemStack(Material.ENDER_PEARL); // Keep as ender pearl if false
        }
        ItemMeta EveryoneCGmeta = EveryoneCG.getItemMeta();
        if (EveryoneCGmeta != null) {
            List<String> lore = Arrays.asList(
                "Everyone is a caregiver: " + (stats.getAllCaregiver() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                "Anyone opted to be a caregiver,",
                "can be one for you.",
                ChatColor.RED + "WARNING:",
                "That means anyone can change some of your settings."
            );
            EveryoneCGmeta.setLore(lore);
            EveryoneCGmeta.setDisplayName("Caregivers");
            EveryoneCG.setItemMeta(EveryoneCGmeta);   
        }
        int slot = 9;
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player);
        for (int i = start; i < Math.min(end, onlinePlayers.size()); i++) {
            Player otherPlayer = onlinePlayers.get(i);
            UUID otherUUID = otherPlayer.getUniqueId();
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta headMeta = (SkullMeta) playerHead.getItemMeta();
            if (headMeta != null) {
                headMeta.setOwningPlayer(otherPlayer);
                List<String> lore = Arrays.asList("Caregiver: " + (stats.isCaregiver(otherUUID, false) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                headMeta.setLore(lore);
                headMeta.setDisplayName(otherPlayer.getName());
                playerHead.setItemMeta(headMeta);
            }
            menu.setItem(slot++, playerHead);
            playerHeadMap.put(otherPlayer.getName(), otherPlayer.getUniqueId());
        }
        if (end < onlinePlayers.size()) {
            ItemStack nextPage = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta nextPageMeta = nextPage.getItemMeta();
            if (nextPageMeta != null) {
                nextPageMeta.setDisplayName("Next Page");
                nextPage.setItemMeta(nextPageMeta);
            }
            menu.setItem(53, nextPage); // Next page item slot
        }
        ItemStack NotCGall = new ItemStack(Material.REDSTONE_LAMP); // Custom button
        ItemMeta NotCGallmeta = NotCGall.getItemMeta();
        if (NotCGallmeta != null) {
            List<String> lore = Arrays.asList(
                "I have to pick my littles: " + (stats.getspecialCG() ? ChatColor.RED + "No" : ChatColor.GREEN + "Yes"),
                "Only my littles and I play.",
                "I can not play with a little unless I am added."
            );
            NotCGallmeta.setLore(lore);
            NotCGallmeta.setDisplayName("My Littles");
            NotCGall.setItemMeta(NotCGallmeta);   
        }
        menu.setItem(45, Back);
        menu.setItem(49, NotCGall);
        menu.setItem(4, EveryoneCG);
        player.openInventory(menu);
        currentPage.put(playerUUID, page);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("CareGiver Settings")) {
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

        if (event.getView().getTitle().contains("CareGiver Settings")) {
            ItemStack clickedItem = event.getCurrentItem();
            int currentPageNumber = currentPage.getOrDefault(playerUUID, 0);
            if (event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE) {
                if (currentPageNumber > 0) {
                    OpenCareGiverSettings(player, plugin, currentPageNumber - 1);
                }
                else if (currentPageNumber == 0) {
                    SettingsMenu.OpenSettings(player, plugin);
                }
            }else if (event.getCurrentItem().getType() == Material.GREEN_STAINED_GLASS_PANE) {
                OpenCareGiverSettings(player, plugin, currentPageNumber + 1);
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                SettingsMenu.OpenSettings(player, plugin);
            }else if (event.getCurrentItem().getType() == Material.ENDER_EYE || event.getCurrentItem().getType() == Material.ENDER_PEARL) {
                stats.setAllCaregiver(!stats.getAllCaregiver());
                OpenCareGiverSettings(player, plugin, currentPageNumber);
            }else if (event.getCurrentItem().getType() == Material.REDSTONE_LAMP) {
                stats.setspecialCG(!stats.getspecialCG());
                OpenCareGiverSettings(player, plugin, currentPageNumber);
            } else if (clickedItem != null && clickedItem.getType().name().contains("PLAYER_HEAD")) {
                if (stats != null) {
                    String playerName = clickedItem.getItemMeta().getDisplayName();
                    // Get the UUID from the map
                    UUID targetUUID = playerHeadMap.get(playerName);

                    if (stats.isCaregiver(targetUUID, false)) {
                        stats.removeCaregiver(targetUUID);
                        player.sendMessage("You have removed " + playerName + " as a caregiver.");
                    } else {
                        stats.addCaregiver(targetUUID);
                        player.sendMessage("You have added " + playerName + " as a caregiver.");
                    }
                    OpenCareGiverSettings(player, plugin, currentPageNumber);
                }
            }
        }
    }
}
