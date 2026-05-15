package com.storynook.nanny;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;
import com.storynook.Integrations.events.ActionId;

import net.citizensnpcs.api.CitizensAPI;

/**
 * Translates gameplay events into score deltas on a {@link BehaviorScoreboard}.
 *
 * <p>Hooked events:
 * <ul>
 *   <li>{@link EntityDamageByEntityEvent} — punch detection on Nanny NPCs.</li>
 *   <li>{@link AsyncPlayerChatEvent} — word-list matching against
 *       naughty_phrases / nice_phrases / compliance_keywords.</li>
 *   <li>{@link PlayerItemConsumeEvent} — water bottle / food consumption.</li>
 * </ul>
 *
 * <p>Word matching is via word-boundary regex (case-insensitive); see
 * {@link #containsAnyPhrase} which is a static pure function for unit testing.
 */
public class BehaviorSignals implements Listener {

    private final Plugin plugin;
    private final BehaviorScoreboard scoreboard;
    private final NannyManager nannyManager;

    private List<String> naughtyPhrases = List.of();
    private List<String> nicePhrases    = List.of();
    private List<String> complianceKeywords = List.of();

    /** Per-(speakerUUID, category) last-fired timestamps for chat throttling. */
    private final Map<String, Long> chatThrottle = new HashMap<>();

    private static final long NAUGHTY_THROTTLE_MS = 30_000L;
    private static final long NICE_THROTTLE_MS    = 60_000L;

    public BehaviorSignals(Plugin plugin, BehaviorScoreboard scoreboard, NannyManager nannyManager) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.nannyManager = nannyManager;
        loadWordLists();
    }

    public void loadWordLists() {
        File f = new File(plugin.getDataFolder(), "nanny_behavior_words.yml");
        if (!f.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(f);
        naughtyPhrases = y.getStringList("naughty_phrases");
        nicePhrases = y.getStringList("nice_phrases");
        complianceKeywords = y.getStringList("compliance_keywords");
    }

    /** Pure helper: word-boundary regex match against any phrase in the list. */
    public static boolean containsAnyPhrase(String message, List<String> phrases) {
        if (message == null || phrases == null || phrases.isEmpty()) return false;
        String lower = message.toLowerCase();
        for (String phrase : phrases) {
            String quoted = Pattern.quote(phrase.toLowerCase());
            if (Pattern.compile("\\b" + quoted + "\\b").matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Punch detection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!plugin.citizensEnabled) return;
        if (!CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) return;

        Player puncher = (Player) event.getDamager();
        for (NannyEntity nanny : nannyManager.getActiveNannies().values()) {
            if (!event.getEntity().equals(nanny.getNpcEntity())) continue;
            NannyData data = nanny.getData();
            scoreboard.record(data, puncher.getUniqueId(), "nanny_assaulted", -15);
            plugin.getIntegrationsBus().fire(puncher, ActionId.NANNY_ASSAULTED, puncher,
                    java.util.Map.of("nanny", data.getNannyUUID().toString()));
            // Cosmetic hurt anim
            try {
                org.bukkit.entity.Entity npcEntity = nanny.getNpcEntity();
                if (npcEntity != null) npcEntity.playEffect(org.bukkit.EntityEffect.HURT);
            } catch (Throwable ignored) {}
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Chat sentiment
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
        Player speaker = event.getPlayer();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handleChatOnMainThread(speaker, message));
    }

    private void handleChatOnMainThread(Player speaker, String message) {
        boolean naughty = containsAnyPhrase(message, naughtyPhrases);
        boolean nice    = containsAnyPhrase(message, nicePhrases);
        if (!naughty && !nice) return;

        int radius = 30;
        Object r = plugin.getGlobalConfig().get("Nanny_Chat_Local_Radius");
        if (r instanceof Number) radius = ((Number) r).intValue();

        for (NannyEntity nanny : nannyManager.getActiveNannies().values()) {
            org.bukkit.Location here = nanny.getLocation();
            if (here == null) continue;
            if (here.getWorld() == null || !here.getWorld().equals(speaker.getWorld())) continue;
            if (here.distanceSquared(speaker.getLocation()) > (double) radius * radius) continue;
            NannyData data = nanny.getData();
            if (naughty && checkThrottle(speaker.getUniqueId(), "naughty", NAUGHTY_THROTTLE_MS)) {
                scoreboard.record(data, speaker.getUniqueId(), "chat_naughty", -3);
                plugin.getIntegrationsBus().fire(speaker, ActionId.BEHAVIOR_NAUGHTY, speaker,
                        java.util.Map.of("delta", -3));
            }
            if (nice && checkThrottle(speaker.getUniqueId(), "nice", NICE_THROTTLE_MS)) {
                scoreboard.record(data, speaker.getUniqueId(), "chat_nice", +2);
                plugin.getIntegrationsBus().fire(speaker, ActionId.BEHAVIOR_NICE, speaker,
                        java.util.Map.of("delta", 2));
            }
        }
    }

    private boolean checkThrottle(UUID speaker, String category, long ms) {
        String key = speaker + ":" + category;
        long now = System.currentTimeMillis();
        Long last = chatThrottle.get(key);
        if (last != null && (now - last) < ms) return false;
        chatThrottle.put(key, now);
        return true;
    }

    // -------------------------------------------------------------------------
    // Proactive hydration / feeding
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!isWaterOrFood(item)) return;
        Player p = event.getPlayer();
        for (NannyEntity nanny : nannyManager.getActiveNannies().values()) {
            NannyData data = nanny.getData();
            if (!data.getOwnerUUID().equals(p.getUniqueId())
                    && !data.getWards().contains(p.getUniqueId())) continue;
            scoreboard.record(data, p.getUniqueId(), "proactive_consume", +1);
            plugin.getIntegrationsBus().fire(p, ActionId.BEHAVIOR_NICE, p,
                    java.util.Map.of("delta", 1, "reason", "consume"));
        }
    }

    private boolean isWaterOrFood(ItemStack item) {
        if (item == null) return false;
        if (NannyInventoryManager.isWaterBottle(item)) return true;
        if (NannyInventoryManager.isAnyFood(item)) return true;
        return false;
    }
}
