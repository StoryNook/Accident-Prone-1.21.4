package com.storynook.Event_Listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;
import com.storynook.items.CustomItemCoolDown;

public class Sit implements Listener{

    public Sit(Plugin plugin) {

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Remove their armor stand if they were seated
        if (seatedPlayers.containsKey(player.getUniqueId())) {
            seatedPlayers.remove(player.getUniqueId()).remove();
        }
    }

    private boolean isStairOrSlab(Block block) {
        String type = block.getType().name().toLowerCase();
        return type.contains("stair") || type.contains("slab");
    }

    private boolean isCarpet(Block block) {
        String type = block.getType().name().toLowerCase();
        return type.contains("carpet");
    }

    public static Map<UUID, ArmorStand> seatedPlayers = new HashMap<>();
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
            Player player = event.getPlayer();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            // Check if the block being clicked is a stair or slab
            if (event.getClickedBlock() != null && (isStairOrSlab(event.getClickedBlock())|| isCarpet(event.getClickedBlock())) && (itemInHand == null || itemInHand.getType() == Material.AIR)){
                CustomItemCoolDown cooldown = new CustomItemCoolDown();
                if(cooldown.cooldown.contains(event.getPlayer().getUniqueId())){
                    return;
                }
                // Create an ArmorStand to sit on
                Location blockLoc = event.getClickedBlock().getLocation();
                Location sitLocation = new Location(blockLoc.getWorld(), 
                        blockLoc.getX() + 0.5, 
                        blockLoc.getY(), 
                        blockLoc.getZ() + 0.5);

                // Adjust sitting position based on block type
                if (isCarpet(event.getClickedBlock())) {
                    // For carpets, sit closer to the ground
                    sitLocation.setY(blockLoc.getY() + 0.15);
                } else {
                    // For stairs/slabs, use original positioning
                    sitLocation.setY(blockLoc.getY() + 0.5);
                }

                // Remove any existing ArmorStand if the player is already seated
                if (seatedPlayers.containsKey(player.getUniqueId())) {
                    seatedPlayers.remove(player.getUniqueId()).remove();
                }

                ArmorStand armorStand = player.getWorld().spawn(sitLocation, ArmorStand.class, asm -> {
                    asm.setVisible(false);
                    asm.setMarker(true);
                    asm.setGravity(false);
                    asm.setRotation(180,0);
                    asm.setCustomName("Chair");
                });

                // Make the player sit
                armorStand.addPassenger(player);

                // Store the ArmorStand reference
                seatedPlayers.put(player.getUniqueId(), armorStand);
                
            }
        }
    }
}
