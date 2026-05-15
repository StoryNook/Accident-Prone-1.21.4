package com.storynook.nanny;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.entity.Player;

import com.storynook.Plugin;

/**
 * Shared decision/enactment surface for Nanny discipline.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #pickAndEnactFromScore} — BASIC tier. Computes the score band,
 *       picks one eligible action (persistent vs event, respecting stacking
 *       cap), enacts via the existing care-engine methods.</li>
 *   <li>{@link #enactFromTag} — AI tier. Tag from AI reply (e.g.
 *       {@code <PUNISH:laxative>}) routes to the same per-action enact methods.
 *       Validates capability allowed + cooldown + stacking cap.</li>
 * </ul>
 *
 * <p>Both share the same per-action enact methods, which delegate to public
 * adapters on {@link NannyCareEngine}.
 */
public class DisciplineDispatcher {

    public enum Band { NONE, WARN, MODERATE, SERIOUS, SEVERE, FLOOR }

    private static final List<String> PERSISTENT_ACTIONS =
            List.of("LEASH_WARD", "BINDING_LEGGINGS", "DIAPER_PUNISHMENT");
    private static final List<String> EVENT_ACTIONS =
            List.of("FORCE_FEED_LAXATIVE", "HYPNOSIS_USE");

    private final Plugin plugin;
    private final BehaviorScoreboard scoreboard;
    private final NannyCareEngine careEngine;
    private final Random random = new Random();

