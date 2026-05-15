package com.storynook.nanny;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Integrations.events.ActionId;

/**
 * Diaper-punishment subsystem.
 *
 * <p>Owns the per-ward timer (target = absolute world tick), violation counting,
 * cursed-pants escalation, and expiration cleanup. Public API:
 *
 * <ul>
 *   <li>{@link #start(NannyData, Player, int)} — enact a new diaper-punishment on a ward.</li>
 *   <li>{@link #isBlocked(Player)} — query for Toilet.canRelieveOnToilet / pee+poop commands.</li>
 *   <li>{@link #recordViolation(Player)} — called by toilet block path.</li>
 *   <li>{@link #tick()} — internal scheduler tick (every 1200 ticks). Checks timer expiration.</li>
 * </ul>
 */
public class DiaperPunishment {

    public static final long TICKS_PER_MC_DAY = 24000L;
    public static final int  CURSED_PANTS_CMD = 626015;

    private final Plugin plugin;
    private BukkitTask tickTask;

    public DiaperPunishment(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start(NannyData data, Player ward, int requestedDays) {
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null) return;
        if (stats.isDiaperPunishment()) return; // already punished

        int min = configInt("Nanny_Behavior_Diaper_Punishment_Min_Days", 1);
        int max = configInt("Nanny_Behavior_Diaper_Punishment_Max_Days", 30);
        int days = clampDays(requestedDays, min, max);

        World w = ward.getWorld();
        long target = w.getFullTime() + daysToTicks(days);

        stats.setDiaperPunishment(true);
        stats.setDiaperPunishmentExpiresAtTick(target);
        stats.setDiaperPunishmentRemainingViolations(
                configInt("Nanny_Behavior_Diaper_Punishment_Violations_Before_Escalation", 3));
        BehaviorScoreboard sb = plugin.getNannyManager().getBehaviorScoreboard();
        if (sb != null) {
            stats.setDiaperPunishmentScoreAtStart(sb.getScore(data, ward.getUniqueId()));
        }
        stats.setDiaperPunishmentNannyUUID(data.getNannyUUID());
        stats.setDiaperPunishmentEscalated(false);

        // Force-equip binding-cursed thick diaper (CMD 626006 baseline, with binding curse)
        ItemStack dia = buildBindingThickDiaper();
        com.storynook.Commands.EquipArmor.applyEquip(ward, dia);
        stats.setUnderwearType(3);
        stats.setLayers(4);
        com.storynook.PlayerStatsManagement.SavePlayerStats.savePlayerStats(ward);

        plugin.getIntegrationsBus().fire(ward, ActionId.DIAPER_PUNISHMENT_STARTED, ward,
                java.util.Map.of("days", days, "nanny", data.getNannyUUID().toString()));
        NannyEventLog startLog = plugin.getNannyManager().getEventLog(data.getNannyUUID());
        if (startLog != null) startLog.log(NannyEventLog.NannyEventType.DIAPER_PUNISHMENT_STARTED,
                ward.getUniqueId(), "days=" + days);
        plugin.getLogger().info("[DiaperPunishment] started " + days + "d on " + ward.getName());
    }

    public boolean isBlocked(Player p) {
        if (p == null) return false;
        PlayerStats stats = plugin.getPlayerStats(p.getUniqueId());
        return stats != null && stats.isDiaperPunishment();
    }

    public void recordViolation(Player ward) {
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null || !stats.isDiaperPunishment()) return;

        int remaining = stats.getDiaperPunishmentRemainingViolations() - 1;
        stats.setDiaperPunishmentRemainingViolations(remaining);

        UUID nid = stats.getDiaperPunishmentNannyUUID();
        NannyData data = null;
        BehaviorScoreboard sb = plugin.getNannyManager().getBehaviorScoreboard();
        if (nid != null) {
            data = plugin.getNannyManager().getAllNannies().get(nid);
            if (data != null && sb != null) {
                sb.record(data, ward.getUniqueId(), "diaper_punishment_violated", -10);
            }
        }

        plugin.getIntegrationsBus().fire(ward, ActionId.DIAPER_PUNISHMENT_VIOLATED, ward,
                java.util.Map.of("remaining", remaining));
        if (data != null) {
            NannyEventLog violLog = plugin.getNannyManager().getEventLog(data.getNannyUUID());
            if (violLog != null) violLog.log(NannyEventLog.NannyEventType.DIAPER_PUNISHMENT_VIOLATED,
                    ward.getUniqueId(), "remaining=" + remaining);
        }

