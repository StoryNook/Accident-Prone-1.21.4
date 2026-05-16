package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.UpdateStats;
import com.storynook.Event_Listeners.FeedingAction;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyInventoryManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Care task: hydrate a thirsty ward. Priority 70 + 5 severity bonus when
 * hydration is below 10. Prefers a water bottle (returns the empty glass
 * bottle to the Nanny's inventory for refill); falls back to a melon slice.
 *
 * <p>Extracted from {@code NannyCareEngine.doHydrate}. Test seam mirrors
 * {@link ChangeTask}.
 */
public final class HydrateTask implements NannyTask {

    private final java.util.function.Function<java.util.UUID, PlayerStats> statsLookup;
    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public HydrateTask(Plugin plugin, NannyCareEngine engine) {
        this(plugin == null ? uuid -> null : plugin::getPlayerStats, engine, plugin);
    }

    /** Test-only constructor — supply a custom stats resolver. */
    public HydrateTask(java.util.function.Function<java.util.UUID, PlayerStats> statsLookup,
                       NannyCareEngine engine, Plugin plugin) {
        this.statsLookup = statsLookup;
        this.engine = engine;
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "hydrate";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        PlayerStats stats = statsLookup.apply(ward.getUniqueId());
        if (stats == null) return null;
        if (stats.getHydration() >= data.getHydrationThreshold()) return null;
        int priority = 70 + (stats.getHydration() < 10 ? 5 : 0);
        return new Candidate(priority, ward, ward.getLocation(), "hydrate");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        NannyInventoryManager inv = engine.getInventoryManager();
        // Preferred: water bottle. Consume it, hydrate the ward, return the empty
        // glass bottle to her inventory so she can refill at a water source.
        ItemStack waterBottle = inv.takeOne(data, NannyInventoryManager::isWaterBottle);
        if (waterBottle != null) {
            PlayerStats wardStats = statsLookup.apply(ward.getUniqueId());
            if (wardStats != null) {
                wardStats.increaseHydration(30);
                UpdateStats.HydrationSpike.put(ward.getUniqueId(), 10);
            }
            inv.addToPersonalInventory(data, new ItemStack(Material.GLASS_BOTTLE, 1));
            nanny.faceLocation(ward.getEyeLocation());
            NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.FED_WARD, ward.getUniqueId(), "water_bottle");
            }
            engine.speakPostAction(ward, "hydrated_ward");
            return Result.DONE;
        }

        // Fallback: melon slice (existing behavior)
        ItemStack melon = inv.takeOne(data, NannyInventoryManager::isMelonSlice);
        if (melon == null) {
            engine.warnLowSupplies(data, ward, "water");
            return Result.FAIL_RETRY;
        }
        FeedingAction.applyFeed(ward, melon); // melon-slice path triggers HydrationSpike
        nanny.faceLocation(ward.getEyeLocation());
        NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.FED_WARD, ward.getUniqueId(), "melon");
        }
        engine.speakPostAction(ward, "hydrated_ward");
        return Result.DONE;
    }
}
