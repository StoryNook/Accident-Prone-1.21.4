package com.storynook.Event_Listeners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import com.storynook.Plugin;
import com.storynook.AccidentsANDWanrings.HandleAccident;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCoolDown;
import com.storynook.PlaySounds;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TickleAccidents implements Listener {
    private final Plugin plugin;

    public TickleAccidents(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTickle(PlayerInteractEntityEvent event) {
        // Check if right clicking with feather on another player
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player tickler = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        ItemStack heldItem = tickler.getInventory().getItemInMainHand();

        if (!heldItem.getType().equals(Material.FEATHER)) return;

        // Get player stats
        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        
        if (targetStats == null || !targetStats.getOptin()) return;
        if (CustomItemCoolDown.cooldown.contains(target.getUniqueId())) return;

        // Check if tickler is a caregiver
        if (targetStats.isCaregiver(tickler.getUniqueId(), true)) {
            // Play giggle sound
            PlaySounds.playsounds(target, "giggle", 5,1.0,0.2, true);
            
            // Calculate accident chance
            Random random = new Random();
            double bladderLevel = targetStats.getBladder();
            double incontinence = targetStats.getBladderIncontinence();
            
            // Higher chance of accident with fuller bladder and higher incontinence
            double chance = (bladderLevel / 100.0) * (incontinence / 10.0) * 0.8;
            
            if (random.nextDouble() < chance && bladderLevel > 20) {
                HandleAccident.handleAccident(true, target, true, "Tickling");
            }
            else {
                target.sendMessage(ChatColor.AQUA + tickler.getName() + ChatColor.WHITE + " ticked you!");
            }
            CustomItemCoolDown.Cooldown(target, 2);
        }
    }
}