        int score = -1;
        if (data != null && sb != null) score = sb.getScore(data, ward.getUniqueId());
        int floor = configInt("Nanny_Behavior_Score_Floor", -100);
        if (shouldEscalate(remaining, score, floor)) escalate(ward);
        com.storynook.PlayerStatsManagement.SavePlayerStats.savePlayerStats(ward);
    }

    private void escalate(Player ward) {
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null || stats.isDiaperPunishmentEscalated()) return;

        ItemStack pants = buildCursedPants();
        com.storynook.Commands.EquipArmor.applyEquip(ward, pants);
        stats.setUnderwearType(0);
        stats.setLayers(0);
        stats.setDiaperPunishmentEscalated(true);

        plugin.getIntegrationsBus().fire(ward, ActionId.CURSED_PANTS_EQUIPPED, ward,
                java.util.Map.of("reason", "violation_escalation"));
        plugin.getIntegrationsBus().fire(ward, ActionId.DIAPER_PUNISHMENT_ESCALATED, ward,
                java.util.Map.of());
        UUID escalateNid = stats.getDiaperPunishmentNannyUUID();
        if (escalateNid != null) {
            NannyData escalateData = plugin.getNannyManager().getAllNannies().get(escalateNid);
            if (escalateData != null) {
                NannyEventLog escalateLog = plugin.getNannyManager().getEventLog(escalateData.getNannyUUID());
                if (escalateLog != null) escalateLog.log(NannyEventLog.NannyEventType.DIAPER_PUNISHMENT_ESCALATED,
                        ward.getUniqueId(), "");
            }
        }
        plugin.getLogger().info("[DiaperPunishment] ESCALATED to cursed pants for " + ward.getName());
    }

    private void expire(Player ward) {
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null || !stats.isDiaperPunishment()) return;

        // Strip cursed pants if equipped
        ItemStack leggings = ward.getInventory().getLeggings();
        if (leggings != null && leggings.getType() == Material.LEATHER_LEGGINGS
                && leggings.getItemMeta() != null && leggings.getItemMeta().isUnbreakable()) {
            ward.getInventory().setLeggings(null);
        }

        // Score credit
        UUID nid = stats.getDiaperPunishmentNannyUUID();
        if (nid != null) {
            NannyData data = plugin.getNannyManager().getAllNannies().get(nid);
            BehaviorScoreboard sb = plugin.getNannyManager().getBehaviorScoreboard();
            if (data != null && sb != null) {
                sb.record(data, ward.getUniqueId(), "diaper_punishment_expired", +10);
            }
        }

        stats.setDiaperPunishment(false);
        stats.setDiaperPunishmentExpiresAtTick(0L);
        stats.setDiaperPunishmentRemainingViolations(3);
        stats.setDiaperPunishmentScoreAtStart(0);
        stats.setDiaperPunishmentNannyUUID(null);
        stats.setDiaperPunishmentEscalated(false);

        plugin.getIntegrationsBus().fire(ward, ActionId.DIAPER_PUNISHMENT_EXPIRED, ward, java.util.Map.of());
        if (nid != null) {
            NannyData expireData = plugin.getNannyManager().getAllNannies().get(nid);
            if (expireData != null) {
                NannyEventLog expireLog = plugin.getNannyManager().getEventLog(expireData.getNannyUUID());
                if (expireLog != null) expireLog.log(NannyEventLog.NannyEventType.DIAPER_PUNISHMENT_EXPIRED,
                        ward.getUniqueId(), "");
            }
        }
        plugin.getLogger().info("[DiaperPunishment] expired for " + ward.getName());
        com.storynook.PlayerStatsManagement.SavePlayerStats.savePlayerStats(ward);
    }

    /** Called every 1200 ticks (60s) by the scheduler task. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerStats stats = plugin.getPlayerStats(p.getUniqueId());
            if (stats == null || !stats.isDiaperPunishment()) continue;
            if (p.getWorld().getFullTime() >= stats.getDiaperPunishmentExpiresAtTick()) {
                expire(p);
            }
        }
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
    }

    // -------------------------------------------------------------------------
    // Static helpers (pure, unit-testable)
    // -------------------------------------------------------------------------

    public static long daysToTicks(int days) {
        return (long) days * TICKS_PER_MC_DAY;
    }

    public static int clampDays(int requested, int min, int max) {
        return Math.max(min, Math.min(max, requested));
    }

    public static boolean shouldEscalate(int remainingStrikes, int score, int floor) {
        return remainingStrikes <= 0 || score <= floor;
    }

    public static ItemStack buildCursedPants() {
        ItemStack pants = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        ItemMeta meta = pants.getItemMeta();
        if (meta == null) return pants;
        meta.setCustomModelData(CURSED_PANTS_CMD);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.setUnbreakable(true);
        meta.setDisplayName(org.bukkit.ChatColor.DARK_RED + "Cursed Pants");
        meta.setLore(java.util.List.of(
                org.bukkit.ChatColor.GRAY + "Imposed by your Nanny as discipline.",
                org.bukkit.ChatColor.GRAY + "Indestructible. Cannot be removed."));
        pants.setItemMeta(meta);
        return pants;
    }

    private ItemStack buildBindingThickDiaper() {
        ItemStack diaper = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        ItemMeta meta = diaper.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(626006); // baseline thick diaper
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.setDisplayName(org.bukkit.ChatColor.DARK_PURPLE + "Punishment Diaper");
            diaper.setItemMeta(meta);
        }
        return diaper;
    }

    private int configInt(String key, int def) {
        Object v = plugin.getGlobalConfig().get(key);
        return (v instanceof Number) ? ((Number) v).intValue() : def;
    }
}
