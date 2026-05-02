package com.storynook.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;
import com.storynook.Event_Listeners.Changing;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCheck;

public class EquipArmor {
    private static Plugin plugin = Plugin.getPlugin(Plugin.class);

    /**
     * Equips a clean diaper / underwear / pull-up leggings ItemStack onto
     * {@code target} without caregiver-auth, distance, or sender-mainhand
     * checks. Used by NannyCareEngine when the Nanny equips a diaper on a
     * ward whose underwearType is 0.
     *
     * Delegates to {@link Changing#applyChange(Player, ItemStack)}, which
     * sets the leggings slot, derives the new underwearType from the
     * item's CustomModelData, and resets wetness/fullness/timeWorn/layers.
     * No-op if either argument is null or the item lacks a 626xxx CMD.
     */
    public static void applyEquip(Player target, ItemStack legging) {
        if (target == null || legging == null) return;
        Changing.applyChange(target, legging);
    }

    public static void equipArmor(Player sender, Player target) {
        
        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        if (targetStats == null) {
            sender.sendMessage("Target player stats not available.");
            return;
        }
        
        // Check if the submitter is a caregiver of the target
        if (!targetStats.isCaregiver(sender.getUniqueId(), false)) {
            sender.sendMessage("You do not have permission to equip armor for this player.");
            return;
        }

        // Check distance (10 blocks)
        Location senderLocation = sender.getLocation();
        Location targetLocation = target.getLocation();
        
        double distance = senderLocation.distance(targetLocation);
        if (distance > 10) {
            sender.sendMessage("The target player is not within your range of 10 blocks.");
            return;
        }
        
        // Get item in hand
        ItemStack itemInHand = sender.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            sender.sendMessage("You must hold an armor piece in your main hand to equip it.");
            return;
        }

        if (itemInHand != null && CustomItemCheck.isDiaper(itemInHand)){
            sender.sendMessage("Can not use command to equip. Use right click to change instead.");
            return;
        }
        
        // Check if the held item is armor
        EquipmentSlot slot = getArmorSlotForItem(itemInHand);
        if (slot == null) {
            sender.sendMessage("The item in your hand is not armor.");
            return;
        }
        
        // Get target's current armor in that slot
        ItemStack currentArmor = getCurrentArmor(target, slot);
        
        // If target is wearing diaper armor, remove it first
        if (currentArmor != null && CustomItemCheck.isDiaper(currentArmor)) {
            if (slot == EquipmentSlot.LEGS) {
                // Remove the diaper armor and equip new armor
                target.getInventory().setLeggings(null);
            } else {
                sender.sendMessage("Target is wearing diaper armor which cannot be replaced with other armor.");
                return;
            }
        }

        // Check if sender's inventory has space for the existing armor
        if (currentArmor != null && !CustomItemCheck.isDiaper(currentArmor)) {
            // Create a copy of the current armor to check if it can fit in sender's inventory
            ItemStack armorCopy = currentArmor.clone();
            
            // Check if sender has room for this item
            if (!sender.getInventory().addItem(armorCopy).isEmpty()) {
                // Inventory is full, block the action
                sender.sendMessage("Your inventory is full. Can not equip armor to " + target.getName());
                return;
            }
        }
        
        // Equip the new armor
        equipArmor(target, slot, itemInHand);
        
        // Remove item from player's hand
        sender.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        
        sender.sendMessage("Equipped " + itemInHand.getType().name() + " to " + target.getName());
        return;
    }
    
    private static EquipmentSlot getArmorSlotForItem(ItemStack item) {
        if (item == null) return null;
        
        Material type = item.getType();
        
        if (type == Material.LEATHER_HELMET || 
            type == Material.CHAINMAIL_HELMET || 
            type == Material.IRON_HELMET || 
            type == Material.GOLDEN_HELMET || 
            type == Material.DIAMOND_HELMET ||
            isNetheriteHelmet(type)) {
            return EquipmentSlot.HEAD;
        }
        
        if (type == Material.LEATHER_CHESTPLATE || 
            type == Material.CHAINMAIL_CHESTPLATE || 
            type == Material.IRON_CHESTPLATE || 
            type == Material.GOLDEN_CHESTPLATE || 
            type == Material.DIAMOND_CHESTPLATE ||
            isNetheriteChestplate(type)) {
            return EquipmentSlot.CHEST;
        }
        
        if (type == Material.LEATHER_LEGGINGS || 
            type == Material.CHAINMAIL_LEGGINGS || 
            type == Material.IRON_LEGGINGS || 
            type == Material.GOLDEN_LEGGINGS || 
            type == Material.DIAMOND_LEGGINGS ||
            isNetheriteLeggings(type)) {
            return EquipmentSlot.LEGS;
        }
        
        if (type == Material.LEATHER_BOOTS || 
            type == Material.CHAINMAIL_BOOTS || 
            type == Material.IRON_BOOTS || 
            type == Material.GOLDEN_BOOTS || 
            type == Material.DIAMOND_BOOTS ||
            isNetheriteBoots(type)) {
            return EquipmentSlot.FEET;
        }
        
        return null;
    }
    
    private static ItemStack getCurrentArmor(Player target, EquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return target.getInventory().getHelmet();
            case CHEST:
                return target.getInventory().getChestplate();
            case LEGS:
                return target.getInventory().getLeggings();
            case FEET:
                return target.getInventory().getBoots();
            default:
                return null;
        }
    }
    
    private static void equipArmor(Player target, EquipmentSlot slot, ItemStack armor) {
        switch (slot) {
            case HEAD:
                target.getInventory().setHelmet(armor);
                break;
            case CHEST:
                target.getInventory().setChestplate(armor);
                break;
            case LEGS:
                target.getInventory().setLeggings(armor);
                break;
            case FEET:
                target.getInventory().setBoots(armor);
                break;
        }
    }
    
    private static boolean isNetheriteHelmet(Material material){
        try{
            return material == Material.valueOf("NETHERITE_HELMET");
        } catch (IllegalArgumentException e){
            return false;
        }
    }

    private static boolean isNetheriteChestplate(Material material){
        try{
            return material == Material.valueOf("NETHERITE_CHESTPLATE");
        } catch (IllegalArgumentException e){
            return false;
        }
    }

    private static boolean isNetheriteLeggings(Material material){
        try{
            return material == Material.valueOf("NETHERITE_LEGGINGS");
        } catch (IllegalArgumentException e){
            return false;
        }
    }

    private static boolean isNetheriteBoots(Material material){
        try{
            return material == Material.valueOf("NETHERITE_BOOTS");
        } catch (IllegalArgumentException e){
            return false;
        }
    }
}