package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyInventoryManager;
import com.storynook.nanny.NannyNavigator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Background task: refill empty glass bottles from a nearby water source.
 * Priority 40 — runs only when no care task (60+) is needed.
 *
 * <p>Extracted from {@code NannyCareEngine.tryRefillBottles}. Scans within 4
 * blocks of the Nanny for WATER_CAULDRON or WATER blocks (toilet cauldrons
 * excluded). Stays within {@code data.getHomeRadius()} of the ward so she
 * doesn't wander off — refill is opportunistic, not a quest.
 *
 * <p>act() handles both the navigation issue and the fill itself: when not at
 * the water source it issues nav and returns CONTINUE; once adjacent it fills
 * one bottle per tick and returns CONTINUE while bottles remain, DONE when
 * out of empty bottles or water. Phase E will move nav issuance to the arbiter
 * and let this act() become a pure "fill one bottle" loop.
 */
public final class RefillBottleTask implements NannyTask {

    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public RefillBottleTask(Plugin plugin, NannyCareEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public String id() {
        return "refill";
    }

    /** sqrt(4.0) — matches the existing {@code waterDistSq <= 4.0} adjacency check. */
    @Override
    public double actionRangeBlocks() {
        return 2.0;
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (nanny == null || engine == null) return null;
        NannyInventoryManager inv = engine.getInventoryManager();
        if (inv == null) return null;
        if (inv.countAvailable(data, NannyInventoryManager::isEmptyGlassBottle) <= 0) return null;

        Location nannyLoc = nanny.getLocation();
        if (nannyLoc == null || nannyLoc.getWorld() == null) return null;
        if (!nannyLoc.getWorld().equals(ward.getWorld())) return null;
        double homeR = data.getHomeRadius();
        if (nannyLoc.distanceSquared(ward.getLocation()) > homeR * homeR) return null;

        // Direct fill if she's literally standing in water (handles flowing water
        // the source-only scan would miss).
        Block atFeet = nannyLoc.getBlock();
        Block water;
        if (atFeet.getType() == Material.WATER) {
            water = atFeet;
        } else {
            water = engine.findWaterRefillSource(nannyLoc, 4);
        }
        if (water == null) return null;
        int dyToWater = water.getY() - nannyLoc.getBlockY();
        if (dyToWater < -1 || dyToWater > 1) return null;

        // Target = water tile for cauldrons; for open WATER, an adjacent standable.
        Location target;
        if (water.getType() == Material.WATER) {
            Block adjacent = engine.findAdjacentStandable(water);
            if (adjacent == null) {
                // Source surrounded by water — direct fill if she's already at the
                // water block (same xz column); otherwise no usable approach.
                target = water.getLocation().add(0.5, 0, 0.5);
            } else {
                target = adjacent.getLocation().add(0.5, 0, 0.5);
            }
        } else {
            target = water.getLocation().add(0.5, 0, 0.5);
        }
        if (target.distanceSquared(ward.getLocation()) > homeR * homeR) return null;

        return new Candidate(40, ward, target, "refill");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        NannyInventoryManager inv = engine.getInventoryManager();
        if (inv.countAvailable(data, NannyInventoryManager::isEmptyGlassBottle) <= 0) return Result.DONE;

        Location nannyLoc = nanny.getLocation();
        if (nannyLoc == null || !nannyLoc.getWorld().equals(ward.getWorld())) return Result.FAIL_RETRY;

        // Re-scan: water can vanish between ticks.
        Block atFeet = nannyLoc.getBlock();
        Block water;
        if (atFeet.getType() == Material.WATER) {
            water = atFeet;
        } else {
            water = engine.findWaterRefillSource(nannyLoc, 4);
        }
        if (water == null) return Result.FAIL_RETRY;

        double waterDistSq = water.getLocation().add(0.5, 0, 0.5).distanceSquared(nannyLoc);
        boolean alreadyAtSource = waterDistSq <= 4.0;

        if (!alreadyAtSource) {
            // Navigate toward the water tile. Phase E will hoist this to the
            // arbiter; for now act() owns the nav re-issuance to preserve the
            // pre-extraction behavior exactly.
            Location target;
            if (water.getType() == Material.WATER) {
                Block adjacent = engine.findAdjacentStandable(water);
                if (adjacent == null) return Result.FAIL_RETRY;
                target = adjacent.getLocation().add(0.5, 0, 0.5);
            } else {
                target = water.getLocation().add(0.5, 0, 0.5);
            }
            double homeR = data.getHomeRadius();
            if (target.distanceSquared(ward.getLocation()) > homeR * homeR) return Result.DONE;
            NannyNavigator nav = engine.getManager().getNavigator(data.getNannyUUID());
            if (nav == null) return Result.FAIL_RETRY;
            if (!nav.isNavigatingToward(target, 3.0)) {
                nav.navigateToAllowWater(target);
            }
            return Result.CONTINUE;
        }

        // Within reach — fill one bottle this tick.
        if (water.getType() == Material.WATER_CAULDRON) {
            org.bukkit.block.data.BlockData bd = water.getBlockData();
            if (bd instanceof org.bukkit.block.data.Levelled) {
                org.bukkit.block.data.Levelled lv = (org.bukkit.block.data.Levelled) bd;
                if (lv.getLevel() > 1) {
                    lv.setLevel(lv.getLevel() - 1);
                    water.setBlockData(lv);
                } else {
                    water.setType(Material.CAULDRON);
                }
            }
        }
        inv.takeOne(data, NannyInventoryManager::isEmptyGlassBottle);
        ItemStack waterBottle = new ItemStack(Material.POTION, 1);
        org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) waterBottle.getItemMeta();
        if (pm != null) {
            pm.setBasePotionType(org.bukkit.potion.PotionType.WATER);
            waterBottle.setItemMeta(pm);
        }
        inv.addToPersonalInventory(data, waterBottle);
        nanny.faceLocation(water.getLocation().add(0.5, 0, 0.5));

        // After filling, re-engage follow so she returns to the ward rather than
        // standing idle at the water source.
        if (data.isFollowMode()) {
            NannyNavigator nav = engine.getManager().getNavigator(data.getNannyUUID());
            if (nav != null) nav.setFollowTarget(ward);
        }

        // CONTINUE if she still has empty bottles AND water remains; DONE otherwise.
        if (inv.countAvailable(data, NannyInventoryManager::isEmptyGlassBottle) <= 0) return Result.DONE;
        // Re-check water: a cauldron we just emptied no longer counts.
        if (water.getType() != Material.WATER_CAULDRON && water.getType() != Material.WATER) return Result.DONE;
        return Result.CONTINUE;
    }
}
