package com.storynook.furniture;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.storynook.Plugin;

/**
 * Soft-containment loop. Runs every 2 ticks. For each contained ward:
 * release if knockback'd, exempt if creative/spectator, snap back if
 * outside the crib's bounding box.
 */
public class CribContainmentTask extends BukkitRunnable {

    private final Plugin plugin;
    private final CribRegistry registry;
    private final KnockbackTracker knockback;
    private long pruneCounter = 0L;

    /** Knockback grace window: 500ms (matches the 10-tick spec at 50ms/tick). */
    private static final long KNOCKBACK_GRACE_MILLIS = 500L;

    /** Prune cadence: every 100 task ticks (= 200 server ticks at 1-per-2-ticks scheduling = ~10s). */
    private static final long PRUNE_INTERVAL_TASK_TICKS = 100L;

    /** Prune max age: 1000ms (matches the 20-tick spec at 50ms/tick). */
    private static final long PRUNE_MAX_AGE_MILLIS = 1000L;

    public CribContainmentTask(Plugin plugin, CribRegistry registry, KnockbackTracker knockback) {
        this.plugin = plugin;
        this.registry = registry;
        this.knockback = knockback;
    }

    @Override
    public void run() {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;

        long nowMillis = System.currentTimeMillis();
        if (++pruneCounter >= PRUNE_INTERVAL_TASK_TICKS) {
            knockback.prune(nowMillis, PRUNE_MAX_AGE_MILLIS);
            pruneCounter = 0L;
        }

        for (UUID wardUuid : registry.containedWards()) {
            UUID cribId = registry.cribIdForWard(wardUuid);
            if (cribId == null) continue;

            Player ward = Bukkit.getPlayer(wardUuid);
            if (ward == null) continue;        // offline; will resync on join

            Crib crib = registry.findById(cribId);
            if (crib == null) {
                releaseWard(wardUuid, ward);
                continue;
            }
            if (!crib.hasBed()) {
                releaseWard(wardUuid, ward);
                continue;
            }

            if (ward.getGameMode() == GameMode.CREATIVE) continue;
            if (ward.getGameMode() == GameMode.SPECTATOR) continue;

            if ((nowMillis - knockback.lastDamageTick(wardUuid)) <= KNOCKBACK_GRACE_MILLIS) {
                releaseWard(wardUuid, ward);
                continue;
            }

            org.bukkit.util.BoundingBox box = crib.containmentBox();
            Location loc = ward.getLocation();
            if (box.contains(loc.getX(), loc.getY(), loc.getZ())) continue;

            // Outside the box — snap back
            Location target = crib.bedHeadLocation();
            if (target != null) ward.teleport(target);
        }
    }

    private void releaseWard(UUID wardUuid, Player ward) {
        registry.releaseWard(wardUuid);
        var stats = plugin.getPlayerStats(wardUuid);
        if (stats != null) stats.setContainedInCribId(null);
    }
}
