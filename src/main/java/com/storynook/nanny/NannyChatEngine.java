package com.storynook.nanny;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.storynook.Plugin;

/**
 * Drives Nanny chat reactions.
 *
 * <p>Listens for {@link AsyncPlayerChatEvent} (skipped when VentureChat is
 * present — {@code NannyVentureChatHook} delegates back here via
 * {@link #fireTriggers(Player, String)}).
 *
 * <p>For each active Nanny within {@code Nanny_Chat_Local_Radius} of the
 * speaker:
 * <ol>
 *   <li>Log WARD_CHAT.</li>
 *   <li>If {@code chatEnabled} is on and {@code chatRespondTo} permits the
 *       speaker, evaluate triggers (name mention / keyword / ambient roll).</li>
 *   <li>Throttle per-Nanny; skip messages with too few words.</li>
 *   <li>BASIC tier: pick a mood-keyed line from {@code nanny_messages.yml},
 *       broadcast it within local radius, log NANNY_CHAT.</li>
 *   <li>AI tier: gated on {@link MembershipProvider#isUnlocked(UUID)}.
 *       AlwaysLockedProvider always returns false, so AI never fires in
 *       Phase 4. On any failure → silent fallback to BASIC.</li>
 * </ol>
 */
public class NannyChatEngine implements Listener {

    /** Priority constants for {@link #speakIfNearby}. Higher wins on same-tick collision. */
    public static final int PRI_DISCIPLINE = 5;
    public static final int PRI_MOB        = 4;
    public static final int PRI_ACCIDENT   = 4;
    public static final int PRI_CARE       = 3;
    public static final int PRI_LIFECYCLE  = 2;
    public static final int PRI_DISCOVERY  = 1;
    public static final int PRI_IDLE       = 0;

    /** Floor between any two broadcasts from the same Nanny — keeps her from blurting two lines back-to-back. */
    private static final long MIN_LINE_GAP_MS = 3000L;

    /** Ambient idle timer bounds — each cycle picks a random delay in this range, resets on any broadcast. */
    private static final long AMBIENT_MIN_MS = 4L * 60L * 1000L;
    private static final long AMBIENT_MAX_MS = 6L * 60L * 1000L;

    /** Chat keyword stem → message category. LinkedHashMap so first match wins. */
    private static final java.util.Map<String, String> KEYWORD_MAP;
    static {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("wet", "keyword_wet");
        m.put("messy", "keyword_messy");
        m.put("hungry", "keyword_hungry");
        m.put("thirsty", "keyword_thirsty");
        m.put("sleepy", "keyword_sleep");
        m.put("sleep", "keyword_sleep");
        m.put("tired", "keyword_tired");
        m.put("cute", "keyword_cute");
        m.put("scared", "keyword_scared");
        m.put("lost", "keyword_lost");
        m.put("bored", "keyword_bored");
        m.put("stuck", "keyword_stuck");
        m.put("mommy", "keyword_mommy");
        m.put("mama", "keyword_mommy");
        m.put("bath", "keyword_bath");
        m.put("play", "keyword_play");
        m.put("story", "keyword_story");
        m.put("cuddle", "keyword_cuddle");
        m.put("hurt", "keyword_hurt");
        m.put("help", "keyword_help");
        m.put("nanny", "keyword_nanny");
        KEYWORD_MAP = java.util.Collections.unmodifiableMap(m);
    }

    private final Plugin plugin;
    private final NannyManager manager;

    /** category → tier → response list */
    private Map<String, Map<String, List<String>>> messages = new HashMap<>();

    /** nannyUUID → epoch millis of last response, for per-Nanny floor + ambient timer reset. */
    private final Map<UUID, Long> lastResponse = new HashMap<>();

    /** "{nannyUUID}:{throttleKey}:{wardUUID}" → epoch millis of last fire, per-trigger throttle. */
    private final Map<String, Long> triggerThrottle = new HashMap<>();

    /** nannyUUID → epoch millis when the next ambient line is allowed to fire. */
    private final Map<UUID, Long> nextAmbientFireAt = new HashMap<>();

    private final Random random = new Random();
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private org.bukkit.scheduler.BukkitTask reminderTask;
    private org.bukkit.scheduler.BukkitTask ambientTask;

    public NannyChatEngine(Plugin plugin, NannyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        loadMessages();
    }

    // -------------------------------------------------------------------
    // Message bank loading
    // -------------------------------------------------------------------

    public void reload() {
        loadMessages();
    }

