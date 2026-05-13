package com.storynook.furniture;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.storynook.Plugin;

/**
 * Wires Bukkit damage + teleport events into the soft-containment system.
 * EntityDamageEvent stamps {@link KnockbackTracker} for knockback-style
 * causes. PlayerTeleportEvent releases a contained ward when their
 * destination is outside the crib's bounding box (rule A from spec).
 */
public class ContainmentEventListener implements Listener {

    private final Plugin plugin;
    private final CribRegistry registry;
    private final KnockbackTracker knockback;

    public ContainmentEventListener(Plugin plugin, CribRegistry registry, KnockbackTracker knockback) {
        this.plugin = plugin;
        this.registry = registry;
        this.knockback = knockback;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!KnockbackTracker.isKnockbackCause(event.getCause())) return;
        knockback.stamp(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        Player p = event.getPlayer();
        UUID cribId = registry.cribIdForWard(p.getUniqueId());
        if (cribId == null) return;
        Crib crib = registry.findById(cribId);
        if (crib == null) return;
        if (event.getTo() == null) return;
        org.bukkit.util.BoundingBox box = crib.containmentBox();
        var to = event.getTo();
        boolean inside = box.contains(to.getX(), to.getY(), to.getZ());
        if (!inside) {
            registry.releaseWard(p.getUniqueId());
            var stats = plugin.getPlayerStats(p.getUniqueId());
            if (stats != null) stats.setContainedInCribId(null);
        }
    }
}
