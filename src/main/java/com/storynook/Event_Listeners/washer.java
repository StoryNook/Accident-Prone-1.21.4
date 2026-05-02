package com.storynook.Event_Listeners;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.storynook.items.CustomItemCheck;

public class washer implements Listener{
    private JavaPlugin plugin;
    public washer(JavaPlugin plugin){this.plugin = plugin;}
    
    @EventHandler
    public void onWasherPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 626014) {
            Furnace furnace = (Furnace) event.getBlockPlaced().getState();
            PersistentDataContainer data = furnace.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "CustomModelData");
            NamespacedKey xKey = new NamespacedKey(plugin, "ItemFrameX");
            NamespacedKey yKey = new NamespacedKey(plugin, "ItemFrameY");
            NamespacedKey zKey = new NamespacedKey(plugin, "ItemFrameZ");

            data.set(key, PersistentDataType.INTEGER, 626014);
            
            Block block = event.getBlockPlaced();
            Location loc = block.getLocation();
            
            BlockState state = block.getState();
            BlockFace furnaceFacing = ((Directional) state.getBlockData()).getFacing();
            Location frameLocation = loc.clone().add(furnaceFacing.getModX(), furnaceFacing.getModY(), furnaceFacing.getModZ());

            data.set(xKey, PersistentDataType.INTEGER, (int) frameLocation.getX());
            data.set(yKey, PersistentDataType.INTEGER, (int) frameLocation.getY());
            data.set(zKey, PersistentDataType.INTEGER, (int) frameLocation.getZ());
            furnace.update();
            ItemFrame itemFrame = (ItemFrame) loc.getWorld().spawnEntity(frameLocation, EntityType.ITEM_FRAME);
            itemFrame.setFacingDirection(furnaceFacing, true);
            
            itemFrame.setInvulnerable(true);
            itemFrame.setSilent(true);

            // Set the custom modeled item in the item frame
            ItemStack furnaceItem = new ItemStack(Material.FURNACE);
            ItemMeta meta = furnaceItem.getItemMeta();
            meta.setCustomModelData(626014);
            meta.setDisplayName("");
            furnaceItem.setItemMeta(meta);

            itemFrame.setItem(furnaceItem);
            
        }
    }  
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        if (block.getState() instanceof Furnace) {
            Furnace furnace = (Furnace) block.getState();
            PersistentDataContainer data = furnace.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "CustomModelData");
            
            if (data.has(key, PersistentDataType.INTEGER)) {
                int customModelData = data.get(key, PersistentDataType.INTEGER);
                if (customModelData == 626014) {
                    event.setCancelled(true);
    
                    // Drop the custom furnace item
                    ItemStack dropItem = new ItemStack(Material.FURNACE);
                    ItemMeta meta = dropItem.getItemMeta();
                    meta.setCustomModelData(626014);
                    meta.setDisplayName("Washing Machine");
                    dropItem.setItemMeta(meta);
                    
                    block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
    
                    // Clear furnace inventory
                    FurnaceInventory inv = furnace.getInventory();
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack item = inv.getItem(i);
                        if (item != null && !item.getType().equals(Material.AIR)) {
                            block.getWorld().dropItemNaturally(block.getLocation(), item);
                        }
                    }
    
                    // Remove ItemFrame using stored location
                    NamespacedKey xKey = new NamespacedKey(plugin, "ItemFrameX");
                    NamespacedKey yKey = new NamespacedKey(plugin, "ItemFrameY");
                    NamespacedKey zKey = new NamespacedKey(plugin, "ItemFrameZ");

    
                    if (data.has(xKey, PersistentDataType.INTEGER) && data.has(yKey, PersistentDataType.INTEGER) && data.has(zKey, PersistentDataType.INTEGER)) {
                        int x = data.get(xKey, PersistentDataType.INTEGER);
                        int y = data.get(yKey, PersistentDataType.INTEGER);
                        int z = data.get(zKey, PersistentDataType.INTEGER);
    
                        Location frameLocation = new Location(block.getWorld(), x, y, z);
    
                        // Remove the ItemFrame at stored location
                        List<Entity> nearbyEntities = (List<Entity>) block.getWorld().getNearbyEntities(frameLocation, 1.0, 1.0, 1.0);
                        
                        for (Entity entity : nearbyEntities) {
                            if (entity instanceof ItemFrame){
                                ItemFrame itemframe = (ItemFrame) entity;
                                Location frameLoc = itemframe.getLocation();
                                if (Math.floor(frameLoc.getX()) == Math.floor(frameLocation.getX()) &&
                                    Math.floor(frameLoc.getY()) == Math.floor(frameLocation.getY()) &&
                                    Math.floor(frameLoc.getZ()) == Math.floor(frameLocation.getZ())) {

                                    try {
                                        itemframe.remove();
                                        break;
                                    } catch (Exception e) {
                                        // Log any exceptions to understand potential issues
                                        Bukkit.getLogger().warning("Error removing ItemFrame: " + e.getMessage());
                                    }
                                }
                            }
                        }
    
                        // Remove stored location data
                        data.remove(xKey);
                        data.remove(yKey);
                        data.remove(zKey);
                    }
    
                    // Remove the furnace
                    block.setType(Material.AIR);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onItemFrameUse(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        if (entity instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) entity;
            ItemStack item = itemFrame.getItem();

            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                if (item.getItemMeta().hasCustomModelData()) {
                    int modelData = item.getItemMeta().getCustomModelData();
                    
                    // Check if the model data matches the locked item
                    if (modelData == 626014) {
                        event.setCancelled(true);  // Prevent rotation
                         Block attachedBlock = itemFrame.getLocation().getBlock().getRelative(itemFrame.getAttachedFace());

                        // Check if the block has an inventory (furnace, chest, etc.)
                        BlockState state = attachedBlock.getState();
                        if (state instanceof InventoryHolder) {
                            InventoryHolder container = (InventoryHolder) state;
                            event.getPlayer().openInventory(container.getInventory());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        Block furnaceBlock = event.getBlock();
        if (furnaceBlock.getState() instanceof Furnace) {
            Furnace furnace = (Furnace) furnaceBlock.getState();
            ItemStack smeltingItem = furnace.getInventory().getSmelting();
            PersistentDataContainer data = furnace.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "CustomModelData");
            if(data.has(key, PersistentDataType.INTEGER)){
                int customModelData = data.get(key, PersistentDataType.INTEGER);
                if (customModelData == 626014) {
                    if (!CustomItemCheck.isValidSmeltingItem(smeltingItem)) {
                        event.setCancelled(true);
                    }
                }
            }
            else {
                if (CustomItemCheck.isValidSmeltingItem(smeltingItem)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack smeltingItem = event.getSource();
        Block block = event.getBlock();
        Furnace furnace = (Furnace) block.getState();
        PersistentDataContainer data = furnace.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "CustomModelData");
        if(data.has(key, PersistentDataType.INTEGER)){
            int customModelData = data.get(key, PersistentDataType.INTEGER);

            if (customModelData == 626014) {
                if (CustomItemCheck.isValidSmeltingItem(smeltingItem)) {
                    ItemStack result = smeltingItem.clone();
                    ItemMeta meta = result.getItemMeta();
                    
                    if (meta != null) {
                        if (meta.getCustomModelData() == 626016 || 
                        meta.getCustomModelData() == 626017 ||
                        meta.getCustomModelData() == 626018) {
                            meta.setCustomModelData(626015);
                            meta.setLore(null);
                        }
                        else if (meta.getCustomModelData() == 626019 || 
                        meta.getCustomModelData() == 626020 || 
                        meta.getCustomModelData() == 626021) {
                            meta.setCustomModelData(626002);
                            meta.setDisplayName("Underwear");
                            meta.setLore(null);
                        }
                        result.setItemMeta(meta);
                    }
                    event.setResult(result);
                    
                } else{
                    event.setCancelled(true);  // Prevent smelting of unauthorized items
                }
            }
        }
        else {
            if (CustomItemCheck.isValidSmeltingItem(smeltingItem)) {
                event.setCancelled(true);
            }
        }
    }
}
