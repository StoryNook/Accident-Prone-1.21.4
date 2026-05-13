package com.storynook.furniture.carry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import com.storynook.Integrations.IntegrationsBus;
import com.storynook.Integrations.events.ActionId;
import com.storynook.Plugin;
import com.storynook.furniture.Crib;
import com.storynook.furniture.CribPdcKeys;
import com.storynook.furniture.CribRegistry;

public class CarryDropListener implements Listener {

    private final Plugin plugin;
    private final CarryManager carry;
    private final CribRegistry registry;
    private final CribPdcKeys keys;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final long COOLDOWN_MILLIS = 2000L;

    public CarryDropListener(Plugin plugin, CarryManager carry, CribRegistry registry, CribPdcKeys keys) {
        this.plugin = plugin;
        this.carry = carry;
        this.registry = registry;
        this.keys = keys;
    }

    @EventHandler
    public void onRightClickBlock(PlayerInteractEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player caregiver = event.getPlayer();
        if (!carry.isCarrying(caregiver.getUniqueId())) return;
        if (caregiver.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        UUID wardUuid = carry.wardOf(caregiver.getUniqueId());
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward == null) return;

        Crib targetCrib = null;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block b = event.getClickedBlock();
            String world = b.getWorld().getName();
            targetCrib = registry.findByFloorBlock(world, b.getX(), b.getY(), b.getZ());
            if (targetCrib == null) {
                for (Crib c : registry.findByChunk(world, b.getX() >> 4, b.getZ() >> 4)) {
                    if ((c.bedHeadX() == b.getX() && c.bedHeadY() == b.getY() && c.bedHeadZ() == b.getZ())
                     || (c.bedFootX() == b.getX() && c.bedFootY() == b.getY() && c.bedFootZ() == b.getZ())) {
                        targetCrib = c;
                        break;
                    }
                }
            }
        }

        startDropCountdown(caregiver, ward, targetCrib);
    }

    @EventHandler
    public void onRightClickEntity(PlayerInteractAtEntityEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player caregiver = event.getPlayer();
        if (!carry.isCarrying(caregiver.getUniqueId())) return;
        if (caregiver.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains(CribPdcKeys.SCOREBOARD_TAG)) return;
        UUID wardUuid = carry.wardOf(caregiver.getUniqueId());
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward == null) return;
        Crib crib = Crib.fromPdc(interaction, keys);
        if (crib != null) crib = registry.findById(crib.id());
        startDropCountdown(caregiver, ward, crib);
    }

    private void startDropCountdown(Player caregiver, Player ward, Crib targetCrib) {
        long now = System.currentTimeMillis();
        if (carry.isOnCooldown(caregiver.getUniqueId(), now)) {
            sendActionBar(caregiver, "Catch your breath.");
            return;
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (!caregiver.isOnline() || !ward.isOnline()) { cancelOut(); return; }
                if (!carry.isCarrying(caregiver.getUniqueId())) { cancelOut(); return; }
                int progress = (int) Math.round(100.0 * ticks / COUNTDOWN_TICKS);
                String label = (targetCrib != null) ? "Tucking into crib" : "Setting down";
                sendActionBar(caregiver, label + ": " + progress + "%");
                if (ticks >= COUNTDOWN_TICKS) {
                    completeDrop(caregiver, ward, targetCrib);
                    cancelOut();
                }
            }

            private void cancelOut() {
                carry.setCooldown(caregiver.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MILLIS);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void completeDrop(Player caregiver, Player ward, Crib targetCrib) {
        caregiver.removePassenger(ward);
        if (targetCrib != null) {
            org.bukkit.Location target = targetCrib.bedHeadLocation();
            if (target != null) ward.teleport(target);
            registry.containWard(ward.getUniqueId(), targetCrib.id());
            var stats = plugin.getPlayerStats(ward.getUniqueId());
            if (stats != null) stats.setContainedInCribId(targetCrib.id());
        }
        carry.setCooldown(caregiver.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MILLIS);

        try {
            IntegrationsBus bus = plugin.getIntegrationsBus();
            if (bus != null) {
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("dropped_in_crib", targetCrib != null);
                ctx.put("crib_id", targetCrib != null ? targetCrib.id().toString() : null);
                bus.fire(caregiver, ActionId.CARRY_DROP, ward, ctx);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("CARRY_DROP integration fire failed: " + t.getMessage());
        }
    }

    private static void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            new net.md_5.bungee.api.chat.TextComponent(text));
    }
}
