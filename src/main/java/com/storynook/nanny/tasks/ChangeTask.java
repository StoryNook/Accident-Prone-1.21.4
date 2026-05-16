package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Event_Listeners.Changing;
import com.storynook.Event_Listeners.DiaperPail;
import com.storynook.nanny.Capability;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyPolicy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    private final NannyCareEngine engine;
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
        PlayerStats stats = statsLookup.apply(ward.getUniqueId());
        if (stats == null) return Result.FAIL_RETRY;

        com.storynook.nanny.NannyInventoryManager inv = engine.getInventoryManager();
        ItemStack cleanDiaper = inv.takeOne(data, com.storynook.nanny.NannyInventoryManager::isCleanDiaper);
        if (cleanDiaper == null) {
            cleanDiaper = inv.tryCraftDiaper(data);
            if (cleanDiaper == null) {
                engine.warnLowSupplies(data, ward, "diapers");
                return Result.FAIL_RETRY;
            }
        }

        // Snapshot pre-change state for the soiled-pail item and the event log.
        // Must capture underwearType + rashPoints too — these feed the soiled
        // factory dispatch in Changing.createDirtyDiaperItem, which mirrors
        // the player-side flow (so AI Nanny and player change produce the
        // same item).
        int preType = stats.getUnderwearType();
        int preWet = (int) stats.getDiaperWetness();
        int preMess = (int) stats.getDiaperFullness();
        int preRash = (int) stats.getRashPoints();

        // Phase 5a: unlock armor around the change so applyChange + the
        // leggings-slot mutation don't trip ArmorLockListener
        Boolean wasLocked = data.getLockedArmor().get(ward.getUniqueId());
        boolean armorLockingActive = NannyPolicy.allows(data, Capability.ARMOR_LOCK)
                && wasLocked != null && wasLocked;
        if (armorLockingActive) {
            data.getLockedArmor().put(ward.getUniqueId(), false);
        }
        Changing.applyChange(ward, cleanDiaper);

        // Build the soiled stand-in via the shared Changing helper so the
        // Nanny's pail/inventory drop is the same item the player-side change
        // flow produces. Returns null when the ward was already clean (nothing
        // to stash).
        ItemStack soiled = Changing.createDirtyDiaperItem(ward, preType, preWet, preMess, preRash);
        if (soiled != null) {
            inv.addToPersonalInventory(data, soiled);
            if (soiled.getAmount() > 0) {
                // No room in the Nanny's inventory — try a nearby DiaperPail.
                // If none, hand to the ward; leftover drops at their feet.
                boolean pailed = DiaperPail.deposit(ward.getLocation(), 30.0, soiled);
                if (!pailed) {
                    java.util.Map<Integer, ItemStack> leftover = ward.getInventory().addItem(soiled);
                    for (ItemStack drop : leftover.values()) {
                        ward.getWorld().dropItemNaturally(ward.getLocation(), drop);
                    }
                }
            }
        }
        nanny.faceLocation(ward.getEyeLocation());
        if (armorLockingActive) {
            data.getLockedArmor().put(ward.getUniqueId(), true);
            data.save(plugin.getDataFolder());
        }

        NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.CHANGED_WARD, ward.getUniqueId(),
                    "wetness=" + preWet + " fullness=" + preMess);
        }
        engine.speakPostAction(ward, "changed_ward");
        return Result.DONE;
    }
}
