package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.nanny.DisciplineDispatcher;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import org.bukkit.entity.Player;

/**
 * Passive task: delegate Phase 5b discipline picks to the
 * {@link DisciplineDispatcher} (which uses behavior score, band thresholds,
 * stacking cap, and per-action cooldowns).
 *
 * <p>Priority 10 — the lowest of all registered tasks. The dispatcher itself
 * no-ops when no discipline is owed (score band {@code NONE}, praise grace,
 * or every candidate on cooldown), so it is safe for this task to "win"
 * arbitration whenever nothing more urgent is queued — the act() call is
 * essentially a free pulse in that case.
 *
 * <p>Extracted from {@code NannyCareEngine.tryDisciplineActions}. AI-tier
 * Nannies skip this task entirely; AI dispatches discipline via
 * {@code <PUNISH:...>} tags inside chat replies.
 */
public final class DisciplineTask implements NannyTask {

    private final NannyCareEngine engine;
    @SuppressWarnings("unused")
    private final Plugin plugin;

    public DisciplineTask(Plugin plugin, NannyCareEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public String id() {
        return "discipline";
    }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (engine == null) return null;
        // AI tier handles discipline directly in chat replies via <PUNISH:...>
        // tags — skip the Java cascade.
        if (data.getChatTier() == NannyData.ChatTier.AI) return null;

        DisciplineDispatcher dispatcher = engine.getManager().getDisciplineDispatcher();
        if (dispatcher == null) return null;

        // Per the task brief, this is the cheapest passive gate that mirrors
        // the pre-extraction guards. The dispatcher's pickAndEnactFromScore
        // is a no-op when the score band is NONE, praise grace is active, or
        // every action is in cooldown — so it is safe to return a low-priority
        // Candidate here and let act() defer the final decision. The Candidate
        // sits at priority 10 so it never preempts a real care task.
        return new Candidate(10, ward, ward.getLocation(), "discipline");
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        // Defensive — gates should already have filtered, but a guard avoids
        // an NPE if the arbiter dispatched and the engine was torn down between.
        if (engine == null) return Result.DONE;
        DisciplineDispatcher dispatcher = engine.getManager().getDisciplineDispatcher();
        if (dispatcher == null) return Result.DONE;
        dispatcher.pickAndEnactFromScore(data, ward);
        return Result.DONE;
    }
}
