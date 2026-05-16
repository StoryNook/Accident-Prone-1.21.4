package com.storynook.nanny.tasks;

import com.storynook.Plugin;
import com.storynook.nanny.DisciplineDispatcher;
import com.storynook.nanny.NannyCareEngine;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEntity;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Transient task injected by reactive event handlers (punch, curse, etc.) so a
 * discipline reaction can preempt whatever the Nanny is currently doing.
 *
 * <p>Priority 100 base, plus mood modifier read from
 * {@link NannyData#getMoodTier()}:
 * <ul>
 *   <li>SWEET: -15 (still reacts, yields to most care tasks above ~85)</li>
 *   <li>CARING: -10 (gentle default)</li>
 *   <li>STRICT: +0 (no modifier; pure base priority)</li>
 *   <li>WARDEN: +10 (will preempt even TableChangeTask + ChangeTask at 100/100)</li>
 *   <li>CUSTOM / null: 0 (treat as neutral)</li>
 * </ul>
 *
 * <p>The mood modifier only affects arbitration priority — <em>which</em>
 * discipline action runs is decided by {@link DisciplineDispatcher} based on
 * the cumulative behavior score the originating event already recorded.
 */
public final class ReactiveDisciplineTask implements NannyTask {

    public enum Severity { MINOR, MODERATE, SEVERE }

    @SuppressWarnings("unused")
    private final Plugin plugin;
    private final NannyCareEngine engine;
    private final UUID boundWardUUID;
    @SuppressWarnings("unused")
    private final Severity severity;
    private final String reason;

    public ReactiveDisciplineTask(Plugin plugin, NannyCareEngine engine,
                                   UUID boundWardUUID, Severity severity, String reason) {
        this.plugin = plugin;
        this.engine = engine;
        this.boundWardUUID = boundWardUUID;
        this.severity = severity;
        this.reason = reason;
    }

    @Override
    public String id() { return "reactive_discipline"; }

    @Override
    public Candidate evaluate(NannyEntity nanny, NannyData data, Player ward) {
        if (!ward.getUniqueId().equals(boundWardUUID)) return null;
        int priority = 100 + moodModifier(data);
        return new Candidate(priority, ward, ward.getLocation(), reason);
    }

    @Override
    public Result act(NannyEntity nanny, NannyData data, Player ward) {
        if (engine == null || engine.getManager() == null) return Result.DONE;
        DisciplineDispatcher dispatcher = engine.getManager().getDisciplineDispatcher();
        if (dispatcher == null) return Result.DONE;
        // The dispatcher uses the cumulative behavior score the originating
        // event already recorded; severity here is metadata, used by future
        // enactReactive(...) overrides but not by today's score-based pick.
        dispatcher.pickAndEnactFromScore(data, ward);
        return Result.DONE;
    }

    static int moodModifier(NannyData data) {
        if (data == null) return 0;
        NannyData.MoodTier mood = data.getMoodTier();
        if (mood == null) return 0;
        return switch (mood) {
            case SWEET -> -15;
            case CARING -> -10;
            case STRICT -> 0;
            case WARDEN -> 10;
            case CUSTOM -> 0;
        };
    }
}