    private void loadMessages() {
        Map<String, Map<String, List<String>>> next = new HashMap<>();
        File file = new File(plugin.getDataFolder(), "nanny_messages.yml");
        if (!file.exists()) {
            messages = next;
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String category : yaml.getKeys(false)) {
            Object catObj = yaml.get(category);
            if (!(catObj instanceof ConfigurationSection)) continue;
            ConfigurationSection sec = (ConfigurationSection) catObj;
            Map<String, List<String>> tierMap = new HashMap<>();
            for (String tier : sec.getKeys(false)) {
                List<String> lines = sec.getStringList(tier);
                if (lines != null && !lines.isEmpty()) {
                    tierMap.put(tier.toUpperCase(), lines);
                }
            }
            next.put(category, tierMap);
        }
        messages = next;
    }

    // -------------------------------------------------------------------
    // Chat trigger entry points
    // -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (plugin.VentureChat) return; // dedup — NannyVentureChatHook handles it
        Player speaker = event.getPlayer();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> fireTriggers(speaker, message));
    }

    public void fireTriggers(Player speaker, String message) {
        if (speaker == null || message == null) return;
        if (!plugin.citizensEnabled) return;

        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        int minWords = configInt("Nanny_Chat_Min_Words", 3);
        int throttleSec = configInt("Nanny_Chat_AI_Cooldown_Seconds", 30);
        int ambientPct = configInt("Nanny_Chat_Ambient_Chance", 1);

        Location loc = speaker.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        for (Map.Entry<UUID, NannyEntity> entry : manager.getActiveNannies().entrySet()) {
            UUID nannyUUID = entry.getKey();
            NannyEntity nanny = entry.getValue();
            NannyData data = nanny.getData();

            Location here = nanny.getLocation();
            if (here == null) continue;
            if (!here.getWorld().equals(loc.getWorld())) continue;
            if (here.distanceSquared(loc) > radius * radius) continue;

            NannyEventLog log = manager.getEventLog(nannyUUID);
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.WARD_CHAT, speaker.getUniqueId(),
                        truncate(message, 80));
            }

            if (!data.isChatEnabled()) continue;
            if (!isAllowedSpeaker(speaker, data)) continue;
            if (countWords(message) < minWords) continue;

            long now = System.currentTimeMillis();
            Long last = lastResponse.get(nannyUUID);
            if (last != null && (now - last) < throttleSec * 1000L) continue;

            String category = pickCategory(message, data, ambientPct);
            if (category == null) continue;

            lastResponse.put(nannyUUID, now);
            respond(speaker, data, nanny, category, message);
        }
    }

    // -------------------------------------------------------------------
    // Trigger evaluation
    // -------------------------------------------------------------------

    private boolean isAllowedSpeaker(Player speaker, NannyData data) {
        UUID id = speaker.getUniqueId();
        switch (data.getChatRespondTo()) {
            case OWNER:
                return id.equals(data.getOwnerUUID());
            case LISTED:
                return id.equals(data.getOwnerUUID())
                        || (data.getChatListedPlayers() != null
                            && data.getChatListedPlayers().contains(id))
                        || data.getWards().contains(id);
            case ANYONE:
            default:
                return true;
        }
    }

    private int countWords(String message) {
        if (message == null) return 0;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    private String pickCategory(String message, NannyData data, int ambientPct) {
        String lower = message.toLowerCase();
        String name = data.getName() == null ? "" : data.getName().toLowerCase();
        if (!name.isEmpty() && lower.contains(name)) {
            return "greeting";
        }
        for (java.util.Map.Entry<String, String> e : KEYWORD_MAP.entrySet()) {
            if (lower.contains(e.getKey())) {
                if (messages.containsKey(e.getValue())) return e.getValue();
            }
        }
        // Idle ambient is now timer-driven (D3) — chat path no longer rolls for it
        return null;
    }

    // -------------------------------------------------------------------
    // Response dispatch (BASIC + AI)
    // -------------------------------------------------------------------

    private void respond(Player speaker, NannyData data, NannyEntity nanny,
                         String category, String userMessage) {
        if (data.getChatTier() == NannyData.ChatTier.AI) {
            MembershipProvider provider = manager.getMembershipProvider();
            String endpoint = configString("Nanny_Chat_AI_Endpoint", "");
            boolean unlocked = provider != null && provider.isUnlocked(data.getOwnerUUID());
            if (unlocked && endpoint != null && !endpoint.isEmpty()) {
                requestAiResponse(endpoint, data, userMessage, aiText -> {
                    if (aiText != null && !aiText.isEmpty()) {
                        broadcast(nanny, data, applyPlaceholders(aiText, speaker, data));
                    } else {
                        String fallback = pickBasicLine(category, data);
                        if (fallback != null) broadcast(nanny, data, applyPlaceholders(fallback, speaker, data));
                    }
                });
                return;
            }
        }
        String line = pickBasicLine(category, data);
        if (line != null) broadcast(nanny, data, applyPlaceholders(line, speaker, data));
    }

    private String pickBasicLine(String category, NannyData data) {
        Map<String, List<String>> byTier = messages.get(category);
        if (byTier == null || byTier.isEmpty()) return null;
        String tierKey = data.getMoodTier() != null
                ? data.getMoodTier().name() : "CARING";
        if (tierKey.equals("CUSTOM")) tierKey = "CARING";
        List<String> lines = byTier.get(tierKey);
        if (lines == null || lines.isEmpty()) lines = byTier.get("CARING");
        if (lines == null || lines.isEmpty()) return null;
        return lines.get(random.nextInt(lines.size()));
    }

    /**
     * Substitutes {@code {ward}}, {@code {you}}, {@code {nanny}}, {@code {biome}},
     * and {@code {time}} placeholders in a YAML line. Lines without placeholders
     * pass through unchanged.
     */
    private String applyPlaceholders(String line, Player ward, NannyData data) {
        if (line == null) return null;
        if (line.indexOf('{') < 0) return line;
        String wardName = (ward != null && ward.getDisplayName() != null)
                ? ward.getDisplayName() : "little one";
        String nannyName = (data != null && data.getName() != null)
                ? data.getName() : "Nanny";
        line = line.replace("{ward}", wardName);
        line = line.replace("{you}", "you");
        line = line.replace("{nanny}", nannyName);
        if (line.contains("{biome}") && ward != null && ward.getLocation() != null) {
            org.bukkit.block.Biome b = ward.getLocation().getBlock().getBiome();
            line = line.replace("{biome}", b.name().toLowerCase().replace('_', ' '));
        }
        if (line.contains("{time}") && ward != null && ward.getWorld() != null) {
            long t = ward.getWorld().getTime();
            String tw;
            if (t < 6000) tw = "morning";
            else if (t < 12000) tw = "afternoon";
            else if (t < 13000) tw = "dusk";
            else tw = "night";
            line = line.replace("{time}", tw);
        }
        return line;
    }

    /**
     * Single entry point for all event-driven Nanny speech. For each active
     * Nanny within {@code Nanny_Chat_Local_Radius} of {@code ward}:
     * <ol>
     *   <li>Skip if {@code chatEnabled} is off or {@code chatRespondTo} excludes the ward</li>
     *   <li>Skip if the per-(nanny, throttleKey, ward) throttle has not elapsed</li>
     *   <li>Skip if the per-Nanny min-gap has not elapsed (prevents back-to-back lines)</li>
     *   <li>Pick a mood-keyed line from {@code nanny_messages.yml}, substitute placeholders</li>
     *   <li>Broadcast in local radius, log NANNY_CHAT</li>
     * </ol>
     *
     * @param priority  Used by future phases to break same-tick ties between
     *                  competing triggers. Currently informational; the
     *                  per-trigger throttle + min-gap already prevent overlap
     *                  in practice.
     */
    public void speakIfNearby(Player ward, String category,
                              String throttleKey, long throttleMs, int priority) {
        if (ward == null || category == null) return;
        if (!plugin.citizensEnabled) return;
        Location loc = ward.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        double r2 = (double) radius * radius;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, NannyEntity> entry : manager.getActiveNannies().entrySet()) {
            UUID nannyUUID = entry.getKey();
            NannyEntity nanny = entry.getValue();
            NannyData data = nanny.getData();
            if (!data.isChatEnabled()) continue;

            Location here = nanny.getLocation();
            if (here == null) continue;
            if (!here.getWorld().equals(loc.getWorld())) continue;
            if (here.distanceSquared(loc) > r2) continue;

            if (!isAllowedSpeaker(ward, data)) continue;

            // Per-Nanny min-gap: don't fire two lines back-to-back regardless of category
            Long lastAny = lastResponse.get(nannyUUID);
            if (lastAny != null && (now - lastAny) < MIN_LINE_GAP_MS) continue;

            // Per-(nanny, throttleKey, ward) throttle
            String tk = nannyUUID + ":" + throttleKey + ":" + ward.getUniqueId();
            Long last = triggerThrottle.get(tk);
            if (last != null && (now - last) < throttleMs) continue;

            String line = pickBasicLine(category, data);
            if (line == null) continue;
            line = applyPlaceholders(line, ward, data);

            triggerThrottle.put(tk, now);
            lastResponse.put(nannyUUID, now);
            broadcast(nanny, data, line);
        }
    }

    private void broadcast(NannyEntity nanny, NannyData data, String line) {
        if (line == null || line.isEmpty()) return;
        Location here = nanny.getLocation();
        if (here == null || here.getWorld() == null) return;
        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        double r2 = radius * radius;
        String formatted = ChatColor.LIGHT_PURPLE + "[" + data.getName() + "] "
                + ChatColor.WHITE + line;
        for (Player nearby : here.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(here) <= r2) {
                nearby.sendMessage(formatted);
            }
        }
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.NANNY_CHAT, null, truncate(line, 80));
        }
        // Push the ambient timer forward — any line counts as "she just spoke"
        nextAmbientFireAt.put(data.getNannyUUID(),
                System.currentTimeMillis() + randomAmbientDelay());
    }

    private long randomAmbientDelay() {
        return AMBIENT_MIN_MS
                + (long) (random.nextDouble() * (AMBIENT_MAX_MS - AMBIENT_MIN_MS));
    }

    // -------------------------------------------------------------------
    // AI HTTP call
    // -------------------------------------------------------------------

    private interface AiCallback { void onResult(String text); }

    private void requestAiResponse(String endpoint, NannyData data, String userMessage,
                                   AiCallback callback) {
        aiExecutor.submit(() -> {
            String result = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                String body = "{\"name\":\"" + jsonEscape(data.getName())
                        + "\",\"mood\":\"" + (data.getMoodTier() != null
                                ? data.getMoodTier().name() : "CARING")
                        + "\",\"message\":\"" + jsonEscape(userMessage) + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStream is = conn.getInputStream();
                         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        char[] buf = new char[1024];
                        int n;
                        while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
                    }
                    result = sb.toString().trim();
                }
            } catch (IOException e) {
                result = null;
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(finalResult));
        });
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    // -------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------

    private int configInt(String key, int def) {
        Object o = plugin.getGlobalConfig().get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        return def;
    }

    private String configString(String key, String def) {
        Object o = plugin.getGlobalConfig().get(key);
        return (o instanceof String) ? (String) o : def;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public void shutdown() {
        stopReminderTask();
        stopAmbientTask();
        aiExecutor.shutdownNow();
    }

    /**
     * Starts the ambient idle-chat task. Each Nanny gets her own per-cycle
     * delay in [AMBIENT_MIN_MS, AMBIENT_MAX_MS]. Any successful broadcast —
     * from this task, {@link #fireTriggers}, {@link #speakIfNearby},
     * or {@link #tickReminders} — pushes the next fire time forward via the
     * {@link #broadcast} hook, so we never get "she just spoke, now rambles".
     * Fires {@code idle_following} / {@code idle_raining} / {@code idle_night}
     * / {@code idle_outdoors} / {@code idle_at_home} based on context, with
     * fallback to {@code idle_ambient} if a specific sub-context is empty.
     */
    public void startAmbientTask() {
        if (ambientTask != null) return;
        ambientTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::tickAmbient, 100L, 100L);
    }

    public void stopAmbientTask() {
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
    }

    private void tickAmbient() {
        if (!plugin.citizensEnabled) return;
        long now = System.currentTimeMillis();
        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        double r2 = (double) radius * radius;

        for (Map.Entry<UUID, NannyEntity> entry : manager.getActiveNannies().entrySet()) {
            UUID nannyUUID = entry.getKey();
            NannyEntity nanny = entry.getValue();
            NannyData data = nanny.getData();
            if (!data.isChatEnabled()) continue;

            Long next = nextAmbientFireAt.get(nannyUUID);
            if (next == null) {
                // First sighting — schedule the first fire
                nextAmbientFireAt.put(nannyUUID, now + randomAmbientDelay());
                continue;
            }
            if (now < next) continue;

            Player target = findAmbientTarget(nanny, data, r2);
            if (target == null) {
                // No one in range — push the timer forward 30s and try again
                nextAmbientFireAt.put(nannyUUID, now + 30_000L);
                continue;
            }

            String category = pickIdleCategory(target, data);
            // Fire through speakIfNearby with a near-zero per-trigger throttle —
            // the per-Nanny MIN_LINE_GAP_MS still gates back-to-back lines.
            // broadcast() resets the timer; if speakIfNearby drops the call
            // (no line / chatEnabled off / out of range), schedule the next try.
            speakIfNearby(target, category, "ambient", 1L, PRI_IDLE);
            // Whether or not it broadcast, the next attempt should be one
            // ambient cycle away — broadcast() already set this when successful;
            // when it didn't, set it explicitly.
            if (!nextAmbientFireAt.containsKey(nannyUUID)
                    || nextAmbientFireAt.get(nannyUUID) <= now) {
                nextAmbientFireAt.put(nannyUUID, now + randomAmbientDelay());
            }
        }
    }

    private Player findAmbientTarget(NannyEntity nanny, NannyData data, double r2) {
        Location here = nanny.getLocation();
        if (here == null || here.getWorld() == null) return null;

        // Owner first if in range
        Player owner = Bukkit.getPlayer(data.getOwnerUUID());
        if (owner != null && owner.isOnline()
                && owner.getWorld().equals(here.getWorld())
                && owner.getLocation().distanceSquared(here) <= r2) {
            return owner;
        }
        // Otherwise any ward in range
        for (UUID wardUUID : data.getWards()) {
            Player p = Bukkit.getPlayer(wardUUID);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().equals(here.getWorld())) continue;
            if (p.getLocation().distanceSquared(here) > r2) continue;
            return p;
        }
        return null;
    }

    private String pickIdleCategory(Player target, NannyData data) {
        String want;
        if (data.isFollowMode()) {
            want = "idle_following";
        } else {
            org.bukkit.World world = target.getWorld();
            if (world == null) return "idle_ambient";
            long time = world.getTime();
            boolean isNight = time >= 13000 && time < 23000;
            boolean isRaining = world.hasStorm();
            // Sky-light proxy for "outdoors" — > 8 means open sky access
            boolean isOutdoors = target.getLocation().getBlock().getLightFromSky() > 8;

            if (isOutdoors && isRaining) want = "idle_raining";
            else if (isOutdoors && isNight) want = "idle_night";
            else if (isOutdoors) want = "idle_outdoors";
            else want = "idle_at_home";
        }
        // Fall back to generic ambient if the sub-context isn't populated yet
        return messages.containsKey(want) ? want : "idle_ambient";
    }

    /**
     * Starts the 60-second potty-reminder loop. For each active Nanny with
     * {@link Capability#POTTY_REMINDERS}, broadcasts a random
     * {@code care_reminder} line when a nearby ward's bladder OR bowels
     * exceed the urgency threshold (>= 70). Throttled by the existing
     * per-Nanny {@code lastResponse} map.
     */
    public void startReminderTask() {
        if (reminderTask != null) return;
        reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickReminders, 1200L, 1200L);
    }

    public void stopReminderTask() {
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
    }

    private void tickReminders() {
        if (!plugin.citizensEnabled) return;
        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        int throttleSec = configInt("Nanny_Chat_AI_Cooldown_Seconds", 30);
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, NannyEntity> entry : manager.getActiveNannies().entrySet()) {
            UUID nannyUUID = entry.getKey();
            NannyEntity nanny = entry.getValue();
            NannyData data = nanny.getData();

            if (!data.isChatEnabled()) continue;
            if (!NannyPolicy.allows(data, Capability.POTTY_REMINDERS)) continue;

            Long last = lastResponse.get(nannyUUID);
            if (last != null && (now - last) < throttleSec * 1000L) continue;

            Location here = nanny.getLocation();
            if (here == null || here.getWorld() == null) continue;

            Player target = null;
            double r2 = radius * radius;
            for (UUID wardUUID : data.getWards()) {
                Player p = Bukkit.getPlayer(wardUUID);
                if (p == null || !p.isOnline()) continue;
                if (!p.getWorld().equals(here.getWorld())) continue;
                if (p.getLocation().distanceSquared(here) > r2) continue;
                com.storynook.PlayerStatsManagement.PlayerStats stats = plugin.getPlayerStats(wardUUID);
                if (stats == null) continue;
                if (stats.getBladder() >= 70 || stats.getBowels() >= 70) {
                    target = p;
                    break;
                }
            }
            if (target == null) continue;

            String line = pickBasicLine("care_reminder", data);
            if (line == null) continue;
            lastResponse.put(nannyUUID, now);
            broadcast(nanny, data, applyPlaceholders(line, target, data));
        }
    }
}
