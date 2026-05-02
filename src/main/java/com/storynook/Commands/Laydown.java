package com.storynook.Commands;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
// import org.bukkit.entity.Pose;

public class Laydown {
    
    public static void layPlayerDown(Player player) {
        Location playerLocation = player.getLocation();
        Block blockBelow = playerLocation.getBlock().getRelative(0, -1, 0);
        
        // Get the block's type and adjust height accordingly
        Material blockType = blockBelow.getType();
        
        // Calculate the appropriate height for laying down
        double adjustedY = blockBelow.getY() + getBlockHeight(blockType);
        
        // Set the player's location to the adjusted position
        Location layLocation = new Location(
            playerLocation.getWorld(),
            playerLocation.getX(),
            adjustedY,
            playerLocation.getZ(),
            playerLocation.getYaw(),
            0
        );
        
        // Apply slight offset to prevent falling through blocks
        layLocation.add(0, 0.3, 0);
        // player.setPose(entity.Pose.SLEEPING);

        // player.setPose(org.bukkit.entity.Pose.SLEEPING);
        
        player.teleport(layLocation);
        player.setVelocity(new Vector(0, 0, 0));
    }
    
    private static double getBlockHeight(Material material) {

         String materialName = material.name();
        
        // Check for slab types
        if (materialName.contains("_SLAB")) {
            return 0.5; // Half block height for slabs
        }
        
        // Check for double slab types
        if (materialName.contains("DOUBLE_") && materialName.contains("_SLAB")) {
            return 1.0; // Full block height for double slabs
        }

        // Handle different block types and return appropriate height adjustment
        switch (materialName) {
            case "AIR":
            case "WATER":
            case "LAVA":
                return 0.5; // Default for air/water
            default:
                // For regular blocks, use standard height
                if (material.isSolid()) {
                    return 0.5; // Standard block height for solid blocks
                }
                return 0.5; // Default fallback
        }
    }
}