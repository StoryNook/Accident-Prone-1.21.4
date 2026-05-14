package com.storynook.Integrations;

import com.storynook.Integrations.events.AccidentProneActionEvent;
import com.storynook.Integrations.events.ActionId;
import com.storynook.PlayerStatsManagement.PlayerStats;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IntegrationsBus {

    private interface StatePredicate {
        boolean test(Player worker, Player target, Map<String, Object> ctx, Map<String, Object> icfg);
    }

    private static class Descriptor {
        final String featureFlagKey;
        final boolean requiresCaregiverRelationship;
        final StatePredicate state;
        final String cooldownKey;
        final String scope;
        Descriptor(String f, boolean cg, StatePredicate s, String ck, String sc) {
            featureFlagKey = f; requiresCaregiverRelationship = cg; state = s;
            cooldownKey = ck; scope = sc;
        }
    }

    private static final Map<String, Descriptor> TABLE = new HashMap<String, Descriptor>();
    static {
        TABLE.put(ActionId.CHANGE, new Descriptor("Caregivers", true,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    int wet = ((Number) c.getOrDefault("wetness", 0)).intValue();
                    int full = ((Number) c.getOrDefault("fullness", 0)).intValue();
                    int minW = ((Number) i.getOrDefault("Caregiver_Min_Wetness", 30)).intValue();
                    int minF = ((Number) i.getOrDefault("Caregiver_Min_Fullness", 30)).intValue();
                    return (wet >= minW || full >= minF) && (w != t);
                }
            }, "Cooldown_Change_Seconds", "pair"));

        TABLE.put(ActionId.FEED, new Descriptor("Caregivers", true,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    int hyd = ((Number) c.getOrDefault("hydration", 100)).intValue();
                    int below = ((Number) i.getOrDefault("Caregiver_Feed_Below", 70)).intValue();
                    return hyd < below && w != t;
                }
            }, "Cooldown_Feed_Seconds", "pair"));

        TABLE.put(ActionId.PAIL_FILL, new Descriptor("Diapers", false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    return Boolean.TRUE.equals(c.get("dirtyConsumed"));
                }
            }, "Cooldown_Pail_Fill_Seconds", "worker"));

        TABLE.put(ActionId.WASH_PANTS, new Descriptor("Diapers", false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    return Boolean.TRUE.equals(c.get("dirtyPantsConsumed"));
                }
            }, "Cooldown_Wash_Pants_Seconds", "worker"));

        TABLE.put(ActionId.EQUIP_ARMOR_ON_LITTLE, new Descriptor("Caregivers", true,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    return w != t && Boolean.TRUE.equals(c.get("slotWasEmpty"));
                }
            }, "Cooldown_Equip_Seconds", "pair_slot"));

        TABLE.put(ActionId.CRAFT_UNDERWEAR, new Descriptor("Diapers", false, null, null, "worker"));
        TABLE.put(ActionId.CRAFT_PULLUP, new Descriptor("Diapers", false, null, null, "worker"));
        TABLE.put(ActionId.CRAFT_DIAPER, new Descriptor("Diapers", false, null, null, "worker"));
        TABLE.put(ActionId.CRAFT_THICK_DIAPER, new Descriptor("Diapers", false, null, null, "worker"));
        TABLE.put(ActionId.CRAFT_CRIB, new Descriptor(null, false, null, null, "worker"));
        TABLE.put(ActionId.CRAFT_WASHER, new Descriptor("Diapers", false, null, null, "worker"));

        TABLE.put(ActionId.TOILET_RELIEF, new Descriptor("Accidents", false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    int b = ((Number) c.getOrDefault("bladder", 0)).intValue();
                    int p = ((Number) c.getOrDefault("bowels", 0)).intValue();
                    return b >= 50 || p >= 50;
                }
            }, "Cooldown_Toilet_Relief_Seconds", "worker_stat"));

        TABLE.put(ActionId.ACCIDENT_HANDLED, new Descriptor("Accidents", false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    Boolean voluntary = (Boolean) c.get("isVoluntary");
                    int ut = ((Number) c.getOrDefault("underwearType", 0)).intValue();
                    return ut > 0 && !Boolean.TRUE.equals(voluntary);
                }
            }, "Cooldown_Accident_Handled_Seconds", "worker"));

        TABLE.put(ActionId.HYDRATE_THRESHOLD, new Descriptor(null, false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    int hyd = ((Number) c.getOrDefault("hydration", 0)).intValue();
                    int thr = ((Number) i.getOrDefault("Hydrate_Threshold", 80)).intValue();
                    return hyd >= thr;
                }
            }, "Cooldown_Hydrate_Threshold_Seconds", "worker"));

        TABLE.put(ActionId.CARRY_PICKUP, new Descriptor("Caregivers", true, null,
            "Cooldown_Carry_Pickup_Seconds", "pair"));

        TABLE.put(ActionId.CARRY_DROP, new Descriptor("Caregivers", true, null,
            "Cooldown_Carry_Drop_Seconds", "pair"));

        TABLE.put(ActionId.HIGHCHAIR_PLACE, new Descriptor("Nursery_Items", false, null,
            "Cooldown_Highchair_Place_Seconds", "pair"));

        TABLE.put(ActionId.CHANGE_ON_TABLE, new Descriptor("Nursery_Items", false,
            new StatePredicate() {
                public boolean test(Player w, Player t, Map<String, Object> c, Map<String, Object> i) {
                    int wet = ((Number) c.getOrDefault("wetness", 0)).intValue();
                    int full = ((Number) c.getOrDefault("fullness", 0)).intValue();
                    return wet > 0 || full > 0;
                }
            }, "Cooldown_Change_On_Table_Seconds", "pair"));

        TABLE.put(ActionId.STOCK_CHANGING_TABLE, new Descriptor("Nursery_Items", false,
            null, "Cooldown_Stock_Changing_Table_Seconds", "worker"));
    }

    private final IIntegrationsBusHost host;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<String, Long>();

    public IntegrationsBus(IIntegrationsBusHost host) { this.host = host; }

    public void fire(Player worker, String actionId, Player target, Map<String, Object> ctx) {
        if (worker == null || actionId == null) return;
        Map<String, Object> icfg = host.getIntegrationsConfig();
        Map<String, Object> gcfg = host.getGlobalConfig();
        if (icfg == null) return;
        if (!Boolean.TRUE.equals(icfg.get("Integrations_enabled"))) return;

        Descriptor d = TABLE.get(actionId);
        if (d == null) return;

        if (d.featureFlagKey != null && gcfg != null
                && !Boolean.TRUE.equals(gcfg.get(d.featureFlagKey))) return;

        Map<String, Object> safeCtx = ctx == null ? new HashMap<String, Object>() : ctx;
        if (d.state != null && !d.state.test(worker, target, safeCtx, icfg)) return;

        if (d.requiresCaregiverRelationship) {
            if (target == null) return;
            PlayerStats targetStats = host.getPlayerStats(target.getUniqueId());
            if (targetStats == null) return;
            if (!targetStats.isCaregiver(worker.getUniqueId(), true)) return;
        }

        if (d.cooldownKey != null) {
            String key = makeCooldownKey(actionId, worker, target, safeCtx, d.scope);
            long now = System.currentTimeMillis();
            long windowMs = ((Number) icfg.getOrDefault(d.cooldownKey, 60)).longValue() * 1000L;
            Long last = cooldowns.get(key);
            if (last != null && (now - last) < windowMs) return;
            cooldowns.put(key, now);
        }

        AccidentProneActionEvent ev = new AccidentProneActionEvent(worker, target, actionId, safeCtx);
        host.getServer().getPluginManager().callEvent(ev);
    }

    private String makeCooldownKey(String actionId, Player worker, Player target,
                                    Map<String, Object> ctx, String scope) {
        String w = worker.getUniqueId().toString();
        if ("pair".equals(scope) && target != null) {
            return actionId + ":pair:" + w + ":" + target.getUniqueId();
        }
        if ("pair_slot".equals(scope) && target != null) {
            String slot = String.valueOf(ctx.getOrDefault("slot", "?"));
            return actionId + ":pair_slot:" + w + ":" + target.getUniqueId() + ":" + slot;
        }
        if ("worker_stat".equals(scope)) {
            String stat = String.valueOf(ctx.getOrDefault("stat", "?"));
            return actionId + ":worker_stat:" + w + ":" + stat;
        }
        return actionId + ":worker:" + w;
    }

    public void clearCooldownsForPlayer(UUID workerUuid) {
        if (workerUuid == null) return;
        String needle = workerUuid.toString();
        cooldowns.keySet().removeIf(k -> k.contains(needle));
    }

    public void clearAllCooldowns() { cooldowns.clear(); }
}
