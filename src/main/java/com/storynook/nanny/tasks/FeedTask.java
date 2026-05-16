package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Event_Listeners.FeedingAction;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Care task: feed a hungry ward. Priority 75 + 10 severity bonus when food
 * level is at or below 4 (one heart of hunger left).
 *
 * <p>Extracted from {@code NannyCareEngine.doFeed}. Test seam mirrors
 * {@link ChangeTask}.
 */
public final class FeedTask implements NannyTask {

    private final java.util.function.Function<java.util.UUID, PlayerStats> statsLookup;
    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public FeedTask(Plugin plugin, NannyCareEngine engine) {
        this(plugin == null ? uuid -> null : plugin::getPlayerStats, engine, plugin);
    }

    /** Test-only constructor — supply a custom stats resolver. */
    public FeedTask(java.util.function.Function<java.util.UUID, PlayerStats> statsLookup,
                    NannyCareEngine engine, Plugin plugin) {
        this.statsLookup = statsLookup;
        this.engine = engine;
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "feed";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (ward.getFoodLevel() >= data.getFeedThreshold()) return null;
        int priority = 75 + (ward.getFoodLevel() <= 4 ? 10 : 0);
        return new Candidate(priority, ward, ward.getLocation(), "feed");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        NannyInventoryManager inv = engine.getInventoryManager();
        ItemStack food = inv.takeOne(data, NannyInventoryManager::isAnyFood);
        if (food == null) {
            ItemStack crafted = inv.tryCraftFood(data);
            if (crafted == null) {
                engine.warnLowSupplies(data, ward, "food");
                return Result.FAIL_RETRY;
            }
            food = crafted;
        }
        FeedingAction.applyFeed(ward, food);
        nanny.faceLocation(ward.getEyeLocation());
        NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.FED_WARD, ward.getUniqueId(),
                    "food=" + food.getType().name());
        }
        engine.speakPostAction(ward, "fed_ward");
        return Result.DONE;
    }
}
