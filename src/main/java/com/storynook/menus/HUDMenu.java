package com.storynook.menus;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.scoreboard.ScoreboardManager;

import com.storynook.Plugin;
import com.storynook.ScoreBoard;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.SavePlayerStats;

import net.md_5.bungee.api.ChatColor;


public class HUDMenu implements Listener{
    private Plugin plugin;
    public HUDMenu (Plugin plugin) {
        this.plugin = plugin;
    }
    public static void HUDMenuUI(Player player, Plugin plugin) {
        UUID playerUUID = player.getUniqueId();
        PlayerStats stats = plugin.getPlayerStats(playerUUID);
        Inventory menu = Bukkit.createInventory(player, 9, "HUD Settings");

        ItemStack Back = new ItemStack(Material.RED_STAINED_GLASS_PANE); // Custom button
        ItemMeta BackMeta = Back.getItemMeta();
        if (BackMeta != null) {
            BackMeta.setDisplayName("Back to Settings");
            Back.setItemMeta(BackMeta);   
        }
        ItemStack ScoreBoard = new ItemStack(Material.PAINTING); // Custom button
        ItemMeta ScoreobardMeta = ScoreBoard.getItemMeta();
        if (ScoreobardMeta != null) {
            String UISetting;
            if (stats.getUI() == 0) {UISetting = "Hidden";}
            else if (stats.getUI() == 1) {UISetting = "Scoreboard";}
            else if(stats.getUI() == 2){UISetting = "HotBar";}
            else{UISetting = "Error";}
            List<String> lore = Arrays.asList(
                "Current UI Selected " + ChatColor.YELLOW + UISetting,
                "Different styles for you to pick from.",
                "Scoreboard style is defualt."
            );
            ScoreobardMeta.setLore(lore);
            ScoreobardMeta.setDisplayName("Stats Visable");
            ScoreBoard.setItemMeta(ScoreobardMeta);   
        }
        ItemStack toggleBar = new ItemStack(Material.SLIME_BALL); // Custom button
        ItemMeta toggleBarMeta = toggleBar.getItemMeta();
        toggleBarMeta.setCustomModelData(626005);
        if (toggleBarMeta != null) {
            
            List<String> lore = Arrays.asList(
                "Currently: " + (stats.getfillbar() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                "Shows you how much room you have.",
                "Yellow means wetness.",
                "Green is messiness if enabled."
            );
            toggleBarMeta.setLore(lore);
            toggleBarMeta.setDisplayName("Underwear Status Bar");
            toggleBar.setItemMeta(toggleBarMeta);   
        }
        ItemStack toggleFill = new ItemStack(Material.WATER_BUCKET); // Custom button
        ItemMeta toggleFillmeta = toggleFill.getItemMeta();
        if (toggleFillmeta != null) {
            List<String> lore = Arrays.asList(
                "Currently: " + (stats.getshowfill() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                "Shows a live feed,",
                "of you bladder and bowel",
                "rill Rates.",
                "Meaning every 2 seconds your bladder",
                "and bowels fill at that rate.",
                stats.getHardcore() ? ChatColor.RED + "SETTING LOCKED" : ""
            );
            toggleFillmeta.setLore(lore);
            toggleFillmeta.setDisplayName("Fill Rates Visible");
            toggleFill.setItemMeta(toggleFillmeta);   
        }
        ItemStack showunderwear = new ItemStack(Material.SLIME_BALL); // Custom button
        ItemMeta showunderwearmeta = showunderwear.getItemMeta();
        showunderwearmeta.setCustomModelData(626001);
        EquippableComponent showunderwearEquip = showunderwearmeta.getEquippable();
        showunderwearEquip.setSlot(EquipmentSlot.LEGS);
        showunderwearEquip.setModel(NamespacedKey.minecraft("diapers_thick"));
        showunderwearmeta.setEquippable(showunderwearEquip);
        if (toggleFillmeta != null) {
            List<String> lore = Arrays.asList(
                "Currently: " + (stats.getshowunderwear() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                "Show you your underwear on your HUD"
            );
            showunderwearmeta.setLore(lore);
            showunderwearmeta.setDisplayName("View Underwear");
            showunderwear.setItemMeta(showunderwearmeta);   
        }
        menu.setItem(0, Back);
        menu.setItem(1, ScoreBoard);
        menu.setItem(2, toggleBar);
        menu.setItem(3, toggleFill);
        menu.setItem(4, showunderwear);
        player.openInventory(menu);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("HUD Settings")) {
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
        else if (event.getView().getTitle().equals("HUD Settings")) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (event.getCurrentItem().getType() == Material.RED_STAINED_GLASS_PANE) {
                SettingsMenu.OpenSettings(player, plugin);
            }
            else if (event.getCurrentItem().getType() == Material.PAINTING) {
                if(stats.getOptin()){
                    int newUI = stats.getUI();
                    newUI++;
                    if (newUI > 2) {
                        newUI = 0;
                    }
                    stats.setUI(newUI);
                    if (stats.getUI() == 1){updateScoreboard(player, stats); player.sendMessage("Scoreboard Style Selected");}
                    if (stats.getUI() == 0){updateScoreboard(player, stats); player.sendMessage("Stats Hidden");}
                    if (stats.getUI() == 2){updateScoreboard(player, stats); player.sendMessage("Hotbar Style Selected");}
                    SavePlayerStats.savePlayerStats(player); // Save the updated stats
                }
                HUDMenuUI(player, plugin);
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 626005) {
                if(stats.getOptin()){
                    stats.setfillbar(!stats.getfillbar());
                    player.sendMessage(stats.getfillbar() ? "You have enabled the status bar." : "You have removed the status bar.");
                    updateScoreboard(player, stats);
                    HUDMenuUI(player, plugin);
                }
            }
            else if (event.getCurrentItem().getType() == Material.WATER_BUCKET) {
                if(stats.getOptin()){
                    if (!stats.getHardcore()) {
                        stats.setshowfill(!stats.getshowfill());
                        player.sendMessage(stats.getshowfill() ? "You are now seeing the fill rates." : "You no longer are seeing the fill rates.");
                        updateScoreboard(player, stats);
                    }
                    else{
                        player.sendMessage("You are in hardcore mode, this setting is disabled");
                    }
                    HUDMenuUI(player, plugin);
                }
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 626001) {
                if(stats.getOptin()){
                    stats.setshowunderwear(!stats.getshowunderwear());
                    player.sendMessage(stats.getshowunderwear() ? "Your underwear is showing on your stats." : "Your underwear is now hidden from you.");
                    updateScoreboard(player, stats);
                    HUDMenuUI(player, plugin);
                }
            }
        }
    }
    private void updateScoreboard(Player player, PlayerStats stats) {
        // Ensure ScoreboardManager is not null
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            plugin.getLogger().warning("ScoreboardManager is null; Unable to set player scoreboard!");
            return;
        }

        // Update the scoreboard or clear it
        if (stats.getOptin() && stats.getUI() == 1) {
            ScoreBoard.createSidebar(player);
        } else if(stats.getOptin() && !(stats.getUI() == 1)){
            player.setScoreboard(scoreboardManager.getNewScoreboard()); // Clear the scoreboard
        }
    }
}
