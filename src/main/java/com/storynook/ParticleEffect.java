package com.storynook;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.storynook.PlayerStatsManagement.PlayerStats;


public class ParticleEffect extends BukkitRunnable {

    private Player player;
    private Plugin plugin;
    
    public ParticleEffect(Player player, Plugin plugin) {
        this.player = player;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel(); // Stop the task if the player is offline
            return;
        }
        double scale = player.getHeight() / 1.8;
        
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        if (stats != null && stats.getParticleEffects() == 2 && stats.getDiaperFullness() >= 100) {
            Location stinkLocation = calculateEffectLocation(player, scale, -0.3);
            spawnStinkParticles(stinkLocation, scale);
        }
        else if(stats.getParticleEffects() == 1 && stats.getDiaperWetness() >= 100){
            Location honeyLocation = calculateEffectLocation(player, scale, 0);
            spawnHoneyDripParticles(honeyLocation, scale);
        }
        else if (stats.getParticleEffects() == 3 && stats.getDiaperWetness() >= 100 || stats.getDiaperFullness() >= 100){
            if (stats.getDiaperFullness() >= 100) {
                Location stinkLocation = calculateEffectLocation(player, scale, -0.3);
                spawnStinkParticles(stinkLocation, scale);
            }
            if (stats.getDiaperWetness() >= 100) {
                Location honeyLocation = calculateEffectLocation(player, scale, 0);
                spawnHoneyDripParticles(honeyLocation, scale);
            }
        }
    }
    private Location calculateEffectLocation(Player player, double scale, double zOffset) {
        Location playerLocation = player.getLocation();
        
        double waveOffsetX = Math.sin(System.currentTimeMillis() / 200.0) * 0.2 * scale;
        double waveOffsetZ = Math.cos(System.currentTimeMillis() / 200.0) * 0.2 * scale;
        
        return playerLocation.add(waveOffsetX, scale, waveOffsetZ + (zOffset * scale));
    }

    private void spawnStinkParticles(Location location, double scale) {
        double radius = 0.5 * scale;  // Increase radius for better cloud spread
        double heightSpeed = 0.1 * scale;  // Adjust vertical speed for more gradual rise
        int particleCount = 20;  // Significantly more particles for a denser cloud
       
        // Using a bright, noticeable color
        Color stinkColor = Color.fromRGB(102, 76, 40);;  // Bright green color
        Particle.DustOptions dustOptions = new Particle.DustOptions(stinkColor, (float)(1.0 * scale));  // Adjust size if needed
        

        Location playerLocation = player.getLocation();
        Vector direction = playerLocation.getDirection().normalize().multiply(-0.5 * scale); // Behind the player
        Location stinkLocation = playerLocation.add(direction).add(0, 0.8 * scale, 0);  // Waist height
    
        for (int i = 0; i < particleCount; i++) {
            double xOffset = (Math.random() - 0.5) * radius;
            double zOffset = (Math.random() - 0.5) * radius;
            double yOffset = Math.random() * heightSpeed;
    
            Location particleLocation = stinkLocation.clone().add(xOffset, yOffset, zOffset);
            player.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 1, 0, 0, 0, dustOptions);
        }

        // Changing particle type to POOF for a more cloud-like appearance
        // for (int i = 0; i < particleCount; i++) {
        //     double xOffset = (Math.random() - 0.5) * radius;
        //     double zOffset = (Math.random() - 0.5) * radius;
        //     double yOffset = Math.random() * heightSpeed;
            
        //     Location particleLocation = location.clone().add(xOffset, yOffset, zOffset);
        //     player.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 1, 0, -0.3, 0, dustOptions);
        // }
    }

    // private void spawnStinkParticles(Location location, double scale) {
    //     double radius = 0.4 * scale; // Scale the stink effect
    //     double heightSpeed = 0.2 * scale; // Scale vertical movement
    //     double waveFrequency = 3; // Keep the frequency constant
    
    //     Color stinkColor = Color.fromRGB(102, 76, 40); // Greenish-brown color
    //     Particle.DustOptions dustOptions = new Particle.DustOptions(stinkColor, (float)(2 * scale)); // Scale size
        
    //     int particleCount = 5;  // More particles
    //     double phaseIncrement = (2 * Math.PI / particleCount);
    //     for (int i = 0; i < particleCount; i++) {
    //         double phase = phaseIncrement * i;
    //         double xOffset = radius * Math.sin(waveFrequency * System.currentTimeMillis() / 500.0 + phase);
    //         double yOffset = heightSpeed * System.currentTimeMillis() / 500.0;
    //         double zOffset = radius * Math.cos(waveFrequency * System.currentTimeMillis() / 500.0 + phase);
    //         Location particleLocation = location.clone().add(xOffset, yOffset, zOffset);
    //         player.getWorld().spawnParticle(Particle.REDSTONE, particleLocation, 1, 0, 0, 0, dustOptions);
    //     }
    // }

    private void spawnHoneyDripParticles(Location location, double scale) {
        int particleCount = (int)(2 * scale); // Scale particle count
    
        for (int i = 0; i < particleCount; i++) {
            double xOffset = (Math.random() - 0.5) * 0.3 * scale;
            double zOffset = (Math.random() - 0.5) * 0.3 * scale;
    
            Location particleLocation = location.clone().add(xOffset, -0.3 * scale, zOffset);
            player.getWorld().spawnParticle(Particle.DRIPPING_HONEY, particleLocation, 1, 0, -0.1 * scale, 0, -100);
        }
    }
}
