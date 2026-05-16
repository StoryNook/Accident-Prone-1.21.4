package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import org.bukkit.entity.Player;

/**
 * Care task: change a ward's diaper when wetness or fullness exceeds the
 * change threshold (or both at 100/100 when the ward is under diaper
 * punishment). Priority 90 + 20 severity bonus when at 100/100.
 *
 * <p>Extracted from {@code NannyCareEngine.doChange}. See spec
 * docs/superpowers/specs/2026-05-16-nanny-task-dispatch-design.md.
 *
 * <p><b>Test seam:</b> the {@code Plugin} reference is held only to forward
 * to {@link Plugin#getPlayerStats(java.util.UUID)} inside evaluate() / act().
 * Production wires it as the real plugin; unit tests can supply a stub via
 * {@link #ChangeTask(java.util.function.Function, NannyCareEngine, Plugin)}.
 */
public final class ChangeTask implements NannyTask {

    private final java.util.function.Function<java.util.UUID, PlayerStats> statsLookup;
    private final NannyCareEngine engine; // for delegated helpers during act()
    @SuppressWarnings("unused") // reserved for act() additions (data folder, etc.)
    private final Plugin plugin;

    public ChangeTask(Plugin plugin, NannyCareEngine engine) {
        this(plugin == null ? uuid -> null : plugin::getPlayerStats, engine, plugin);
    }

    /** Test-only constructor — supply a custom stats resolver. */
    public ChangeTask(java.util.function.Function<java.util.UUID, PlayerStats> statsLookup,
                      NannyCareEngine engine, Plugin plugin) {
        this.statsLookup = statsLookup;
        this.engine = engine;
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "change";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        PlayerStats stats = statsLookup.apply(ward.getUniqueId());
        if (stats == null) return null;

        boolean needsChange;
        if (stats.isDiaperPunishment()) {
            needsChange = stats.getDiaperWetness() >= 100 && stats.getDiaperFullness() >= 100;
        } else {
            needsChange = stats.getDiaperWetness() > data.getChangeThreshold()
                    || stats.getDiaperFullness() > data.getChangeThreshold();
        }
        if (!needsChange) return null;

        int priority = 90;
        if (stats.getDiaperWetness() >= 100 || stats.getDiaperFullness() >= 100) priority += 20;

        return new Candidate(priority, ward, ward.getLocation(), "change");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        // Body filled in Task 13 (when ChangeTask is wired to the arbiter end-to-end).
        return Result.FAIL_RETRY;
    }
}
