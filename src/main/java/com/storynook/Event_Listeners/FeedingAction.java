package com.storynook.Event_Listeners;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.UpdateStats;
import com.storynook.items.CustomItemCoolDown;

public class FeedingAction implements Listener {
    private static Plugin plugin;
    private static final List<Material> FOOD_MATERIALS = Arrays.asList(
        Material.SUSPICIOUS_STEW, Material.APPLE, Material.BAKED_POTATO,
        Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_COD,
        Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_RABBIT,
        Material.COOKED_SALMON, Material.GOLDEN_CARROT, Material.COOKIE,
        Material.GOLDEN_APPLE, Material.PUMPKIN_PIE, Material.RABBIT_STEW,
        Material.MUSHROOM_STEW, Material.BREAD, Material.BEETROOT_SOUP,
        Material.ENCHANTED_GOLDEN_APPLE, Material.ROTTEN_FLESH, Material.MELON_SLICE
    );

    public FeedingAction(Plugin plugin) {
        FeedingAction.plugin = plugin;
    }

    /**
     * Applies the food-stat side effects of a feeding action without the
     * caregiver-auth, cooldown, or PlayerInteractEntityEvent gating. Used by
     * onPlayerInteractEntity (after auth) and by NannyCareEngine when the
     * Nanny acts autonomously.
     *
     * Handles laxative PDC tag, melon-slice hydration spike, and standard
     * food-level + saturation increase. No-op if target stats are
     * unavailable, food is null, or food is not in FOOD_MATERIALS.
     */
    public static void applyFeed(Player target, ItemStack food) {
        if (target == null || food == null) return;
        if (!FOOD_MATERIALS.contains(food.getType())) return;
        PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
        if (stats == null) return;

        float foodLevel = target.getFoodLevel();
        float saturation = target.getSaturation();
        ItemMeta meta = food.getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "laxative_effect"),
                PersistentDataType.BYTE)) {
            if (stats.getMessing()) {
                if (stats.getLaxEffectDelay() == 0) {
                    stats.setLaxEffectDelay((int) (Math.random() * (300 - 175 + 1)) + 175);
                    UpdateStats.Startingdelay.put(target.getUniqueId(), stats.getLaxEffectDelay());
                }
                stats.increaseLaxEffectDuration(30);
            }
        } else if (food.getType() == Material.MELON_SLICE) {
            UpdateStats.HydrationSpike.put(target.getUniqueId(), 10);
            stats.increaseHydration(30);
        }
        target.setFoodLevel((int) Math.min(20, foodLevel + food.getType().getMaxStackSize()));
        target.setSaturation(Math.min(20, saturation + 1));
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }

        Player caregiver = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        ItemStack itemInHand = caregiver.getInventory().getItemInMainHand();

        // Check if item is food
        if (!FOOD_MATERIALS.contains(itemInHand.getType())) {
            return;
        }

        // Check if on cooldown
        if (CustomItemCoolDown.cooldown.contains(caregiver.getUniqueId())) {
            return;
        }

        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        
        // Check if caregiver is authorized
        if (targetStats != null && targetStats.isCaregiver(caregiver.getUniqueId(), true)) {
            // Feed the target player
            if (itemInHand.getAmount() >= 1) {
                ItemMeta foodMeta = itemInHand.getItemMeta();

                if (foodMeta != null) {
                    // Apply target-side food/laxative/hydration stat effects
                    applyFeed(target, itemInHand);

                    String foodName = itemInHand.getType().toString().toLowerCase()
                            .replace("_", " ");
                    String[] feedMessages = {
                        ChatColor.LIGHT_PURPLE + "Your caregiver " + ChatColor.YELLOW + caregiver.getName() + 
                            ChatColor.LIGHT_PURPLE + " fed you some " + ChatColor.AQUA + foodName + ChatColor.LIGHT_PURPLE + "! Nom nom~",
                        ChatColor.LIGHT_PURPLE + "Aww, " + ChatColor.YELLOW + caregiver.getName() + 
                            ChatColor.LIGHT_PURPLE + " helped you eat your " + ChatColor.AQUA + foodName + ChatColor.LIGHT_PURPLE + "!",
                        ChatColor.LIGHT_PURPLE + "Your tummy feels happy as " + ChatColor.YELLOW + caregiver.getName() + 
                            ChatColor.LIGHT_PURPLE + " feeds you " + ChatColor.AQUA + foodName + ChatColor.LIGHT_PURPLE + "!",
                        ChatColor.YELLOW + caregiver.getName() + ChatColor.LIGHT_PURPLE + 
                            " makes sure you're well fed with some yummy " + ChatColor.AQUA + foodName + ChatColor.LIGHT_PURPLE + "!"
                    };

                    int messageIndex = (int)(Math.random() * feedMessages.length);
                    target.sendMessage(feedMessages[messageIndex]);
                    
                    // Reduce item count by 1
                    itemInHand.setAmount(itemInHand.getAmount() - 1);
                    
                    // Apply cooldown
                    CustomItemCoolDown.Cooldown(caregiver, 2);
                }
            }
        }
    }
}