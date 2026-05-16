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
    /** Min ms between two punch-response lines from the same Nanny (avoid spam on rapid punches). */
    private static final long PUNCH_SPEAK_THROTTLE_MS = 4_000L;
    /** Per-nanny last-punch-speak timestamp. */
    private final Map<UUID, Long> lastPunchSpeak = new HashMap<>();
    private static final java.util.List<String> PUNCH_LINES = java.util.List.of(
            "Ow! That hurt!",
            "Don't you dare hit Nanny!",
            "Hands to yourself, young one.",
            "I am not your punching bag.",
            "Try that again and we'll have words.",
            "That is NOT how we behave.",
            "If you hit me again you'll be in serious trouble."
    );
    private final java.util.Random punchLineRandom = new java.util.Random();

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

    /**
     * Primary punch path — Citizens swallows {@code EntityDamageByEntityEvent} and
     * {@code NPCLeftClickEvent} for protected Player NPCs (the Nanny is one), so we
     * detect punches via the arm-swing animation + a 4-block ray-cast for the target.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(org.bukkit.event.player.PlayerAnimationEvent event) {
        try {
            if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
            if (!plugin.citizensEnabled) return;
            if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
            Player p = event.getPlayer();
            org.bukkit.util.RayTraceResult rtr = p.getWorld().rayTraceEntities(
                    p.getEyeLocation(), p.getEyeLocation().getDirection(), 4.0,
                    e -> e != null && !e.equals(p) && e instanceof org.bukkit.entity.LivingEntity);
            org.bukkit.entity.Entity target = rtr == null ? null : rtr.getHitEntity();
            if (target == null || !CitizensAPI.getNPCRegistry().isNPC(target)) return;
            applyPunchScore(p, target, "swing");
        } catch (Throwable t) {
            plugin.getLogger().warning("[BehaviorSignals] onPlayerAnimation error: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Fallback path for non-protected NPCs (other plugins' Citizens NPCs we might hit). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Nanny_Behavior_enabled"))) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!plugin.citizensEnabled) return;
        if (!CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) return;
        applyPunchScore((Player) event.getDamager(), event.getEntity(), "damage");
    }

    private void applyPunchScore(Player puncher, org.bukkit.entity.Entity npcEntity, String via) {
        for (NannyEntity nanny : nannyManager.getActiveNannies().values()) {
            if (!npcEntity.equals(nanny.getNpcEntity())) continue;
            NannyData data = nanny.getData();
            int before = scoreboard.getScore(data, puncher.getUniqueId());
            scoreboard.record(data, puncher.getUniqueId(), "nanny_assaulted", -15);
            int after = scoreboard.getScore(data, puncher.getUniqueId());
            plugin.getLogger().info("[BehaviorSignals] punch (" + via + ") -> score "
                    + before + " -> " + after + " on " + data.getName() + " by " + puncher.getName());
            plugin.getIntegrationsBus().fire(puncher, ActionId.NANNY_ASSAULTED, puncher,
                    java.util.Map.of("nanny", data.getNannyUUID().toString()));
            // Visual + audio feedback. Player NPCs ignore EntityEffect.HURT — Paper's
            // playHurtAnimation sends the proper packet for any entity including players,
            // but is not in spigot-api so we call it reflectively.
            try {
                org.bukkit.Location loc = npcEntity.getLocation();
                if (loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR,
                            loc.clone().add(0, 1, 0), 5, 0.2, 0.4, 0.2, 0.0);
                }
                // Knockback — away from puncher, slight upward pop.
                org.bukkit.util.Vector away = npcEntity.getLocation().toVector()
                        .subtract(puncher.getLocation().toVector());
                if (away.lengthSquared() > 0.0001) {
                    away.normalize().multiply(0.5).setY(0.25);
                    npcEntity.setVelocity(away);
                }
                try {
                    float yaw = puncher.getLocation().getYaw();
                    java.lang.reflect.Method m = npcEntity.getClass().getMethod("playHurtAnimation", float.class);
                    m.invoke(npcEntity, yaw);
                } catch (NoSuchMethodException nsm) {
                    // Spigot fallback — no hurt-anim packet API. Sound+particle still fire.
                }
            } catch (Throwable ignored) {}

            // Verbal scold — throttled per-nanny so rapid punches don't spam chat.
            // AI tier: route through the AI as a synthetic chat event so she scolds in character.
            // BASIC tier: random pick from a fixed pool.
            long now = System.currentTimeMillis();
            Long last = lastPunchSpeak.get(data.getNannyUUID());
            if (last == null || (now - last) >= PUNCH_SPEAK_THROTTLE_MS) {
                lastPunchSpeak.put(data.getNannyUUID(), now);
                NannyChatEngine chat = nannyManager.getChatEngine();
                if (chat != null) {
                    if (data.getChatTier() == NannyData.ChatTier.AI) {
                        chat.fireTriggers(puncher, "*just punched you in the face*");
                    } else {
                        String line = PUNCH_LINES.get(punchLineRandom.nextInt(PUNCH_LINES.size()));
                        chat.speak(nanny, data, line);
                    }
                }
            }
            // Punishment escalation: if the puncher is serving a diaper-punishment
            // from THIS Nanny, react by dosing them with water + laxative. Cooldowns
            // inside triggerPunishmentOverdose prevent spam.
            NannyCareEngine care = nannyManager.getCareEngine();
            if (care != null) care.triggerPunishmentOverdose(data, puncher);
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Chat sentiment
    // -------------------------------------------------------------------------

    // No ignoreCancelled — VentureChat cancels AsyncPlayerChatEvent and reposts
    // its own VentureChatEvent; we still want to score the speaker's words.
    @EventHandler(priority = EventPriority.MONITOR)
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
            // Speaker relationship gate. Now that VentureChat routing means the
            // Nanny hears chat from anyone in local range (not just owner+wards),
            // we must not score visitors against this Nanny — they aren't her
            // little, and a behavior record for a non-little is meaningless.
            // Owner chat is also unscored: the owner commands the Nanny, the
            // little is the one being judged.
            SpeakerRelationship rel = data.relationshipOf(speaker.getUniqueId());
            if (rel != SpeakerRelationship.LITTLE) continue;
            if (naughty && checkThrottle(speaker.getUniqueId(), "naughty", NAUGHTY_THROTTLE_MS)) {
                int before = scoreboard.getScore(data, speaker.getUniqueId());
                scoreboard.record(data, speaker.getUniqueId(), "chat_naughty", -3);
                int after = scoreboard.getScore(data, speaker.getUniqueId());
                plugin.getLogger().info("[BehaviorSignals] chat naughty -> score "
                        + before + " -> " + after + " on " + data.getName() + " by " + speaker.getName());
                plugin.getIntegrationsBus().fire(speaker, ActionId.BEHAVIOR_NAUGHTY, speaker,
                        java.util.Map.of("delta", -3));
                // Punishment escalation: naughty chat during active diaper-punishment
                // triggers an extra dose. Cooldowns inside the method gate spam.
                NannyCareEngine care = nannyManager.getCareEngine();
                if (care != null) care.triggerPunishmentOverdose(data, speaker);
            }
            if (nice && checkThrottle(speaker.getUniqueId(), "nice", NICE_THROTTLE_MS)) {
                int before = scoreboard.getScore(data, speaker.getUniqueId());
                scoreboard.record(data, speaker.getUniqueId(), "chat_nice", +2);
                int after = scoreboard.getScore(data, speaker.getUniqueId());
                plugin.getLogger().info("[BehaviorSignals] chat nice -> score "
                        + before + " -> " + after + " on " + data.getName() + " by " + speaker.getName());
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
