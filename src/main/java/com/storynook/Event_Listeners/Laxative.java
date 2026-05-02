package com.storynook.Event_Listeners;


import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.UpdateStats;

public class Laxative implements Listener{
    private final Plugin plugin;
    public Laxative(Plugin plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "laxative_effect"),
                PersistentDataType.BYTE)) {
                applyLaxDose(player, 30);
            }
        }
    }

    @EventHandler
    public void onFoodPoisoning(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.FOOD) return;
        if (event.getNewEffect() == null
                || !PotionEffectType.HUNGER.equals(event.getNewEffect().getType())) return;
        if (!(event.getEntity() instanceof Player)) return;

        applyLaxDose((Player) event.getEntity(), 25);
    }

    private void applyLaxDose(Player player, int durationBoost) {
        applyLaxDose(plugin, player, durationBoost);
    }

    public static void applyLaxDose(Plugin plugin, Player player, int durationBoost) {
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        if (stats == null || !stats.getMessing()) return;

        if (stats.getLaxEffectDelay() == 0 && stats.getLaxEffectDuration() == 0) {
            int delay = (int) (Math.random() * (250 - 125 + 1)) + 125;
            stats.setLaxEffectDelay(delay);
            UpdateStats.Startingdelay.put(player.getUniqueId(), delay);
        }
        stats.increaseLaxEffectDuration(durationBoost);
        stats.increaseLaxEffectIntensity(1);

        if (stats.getLaxEffectDelay() == 0 && stats.getLaxEffectDuration() > 0) {
            double configBowelRate = plugin.getConfig().getDouble("Bowel_Fill_Rate", 0.14);
            if (stats.getBowelFillRate() > configBowelRate) {
                stats.setBowelFillRate(stats.getBowelFillRate() * 1.35);
            }
        }
    }

    private boolean isLaxative(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 626012;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        if (event.getRecipe() == null || event.getInventory() == null) return;
        
        ItemStack result = event.getRecipe().getResult();
        ItemMeta resultmeta = result.getItemMeta();


        if (resultmeta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "laxative_effect"),
            PersistentDataType.BYTE)) {
    
            int laxativeCount = 0;
            Material foodType = null;
            ItemMeta meta = null;
            int foodCount = 0;

            for (ItemStack item : matrix) {
                if (item == null) continue;

                if (isLaxative(item)) {
                    laxativeCount += item.getAmount();
                } else if (item.getType().isEdible()) {
                    if (foodType == null) {
                        foodType = item.getType();
                        if (item.hasItemMeta()) {
                            meta = item.getItemMeta();
                        }
                    } else if (!item.getType().equals(foodType)) {
                        // Multiple different food items - fail crafting
                        inventory.setResult(null);
                        return;
                    }
                    foodCount += item.getAmount();;
                } else{
                    inventory.setResult(null);
                    return;
                }
            }
            if (foodType != null && laxativeCount >= 1 && foodCount >= 1) {

                result = new ItemStack(foodType);
                ItemMeta Newmeta = result.getItemMeta();
                if (meta != null) {
                        Newmeta = meta;
                }
                if (Newmeta != null) {
                    NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");
                    String crafterName = ((Player) inventory.getViewers().get(0)).getName();
                    Newmeta.getPersistentDataContainer().set(
                        craftedByKey,
                        PersistentDataType.STRING,
                        crafterName
                    );
                    Newmeta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "laxative_effect"),
                        PersistentDataType.BYTE,
                        (byte) 1
                    );
                    Newmeta.setLore(Collections.singletonList(ChatColor.RED + "Has Laxative"));
                    
                    result.setItemMeta(Newmeta);
                }
                inventory.setResult(result);
            } else {
                return;
            }
        }
        else {
            for (ItemStack item : matrix) {
                if (item == null) continue;
                if (isLaxative(item)) {
                    inventory.setResult(null);
                    return;
                } 
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Inventory inventory = event.getInventory();

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

                    if (meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {
                        updateLoreForPlayer(item, player);
                        inventory.setItem(i, item); // Update the item in the inventory
                    }
                }
            }
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Inventory inventory = event.getInventory();

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

                    if (meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {
                        updateLoreForPlayer(item, player);
                        inventory.setItem(i, item); // Update the item in the inventory
                    }
                }
            }
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent  event) {
        if (!(event.getEntity() instanceof Player)) {
            return; // Ensure that the entity is a player
        }
    
        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();
        if (event.getItem() != null) {
            ItemMeta meta = item.getItemMeta();
            NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

            if (meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {
                updateLoreForPlayer(item, player);
            }
        }
    }

    private void updateLoreForPlayer(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey laxativeKey = new NamespacedKey(plugin, "laxative_effect");
        NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");

        if (meta.getPersistentDataContainer().has(laxativeKey, PersistentDataType.BYTE) &&
                meta.getPersistentDataContainer().has(craftedByKey, PersistentDataType.STRING)) {

            String crafterName = meta.getPersistentDataContainer()
                    .get(craftedByKey, PersistentDataType.STRING);
            PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
            if (crafterName != null && crafterName.equals(player.getName()) && !stats.getHardcore()) {
                meta.setLore(Collections.singletonList(ChatColor.RED + "Has Laxative"));
            } else {
                meta.setLore(null);
            }

            item.setItemMeta(meta);
        }
    }
    @EventHandler
    public void onBrew(BrewEvent event) {
        BrewerInventory inventory = event.getContents();

        // Get the ingredient
        ItemStack ingredient = inventory.getIngredient();
        if (ingredient == null || ingredient.getType() != Material.GLOWSTONE_DUST) return;

        // Check for Custom Model Data (e.g., 12345)
        ItemMeta meta = ingredient.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 626012) return;

        // Cancel the brewing process
        event.setCancelled(true);

        // BrewEvent can fire with no viewers (hopper-fed stands, brewer walked away).
        // Without this guard, getViewers().get(0) throws IndexOutOfBoundsException
        // before the ingredient-consumption block runs, leaving the laxative ingredient
        // in the stand and re-triggering the brew cycle forever.
        String crafterName = null;
        if (!inventory.getViewers().isEmpty() && inventory.getViewers().get(0) instanceof Player) {
            crafterName = ((Player) inventory.getViewers().get(0)).getName();
        }

        // You can still add custom data without changing potion properties
        for (int i = 0; i < 3; i++) {
            ItemStack potion = inventory.getItem(i);
            if (potion != null && potion.getType() == Material.POTION) {

                PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();

                if (potionMeta != null) {
                    if (crafterName != null) {
                        NamespacedKey craftedByKey = new NamespacedKey(plugin, "crafted_by");
                        potionMeta.getPersistentDataContainer().set(
                            craftedByKey,
                            PersistentDataType.STRING,
                            crafterName
                        );
                    }
                    potionMeta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "laxative_effect"),
                        PersistentDataType.BYTE,
                        (byte) 1
                    );
                    
                    potionMeta.setLore(Collections.singletonList(ChatColor.RED + "Has Laxative"));
                    ItemStack customPotion = potion.clone();
                    customPotion.setItemMeta(potionMeta);
                    
                    // Set the modified potion back in the brewing stand
                    inventory.setItem(i, customPotion);
                }
            }
        }
        // Optionally consume the ingredient if needed
        if (ingredient.getAmount() > 1) {
            ingredient.setAmount(ingredient.getAmount() - 1);
        } else if (ingredient.getAmount() == 1) {
            inventory.setIngredient(null);
        }
    }


    // @EventHandler
    // public void onPotionEffect(EntityPotionEffectEvent event) {
    //     if (event.getEntity() instanceof Player) {
    //         Player player = (Player) event.getEntity();
    //         PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

    //         if (stats != null && event.getNewEffect() != null) {
    //             PotionEffectType effectType = event.getNewEffect().getType();
                
    //             if (effectType.equals(PotionEffectType.SPEED)) {
    //                 int amplifier = event.getNewEffect().getAmplifier();
    //                 stats.setBladderFillRate(1 + (int) (0.2 * (amplifier + 1))); // Increase Bladder and bowel fill rate because of speed potion
    //                 stats.setBowelFillRate(1 + (int) (0.2 * (amplifier + 1)));
    //             }
    //         }
    //     }
    // }
}
