package com.storynook.Event_Listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LeashControl implements Listener {
    private Plugin plugin;
    private final Map<UUID, UUID> leashedPlayers = new HashMap<>(); // target UUID -> caregiver UUID
    private static final double MAX_DISTANCE = 20.0;
    private static final double PULL_STRENGTH = 0.5;

    public LeashControl(Plugin plugin) {
        this.plugin = plugin;
        
        // Start a repeating task to check leash distances
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            checkLeashDistances();
        }, 5L, 5L); // Check every 5 ticks
    }

    @EventHandler
    public void onPlayerLeash(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player caregiver = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        ItemStack itemInHand = caregiver.getInventory().getItemInMainHand();

        if (itemInHand.getType() != Material.LEAD) return;

        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        
        if (targetStats != null && targetStats.isCaregiver(caregiver.getUniqueId(), false)) {
            // Create leash connection
            target.setLeashHolder(caregiver);
            leashedPlayers.put(target.getUniqueId(), caregiver.getUniqueId());
            
            // Remove one lead from caregiver's inventory
            if (itemInHand.getAmount() > 1) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            } else {
                caregiver.getInventory().setItemInMainHand(null);
            }
        }
    }

    @EventHandler
    public void onPlayerUnleash(PlayerUnleashEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player target = (Player) event.getEntity();
        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        
        // Cancel the unleash event if the target is leashed
        if (targetStats != null && target.isLeashed()) {
            event.setCancelled(true);
        }
    }

    private void checkLeashDistances() {
        // Create a copy of the map to avoid concurrent modification
        new HashMap<>(leashedPlayers).forEach((targetUUID, caregiverUUID) -> {
            Player target = plugin.getServer().getPlayer(targetUUID);
            Player caregiver = plugin.getServer().getPlayer(caregiverUUID);

            if (target == null || caregiver == null) {
                // If either player is offline, remove the leash
                leashedPlayers.remove(targetUUID);
                return;
            }

            double distance = target.getLocation().distance(caregiver.getLocation());

            if (distance > MAX_DISTANCE) {
                // Calculate direction vector from target to caregiver
                Location targetLoc = target.getLocation();
                Location caregiverLoc = caregiver.getLocation();
                Vector direction = caregiverLoc.toVector().subtract(targetLoc.toVector()).normalize();

                // Calculate pull strength based on how far over the limit they are
                double pullMultiplier = (distance - MAX_DISTANCE) / 10.0;
                pullMultiplier = Math.min(pullMultiplier, 2.0); // Cap the pull multiplier
                
                // Apply the pull force
                Vector pullForce = direction.multiply(PULL_STRENGTH * pullMultiplier);
                
                // Maintain the target's Y velocity to allow for jumping
                pullForce.setY(target.getVelocity().getY());
                
                // Apply the force to the target
                target.setVelocity(pullForce);
            }
        });
    }

    // Method to remove leash
    public void removeLeash(Player target) {
        leashedPlayers.remove(target.getUniqueId());
        target.setLeashHolder(null);
    }

    // Method to check if a player is leashed
    public boolean isLeashed(Player target) {
        return leashedPlayers.containsKey(target.getUniqueId());
    }

    // Method to get the caregiver of a leashed player
    public Player getCaregiverOf(Player target) {
        UUID caregiverUUID = leashedPlayers.get(target.getUniqueId());
        return caregiverUUID != null ? plugin.getServer().getPlayer(caregiverUUID) : null;
    }
}