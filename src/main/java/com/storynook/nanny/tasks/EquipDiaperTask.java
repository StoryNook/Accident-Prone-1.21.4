package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Commands.EquipArmor;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyInventoryManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Care task: equip a diaper or pull-up on a ward who has no absorbent
 * undergarment (underwearType == 0, i.e. plain non-absorbent underwear).
 *
 * <p>Extracted from {@code NannyCareEngine.doEquipDiaper}. Priority 65 —
 * less urgent than hunger/thirst because plain underwear isn't dangerous.
 *
 * <p>Test seam mirrors {@link ChangeTask}.
 */
public final class EquipDiaperTask implements NannyTask {

    private final java.util.function.Function<java.util.UUID, PlayerStats> statsLookup;
    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public EquipDiaperTask(Plugin plugin, NannyCareEngine engine) {
        this(plugin == null ? uuid -> null : plugin::getPlayerStats, engine, plugin);
    }

    /** Test-only constructor — supply a custom stats resolver. */
    public EquipDiaperTask(java.util.function.Function<java.util.UUID, PlayerStats> statsLookup,
                           NannyCareEngine engine, Plugin plugin) {
        this.statsLookup = statsLookup;
        this.engine = engine;
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "equip";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        PlayerStats stats = statsLookup.apply(ward.getUniqueId());
        if (stats == null) return null;
        if (stats.getUnderwearType() != 0) return null;
        return new Candidate(65, ward, ward.getLocation(), "equip");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        NannyInventoryManager inv = engine.getInventoryManager();
        ItemStack diaper = inv.takeOne(data, NannyInventoryManager::isCleanDiaper);
        if (diaper == null) {
            diaper = inv.tryCraftDiaper(data);
            if (diaper == null) {
                engine.warnLowSupplies(data, ward, "diapers");
                return Result.FAIL_RETRY;
            }
        }
        EquipArmor.applyEquip(ward, diaper);
        nanny.faceLocation(ward.getEyeLocation());
        NannyEventLog log = engine.getManager().getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.EQUIPPED_WARD, ward.getUniqueId(),
                    "cmd=" + (diaper.getItemMeta() != null && diaper.getItemMeta().hasCustomModelData()
                            ? diaper.getItemMeta().getCustomModelData() : 0));
        }
        engine.speakPostAction(ward, "equipped_diaper");
        return Result.DONE;
    }
}
