package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.nanny.Capability;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyPolicy;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Background task: tuck a tired ward (foodLevel ≤ 6) into the nearest crib.
 * Priority 30 — runs only when no care task (60+) and no refill/deposit
 * (40/45) needs doing. The ward is considered "tired" using the foodLevel
 * proxy from the pre-extraction engine code.
 *
 * <p>Extracted from {@code NannyCareEngine.doPlaceInCrib}. Branches on
 * new-system cribs (via {@link com.storynook.furniture.CribRegistry}) vs.
 * legacy "Crib"-named ArmorStands. Kill-switch {@code Crib_New_System=false}
 * in global config forces the legacy path.
 */
public final class CribPlacementTask implements NannyTask {

    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public CribPlacementTask(Plugin plugin, NannyCareEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public String id() {
        return "crib";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (engine == null) return null;
        if (ward.getFoodLevel() > 6) return null;
        if (ward.getVehicle() != null) return null;
        if (!NannyPolicy.allows(data, Capability.CRIB_PLACEMENT)) return null;

        Location wardLoc = ward.getLocation();
        if (wardLoc == null || wardLoc.getWorld() == null) return null;

        com.storynook.furniture.CribRegistry registry =
                engine.getManager().getPlugin().getCribRegistry();
        Object killSwitch = engine.getManager().getPlugin().getGlobalConfig().get("Crib_New_System");
        boolean newSystemEnabled = !(killSwitch instanceof Boolean) || ((Boolean) killSwitch);

        Location target = null;
        if (registry != null && newSystemEnabled) {
            com.storynook.furniture.CribLookupResult lookup =
                    registry.findNearestCrib(wardLoc, data.getHomeRadius());
            if (lookup instanceof com.storynook.furniture.CribLookupResult.NewCribResult newRes) {
                com.storynook.furniture.Crib crib = newRes.crib();
                if (crib.hasBed()) target = crib.bedHeadLocation();
            } else if (lookup instanceof com.storynook.furniture.CribLookupResult.LegacyCribResult legacyRes) {
                target = legacyRes.armorStand().getLocation();
            }
        } else {
            // Legacy-only scan (kill-switch off or registry not yet wired).
            ArmorStand legacy = findLegacyCrib(wardLoc, data.getHomeRadius());
            if (legacy != null) target = legacy.getLocation();
        }
        if (target == null) return null;

        return new Candidate(30, ward, target, "crib");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.CRIB_PLACEMENT)) return Result.DONE;
        if (ward.getVehicle() != null) return Result.DONE;

        Location wardLoc = ward.getLocation();
        if (wardLoc == null || wardLoc.getWorld() == null) return Result.DONE;

        com.storynook.furniture.CribRegistry registry =
                engine.getManager().getPlugin().getCribRegistry();
        if (registry == null) {
            // Registry not yet wired (Plugin.onEnable hasn't run Task 26 yet — defensive).
            legacyFallbackPlace(data, ward, wardLoc);
            return Result.DONE;
        }
        Object killSwitch = engine.getManager().getPlugin().getGlobalConfig().get("Crib_New_System");
        boolean newSystemEnabled = !(killSwitch instanceof Boolean) || ((Boolean) killSwitch);

        com.storynook.furniture.CribLookupResult lookup =
                newSystemEnabled
                    ? registry.findNearestCrib(wardLoc, data.getHomeRadius())
                    : findLegacyCribOnly(wardLoc, data.getHomeRadius());

        if (lookup instanceof com.storynook.furniture.CribLookupResult.NewCribResult newRes) {
            com.storynook.furniture.Crib crib = newRes.crib();
            if (!crib.hasBed()) return Result.DONE;
            Location bedHead = crib.bedHeadLocation();
            if (bedHead == null) return Result.DONE;
            ward.teleport(bedHead);
            registry.containWard(ward.getUniqueId(), crib.id());
            com.storynook.PlayerStatsManagement.PlayerStats wardStats =
                    engine.getManager().getPlugin().getPlayerStats(ward.getUniqueId());
            if (wardStats != null) wardStats.setContainedInCribId(crib.id());

            NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB,
                        ward.getUniqueId(), "crib_placement_new");
            }
            engine.speakPostAction(ward, "tucked_in");
            return Result.DONE;
        }

        if (lookup instanceof com.storynook.furniture.CribLookupResult.LegacyCribResult legacyRes) {
            ArmorStand armor = legacyRes.armorStand();
            ward.teleport(armor.getLocation().add(0, 0.5, 0));
            armor.addPassenger(ward);
            NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB,
                        ward.getUniqueId(), "crib_placement_legacy");
            }
            engine.speakPostAction(ward, "tucked_in");
            return Result.DONE;
        }
        return Result.DONE;
    }

    /** Lightweight scan for an ArmorStand with custom name "Crib" — used by evaluate(). */
    private ArmorStand findLegacyCrib(Location origin, double radius) {
        if (origin == null || origin.getWorld() == null) return null;
        ArmorStand best = null;
        double bestDistSq = radius * radius;
        for (Entity e : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(e instanceof ArmorStand as)) continue;
            if (as.getCustomName() == null || !"Crib".equals(as.getCustomName())) continue;
            double d2 = as.getLocation().distanceSquared(origin);
            if (d2 < bestDistSq) { bestDistSq = d2; best = as; }
        }
        return best;
    }

    /** Wraps {@link #findLegacyCrib} in a {@link com.storynook.furniture.CribLookupResult}. */
    private com.storynook.furniture.CribLookupResult findLegacyCribOnly(Location origin, double radius) {
        ArmorStand best = findLegacyCrib(origin, radius);
        return best == null
            ? com.storynook.furniture.CribLookupResult.None.INSTANCE
            : new com.storynook.furniture.CribLookupResult.LegacyCribResult(best);
    }

    /**
     * Mirrors {@code NannyCareEngine.legacyCribFallback} — used only when the
     * registry hasn't been wired yet (defensive against early-tick edge cases).
     */
    private void legacyFallbackPlace(NannyData data, Player ward, Location wardLoc) {
        ArmorStand nearest = findLegacyCrib(wardLoc, data.getHomeRadius());
        if (nearest == null) return;
        ward.teleport(nearest.getLocation().add(0, 0.5, 0));
        nearest.addPassenger(ward);
        NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB,
                    ward.getUniqueId(), "crib_placement");
        }
        engine.speakPostAction(ward, "tucked_in");
    }
}
