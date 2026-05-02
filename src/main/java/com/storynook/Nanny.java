package com.storynook;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Nanny {
    private final UUID uuid;
    private final Entity entity;
    private final Player owner;
    private final Map<String, ItemStack> clothingItems;
    private boolean isCarryingPlayer;
    private Location targetLocation;
    
    public Nanny(Player owner, EntityType entityType) {
        this.owner = owner;
        this.uuid = UUID.randomUUID();
        this.entity = createCustomEntity(owner.getLocation(), entityType);
        this.clothingItems = new HashMap<>();
        this.isCarryingPlayer = false;
        
        // Initialize default clothing
        initializeDefaultClothing();
        
        // Set up entity properties
        setupEntity();
    }
    
    private Entity createCustomEntity(Location location, EntityType entityType) {
        Entity entity = location.getWorld().spawnEntity(location, entityType);
        
        // Make it non-hostile
        if (entity instanceof Creature) {
            ((Creature) entity).setTarget(null);
        }
        
        // Set custom name
        entity.setCustomName("Nanny");
        entity.setCustomNameVisible(true);
        
        return entity;
    }
    
    private void setupEntity() {
        // Set up equipment based on entity type
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            EntityEquipment equipment = livingEntity.getEquipment();
            
            if (equipment != null) {
                // Set default clothing items
                equipment.setHelmet(clothingItems.getOrDefault("helmet", null));
                equipment.setChestplate(clothingItems.getOrDefault("chestplate", null));
                equipment.setLeggings(clothingItems.getOrDefault("leggings", null));
                equipment.setBoots(clothingItems.getOrDefault("boots", null));
            }
        }
        
        // Make it passive
        if (entity instanceof Mob) {
            ((Mob) entity).setTarget(null);
        }
        
    }
    
    private void initializeDefaultClothing() {
        // Default clothing items
        clothingItems.put("helmet", new ItemStack(Material.LEATHER_HELMET));
        clothingItems.put("chestplate", new ItemStack(Material.LEATHER_CHESTPLATE));
        clothingItems.put("leggings", new ItemStack(Material.LEATHER_LEGGINGS));
        clothingItems.put("boots", new ItemStack(Material.LEATHER_BOOTS));
    }
    
    public void changeEntityType(EntityType newType) {
        if (entity != null) {
            Location location = entity.getLocation();
            entity.remove();
            
            // Create new entity of the specified type
            // entity = createCustomEntity(location, newType);
            setupEntity();
        }
    }
    
    public void changeClothing(String slot, ItemStack item) {
        if (clothingItems.containsKey(slot)) {
            clothingItems.put(slot, item);
            updateEquipment();
        }
    }
    
    public void changeClothing(Map<String, ItemStack> newClothing) {
        clothingItems.putAll(newClothing);
        updateEquipment();
    }
    
    private void updateEquipment() {
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            EntityEquipment equipment = livingEntity.getEquipment();
            
            if (equipment != null) {
                // equipment.setHelmet(cclothingItems.get("helmet"));
                equipment.setChestplate(clothingItems.get("chestplate"));
                equipment.setLeggings(clothingItems.get("leggings"));
                equipment.setBoots(clothingItems.get("boots"));
            }
        }
    }
    
    public void pickUpPlayer() {
        if (!isCarryingPlayer && owner != null) {
            isCarryingPlayer = true;
            // Implement pickup logic here
            // This would involve making the player ride the entity
        }
    }
    
    public void putDownPlayer() {
        if (isCarryingPlayer && owner != null) {
            isCarryingPlayer = false;
            // Implement put down logic here
        }
    }
    
    public void moveToLocation(Location location) {
        this.targetLocation = location;
        if (entity instanceof Mob) {
            ((Mob) entity).setTarget(null);
            // Implement movement logic
        }
    }
    
    public void followOwner() {
        if (owner != null && entity != null) {
            Location ownerLocation = owner.getLocation();
            // Implement following logic
        }
    }
    
    public void setCustomName(String name) {
        if (entity != null) {
            entity.setCustomName(name);
        }
    }
    
    public void remove() {
        if (entity != null) {
            entity.remove();
        }
    }
    
    public Entity getEntity() {
        return entity;
    }
    
    public Player getOwner() {
        return owner;
    }
    
    public UUID getUUID() {
        return uuid;
    }
    
    public boolean isCarryingPlayer() {
        return isCarryingPlayer;
    }
    
    public Location getLocation() {
        return entity != null ? entity.getLocation() : null;
    }
    
    public EntityType getEntityType() {
        return entity != null ? entity.getType() : null;
    }
}
