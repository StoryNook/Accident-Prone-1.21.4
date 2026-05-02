package com.storynook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.storynook.PlayerStatsManagement.PlayerStats;

public class PlaySounds {
    // public static Map<UUID, UUID> playsounds = new HashMap<>();
    // public Map<UUID, String> SoundPlayed = new HashMap<>();
    private static Plugin plugin;
    public PlaySounds(Plugin plugin) {
        this.plugin = plugin;
    }

    private static List<SoundPlayback> activeSounds = new ArrayList<>();

    public static class SoundPlayback {
        UUID targetUuid;
        UUID triggerUuid;
        String soundName;

        SoundPlayback(UUID target, UUID trigger, String sound) {
            this.targetUuid = target;
            this.triggerUuid = trigger;
            this.soundName = sound;
        }
    }
    public static void playsounds (Player player, String categoryName, int triggerDistance, Double maxVolume, Double minVolume, Boolean bypass){
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        World world = player.getLocation().getWorld();
        Collection<Entity> nearbyEntities = world.getNearbyEntities(player.getLocation(), triggerDistance, triggerDistance, triggerDistance);

        int shouldBroadcast = stats.getlethear();
        List<Player> affectedPlayers = new ArrayList<>();

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                if (targetPlayer.hasMetadata("NPC")) continue;
                PlayerStats targetstats = plugin.getPlayerStats(targetPlayer.getUniqueId());
                if (targetstats == null) continue;
                if (targetPlayer != null && targetPlayer.getLocation() != null) {
                    if (shouldBroadcast == 0 || shouldBroadcast == 1) {
                        if ((targetstats.getcanhear() == 0 && shouldBroadcast == 0)
                         || shouldBroadcast == 0 && (targetstats.getcanhear() == 0 || (targetstats.getcanhear() == 1 && stats.isCaregiver(targetPlayer.getUniqueId(), true)))
                         || (shouldBroadcast == 1 && stats.isCaregiver(targetPlayer.getUniqueId(), true))
                         || bypass) {
                            affectedPlayers.add(targetPlayer);
                        }
                        else if (targetPlayer.equals(player)) {
                            affectedPlayers.add(player);
                        }
                    }
                    else {
                        if (targetPlayer.equals(player)) {
                            affectedPlayers.add(player);
                        }
                    }  
                }
            }
        }
        for (Player target : affectedPlayers) {
            // Skip checking if target is the main player
            String soundName = stats.getRandomSoundFromCategory(categoryName);
            activeSounds.add(new SoundPlayback(target.getUniqueId(), player.getUniqueId(), soundName));
            if (target.getUniqueId().equals(player.getUniqueId())) {
                // Play sound for main player without additional checks
                target.playSound(target.getLocation(), 
                                "minecraft:" + soundName, 
                                SoundCategory.PLAYERS, 
                                1.0f, 1.0f);
                continue;
            }

            double distance = target.getLocation().distance(player.getLocation());

            float volume = (float) ((float) ((triggerDistance - distance) / triggerDistance * (maxVolume - minVolume)) + minVolume);

            // Get target's PlayerStats and check if the sound is enabled
            PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
            if (targetStats == null) continue;
            Map<String, Map<String, Boolean>> targetSounds = targetStats.getStoredSounds();

            boolean isSoundEnabled = false;
            if (targetSounds.containsKey(categoryName)) {
                Map<String, Boolean> soundsInCategory = targetSounds.get(categoryName);
                if (soundsInCategory.containsKey(soundName) && soundsInCategory.get(soundName)) {
                    isSoundEnabled = true;
                }
            }

            // Only play sound if it's enabled for the target
            if (isSoundEnabled) {
                target.playSound(target.getLocation(), 
                                "minecraft:" + soundName, 
                                SoundCategory.PLAYERS, 
                                volume, 1.0f);
            }
        }
    }
    public static void stopSounds(Player triggerPlayer) {
        List<SoundPlayback> toStop = new ArrayList<>();

        for (SoundPlayback playback : activeSounds) {
            if (playback.triggerUuid.equals(triggerPlayer.getUniqueId())) {
                // Check if target is out of bounds or other conditions
                // Player target = Bukkit.getPlayer(playback.targetUuid);
                // if (target != null && triggerPlayer.getLocation().distance(target.getLocation()) > 0 /* define your condition here */) {
                    toStop.add(playback);
                // }
            }
        }

        for (SoundPlayback playback : toStop) {
            activeSounds.remove(playback);

            Player target = Bukkit.getPlayer(playback.targetUuid);
            if (target != null) {
                target.stopSound("minecraft:"+playback.soundName, SoundCategory.PLAYERS);
            }
        }
    }
}