    public DisciplineDispatcher(Plugin plugin, BehaviorScoreboard scoreboard, NannyCareEngine careEngine) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.careEngine = careEngine;
    }

    // -------------------------------------------------------------------------
    // Static decision helpers — pure functions for unit testing
    // -------------------------------------------------------------------------

    public static Band computeBand(int score, Map<String, Integer> thresholds) {
        int warn     = thresholds.getOrDefault("warn", -20);
        int moderate = thresholds.getOrDefault("moderate", -40);
        int serious  = thresholds.getOrDefault("serious", -65);
        int severe   = thresholds.getOrDefault("severe", -90);
        int floor    = thresholds.getOrDefault("floor", -100);
        if (score <= floor)    return Band.FLOOR;
        if (score <= severe)   return Band.SEVERE;
        if (score <= serious)  return Band.SERIOUS;
        if (score <= moderate) return Band.MODERATE;
        if (score <= warn)     return Band.WARN;
        return Band.NONE;
    }

    public static int persistentSlotCap(Band band) {
        switch (band) {
            case NONE:
            case WARN:     return 0;
            case MODERATE: return 1;
            case SERIOUS:  return 2;
            case SEVERE:   return 3;
            case FLOOR:    return 3;
        }
        return 0;
    }

    public static int severityRank(String action) {
        switch (action) {
            case "LEASH_WARD":         return 1;
            case "BINDING_LEGGINGS":   return 2;
            case "DIAPER_PUNISHMENT":  return 3;
            default:                   return 0;
        }
    }

    public static String pickLeastSevere(List<String> active) {
        return active.stream()
                .min((a, b) -> Integer.compare(severityRank(a), severityRank(b)))
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // BASIC entry point
    // -------------------------------------------------------------------------

    public void pickAndEnactFromScore(NannyData data, Player ward) {
        int score = scoreboard.getScore(data, ward.getUniqueId());
        Band band = computeBand(score, thresholdsFromConfig());
        if (band == Band.NONE) return;

        // Praise grace check — recent AI <REWARD:praise> suppresses Java picks
        Long graceUntil = data.getPraiseGraceUntil().get(ward.getUniqueId());
        if (graceUntil != null && graceUntil > System.currentTimeMillis()) return;

        int slotCap = persistentSlotCap(band);
        List<String> activePersistent = data.getActivePersistentPunishments()
                .getOrDefault(ward.getUniqueId(), List.of());

        // Persistent path
        if (activePersistent.size() < slotCap) {
            List<String> candidates = new ArrayList<>();
            for (String act : PERSISTENT_ACTIONS) {
                if (activePersistent.contains(act)) continue;
                if (!isAllowed(data, act)) continue;
                if (isInCooldown(data, ward, act)) continue;
                candidates.add(act);
            }
            if (!candidates.isEmpty()) {
                String pick = candidates.get(random.nextInt(candidates.size()));
                enact(data, ward, pick, null);
                return;
            }
        }

        // Event path
        List<String> events = new ArrayList<>();
        for (String act : EVENT_ACTIONS) {
            if (!isAllowed(data, act)) continue;
            if (isInCooldown(data, ward, act)) continue;
            events.add(act);
        }
        if (!events.isEmpty()) {
            String pick = events.get(random.nextInt(events.size()));
            enact(data, ward, pick, null);
        }
    }

    // -------------------------------------------------------------------------
    // AI entry point
    // -------------------------------------------------------------------------

    /**
     * @param tagAction one of: laxative, leash, binding, hypno, diaper, praise
     * @param duration  for "diaper" only — days, clamped to configured min/max; ignored otherwise
     */
    public void enactFromTag(NannyData data, Player ward, String tagAction, Integer duration) {
        String capName = mapTagToCapability(tagAction);
        if (capName == null) {
            if ("praise".equals(tagAction)) {
                int seconds = configInt("Nanny_Behavior_Praise_Grace_Seconds", 300);
                data.getPraiseGraceUntil().put(ward.getUniqueId(),
                        System.currentTimeMillis() + seconds * 1000L);
                plugin.getLogger().info("[Discipline] praise grace set for "
                        + ward.getName() + " (" + seconds + "s)");
                return;
            }
            plugin.getLogger().info("[Discipline] unknown tag action: " + tagAction);
            return;
        }
        if (!isAllowed(data, capName)) {
            plugin.getLogger().info("[Discipline] tag " + tagAction + " disallowed by policy");
            return;
        }
        if (isInCooldown(data, ward, capName)) {
            plugin.getLogger().info("[Discipline] tag " + tagAction + " on cooldown");
            return;
        }
        enact(data, ward, capName, duration);
    }

    // -------------------------------------------------------------------------
    // Enactment
    // -------------------------------------------------------------------------

    private void enact(NannyData data, Player ward, String capName, Integer duration) {
        boolean persistent = PERSISTENT_ACTIONS.contains(capName);

        // Apply cooldown immediately so concurrent enacts can't double-fire
        long cooldownMs = configInt("Nanny_Behavior_Discipline_Cooldown_Minutes", 5) * 60_000L;
        data.getDisciplineCooldowns()
                .computeIfAbsent(ward.getUniqueId(), k -> new HashMap<>())
                .put(capName, System.currentTimeMillis() + cooldownMs);

        if (persistent) {
            data.getActivePersistentPunishments()
                    .computeIfAbsent(ward.getUniqueId(), k -> new ArrayList<>())
                    .add(capName);
        }

        switch (capName) {
            case "FORCE_FEED_LAXATIVE": careEngine.publicForceFeedLaxative(data, ward); break;
            case "LEASH_WARD":          careEngine.publicLeash(data, ward); break;
            case "BINDING_LEGGINGS":    careEngine.publicEquipBindingLeggings(data, ward); break;
            case "HYPNOSIS_USE":        careEngine.publicHypnotize(data, ward); break;
            case "DIAPER_PUNISHMENT":
                int days = duration == null
                        ? randomDays()
                        : Math.max(configInt("Nanny_Behavior_Diaper_Punishment_Min_Days", 1),
                                   Math.min(configInt("Nanny_Behavior_Diaper_Punishment_Max_Days", 30), duration));
                plugin.getDiaperPunishment().start(data, ward, days);
                break;
        }
    }

    private int randomDays() {
        int min = configInt("Nanny_Behavior_Diaper_Punishment_Min_Days", 1);
        int max = configInt("Nanny_Behavior_Diaper_Punishment_Max_Days", 30);
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private boolean isAllowed(NannyData data, String capName) {
        try {
            Capability cap = Capability.valueOf(capName);
            return NannyPolicy.allows(data, cap);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isInCooldown(NannyData data, Player ward, String capName) {
        Map<String, Long> m = data.getDisciplineCooldowns().get(ward.getUniqueId());
        if (m == null) return false;
        Long until = m.get(capName);
        return until != null && until > System.currentTimeMillis();
    }

    private String mapTagToCapability(String tagAction) {
        switch (tagAction) {
            case "laxative": return "FORCE_FEED_LAXATIVE";
            case "leash":    return "LEASH_WARD";
            case "binding":  return "BINDING_LEGGINGS";
            case "hypno":    return "HYPNOSIS_USE";
            case "diaper":   return "DIAPER_PUNISHMENT";
            default:         return null;
        }
    }

    private Map<String, Integer> thresholdsFromConfig() {
        Map<String, Integer> t = new HashMap<>();
        t.put("warn",     configInt("Nanny_Behavior_Score_Threshold_Warn", -20));
        t.put("moderate", configInt("Nanny_Behavior_Score_Threshold_Moderate", -40));
        t.put("serious",  configInt("Nanny_Behavior_Score_Threshold_Serious", -65));
        t.put("severe",   configInt("Nanny_Behavior_Score_Threshold_Severe", -90));
        t.put("floor",    configInt("Nanny_Behavior_Score_Floor", -100));
        return t;
    }

    private int configInt(String key, int def) {
        Object v = plugin.getGlobalConfig().get(key);
        return (v instanceof Number) ? ((Number) v).intValue() : def;
    }
}
