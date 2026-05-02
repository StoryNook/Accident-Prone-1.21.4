package com.storynook.Event_Listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.util.Vector;

import com.storynook.Plugin;
import com.storynook.AccidentsANDWanrings.HandleAccident;
import com.storynook.PlayerStatsManagement.PlayerStats;

public class AccidentsRandom implements Listener {
    private Map<UUID, Boolean> cooldown = new HashMap<>();
    private final Plugin plugin;
    public AccidentsRandom(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLightningStrike(LightningStrikeEvent event) {
        Location strikeLocation = event.getLightning().getLocation();
        
        // Iterate through all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isNearLightningInWorld(player.getLocation(), strikeLocation)) {
                handleThunderEffect(player);
            }
        }
    }
    private boolean isNearLightningInWorld(Location location, Location lightningLocation) {
        // First check if they are in the same world
        if (location.getWorld().getEnvironment() == World.Environment.NORMAL) {
            return location.distance(lightningLocation) <= 50;
        }
        return false;
    }

    // Handle an accident if the player is near thunder
    private void handleThunderEffect(Player player) {
        if (cooldown.getOrDefault(player.getUniqueId(), false)) return;
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        Random random = new Random();
        if (stats != null && stats.getOptin()) {
            double maxIncontinence = Math.max(stats.getBladderIncontinence(), stats.getBowelIncontinence());
            double chance = Math.min(4, Math.max(0, maxIncontinence / 2));// 1 in 4 chance of having an accident
            if (random.nextInt(4) < chance) {
                boolean bladderAccident = (stats.getBladderIncontinence() * stats.getBladder()) > (stats.getBowelIncontinence() * stats.getBowels());
                if (bladderAccident ? stats.getBladder() > 10 : stats.getBowels() > 10) {
                    HandleAccident.handleAccident(bladderAccident, player, true, "Lightening");
                    String message = plugin.getRandomMessage("Ligtening_Accidnet");
                    player.sendMessage(message);
                    // player.sendMessage("You got so scared by the lightning that you had an accident!");
                    
                    cooldown.put(player.getUniqueId(), true);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        cooldown.put(player.getUniqueId(), false);
                    }, 200L);
                }
                return;
            }
        }
    }
    //Mob attack envirorment check
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        Entity damager = event.getDamager();

        // Check if it is night or the player is in a dark place
        boolean isNight = player.getWorld().getTime() > 12300 && player.getWorld().getTime() < 23850;
        int lightLevel = player.getLocation().getBlock().getLightLevel();
        boolean isDark = lightLevel < 7;

        if ((isNight || isDark) && damager instanceof Mob) {
            // Check if the mob is not on the player's screen
            Vector toEntity = damager.getLocation().toVector().subtract(player.getLocation().toVector());
            Vector direction = player.getLocation().getDirection();

            // Check if mob is outside field of view (Use a dot product for a simple "in-front" check)
            double fieldOfView = 0.5; // Adjust for larger FOV
            if (direction.dot(toEntity.normalize()) < fieldOfView) {
                handleScaryEvent(player);
            }
        }
    }
    //Mob Attack chance
    private void handleScaryEvent(Player player) {
        if (cooldown.getOrDefault(player.getUniqueId(), false)) return;

        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        Random random = new Random();

        if (stats != null && stats.getOptin()) {
            double maxIncontinence = Math.max(stats.getBladderIncontinence(), stats.getBowelIncontinence());
            double chance = Math.log(maxIncontinence + 1) / Math.log(10) * 4;

            if (random.nextInt(4) < chance) {
                if ((stats.getBladder() > 10 && stats.getBladderIncontinence() > stats.getBowelIncontinence()) || (stats.getBowels() > 10 && stats.getBowelIncontinence() > stats.getBladderIncontinence())) {
                    boolean bladderAccident = stats.getBladder() > stats.getBowels() && stats.getBladderIncontinence() >= stats.getBowelIncontinence();
                    if (bladderAccident) {HandleAccident.handleAccident(bladderAccident, player, true, "Fear Wetting");}
                    else{HandleAccident.handleAccident(bladderAccident, player, true, "Fear Messing");}

                    cooldown.put(player.getUniqueId(), true);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        cooldown.put(player.getUniqueId(), false);
                    }, 200L);
                }
            }
        }
    }
    //Bedwetting chance
    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        // Check if world time indicates it's daytime (usually around 0 to 1,000 ticks in Minecraft)
        long worldTime = player.getWorld().getTime();
        if (worldTime >= 0 && worldTime < 1000) {
            PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
            if (stats != null && stats.getOptin()) {
                
                if (stats.getBedwetting() == 1) {
                    double chance = Math.min(4, Math.max(0, stats.getBladderIncontinence() / 2));
                    Random random = new Random();
                    if (random.nextInt(4) < chance) {
                        if (stats.getBladder() > 10) {
                            HandleAccident.handleAccident(true, player,false, "Bedwetting");
                            return;
                        }
                    }
                }
                else if(stats.getBedwetting() == 2){
                    if (stats.getBladder() > 10) {
                        HandleAccident.handleAccident(true, player,false, "Bedwetting");
                        return;
                    }
                }
                stats.increaseBladder(40);
                if(stats.getMessing()){stats.increaseBowels(20);}
            }
        }
    }
}
