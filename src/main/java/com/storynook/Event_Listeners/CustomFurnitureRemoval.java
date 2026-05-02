package com.storynook.Event_Listeners;

import java.util.Collection;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.util.Vector;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.entity.Item;

import com.storynook.items.CustomItemCheck;
import com.storynook.items.CustomItemCoolDown;
import com.storynook.Event_Listeners.DiaperPail;

public class CustomFurnitureRemoval implements Listener{
    @EventHandler
    public void PunchFurniture(PlayerInteractEvent event){
        if (event.getAction() == Action.LEFT_CLICK_BLOCK ) {
            CustomItemCoolDown cooldown = new CustomItemCoolDown();
            if(cooldown.cooldown.contains(event.getPlayer().getUniqueId())){
                return;
            }
            cooldown.Cooldown(event.getPlayer(), 1);
            Location clickLocation = event.getClickedBlock().getLocation();
            
            clickLocation.setX(clickLocation.getX() + 0.5);
            clickLocation.setY(clickLocation.getY() + 0.5);
            clickLocation.setZ(clickLocation.getZ() + 0.5);
            Collection<Entity> nearbyEntities = event.getClickedBlock().getWorld().getNearbyEntities(clickLocation, 1.5, 1.5, 1.5);
            
            double minDistance = Double.MAX_VALUE;
            Entity targetEntity = null;

            for (Entity entity : nearbyEntities) {
                if (entity.getType() == EntityType.ARMOR_STAND) {
                    ArmorStand armorStand = (ArmorStand) entity;
                    String standName = armorStand.getCustomName();
                    
                    double distance = clickLocation.distanceSquared(armorStand.getLocation());
                    if (!armorStand.isVisible() && standName != null && ((standName.startsWith("Pail_") && event.getClickedBlock().getType() == Material.BARRIER) || standName.equals("Crib"))) {
                        if(distance < 0.5){
                            targetEntity = armorStand;
                            break;
                        }
                        else if (distance < minDistance){
                            minDistance = distance;
                            targetEntity = armorStand;
                        }
                    }
                    
                }
            }

            if (targetEntity != null){
                ArmorStand armorStand = (ArmorStand) targetEntity;
                String name = armorStand.getCustomName();
                Location dropLocation = armorStand.getLocation().add(0.5, 1, 0.5);
                ItemStack CustomItem = new ItemStack(Material.SLIME_BALL);
                ItemMeta meta = CustomItem.getItemMeta();
                if(name.contains("Pail_")){
                    try {
                        UUID pailId = UUID.fromString(name.substring(6));
                        File inventoryFile = new File(Bukkit.getServer().getPluginManager().getPlugin("Accident-Prone").getDataFolder(), "DiaperPails/" + pailId + ".yml");
                        Inventory inventory = DiaperPail.pailInventories.remove(pailId);
                        if(inventory == null){
                            if(inventoryFile.exists()){
                                inventory = DiaperPail.loadInventory(inventoryFile);
                            }
                        }
                        
                        if (inventory != null) {
                            for (ItemStack stack : inventory.getContents()) {
                                if (stack != null && !stack.getType().isAir()) {
                                    Item itemEntity = armorStand.getWorld().dropItem(dropLocation, stack.clone());
                                    itemEntity.setVelocity(new Vector(0, 0.2, 0));
                                }
                            }
                        }

                        if (inventoryFile.exists()) {
                            inventoryFile.delete();
                        }

                        meta.setCustomModelData(628000);
                        meta.setDisplayName("Diaper Pail");
                        CustomItem.setItemMeta(meta);  

                    } catch (IllegalArgumentException e) {
                        return;
                    }
                }
                else if(name.equals("Crib")){
                    ItemStack helmet = armorStand.getHelmet();
                    if (helmet != null && !helmet.getType().isAir()) {
                            ItemMeta helmetmeta = helmet.getItemMeta();
                            if ((name != null && name.equals("Crib")) || CustomItemCheck.isCrib(helmetmeta)) {
                                CustomItem = helmet.clone();
                                meta = CustomItem.getItemMeta();
                                
                                // Set the custom name
                                if (meta != null) {
                                    meta.setDisplayName("Crib");
                                    CustomItem.setItemMeta(meta);
                                }
                            }
                        }
                }
                Item droppedItem = armorStand.getWorld().dropItem(dropLocation, CustomItem);
                droppedItem.setVelocity(new Vector(0, 0.2, 0));
                armorStand.remove();
                if(name.contains("Pail_")){
                    event.getClickedBlock().setType(Material.AIR);
                }
            }
        }
    }
}