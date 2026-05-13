package com.storynook.furniture.carry;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.storynook.Integrations.events.ActionId;
import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;

public class CarryPickupListener implements Listener {

    private final Plugin plugin;
    private final CarryManager carry;
    private static final double RANGE = 4.0;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final long COOLDOWN_MILLIS = 2000L;

    public CarryPickupListener(Plugin plugin, CarryManager carry) {
        this.plugin = plugin;
        this.carry = carry;
    }

    @EventHandler
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        Object cgFlag = plugin.getGlobalConfig().get("Caregivers");
        if (!(cgFlag instanceof Boolean) || !((Boolean) cgFlag)) return;

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player ward)) return;
        Player caregiver = event.getPlayer();
        ItemStack inHand = caregiver.getInventory().getItemInMainHand();
        if (inHand.getType() != Material.SADDLE) return;

        PlayerStats wardStats = plugin.getPlayerStats(ward.getUniqueId());
        if (wardStats == null) return;
        if (wardStats.getCaregivers() == null || !wardStats.getCaregivers().contains(caregiver.getUniqueId())) {
            sendActionBar(caregiver, "You're not their caregiver.");
            return;
        }
        if (caregiver.getLocation().distance(ward.getLocation()) > RANGE) {
            sendActionBar(caregiver, "Get closer.");
            return;
        }
        if (carry.isCarrying(caregiver.getUniqueId())) return;
        if (carry.isBeingCarried(ward.getUniqueId())) return;
        if (wardStats.getContainedInCribId() != null) return;

        long now = System.currentTimeMillis();
        if (carry.isOnCooldown(caregiver.getUniqueId(), now)) {
            sendActionBar(caregiver, "Catch your breath.");
            return;
        }

        // Snapshot the saddle stack reference for the countdown closure
        final ItemStack saddleSnapshot = inHand;

        // Start 60-tick countdown
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!caregiver.isOnline() || !ward.isOnline()) { cancelOut(); return; }
                if (caregiver.getLocation().distance(ward.getLocation()) > RANGE) { cancelOut(); return; }
                if (caregiver.getInventory().getItemInMainHand().getType() != Material.SADDLE) { cancelOut(); return; }
                if (wardStats.getContainedInCribId() != null) { cancelOut(); return; }
                if (carry.isCarrying(caregiver.getUniqueId())) { cancelOut(); return; }

                int progress = (int) Math.round(100.0 * ticks / COUNTDOWN_TICKS);
                sendActionBar(caregiver, "Picking up " + ward.getName() + ": " + progress + "%");
                sendActionBar(ward, "Being picked up by " + caregiver.getName());

                if (ticks >= COUNTDOWN_TICKS) {
                    completePickup(caregiver, ward, saddleSnapshot);
                    cancelOut();
                }
            }

            private void cancelOut() {
                carry.setCooldown(caregiver.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MILLIS);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void completePickup(Player caregiver, Player ward, ItemStack saddleInHand) {
        if (saddleInHand.getAmount() > 1) {
            saddleInHand.setAmount(saddleInHand.getAmount() - 1);
        } else {
            caregiver.getInventory().setItemInMainHand(null);
        }
        caregiver.addPassenger(ward);
        carry.recordCarry(caregiver.getUniqueId(), ward.getUniqueId());
        carry.setCooldown(caregiver.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MILLIS);

        try {
            fireIntegration(caregiver, ward);
        } catch (Throwable t) {
            plugin.getLogger().warning("CARRY_PICKUP integration fire failed: " + t.getMessage());
        }
    }

    /**
     * Fires the CARRY_PICKUP integration action.
     * Signature: bus.fire(Player worker, String actionId, Player target, Map&lt;String,Object&gt; ctx)
     */
    private void fireIntegration(Player caregiver, Player ward) {
        com.storynook.Integrations.IntegrationsBus bus = plugin.getIntegrationsBus();
        if (bus == null) return;
        Map<String, Object> ctx = new HashMap<>();
        bus.fire(caregiver, ActionId.CARRY_PICKUP, ward, ctx);
    }

    private static void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            new net.md_5.bungee.api.chat.TextComponent(text));
    }
}
