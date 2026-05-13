package com.storynook.furniture.carry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.storynook.Plugin;

/**
 * Tracks active caregiver↔ward carry relationships. The
 * {@link org.bukkit.event.entity.EntityDismountEvent} handler is the single chokepoint that
 * returns the saddle on any dismount cause.
 */
public class CarryManager implements Listener {

    private final Map<UUID, UUID> caregiverToWard = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> wardToCaregiver = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, StruggleState> struggleStates = new ConcurrentHashMap<>();

    private final Plugin plugin;

    /** Production constructor — wires saddle return through the plugin reference. */
    public CarryManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Test constructor — pure-data, no Bukkit listeners fire. */
    public CarryManager() {
        this.plugin = null;
    }

    public void recordCarry(UUID caregiver, UUID ward) {
        caregiverToWard.put(caregiver, ward);
        wardToCaregiver.put(ward, caregiver);
    }

    public void clearCarry(UUID caregiver) {
        UUID ward = caregiverToWard.remove(caregiver);
        if (ward != null) wardToCaregiver.remove(ward);
    }

    public void clearCarryByWard(UUID ward) {
        UUID caregiver = wardToCaregiver.remove(ward);
        if (caregiver != null) caregiverToWard.remove(caregiver);
    }

    public boolean isCarrying(UUID caregiver) { return caregiverToWard.containsKey(caregiver); }
    public boolean isBeingCarried(UUID ward)  { return wardToCaregiver.containsKey(ward); }

    public UUID wardOf(UUID caregiver) { return caregiverToWard.get(caregiver); }
    public UUID caregiverOf(UUID ward) { return wardToCaregiver.get(ward); }

    public void setCooldown(UUID caregiver, long endTick) {
        cooldowns.put(caregiver, endTick);
    }

    public boolean isOnCooldown(UUID caregiver, long currentTick) {
        Long end = cooldowns.get(caregiver);
        return end != null && currentTick < end;
    }

    public Map<UUID, UUID> activeCarries() {
        return new HashMap<>(caregiverToWard);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (plugin == null) return;
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (!(event.getEntity() instanceof Player ward)) return;
        UUID caregiverUuid = wardToCaregiver.get(ward.getUniqueId());
        if (caregiverUuid == null) return;

        Player caregiver = Bukkit.getPlayer(caregiverUuid);
        clearCarryByWard(ward.getUniqueId());

        ItemStack saddle = new ItemStack(Material.SADDLE, 1);
        if (caregiver != null) {
            Map<Integer, ItemStack> leftover = caregiver.getInventory().addItem(saddle);
            if (!leftover.isEmpty()) {
                caregiver.getWorld().dropItemNaturally(caregiver.getLocation(), saddle);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (plugin == null) return;
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (!(event.getEntered() instanceof Player caregiver)) return;
        UUID wardUuid = caregiverToWard.get(caregiver.getUniqueId());
        if (wardUuid == null) return;
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward == null) return;

        // Vanilla will dismount the ward when caregiver mounts. Re-add one tick later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (caregiver.isOnline() && ward.isOnline()) {
                caregiver.addPassenger(ward);
            }
        }, 1L);
    }

    public StruggleState struggleStateFor(UUID ward) {
        return struggleStates.computeIfAbsent(ward, k -> new StruggleState());
    }

    @EventHandler
    public void onSneakToggle(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        onStruggleInput(event.getPlayer());
    }

    @EventHandler
    public void onMoveForJump(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.getVehicle() == null) return;
        if (event.getFrom().getY() < event.getTo().getY()
            && (event.getTo().getY() - event.getFrom().getY()) > 0.4
            && event.getFrom().getY() == Math.floor(event.getFrom().getY())) {
            onStruggleInput(p);
        }
    }

    private void onStruggleInput(Player ward) {
        if (plugin == null) return;
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (!isBeingCarried(ward.getUniqueId())) return;
        UUID caregiverUuid = wardToCaregiver.get(ward.getUniqueId());
        Player caregiver = Bukkit.getPlayer(caregiverUuid);
        if (caregiver == null) return;

        StruggleState s = struggleStateFor(ward.getUniqueId());
        long now = System.currentTimeMillis() / 50L;   // approximate ticks (50ms/tick)
        if (s.recordEvent(now)) {
            applyStruggleBurst(caregiver, ward);
        }
    }

    private void applyStruggleBurst(Player caregiver, Player ward) {
        caregiver.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, 100, 1, true, true, true));
        if (caregiver.getVehicle() != null && !(caregiver.getVehicle() instanceof Player)) {
            caregiver.leaveVehicle();
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 20;
                if (ticks > 100) { cancel(); return; }
                if (!ward.isOnline()) { cancel(); return; }
                ward.setExhaustion(ward.getExhaustion() + 0.5f);
                var stats = plugin.getPlayerStats(ward.getUniqueId());
                if (stats != null) {
                    stats.setBladder(Math.min(10.0, stats.getBladder() + 0.1));
                    Object messing = plugin.getGlobalConfig().get("Messing");
                    if (messing instanceof Boolean && (Boolean) messing) {
                        stats.setBowels(Math.min(10.0, stats.getBowels() + 0.1));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
