package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.Event_Listeners.DiaperPail;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import com.storynook.nanny.NannyInventoryManager;
import com.storynook.nanny.NannyNavigator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Background task: deposit soiled diapers in a nearby pail. Priority 45 —
 * runs only when no care task (60+) is needed. Slightly higher than refill
 * (40) because a backed-up inventory blocks further changes.
 *
 * <p>Extracted from {@code NannyCareEngine.tryDepositSoiled}. Searches the
 * full home radius for a Pail_ ArmorStand; navigates if not adjacent and
 * deposits one item per tick once at the pail. Phase E will move nav
 * issuance to the arbiter.
 */
public final class DepositSoiledTask implements NannyTask {

    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public DepositSoiledTask(Plugin plugin, NannyCareEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public String id() {
        return "deposit";
    }

    /** sqrt(4.0) — matches the existing {@code pailDistSq > 4.0} adjacency check. */
    @Override
    public double actionRangeBlocks() {
        return 2.0;
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (nanny == null || engine == null) return null;
        NannyInventoryManager inv = engine.getInventoryManager();
        if (inv == null) return null;
        if (inv.countAvailable(data, NannyInventoryManager::isSoiledDiaper) <= 0) return null;

        Location nannyLoc = nanny.getLocation();
        if (nannyLoc == null || nannyLoc.getWorld() == null) return null;
        if (!nannyLoc.getWorld().equals(ward.getWorld())) return null;
        double homeR = data.getHomeRadius();
        if (nannyLoc.distanceSquared(ward.getLocation()) > homeR * homeR) return null;

        Location pail = engine.findNearestPail(nannyLoc, homeR);
        if (pail == null) return null;

        return new Candidate(45, ward, pail, "deposit");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        NannyInventoryManager inv = engine.getInventoryManager();
        int soiledCount = inv.countAvailable(data, NannyInventoryManager::isSoiledDiaper);
        if (soiledCount <= 0) return Result.DONE;

        Location nannyLoc = nanny.getLocation();
        if (nannyLoc == null || !nannyLoc.getWorld().equals(ward.getWorld())) return Result.FAIL_RETRY;

        double homeR = data.getHomeRadius();
        Location pail = engine.findNearestPail(nannyLoc, homeR);
        if (pail == null) return Result.FAIL_RETRY;

        double pailDistSq = pail.distanceSquared(nannyLoc);
        if (pailDistSq > 4.0) {
            // Navigate toward the pail. Phase E will hoist this to the arbiter.
            NannyNavigator nav = engine.getManager().getNavigator(data.getNannyUUID());
            if (nav == null) return Result.FAIL_RETRY;
            if (!nav.isNavigatingToward(pail, 4.0)) {
                nav.navigateTo(pail);
            }
            return Result.CONTINUE;
        }

        // Adjacent — deposit one soiled item this tick.
        ItemStack soiled = inv.takeOne(data, NannyInventoryManager::isSoiledDiaper);
        if (soiled == null) return Result.DONE;
        boolean ok = DiaperPail.deposit(nannyLoc, 3.0, soiled);
        if (!ok) {
            // Pail full or vanished — put the item back and bail.
            inv.addToPersonalInventory(data, soiled);
            return Result.FAIL_RETRY;
        }

        // After deposit, re-engage follow so she returns to the ward.
        if (data.isFollowMode()) {
            NannyNavigator nav = engine.getManager().getNavigator(data.getNannyUUID());
            if (nav != null) nav.setFollowTarget(ward);
        }

        // CONTINUE while more soiled items remain; DONE when empty.
        if (inv.countAvailable(data, NannyInventoryManager::isSoiledDiaper) <= 0) return Result.DONE;
        return Result.CONTINUE;
    }
}
