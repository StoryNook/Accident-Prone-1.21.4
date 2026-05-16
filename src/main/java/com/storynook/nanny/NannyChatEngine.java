package com.storynook.nanny;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    /** Categories whose lines feed the AI voice-exemplar block — prefix match. */
    private static final String[] VOICE_SAMPLE_PREFIXES = {"idle_", "keyword_"};
    /** Categories whose lines feed the AI voice-exemplar block — exact match. */
    private static final java.util.Set<String> VOICE_SAMPLE_EXACT =
            java.util.Set.of("greeting");

    private final Plugin plugin;
    private final NannyManager manager;

    /** category → tier → response list */
    private Map<String, Map<String, List<String>>> messages = new HashMap<>();

    /** Flattened fragment key → prose. Built from always_on: + capabilities: in nanny_personalities.yml. */
    private Map<String, String> personalities = new HashMap<>();

    /** nannyUUID → epoch millis of last response, for per-Nanny floor + ambient timer reset. */
    private final Map<UUID, Long> lastResponse = new HashMap<>();

    /** nannyUUID → epoch millis of last user-chat-initiated reply. Used only by
     *  {@link #fireTriggers} so that background lines (ambient, reminders,
     *  post-action commentary) don't consume the AI cost throttle. */
    private final Map<UUID, Long> lastChatReply = new HashMap<>();

    /** "{nannyUUID}:{throttleKey}:{wardUUID}" → epoch millis of last fire, per-trigger throttle. */
    private final Map<String, Long> triggerThrottle = new HashMap<>();

    /** nannyUUID → epoch millis when the next ambient line is allowed to fire. */
    private final Map<UUID, Long> nextAmbientFireAt = new HashMap<>();

    /**
     * Rolling AI chat history per (nanny, ward) pair. Each entry is a 2-tuple of
     * {role, content} where role is "user" (ward turn) or "assistant" (nanny turn).
     * Bounded to {@code Nanny_Chat_AI_Context_Chat_Count * 2} entries (one round
     * trip = one user + one assistant turn).
     *
     * <p>In-memory only. Lost on restart by design — conversation context is
     * ephemeral, and persisting would mean encrypting potentially sensitive
     * roleplay across sessions.
     */
    private final Map<String, Deque<String[]>> aiHistory = new HashMap<>();

    private final Random random = new Random();
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private org.bukkit.scheduler.BukkitTask reminderTask;
    private org.bukkit.scheduler.BukkitTask ambientTask;

    public NannyChatEngine(Plugin plugin, NannyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        loadMessages();
        loadPersonalities();
    }

    // -------------------------------------------------------------------
    // Message bank loading
    // -------------------------------------------------------------------

    public void reload() {
        loadMessages();
        loadPersonalities();
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

    private void loadPersonalities() {
        File file = new File(plugin.getDataFolder(), "nanny_personalities.yml");
        if (!file.exists()) {
            personalities = new HashMap<>();
            return;
        }
        org.bukkit.configuration.file.FileConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        personalities = loadPersonalitiesFromConfig(yaml);
        if (personalities.isEmpty()) {
            plugin.getLogger().warning(
                    "[Nanny AI] nanny_personalities.yml empty — AI ability list disabled");
        }
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
        if (speaker == null || message == null) { plugin.getLogger().info("[NannyChat] bail: speaker/message null"); return; }
        if (!plugin.citizensEnabled) { plugin.getLogger().info("[NannyChat] bail: citizens disabled"); return; }
        // Diagnostic: confirms the chat reached the engine. Comment out once stable.
        plugin.getLogger().info("[NannyChat] fireTriggers " + speaker.getName()
                + ": '" + truncate(message, 60) + "' activeNannies="
                + manager.getActiveNannies().size());

        int radius = configInt("Nanny_Chat_Local_Radius", 30);
        int minWords = configInt("Nanny_Chat_Min_Words", 3);
        int throttleSec = configInt("Nanny_Chat_AI_Cooldown_Seconds", 30);
        int ambientPct = configInt("Nanny_Chat_Ambient_Chance", 1);

        Location loc = speaker.getLocation();
        if (loc == null || loc.getWorld() == null) { plugin.getLogger().info("[NannyChat] bail: speaker location/world null"); return; }
        plugin.getLogger().info("[NannyChat] speaker at " + loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " — iterating " + manager.getActiveNannies().size() + " nanny entries");

        for (Map.Entry<UUID, NannyEntity> entry : manager.getActiveNannies().entrySet()) {
            UUID nannyUUID = entry.getKey();
            NannyEntity nanny = entry.getValue();
            NannyData data = nanny.getData();
            plugin.getLogger().info("[NannyChat] loop iter: nannyUUID=" + nannyUUID + " name=" + (data != null ? data.getName() : "<null data>"));

            try {
                Location here = nanny.getLocation();
                plugin.getLogger().info("[NannyChat]   here=" + (here == null ? "null" : here.getWorld().getName() + " " + here.getBlockX() + "," + here.getBlockY() + "," + here.getBlockZ()));
                if (here == null) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": not spawned"); continue; }
                if (!here.getWorld().equals(loc.getWorld())) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": different world"); continue; }
                double distSq = here.distanceSquared(loc);
                plugin.getLogger().info("[NannyChat]   distSq=" + distSq + " radiusSq=" + (radius * radius));
                if (distSq > radius * radius) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": out of range (" + Math.sqrt(distSq) + " > " + radius + ")"); continue; }
            } catch (Throwable t) {
                plugin.getLogger().warning("[NannyChat] EXCEPTION in location check for " + (data != null ? data.getName() : "?") + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
                continue;
            }

            NannyEventLog log = manager.getEventLog(nannyUUID);
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.WARD_CHAT, speaker.getUniqueId(),
                        truncate(message, 80));
            }

            if (!data.isChatEnabled()) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": chat disabled"); continue; }
            if (!isAllowedSpeaker(speaker, data)) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": speaker not in chatRespondTo=" + data.getChatRespondTo()); continue; }
            if (countWords(message) < minWords) { plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": only " + countWords(message) + " words (need " + minWords + ")"); continue; }

            long now = System.currentTimeMillis();
            // Cost-control throttle for user-initiated chat. Distinct from
            // lastResponse (which is poked by ambient/reminder background lines)
            // so that Nanny saying something autonomously doesn't block her
            // from replying to the user for 30 seconds afterwards.
            Long last = lastChatReply.get(nannyUUID);
            if (last != null && (now - last) < throttleSec * 1000L) {
                plugin.getLogger().info("[NannyChat] skip " + data.getName() + ": throttle active (" + ((now - last) / 1000L) + "s since last chat reply, need " + throttleSec + "s)");
                continue;
            }

            // BASIC tier: gate on Nanny name / keyword match — canned lines would feel
            // repetitive if every message triggered. AI tier bypasses the gate so the
            // model can reply conversationally to anything; the 30s throttle prevents
            // spam. On AI failure, respond() falls back to pickBasicLine(category, ...)
            // which returns null for the "general" sentinel — i.e. silent fallback.
            String category = pickCategory(message, data, ambientPct);
            boolean isAiTier = data.getChatTier() == NannyData.ChatTier.AI;
            if (!isAiTier && category == null) continue;
            if (category == null) category = "general";

            // Update both maps:
            // - lastChatReply guards future user-chat triggers (cost throttle)
            // - lastResponse lets background tasks (ambient idle) push their
            //   timer forward so they don't fire on top of the chat reply
            lastChatReply.put(nannyUUID, now);
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
        plugin.getLogger().info("[NannyChat] respond " + data.getName() + " tier=" + data.getChatTier() + " category=" + category);
        if (data.getChatTier() == NannyData.ChatTier.AI) {
            MembershipProvider provider = manager.getMembershipProvider();
            String endpoint = configString("Nanny_Chat_AI_Endpoint", "");
            boolean unlocked = provider != null && provider.isUnlocked(data.getOwnerUUID());
            plugin.getLogger().info("[NannyChat] AI check: providerClass=" + (provider != null ? provider.getClass().getSimpleName() : "null") + " unlocked=" + unlocked + " endpoint=" + (endpoint == null || endpoint.isEmpty() ? "EMPTY" : "set") + " owner=" + data.getOwnerUUID());
            if (unlocked && endpoint != null && !endpoint.isEmpty()) {
                plugin.getLogger().info("[NannyChat] AI request -> " + endpoint
                        + " model=" + configString("Nanny_Chat_AI_Model", "gpt-4o-mini")
                        + " for: '" + truncate(userMessage, 60) + "'");
                requestAiResponse(endpoint, data, speaker, userMessage, aiText -> {
                    plugin.getLogger().info("[NannyChat] AI response received: "
                            + (aiText == null ? "<null>" : aiText.isEmpty() ? "<empty>" : "'" + truncate(aiText, 60) + "'"));
                    // <SKIP> sentinel — the model decides not to reply. Per system
                    // prompt, model returns only "<SKIP>" (case-insensitive, optional
                    // whitespace) when the message doesn't warrant a response.
                    // Parse tags FIRST so SCORE/PUNISH/REWARD always apply, even when the
                    // visible reply is <SKIP>. The AI can score a message without engaging in chat.
                    ParseResult parsed = parseTagsForTest(aiText);
                    String cleanedText = parsed.cleanedText;
                    DisciplineDispatcher dispatcher = manager.getDisciplineDispatcher();
                    BehaviorScoreboard sb = manager.getBehaviorScoreboard();
                    for (ParsedTag tag : parsed.tags) {
                        if ("SCORE".equals(tag.type)) {
                            if (sb == null) continue;
                            try {
                                int delta = Integer.parseInt(tag.action);
                                if (delta > 15) delta = 15;
                                if (delta < -15) delta = -15;
                                int before = sb.getScore(data, speaker.getUniqueId());
                                sb.record(data, speaker.getUniqueId(), "ai_judgment", delta);
                                int after = sb.getScore(data, speaker.getUniqueId());
                                plugin.getLogger().info("[Discipline] AI score tag " + delta
                                        + " on " + speaker.getName() + ": " + before + " -> " + after);
                            } catch (NumberFormatException nfe) {
                                plugin.getLogger().info("[Discipline] bad SCORE tag: " + tag.action);
                            }
                        } else if ("PUNISH".equals(tag.type) || "REWARD".equals(tag.type)) {
                            if (dispatcher != null) {
                                Integer dur = tag.duration;
                                dispatcher.enactFromTag(data, speaker, tag.action, dur);
                            }
                        }
                    }

                    // After applying tags, check for SKIP semantics — either explicit "<SKIP>"
                    // or an entirely tag-only reply with no visible prose left.
                    String trimmed = cleanedText == null ? "" : cleanedText.trim();
                    if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("<SKIP>")) {
                        plugin.getLogger().info("[NannyChat] SKIP'd (tags applied: " + parsed.tags.size() + ")");
                        return;
                    }

                    String resolved;
                    if (cleanedText != null && !cleanedText.isEmpty()) {
                        resolved = applyPlaceholders(cleanedText, speaker, data);
                    } else {
                        String fallback = pickBasicLine(category, data);
                        resolved = fallback == null ? null : applyPlaceholders(fallback, speaker, data);
                    }
                    if (resolved == null) return;
                    broadcast(nanny, data, resolved);
                    // Record both sides of the turn for AI conversational continuity.
                    // The user message is recorded as-sent; the assistant turn is
                    // the actual broadcast text — that way the model sees what
                    // the player saw, even if we fell back to BASIC mid-stream.
                    recordAiTurn(data, speaker, "user", userMessage);
                    recordAiTurn(data, speaker, "assistant", resolved);
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

    /** Public adapter — lets other subsystems (BehaviorSignals punch path) have the Nanny say something. */
    public void speak(NannyEntity nanny, NannyData data, String line) {
        broadcast(nanny, data, line);
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
    // AI HTTP call — OpenAI chat-completions shape
    //
    // Compatible with: OpenAI, Ollama (/v1/chat/completions), LM Studio,
    // OpenRouter, vLLM, any provider that speaks the OpenAI chat API.
    //
    // Request:
    //   POST <endpoint>
    //   Authorization: Bearer <key>          (when API_Key is set)
    //   Content-Type: application/json
    //   {"model": "...", "messages": [...], "temperature": 0.8, "max_tokens": 200}
    //
    // The "messages" array is system prompt + per-(nanny, ward) rolling
    // history + current user turn. Response: choices[0].message.content.
    // -------------------------------------------------------------------

    private interface AiCallback { void onResult(String text); }

    private void requestAiResponse(String endpoint, NannyData data, Player ward,
                                   String userMessage, AiCallback callback) {
        final String model = configString("Nanny_Chat_AI_Model", "gpt-4o-mini");
        final String apiKey = configString("Nanny_Chat_AI_API_Key", "");
        final int historyTurns = configInt("Nanny_Chat_AI_Context_Chat_Count", 10);
        final String body = buildOpenAiRequestBody(model, data, ward, userMessage, historyTurns);

        aiExecutor.submit(() -> {
            String result = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                // LLMs are slower than typical HTTP — especially Ollama on
                // modest hardware. 30s read timeout is a reasonable ceiling
                // before we give up and fall back to BASIC.
                conn.setReadTimeout(30_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                String responseBody = readAll(is);

                if (code >= 200 && code < 300) {
                    result = extractAssistantContent(responseBody);
                    if (result == null || result.isEmpty()) {
                        // Thinking models (e.g. qwen3.5:9b) silently emit empty
                        // content when max_tokens cuts off mid-reasoning. Surface
                        // it so the admin can bump Max_Tokens or pick a different
                        // model instead of debugging "GPU spins but no chat".
                        plugin.getLogger().warning("[Nanny AI] empty content from "
                                + endpoint + " — if using a reasoning/thinking "
                                + "model (qwen3, deepseek-r1, etc.) bump "
                                + "Nanny.Chat.AI.Max_Tokens, otherwise pick a "
                                + "non-thinking model (llama3.1:8b, dolphin-llama3).");
                    }
                } else {
                    plugin.getLogger().warning("[Nanny AI] HTTP " + code + " from "
                            + endpoint + ": " + truncate(responseBody, 200));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[Nanny AI] " + e.getClass().getSimpleName()
                        + ": " + e.getMessage());
                result = null;
            } catch (RuntimeException e) {
                // JsonParseException + friends. Log and fall back.
                plugin.getLogger().warning("[Nanny AI] parse failed: " + e.getMessage());
                result = null;
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.onResult(finalResult));
        });
    }

    private String buildOpenAiRequestBody(String model, NannyData data, Player ward,
                                          String userMessage, int historyTurns) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.5);
        body.addProperty("max_tokens", configInt("Nanny_Chat_AI_Max_Tokens", 4000));

        JsonArray messages = new JsonArray();
        messages.add(makeMessage("system", buildSystemPrompt(data, ward)));

        // Replay the rolling history for this (nanny, ward) pair. Capped to
        // historyTurns rounds (== historyTurns * 2 entries) by recordAiTurn.
        Deque<String[]> hist = aiHistory.get(historyKey(data, ward));
        if (hist != null) {
            for (String[] turn : hist) {
                if (turn != null && turn.length == 2) {
                    messages.add(makeMessage(turn[0], turn[1]));
                }
            }
        }

        messages.add(makeMessage("user", userMessage));
        body.add("messages", messages);
        return body.toString();
    }

    private JsonObject makeMessage(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content == null ? "" : content);
        return m;
    }

    /**
     * Builds the system prompt sent as the first message of every AI request.
     * Assembles: identity → voice exemplars from nanny_messages.yml →
     * capability fragment block from nanny_personalities.yml (gated by
     * NannyPolicy or customSettings, then intersected with global feature flags)
     * → world/ward context → admin override.
     */
    private String buildSystemPrompt(NannyData data, Player ward) {
        String nannyName = (data == null || data.getName() == null) ? "Nanny" : data.getName();
        NannyData.MoodTier mood = (data == null || data.getMoodTier() == null)
                ? NannyData.MoodTier.CARING : data.getMoodTier();
        // For CUSTOM, the voice tier comes from customTone, not from "CUSTOM" itself.
        NannyData.MoodTier voiceTier = (mood == NannyData.MoodTier.CUSTOM)
                ? data.getCustomTone() : mood;
        String moodLabel = voiceTier.name().toLowerCase();
        String wardName = (ward == null || ward.getDisplayName() == null)
                ? "little one" : ward.getDisplayName();

        StringBuilder sb = new StringBuilder();
        // Hard rules first — these take priority over character.
        sb.append("MOST MESSAGES DO NOT NEED A REPLY. The default response is the ");
        sb.append("literal token <SKIP> (no quotes, no other text). Only generate a real ");
        sb.append("reply when ONE of these is clearly true:\n");
        sb.append("  1. The message mentions your name (\"").append(nannyName).append("\"), ");
        sb.append("or a caregiver tier like Nanny, Mommy, Mama.\n");
        sb.append("  2. The message is a question directed at you (contains \"?\" AND ");
        sb.append("seems to ask about something you would know or do).\n");
        sb.append("  3. The message is clearly a direct response to something you just ");
        sb.append("said in the previous turn of this conversation.\n\n");
        sb.append("ALL OTHER messages — server chatter, exclamations, single-word fragments, ");
        sb.append("observations about the world, messages addressed to other players, ");
        sb.append("broadcasts to nobody in particular — MUST return exactly <SKIP> with no ");
        sb.append("explanation.\n\n");
        sb.append("Examples that REQUIRE <SKIP>:\n");
        sb.append("  - \"oh dear\"\n");
        sb.append("  - \"banana\"\n");
        sb.append("  - \"anyone want diamonds?\" (broadcast, not to you)\n");
        sb.append("  - \"going mining brb\"\n");
        sb.append("  - \"Dude! those are my diamonds!\" (addressed to another player)\n");
        sb.append("  - \"lol\" / \"haha\" / \"nice\"\n\n");
        sb.append("Examples that warrant a real reply:\n");
        sb.append("  - \"").append(nannyName).append(" what are you doing?\"\n");
        sb.append("  - \"Why are you so mean?\"\n");
        sb.append("  - \"Mommy I'm hungry\"\n\n");
        sb.append("When (and only when) a real reply is warranted, here is who you are:\n\n");
        sb.append("You are ").append(nannyName)
          .append(", a ").append(moodLabel)
          .append(" caregiver NPC in a Minecraft roleplay server. ");
        sb.append("Your little is named \"").append(wardName).append("\". ");
        sb.append("Speak directly to them. Keep replies short — one or two ");
        sb.append("sentences, no roleplay actions in asterisks, no emoji. ");
        sb.append("Do not narrate or repeat back what they said. Do not break character. ");
        sb.append("You do not perform actions in your reply — the plugin handles care, ");
        sb.append("crafting, and inventory automatically. Speak only about what you would ");
        sb.append("say, not what you do.\n\n");
        sb.append("CRUCIAL: do NOT use phrases that imply an action is happening right ");
        sb.append("now unless the little explicitly asked for that action. Banned: ");
        sb.append("\"down the hatch\", \"eat up\", \"open wide\", \"here you go\", ");
        sb.append("\"hold still\", \"this'll just take a moment\", \"there you go\", ");
        sb.append("\"in you go\". The little reads these as a promise that an action is ");
        sb.append("about to happen and gets confused when nothing follows. If you would ");
        sb.append("normally say such a phrase, just describe how you feel about the ");
        sb.append("situation instead.");

        // --- Voice exemplars ---
        int sampleCount = configInt("Nanny_Chat_AI_Voice_Sample_Count", 6);
        java.util.List<String> samples = sampleVoiceLines(messages, voiceTier.name(),
                sampleCount, random);
        if (!samples.isEmpty()) {
            sb.append("\n\nSpeak in this style — here are example lines you have said before:");
            for (String line : samples) {
                sb.append("\n- \"").append(applyPlaceholders(line, ward, data)).append("\"");
            }
        }

        // --- Capability fragments ---
        java.util.List<String> fragments = resolveFragmentList(data, mood, ward);
        if (!fragments.isEmpty()) {
            sb.append("\n\nYour abilities:");
            for (String prose : fragments) {
                sb.append("\n- ").append(prose);
            }
        }

        // --- Inventory snapshot ---
        String inv = summarizeInventory(data);
        if (inv != null && !inv.isEmpty()) {
            sb.append("\n\nYour supplies right now:").append(inv);
        }

        // --- Behavior score + Tools + Tag instructions (AI tier only) ---
        if (data != null && data.getChatTier() == NannyData.ChatTier.AI) {
            sb.append(summarizeBehavior(data, ward));
            sb.append(summarizeTools(data, ward));
            sb.append(tagInstructions());
        }

        // --- Dynamic world/ward context (existing behavior) ---
        if (ward != null) {
            sb.append("\n\nCurrent context:");
            if (ward.getWorld() != null) {
                long t = ward.getWorld().getTime();
                String tw = (t < 6000) ? "morning" : (t < 12000) ? "afternoon"
                          : (t < 13000) ? "dusk" : "night";
                sb.append(" Time: ").append(tw).append('.');
                if (ward.getWorld().hasStorm()) sb.append(" Weather: raining.");
            }
            com.storynook.PlayerStatsManagement.PlayerStats stats =
                    plugin.getPlayerStats(ward.getUniqueId());
            if (stats != null) {
                if (stats.getDiaperWetness() > 50 || stats.getDiaperFullness() > 50) {
                    sb.append(" Your little's diaper is soiled.");
                }
                if (ward.getFoodLevel() < 10) sb.append(" Your little is hungry.");
                if (stats.getHydration() < 30) sb.append(" Your little is thirsty.");
            }
        }

        // --- Optional admin override ---
        String override = configString("Nanny_Chat_AI_System_Prompt", "");
        if (override != null && !override.isEmpty()) {
            sb.append("\n\n").append(override);
        }
        return sb.toString();
    }

    /**
     * Counts items in the Nanny's personal inventory grouped by category so
     * the AI knows what she actually has on hand. Without this the model
     * hallucinates ("I crafted one for you"). Returns a multi-line bullet
     * string starting with "\n- " for each non-zero category, or empty
     * string if everything is zero.
     */
    private String summarizeInventory(NannyData data) {
        if (data == null) return "";
        org.bukkit.inventory.ItemStack[] inv = data.getPersonalInventory();
        if (inv == null) return "";

        int cleanDiapers = 0, soiledDiapers = 0, food = 0, waterBottles = 0,
            emptyBottles = 0, laxatives = 0, hypnoClocks = 0, cursedDiapers = 0,
            coal = 0;
        for (org.bukkit.inventory.ItemStack stack : inv) {
            if (stack == null || stack.getType() == org.bukkit.Material.AIR) continue;
            int amt = stack.getAmount();
            if (NannyInventoryManager.isCleanDiaper(stack)) {
                // Could be a cursed (binding) diaper — flag separately
                if (stack.getItemMeta() != null
                        && stack.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.BINDING_CURSE)) {
                    cursedDiapers += amt;
                } else {
                    cleanDiapers += amt;
                }
            } else if (isSoiledDiaperIcon(stack)) {
                soiledDiapers += amt;
            } else if (isHypnoClock(stack)) {
                hypnoClocks += amt;
            } else if (isLaxative(stack)) {
                laxatives += amt;
            } else if (NannyInventoryManager.isWaterBottle(stack)) {
                waterBottles += amt;
            } else if (NannyInventoryManager.isEmptyGlassBottle(stack)) {
                emptyBottles += amt;
            } else if (NannyInventoryManager.isAnyFood(stack)) {
                food += amt;
            } else if (stack.getType() == org.bukkit.Material.COAL
                    || stack.getType() == org.bukkit.Material.CHARCOAL) {
                coal += amt;
            }
        }

        StringBuilder s = new StringBuilder();
        if (cleanDiapers > 0)   s.append("\n- ").append(cleanDiapers).append(" clean diaper").append(cleanDiapers == 1 ? "" : "s");
        if (cursedDiapers > 0)  s.append("\n- ").append(cursedDiapers).append(" binding-cursed diaper").append(cursedDiapers == 1 ? "" : "s");
        if (soiledDiapers > 0)  s.append("\n- ").append(soiledDiapers).append(" soiled diaper").append(soiledDiapers == 1 ? "" : "s").append(" (heading to the pail)");
        if (food > 0)           s.append("\n- ").append(food).append(" food item").append(food == 1 ? "" : "s");
        if (waterBottles > 0)   s.append("\n- ").append(waterBottles).append(" water bottle").append(waterBottles == 1 ? "" : "s");
        if (emptyBottles > 0)   s.append("\n- ").append(emptyBottles).append(" empty glass bottle").append(emptyBottles == 1 ? "" : "s");
        if (laxatives > 0)      s.append("\n- ").append(laxatives).append(" laxative").append(laxatives == 1 ? "" : "s");
        if (hypnoClocks > 0)    s.append("\n- ").append(hypnoClocks).append(" hypnosis clock").append(hypnoClocks == 1 ? "" : "s");
        if (coal > 0)           s.append("\n- ").append(coal).append(" coal");
        if (s.length() == 0)    s.append("\n- (empty — you cannot hand anything over until you craft or restock)");
        return s.toString();
    }

    private String summarizeBehavior(NannyData data, Player ward) {
        if (data == null || ward == null) return "";
        BehaviorScoreboard sb = manager.getBehaviorScoreboard();
        if (sb == null) return "";
        int score = sb.getScore(data, ward.getUniqueId());
        int streak = sb.getStreak(data, ward.getUniqueId());
        String gloss = scoreGloss(score);
        StringBuilder s = new StringBuilder();
        s.append("\nBehavior of \"").append(ward.getDisplayName() == null ? "little one" : ward.getDisplayName())
         .append("\" toward you:\n");
        s.append("  Score: ").append(score).append(" (").append(gloss).append(")\n");
        s.append("  Recent trend: ").append(streak > 0 ? "+" : "").append(streak);
        return s.toString();
    }

    private String scoreGloss(int score) {
        if (score >= 60)  return "darling — your favorite little";
        if (score >= 30)  return "behaving well";
        if (score >= -30) return "neutral, ordinary";
        if (score >= -60) return "trending naughty, time to consider correction";
        if (score >= -85) return "steady misbehavior, leaning toward punishment-worthy";
        return "deeply naughty, the maximum tools are appropriate";
    }

    private String summarizeTools(NannyData data, Player ward) {
        if (data == null) return "";
        StringBuilder s = new StringBuilder();
        s.append("\n\nTools you are currently permitted to use:");
        boolean any = false;
        for (Capability cap : Capability.values()) {
            if (!NannyPolicy.allows(data, cap)) continue;
            if (!isFeatureGloballyEnabled(cap)) continue;
            if (cap == Capability.BASIC_CARE || cap == Capability.POTTY_REMINDERS
                    || cap == Capability.CRIB_PLACEMENT || cap == Capability.BLOCK_CAREGIVERS
                    || cap == Capability.ARMOR_LOCK || cap == Capability.ROOM_LOCKDOWN
                    || cap == Capability.EVIL_CRAFTING) continue;  // care primitives, not discipline tools
            s.append("\n  - ").append(cap.name());
            if (cap == Capability.HYPNOSIS_USE) {
                String triggers = "(none)";
                if (ward != null) {
                    com.storynook.PlayerStatsManagement.PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
                    if (stats != null && stats.getHypnoTriggers() != null) {
                        triggers = resolveHypnoTriggers(stats.getHypnoTriggers());
                    }
                }
                s.append(" — active triggers: ").append(triggers);
            } else if (cap == Capability.DIAPER_PUNISHMENT) {
                s.append(" (1-30 days, you choose duration)");
            }
            any = true;
        }
        if (!any) {
            s.append("\n  (none — mood tier or feature flags forbid all discipline tools)");
        }
        // Active persistent punishments
        if (ward != null) {
            java.util.List<String> active = data.getActivePersistentPunishments()
                    .getOrDefault(ward.getUniqueId(), java.util.List.of());
            if (!active.isEmpty()) {
                s.append("\n\nCurrently active persistent punishments:");
                for (String a : active) s.append("\n  - ").append(a);
            }
        }
        return s.toString();
    }

    private String tagInstructions() {
        return "\n\n=== TAG SYSTEM (REQUIRED) ===\n"
             + "EVERY reply you produce — INCLUDING <SKIP> replies — MUST begin with a SCORE\n"
             + "tag on its own line. The tag is parsed and stripped before the player sees\n"
             + "the message. This is non-negotiable; the system uses your SCORE judgment to\n"
             + "track the player's trust over time.\n\n"
             + "Required SCORE scale (signed integer, -15 to +15, clamped):\n"
             + "  <SCORE:+0>                  - genuinely neutral, no behavior signal\n"
             + "  <SCORE:-3>                  - mildly rude or dismissive\n"
             + "  <SCORE:-5>                  - moderately naughty (refusing care, sass)\n"
             + "  <SCORE:-10>                 - clearly naughty (insult, hostility, defiance)\n"
             + "  <SCORE:-15>                 - egregious (slur, threat, deep cruelty)\n"
             + "  <SCORE:+2>                  - mildly nice (cooperative, polite request)\n"
             + "  <SCORE:+3>                  - nice (sincere thanks, kind words)\n"
             + "  <SCORE:+8>                  - very nice (affectionate, helpful)\n\n"
             + "Sycophancy guard: when you see the player at a deeply naughty score and they\n"
             + "suddenly turn sweet, that is suspicious — score it +0 or +1, not +5.\n\n"
             + "Optional discipline tags (use SPARINGLY, only when warranted by accumulated score):\n"
             + "  <PUNISH:laxative>           - force-feed a laxative\n"
             + "  <PUNISH:leash>              - leash to your hand\n"
             + "  <PUNISH:binding>            - equip a binding-cursed diaper\n"
             + "  <PUNISH:hypno>              - speak a hypno trigger word\n"
             + "  <PUNISH:diaper:Nd>          - diaper-punishment for N Minecraft days (1-30)\n"
             + "  <REWARD:praise>             - suppress your next queued punishment for 5 minutes\n\n"
             + "Examples of properly tagged replies:\n"
             + "  '<SCORE:-3> Watch your tone.'\n"
             + "  '<SCORE:+3> Good little one.'\n"
             + "  '<SCORE:+0> <SKIP>'  (no engagement, no behavior signal)\n"
             + "  '<SCORE:-10> Enough. <PUNISH:laxative>'\n"
             + "Never reply WITHOUT a SCORE tag.";
    }

    private boolean isSoiledDiaperIcon(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() != org.bukkit.Material.LEATHER_LEGGINGS) return false;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        int cmd = meta.getCustomModelData();
        return cmd == 626004 || cmd == 626005 || cmd == 626011
                || cmd == 626015 || cmd == 626016 || cmd == 626017 || cmd == 626018;
    }

    private boolean isHypnoClock(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() != org.bukkit.Material.CLOCK) return false;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "hypnosis"),
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private boolean isLaxative(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() != org.bukkit.Material.BREAD) return false;
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "laxative_effect"),
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /**
     * Resolves the ordered list of fragment prose strings to include in the
     * AI system prompt for {@code data}. Steps:
     * <ol>
     *   <li>Add the always-on fragment keys.</li>
     *   <li>Iterate Capability values; include if NannyPolicy allows (fixed
     *       moods) or customSettings has it true (CUSTOM).</li>
     *   <li>Intersect with globally-enabled feature flags so the AI never
     *       advertises tools the plugin cannot fire.</li>
     *   <li>Substitute {hypno_triggers} + standard placeholders in each prose.</li>
     * </ol>
     */
    private java.util.List<String> resolveFragmentList(
            NannyData data, NannyData.MoodTier mood, Player ward) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (personalities.isEmpty()) return result;

        // Always-on
        String[] alwaysOnKeys = {"BASIC_CARE", "WATER_REFILL", "ORE_SPOTTING"};
        java.util.Set<String> alreadyAdded = new java.util.HashSet<>();
        for (String key : alwaysOnKeys) {
            String prose = personalities.get(key);
            if (prose != null) {
                result.add(interpolateFragment(prose, ward, data));
                alreadyAdded.add(key);
            }
        }

        // Gated capabilities
        for (Capability cap : Capability.values()) {
            if (alreadyAdded.contains(cap.name())) continue;
            boolean granted;
            if (mood == NannyData.MoodTier.CUSTOM) {
                granted = Boolean.TRUE.equals(data.getCustomSettings().get(cap.name()));
            } else {
                granted = NannyPolicy.allows(data, cap);
            }
            if (!granted) continue;
            if (!isFeatureGloballyEnabled(cap)) continue;
            String prose = personalities.get(cap.name());
            if (prose != null) result.add(interpolateFragment(prose, ward, data));
        }
        return result;
    }

    /**
     * Cross-feature gate: returns false when the global feature flag
     * controlling a capability is off, so the AI never advertises tools the
     * plugin would refuse to fire. BASIC_CARE / POTTY_REMINDERS /
     * CRIB_PLACEMENT / ARMOR_LOCK / BLOCK_CAREGIVERS / LEASH_WARD /
     * ROOM_LOCKDOWN have no specific gating beyond the existing Nanny system
     * being on (they're caregiving primitives), so they always pass here.
     */
    private boolean isFeatureGloballyEnabled(Capability cap) {
        java.util.Map<String, Object> gc = plugin.getGlobalConfig();
        switch (cap) {
            case HYPNOSIS_USE:
                return Boolean.TRUE.equals(gc.get("Hypno"));
            case FORCE_FEED_LAXATIVE:
                return Boolean.TRUE.equals(gc.get("Messing"))
                    && Boolean.TRUE.equals(gc.get("Diapers"));
            case BINDING_LEGGINGS:
            case EVIL_CRAFTING:
                return Boolean.TRUE.equals(gc.get("Messing"))
                    && Boolean.TRUE.equals(gc.get("Diapers"))
                    && Boolean.TRUE.equals(gc.get("Binding_Diapers"));
            default:
                return true;
        }
    }

    /** Applies {hypno_triggers} + standard placeholders to a fragment. */
    private String interpolateFragment(String prose, Player ward, NannyData data) {
        if (prose == null) return "";
        if (prose.contains("{hypno_triggers}")) {
            java.util.List<com.storynook.PlayerStatsManagement.HypnoTrigger> triggers = null;
            if (ward != null) {
                com.storynook.PlayerStatsManagement.PlayerStats stats =
                        plugin.getPlayerStats(ward.getUniqueId());
                if (stats != null) triggers = stats.getHypnoTriggers();
            }
            prose = prose.replace("{hypno_triggers}", resolveHypnoTriggers(triggers));
        }
        return applyPlaceholders(prose, ward, data);
    }

    /**
     * Records one turn of an AI conversation. Bounded ring buffer per
     * (nanny, ward) pair; oldest entries fall off when the cap is reached.
     */
    private void recordAiTurn(NannyData data, Player ward, String role, String content) {
        if (data == null || ward == null || role == null || content == null) return;
        String key = historyKey(data, ward);
        Deque<String[]> hist = aiHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
        hist.addLast(new String[]{role, content});
        int maxEntries = Math.max(1, configInt("Nanny_Chat_AI_Context_Chat_Count", 10)) * 2;
        while (hist.size() > maxEntries) hist.removeFirst();
    }

    private String historyKey(NannyData data, Player ward) {
        return data.getNannyUUID() + ":" + ward.getUniqueId();
    }

    /**
     * Walks an OpenAI chat-completions response and extracts
     * {@code choices[0].message.content}. Returns null on any structural
     * mismatch — the caller falls back to BASIC.
     */
    private String extractAssistantContent(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return null;
        JsonElement root = JsonParser.parseString(responseBody);
        if (!root.isJsonObject()) return null;
        JsonElement choices = root.getAsJsonObject().get("choices");
        if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().isEmpty()) return null;
        JsonElement first = choices.getAsJsonArray().get(0);
        if (first == null || !first.isJsonObject()) return null;
        JsonElement message = first.getAsJsonObject().get("message");
        if (message == null || !message.isJsonObject()) return null;
        JsonElement content = message.getAsJsonObject().get("content");
        if (content == null || !content.isJsonPrimitive()) return null;
        String text = content.getAsString();
        return text == null ? null : text.trim();
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
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

    /**
     * Reads always_on: and capabilities: sections from a personalities YAML
     * and returns one flat fragment-key → prose map. Static so it can be
     * unit-tested without booting Bukkit.
     */
    static Map<String, String> loadPersonalitiesFromConfig(
            org.bukkit.configuration.file.FileConfiguration yaml) {
        Map<String, String> result = new HashMap<>();
        if (yaml == null) return result;
        for (String section : new String[]{"always_on", "capabilities"}) {
            org.bukkit.configuration.ConfigurationSection sec =
                    yaml.getConfigurationSection(section);
            if (sec == null) continue;
            for (String key : sec.getKeys(false)) {
                String prose = sec.getString(key);
                if (prose != null && !prose.isEmpty()) {
                    result.put(key, prose.trim());
                }
            }
        }
        return result;
    }

    /**
     * Collects every line for {@code tier} from message categories that are
     * voice-bearing (idle_*, keyword_*, greeting), then draws a flat random
     * sample of up to {@code count}. Static + Random-injected so it's
     * deterministically unit-testable.
     */
    static List<String> sampleVoiceLines(
            Map<String, Map<String, List<String>>> messages,
            String tier, int count, Random random) {
        if (count <= 0 || messages == null || tier == null) return new ArrayList<>();
        List<String> pool = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : messages.entrySet()) {
            String category = entry.getKey();
            if (!matchesVoiceCategory(category)) continue;
            Map<String, List<String>> byTier = entry.getValue();
            if (byTier == null) continue;
            List<String> lines = byTier.get(tier);
            if (lines != null) pool.addAll(lines);
        }
        if (pool.isEmpty()) return new ArrayList<>();
        Collections.shuffle(pool, random);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    private static boolean matchesVoiceCategory(String category) {
        if (VOICE_SAMPLE_EXACT.contains(category)) return true;
        for (String prefix : VOICE_SAMPLE_PREFIXES) {
            if (category.startsWith(prefix)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------
    // AI tag parsing — ParsedTag, ParseResult, parseTagsForTest
    // -------------------------------------------------------------------

    /** A single structured tag extracted from an AI reply (e.g. {@code <PUNISH:laxative>}, {@code <SCORE:-5>}). */
    public static class ParsedTag {
        public final String type;     // PUNISH, REWARD, or SCORE
        public final String action;   // laxative/leash/binding/hypno/diaper/praise OR signed integer for SCORE
        public final Integer duration; // null unless tag was <PUNISH:diaper:Nd>
        public ParsedTag(String type, String action, Integer duration) {
            this.type = type;
            this.action = action;
            this.duration = duration;
        }
    }

    /** Result of parsing an AI reply: visible text with tags stripped + the ordered tag list. */
    public static class ParseResult {
        public final String cleanedText;
        public final java.util.List<ParsedTag> tags;
        public ParseResult(String cleanedText, java.util.List<ParsedTag> tags) {
            this.cleanedText = cleanedText;
            this.tags = tags;
        }
    }

    /**
     * Regex: {@code <(PUNISH|REWARD|SCORE):(-?\d+|[a-z_]+)(?::(\d+)d)?>}
     * Group 1 = type (PUNISH/REWARD/SCORE), group 2 = action (named action for PUNISH/REWARD,
     * signed integer for SCORE), group 3 = duration digits (only for {@code <PUNISH:diaper:Nd>}).
     */
    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("<(PUNISH|REWARD|SCORE):(-?\\d+|[a-z_]+)(?::(\\d+)d)?>");

    /**
     * Parses {@code <PUNISH:...>} / {@code <REWARD:...>} tags out of an AI reply.
     * Tags are removed from the visible text; unknown tag shapes are left intact.
     * Package-visible (not private) so unit tests can call it without MockBukkit.
     */
    static ParseResult parseTagsForTest(String text) {
        java.util.List<ParsedTag> tags = new java.util.ArrayList<>();
        if (text == null) return new ParseResult("", tags);
        java.util.regex.Matcher m = TAG_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Integer dur = m.group(3) == null ? null : Integer.parseInt(m.group(3));
            tags.add(new ParsedTag(m.group(1), m.group(2), dur));
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return new ParseResult(sb.toString().trim().replaceAll("\\s+", " "), tags);
    }

    /**
     * Formats a list of active hypno triggers as a comma-joined
     * "{word} ({type})" string for the {hypno_triggers} placeholder. Returns
     * a friendly fallback when the list is null or empty.
     */
    static String resolveHypnoTriggers(
            java.util.List<com.storynook.PlayerStatsManagement.HypnoTrigger> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return "none currently active — the clock generates a random "
                 + "trigger word from your server's word list when used";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (com.storynook.PlayerStatsManagement.HypnoTrigger t : triggers) {
            if (t == null) continue;
            if (!first) sb.append(", ");
            sb.append(t.getWord()).append(" (").append(t.getType()).append(")");
            first = false;
        }
        return sb.toString();
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
