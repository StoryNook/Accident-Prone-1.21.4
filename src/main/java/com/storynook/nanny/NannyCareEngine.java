package com.storynook.nanny;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.Event_Listeners.FeedingAction;
import com.storynook.Commands.EquipArmor;
import com.storynook.nanny.tasks.ChangeTask;
import com.storynook.nanny.tasks.DepositSoiledTask;
import com.storynook.nanny.tasks.EquipDiaperTask;
import com.storynook.nanny.tasks.FeedTask;
import com.storynook.nanny.tasks.HydrateTask;
import com.storynook.nanny.tasks.RefillBottleTask;
import com.storynook.nanny.tasks.Result;

/**
 * Per-tick polling loop that drives autonomous Nanny care actions.
 *
 * <p>Runs at 40 ticks (matching {@code UpdateStats}). For each active Nanny ×
 * each online ward, builds a candidate list from every registered
 * {@link com.storynook.nanny.tasks.NannyTask} and dispatches the winning task
 * via {@link NannyTaskArbiter}. The four care behaviors (change, equip, feed,
 * hydrate) are registered as task classes under {@code com.storynook.nanny.tasks}.
 *
 * <p>The no-care branch ({@code doPlaceInCrib}, {@code tryDisciplineActions},
 * {@code tryReactiveScans}, {@code tryOreSpotting}) still lives inline.
 * Background refill/deposit are dispatched via
 * {@link com.storynook.nanny.tasks.RefillBottleTask} and
 * {@link com.storynook.nanny.tasks.DepositSoiledTask}.
 * Phases C/D extract the remainder into task classes.
 *
 * <p>See spec docs/superpowers/specs/2026-05-16-nanny-task-dispatch-design.md.
 */
public class NannyCareEngine {

    private final Plugin plugin;
    private final NannyManager manager;
    private final NannyInventoryManager inventoryManager;
    private final NannyTaskArbiter arbiter = new NannyTaskArbiter();
    /**
     * HydrateTask instance held for direct invocation by
     * {@link #triggerPunishmentOverdose(NannyData, Player)} — the punishment
     * pathway forces hydration regardless of threshold and bypasses the
     * arbiter. Still registered in arbiter (separate instance is fine; tasks
     * are stateless).
     */
    private HydrateTask hydrateTask;
    /** wardUUID → epoch millis of last hypnotize attempt; throttle to once per 5 min. */
    private final java.util.Map<java.util.UUID, Long> lastHypnotize = new java.util.HashMap<>();
    /** wardUUID → epoch millis of last punishment-mode water overdose. */
    private final java.util.Map<java.util.UUID, Long> lastOverdoseWater = new java.util.HashMap<>();
    /** wardUUID → epoch millis of last punishment-mode laxative overdose. */
    private final java.util.Map<java.util.UUID, Long> lastOverdoseLax   = new java.util.HashMap<>();
    private static final long OVERDOSE_WATER_COOLDOWN_MS = 20_000L;
    private static final long OVERDOSE_LAX_COOLDOWN_MS   = 60_000L;
    /** "nannyUUID:missing" → epoch millis of last low-supplies warning; throttle to once per 5 min. */
    private final java.util.Map<String, Long> lastLowSupplyWarn = new java.util.HashMap<>();
    private static final long LOW_SUPPLY_COOLDOWN_MS = 5 * 60 * 1000L;
    private BukkitTask task;

    public NannyCareEngine(Plugin plugin, NannyManager manager, NannyInventoryManager inventoryManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.inventoryManager = inventoryManager;
        // Phase B Tasks 13–16: route all four care behaviors through the arbiter.
        // The legacy cascade in evaluateAndAct is fully replaced in Task 17.
        arbiter.register(new ChangeTask(plugin, this));
        arbiter.register(new EquipDiaperTask(plugin, this));
        arbiter.register(new FeedTask(plugin, this));
        this.hydrateTask = new HydrateTask(plugin, this);
        arbiter.register(this.hydrateTask);
        // Phase C Task 18: background refill is now first-class.
        arbiter.register(new RefillBottleTask(plugin, this));
        // Phase C Task 19: background pail deposit is now first-class.
        arbiter.register(new DepositSoiledTask(plugin, this));
    }

    // ---------------------------------------------------------------
    // Public engine surface for extracted task classes (Tasks 13–21).
    // Each getter is consumed by one or more *Task classes in the
    // com.storynook.nanny.tasks package; the engine still owns the
    // underlying field. Task 24 considers re-privatising any helper
    // not used outside the engine after the extraction is complete.
    // ---------------------------------------------------------------

    public NannyInventoryManager getInventoryManager() { return inventoryManager; }

    public NannyManager getManager() { return manager; }

    public NannyTaskArbiter getArbiter() { return arbiter; }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ---------------------------------------------------------------
    // Public adapters for DisciplineDispatcher
    // ---------------------------------------------------------------

    public void publicForceFeedLaxative(NannyData data, org.bukkit.entity.Player ward) {
        NannyEntity e = manager.getActiveNannies().get(data.getNannyUUID());
        if (e != null) doForceFeedLaxative(e, data, ward);
    }

    public void publicLeash(NannyData data, org.bukkit.entity.Player ward) {
        NannyEntity e = manager.getActiveNannies().get(data.getNannyUUID());
        if (e != null) doLeash(e, data, ward);
    }

    public void publicEquipBindingLeggings(NannyData data, org.bukkit.entity.Player ward) {
        NannyEntity e = manager.getActiveNannies().get(data.getNannyUUID());
        if (e != null) doEquipBindingLeggings(e, data, ward);
    }

    public void publicHypnotize(NannyData data, org.bukkit.entity.Player ward) {
        NannyEntity e = manager.getActiveNannies().get(data.getNannyUUID());
        if (e != null) doHypnotize(e, data, ward);
    }

    // ---------------------------------------------------------------
    // Per-tick evaluation
    // ---------------------------------------------------------------

    private void tick() {
        if (!plugin.citizensEnabled) return;
        for (NannyEntity entity : manager.getActiveNannies().values()) {
            NannyData data = entity.getData();

            checkHomeEnforcement(entity, data);
            checkFollowBoundary(entity, data);

            for (UUID wardUUID : data.getWards()) {
                Player ward = Bukkit.getPlayer(wardUUID);
                if (ward == null || !ward.isOnline()) continue;
                evaluateAndAct(entity, data, ward);
            }
            Player owner = Bukkit.getPlayer(data.getOwnerUUID());
            if (owner != null && owner.isOnline() && !data.getWards().contains(owner.getUniqueId())) {
                evaluateAndAct(entity, data, owner);
            }
        }
    }

    private void evaluateAndAct(NannyEntity entity, NannyData data, Player ward) {
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null) return;

        // Build candidates across all registered tasks. Currently: change, equip,
        // feed, hydrate. Phase C / D add refill, deposit, crib, discipline,
        // reactive-discipline. Any registered task winning means we dispatch
        // through the arbiter — no per-task whitelist needed.
        java.util.List<NannyTaskArbiter.ScoredCandidate> sorted =
                arbiter.buildAndSortCandidatesAt(entity.getLocation(), entity, data, java.util.List.of(ward));
        if (!sorted.isEmpty()) {
            arbiter.applyLatch(sorted);
            NannyTaskArbiter.ActiveTaskRef active = arbiter.getActiveTask();
            if (active != null) {
                if (isWithinActionRange(entity, ward)) {
                    Result r = active.task().act(entity, data, ward);
                    arbiter.applyActResult(r);
                } else {
                    tryApproachWard(entity, data, ward);
                }
            }
            arbiter.tickTransientTTL();
            return;
        }
        arbiter.tickTransientTTL();

        // No care needed → no-care branch: still inline. Phase C/D extract
        // doPlaceInCrib and tryDisciplineActions into CribPlacementTask /
        // DisciplineTask. tryReactiveScans / tryOreSpotting stay in the engine.
        if (ward.getFoodLevel() <= 6 && NannyPolicy.allows(data, Capability.CRIB_PLACEMENT)) {
            if (isWithinActionRange(entity, ward)) {
                doPlaceInCrib(entity, data, ward);
            } else {
                tryApproachWard(entity, data, ward);
            }
        }
        tryDisciplineActions(entity, data, ward);
        checkSeekArrival(entity, data, ward);
        tryReactiveScans(entity, data, ward);
        tryOreSpotting(entity, data, ward);
    }

    // ---------------------------------------------------------------
    // Action methods
    // ---------------------------------------------------------------

    /**
     * Phase 5a / Crib Redesign Task 23: places a tired ward in the nearest crib.
     * Branches on new-system cribs (via CribRegistry) vs. legacy "Crib"-named
     * ArmorStands. Kill-switch {@code Crib_New_System=false} forces legacy-only path.
     */
    private void doPlaceInCrib(NannyEntity entity, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.CRIB_PLACEMENT)) return;
        if (ward.getVehicle() != null) return;

        Location wardLoc = ward.getLocation();
        if (wardLoc == null || wardLoc.getWorld() == null) return;

        com.storynook.furniture.CribRegistry registry = manager.getPlugin().getCribRegistry();
        if (registry == null) {
            // Registry not yet wired (Plugin.onEnable hasn't run Task 26 yet — defensive).
            // Fall back to legacy scan.
            legacyCribFallback(data, ward, wardLoc);
            return;
        }
        Object killSwitch = manager.getPlugin().getGlobalConfig().get("Crib_New_System");
        boolean newSystemEnabled = !(killSwitch instanceof Boolean) || ((Boolean) killSwitch);

        com.storynook.furniture.CribLookupResult lookup =
            newSystemEnabled
                ? registry.findNearestCrib(wardLoc, data.getHomeRadius())
                : findLegacyCribOnly(wardLoc, data.getHomeRadius());

        if (lookup instanceof com.storynook.furniture.CribLookupResult.NewCribResult newRes) {
            com.storynook.furniture.Crib crib = newRes.crib();
            if (!crib.hasBed()) return;
            Location target = crib.bedHeadLocation();
            if (target == null) return;
            ward.teleport(target);
            registry.containWard(ward.getUniqueId(), crib.id());
            com.storynook.PlayerStatsManagement.PlayerStats wardStats =
                    manager.getPlugin().getPlayerStats(ward.getUniqueId());
            if (wardStats != null) wardStats.setContainedInCribId(crib.id());

            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB, ward.getUniqueId(), "crib_placement_new");
            }
            speakPostAction(ward, "tucked_in");
            return;
        }

        if (lookup instanceof com.storynook.furniture.CribLookupResult.LegacyCribResult legacyRes) {
            org.bukkit.entity.ArmorStand armor = legacyRes.armorStand();
            ward.teleport(armor.getLocation().add(0, 0.5, 0));
            armor.addPassenger(ward);
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB, ward.getUniqueId(), "crib_placement_legacy");
            }
            speakPostAction(ward, "tucked_in");
            return;
        }
        // None — no crib found
    }

    private com.storynook.furniture.CribLookupResult findLegacyCribOnly(Location origin, double radius) {
        org.bukkit.entity.ArmorStand best = null;
        double bestDistSq = radius * radius;
        for (org.bukkit.entity.Entity e : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(e instanceof org.bukkit.entity.ArmorStand as)) continue;
            if (as.getCustomName() == null || !"Crib".equals(as.getCustomName())) continue;
            double d2 = as.getLocation().distanceSquared(origin);
            if (d2 < bestDistSq) { bestDistSq = d2; best = as; }
        }
        return best == null
            ? com.storynook.furniture.CribLookupResult.None.INSTANCE
            : new com.storynook.furniture.CribLookupResult.LegacyCribResult(best);
    }

    private void legacyCribFallback(NannyData data, Player ward, Location wardLoc) {
        org.bukkit.entity.ArmorStand nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        double r = data.getHomeRadius();
        for (org.bukkit.entity.Entity e : wardLoc.getWorld().getNearbyEntities(wardLoc, r, r, r)) {
            if (!(e instanceof org.bukkit.entity.ArmorStand)) continue;
            org.bukkit.entity.ArmorStand as = (org.bukkit.entity.ArmorStand) e;
            if (as.getCustomName() == null || !"Crib".equals(as.getCustomName())) continue;
            double d = as.getLocation().distanceSquared(wardLoc);
            if (d < bestDistSq) { bestDistSq = d; nearest = as; }
        }
        if (nearest == null) return;
        ward.teleport(nearest.getLocation().add(0, 0.5, 0));
        nearest.addPassenger(ward);
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.PLACED_IN_CRIB, ward.getUniqueId(), "crib_placement");
        }
        speakPostAction(ward, "tucked_in");
    }

    private void doForceFeedLaxative(NannyEntity entity, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.FORCE_FEED_LAXATIVE)) return;
        // Cascade: craft → ephemeral spawn. Punishment items are never warned
        // about ("low on laxative" would tip the player off) and the spawned
        // version never enters the Nanny's persistent inventory, so players
        // can't loot it from her.
        ItemStack lax = inventoryManager.tryCraftLaxative(data);
        if (lax == null) lax = spawnLaxative();
        if (lax == null) return;
        speakDiscipline(ward, "force_fed_lax");
        FeedingAction.applyFeed(ward, lax);
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) log.log(NannyEventLog.NannyEventType.FORCE_FED, ward.getUniqueId(), "laxative");
    }

    private void doEquipBindingLeggings(NannyEntity entity, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.BINDING_LEGGINGS)) return;
        ItemStack current = ward.getInventory().getLeggings();
        if (current != null && current.getItemMeta() != null
                && current.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.BINDING_CURSE)) {
            return; // already cursed
        }

        // Cascade: inventory → craft → ephemeral spawn (never enters inventory).
        ItemStack diaper = inventoryManager.takeOne(data, NannyInventoryManager::isCleanDiaper);
        if (diaper == null) diaper = inventoryManager.tryCraftDiaper(data);
        if (diaper == null) diaper = spawnDefaultDiaper();
        if (diaper == null) return;
        // Force the curse onto the equipped item even if takeOne returned an uncursed one
        ItemMeta meta = diaper.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1, true);
            diaper.setItemMeta(meta);
        }
        speakDiscipline(ward, "bound_in_diaper");
        EquipArmor.applyEquip(ward, diaper);
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) log.log(NannyEventLog.NannyEventType.EQUIPPED_WARD, ward.getUniqueId(), "binding");
    }

    private void doLeash(NannyEntity entity, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.LEASH_WARD)) return;
        if (ward.isLeashed()) return;
        org.bukkit.entity.Entity npcEntity = entity.getNpcEntity();
        if (!(npcEntity instanceof org.bukkit.entity.LivingEntity)) return;
        try {
            speakDiscipline(ward, "leashed_ward");
            ward.setLeashHolder((org.bukkit.entity.LivingEntity) npcEntity);
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) log.log(NannyEventLog.NannyEventType.LEASHED_WARD, ward.getUniqueId(), "leash");
        } catch (IllegalArgumentException e) {
            // setLeashHolder rejects some entity types — fail silently
        }
    }

    private void doHypnotize(NannyEntity entity, NannyData data, Player ward) {
        if (!NannyPolicy.allows(data, Capability.HYPNOSIS_USE)) return;
        // Cascade: inventory → ephemeral spawn. The spawned clock is used
        // in-process only and never added back to the Nanny's persistent
        // inventory, so players can't pick it off her.
        ItemStack clock = inventoryManager.takeOne(data, item -> {
            if (item == null || item.getType() != org.bukkit.Material.CLOCK) return false;
            ItemMeta m = item.getItemMeta();
            return m != null && m.getPersistentDataContainer().has(
                    new org.bukkit.NamespacedKey(plugin, "hypnosis"),
                    org.bukkit.persistence.PersistentDataType.BYTE);
        });
        boolean fromInventory = clock != null;
        if (clock == null) clock = spawnHypnoClock();
        if (clock == null) return;
        speakDiscipline(ward, "hypnotized_ward");
        boolean fired = com.storynook.Event_Listeners.Hypno.applyHypnosis(ward, clock);
        // Return the clock to inventory only if it came from there.
        if (fromInventory) inventoryManager.addToPersonalInventory(data, clock);
        if (fired) {
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) log.log(NannyEventLog.NannyEventType.HYPNOTIZED_WARD, ward.getUniqueId(),
                    fromInventory ? "clock" : "spawned");
        }
    }

    // ---------------------------------------------------------------
    // Punishment-item spawn helpers
    //
    // The Nanny conjures these in-process when she can't pull or craft one.
    // They MUST NOT enter NannyData.personalInventory — otherwise a player
    // could open her supplies tab and walk away with a hypno clock, cursed
    // diaper, or laxative. Build, use immediately, let GC collect.
    // ---------------------------------------------------------------

    private ItemStack spawnHypnoClock() {
        ItemStack clock = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = clock.getItemMeta();
        if (meta == null) return null;
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new org.bukkit.NamespacedKey(plugin, "hypnosis"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        String word = plugin.getRandomHypnoWord();
        if (word == null || word.isEmpty()) word = "naptime";
        pdc.set(new org.bukkit.NamespacedKey(plugin, "HypnoTriggerWord"),
                org.bukkit.persistence.PersistentDataType.STRING, word);
        pdc.set(new org.bukkit.NamespacedKey(plugin, "HypnoType"),
                org.bukkit.persistence.PersistentDataType.STRING, "wetting");
        clock.setItemMeta(meta);
        return clock;
    }

    private ItemStack spawnDefaultDiaper() {
        // CMD 626006 = baseline thick diaper. Cursed in doEquipBindingLeggings.
        ItemStack diaper = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        ItemMeta meta = diaper.getItemMeta();
        if (meta == null) return null;
        meta.setCustomModelData(626006);
        diaper.setItemMeta(meta);
        return diaper;
    }

    private ItemStack spawnLaxative() {
        // Matches ItemManager.Laxative — bread + laxative_effect PDC tag, which
        // FeedingAction.applyFeed reads to trigger the bowel-spike effect.
        ItemStack lax = new ItemStack(Material.BREAD, 1);
        ItemMeta meta = lax.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "laxative_effect"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Laxative");
        lax.setItemMeta(meta);
        return lax;
    }

    /**
     * Shared discipline-line broadcast — single bucket so consecutive Phase 5b
     * actions don't all blurt. Tries the action-specific category first
     * (force_fed_lax / bound_in_diaper / leashed_ward / hypnotized_ward); if
     * that category has no lines for the Nanny's tier, MIN_LINE_GAP_MS isn't
     * tripped and the generic discipline call fills in.
     */
    private void speakDiscipline(Player ward, String specificCategory) {
        if (ward == null) return;
        NannyChatEngine ce = manager.getChatEngine();
        if (ce == null) return;
        if (specificCategory != null) {
            ce.speakIfNearby(ward, specificCategory, "discipline:" + specificCategory,
                    30_000L, NannyChatEngine.PRI_DISCIPLINE);
        }
        ce.speakIfNearby(ward, "discipline", "discipline:generic", 30_000L,
                NannyChatEngine.PRI_DISCIPLINE);
    }

    /**
     * Mood-keyed line spoken after a successful care action. Shared 30s throttle per category.
     *
     * <p>Public so extracted *Task classes can call it after a successful act().
     * Task 24 (Phase F) reviews whether this can be re-privatised.
     */
    public void speakPostAction(Player ward, String category) {
        if (ward == null || category == null) return;
        NannyChatEngine ce = manager.getChatEngine();
        if (ce != null) {
            ce.speakIfNearby(ward, category, "care:" + category, 30_000L,
                    NannyChatEngine.PRI_CARE);
        }
    }

    // ---------------------------------------------------------------
    // D4: Reactive scans (mob warnings, low HP, night, lava)
    // ---------------------------------------------------------------

    /** Per-mob throttle so the same hostile doesn't re-trigger within 5 minutes. */
    private final java.util.Map<java.util.UUID, Long> mobWarningThrottle = new java.util.HashMap<>();
    /** Per-vein lava throttle keyed by world,x,y,z. */
    private final java.util.Map<String, Long> lavaWarningThrottle = new java.util.HashMap<>();

    private void tryReactiveScans(NannyEntity entity, NannyData data, Player ward) {
        if (!isWithinActionRange(entity, ward)) return; // Nanny isn't here to react

        // ward_low_health: HP below 6 (3 hearts)
        if (ward.getHealth() < 6.0) {
            NannyChatEngine ce = manager.getChatEngine();
            if (ce != null) {
                ce.speakIfNearby(ward, "ward_low_health",
                        "reactive:low_hp", 60_000L,
                        NannyChatEngine.PRI_ACCIDENT);
            }
        }

        // night_warning: outdoors AND world time = night. 1-hour throttle = at most once per game-day-ish.
        org.bukkit.World world = ward.getWorld();
        if (world != null) {
            long t = world.getTime();
            boolean isNight = t >= 13000 && t < 23000;
            boolean isOutdoors = ward.getLocation().getBlock().getLightFromSky() > 8;
            if (isNight && isOutdoors) {
                NannyChatEngine ce = manager.getChatEngine();
                if (ce != null) {
                    ce.speakIfNearby(ward, "night_warning",
                            "reactive:night", 60L * 60L * 1000L,
                            NannyChatEngine.PRI_LIFECYCLE);
                }
            }
        }

        // lava_warning: any LAVA block within 4-block cube around ward
        org.bukkit.block.Block lava = findNearbyLava(ward.getLocation(), 4);
        if (lava != null) {
            String veinKey = lava.getWorld().getName() + "," + lava.getX() + ","
                    + lava.getY() + "," + lava.getZ();
            long now = System.currentTimeMillis();
            Long lastSeen = lavaWarningThrottle.get(veinKey);
            if (lastSeen == null || (now - lastSeen) > 5L * 60L * 1000L) {
                lavaWarningThrottle.put(veinKey, now);
                NannyChatEngine ce = manager.getChatEngine();
                if (ce != null) {
                    ce.speakIfNearby(ward, "lava_warning",
                            "reactive:lava", 30_000L,
                            NannyChatEngine.PRI_MOB);
                }
            }
        }

        // mob_warning: SWEET/CARING tiers only (STRICT/WARDEN's empty list no-ops).
        // Creeper override: 12-block radius regardless of ward-busy state.
        // Standard hostiles: 8-block radius AND (ward inventory open OR low light).
        NannyData.MoodTier tier = data.getMoodTier();
        boolean tierWarns = (tier == NannyData.MoodTier.SWEET
                || tier == NannyData.MoodTier.CARING
                || tier == NannyData.MoodTier.CUSTOM);
        if (!tierWarns) return;

        org.bukkit.entity.Entity nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        boolean creeper = false;
        for (org.bukkit.entity.Entity e : ward.getNearbyEntities(12, 8, 12)) {
            if (!isHostile(e)) continue;
            double d = e.getLocation().distanceSquared(ward.getLocation());
            boolean isCreeper = e instanceof org.bukkit.entity.Creeper;
            // Creepers always count within 12 blocks; others only count within 8
            if (!isCreeper && d > 64.0) continue;
            if (d < bestDistSq) {
                bestDistSq = d;
                nearest = e;
                creeper = isCreeper;
            }
        }
        if (nearest == null) return;

        // Per-mob throttle
        long now = System.currentTimeMillis();
        Long mobLast = mobWarningThrottle.get(nearest.getUniqueId());
        if (mobLast != null && (now - mobLast) < 5L * 60L * 1000L) return;

        if (!creeper) {
            // Non-creeper requires the ward to be busy (not paying attention)
            boolean inventoryOpen = false;
            NannyReactiveListener rl = manager.getReactiveListener();
            if (rl != null) inventoryOpen = rl.isInventoryOpen(ward.getUniqueId());
            boolean lowLight = ward.getLocation().getBlock().getLightLevel() < 8;
            if (!inventoryOpen && !lowLight) return;
        }

        mobWarningThrottle.put(nearest.getUniqueId(), now);
        String category = creeper ? "mob_warning_creeper" : "mob_warning";
        NannyChatEngine ce = manager.getChatEngine();
        if (ce != null) {
            ce.speakIfNearby(ward, category,
                    "reactive:mob", 60_000L,
                    NannyChatEngine.PRI_MOB);
        }
    }

    private boolean isHostile(org.bukkit.entity.Entity e) {
        return e instanceof org.bukkit.entity.Monster
                || e instanceof org.bukkit.entity.Slime
                || e instanceof org.bukkit.entity.Phantom
                || e instanceof org.bukkit.entity.Hoglin;
    }

    // ---------------------------------------------------------------
    // D5: Ore spotting (only when Nanny is in follow mode following the owner)
    // ---------------------------------------------------------------

    /** "world,x,y,z" → epoch millis when this vein was last announced. */
    private final java.util.LinkedHashMap<String, Long> oreSpottedCache =
            new java.util.LinkedHashMap<String, Long>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) {
                    return size() > 500;
                }
            };

    private static final long ORE_VEIN_TTL_MS = 30L * 60L * 1000L;

    private void tryOreSpotting(NannyEntity entity, NannyData data, Player ward) {
        if (!data.isFollowMode()) return;
        // Spec: only narrate when Nanny is following THE spotter (the owner in practice)
        if (!ward.getUniqueId().equals(data.getOwnerUUID())) return;

        org.bukkit.Location loc = ward.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        org.bukkit.World w = loc.getWorld();
        int cx = loc.getBlockX(), cy = loc.getBlockY(), cz = loc.getBlockZ();

        org.bukkit.block.Block found = null;
        String category = null;
        long now = System.currentTimeMillis();

        outer:
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    org.bukkit.block.Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    String cat = oreCategory(b.getType());
                    if (cat == null) continue;

                    String veinKey = w.getName() + "," + b.getX() + ","
                            + b.getY() + "," + b.getZ();
                    Long lastSeen = oreSpottedCache.get(veinKey);
                    if (lastSeen != null && (now - lastSeen) < ORE_VEIN_TTL_MS) continue;

                    // Gold gets a 50% suppression — too common otherwise
                    if (cat.equals("ore_spotted_gold") && random.nextBoolean()) {
                        oreSpottedCache.put(veinKey, now);
                        continue;
                    }

                    found = b;
                    category = cat;
                    oreSpottedCache.put(veinKey, now);
                    break outer;
                }
            }
        }

        if (found == null || category == null) return;

        NannyChatEngine ce = manager.getChatEngine();
        if (ce != null) {
            ce.speakIfNearby(ward, category,
                    "discovery:" + category, 30_000L,
                    NannyChatEngine.PRI_DISCOVERY);
        }
    }

    private final java.util.Random random = new java.util.Random();

    private String oreCategory(Material m) {
        switch (m) {
            case DIAMOND_ORE:
            case DEEPSLATE_DIAMOND_ORE:
                return "ore_spotted_diamond";
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                return "ore_spotted_emerald";
            case ANCIENT_DEBRIS:
                return "ore_spotted_ancient_debris";
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
            case NETHER_GOLD_ORE:
                return "ore_spotted_gold";
            default:
                return null;
        }
    }

    private org.bukkit.block.Block findNearbyLava(org.bukkit.Location origin, int radius) {
        org.bukkit.World w = origin.getWorld();
        if (w == null) return null;
        int cx = origin.getBlockX(), cy = origin.getBlockY(), cz = origin.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() == org.bukkit.Material.LAVA) return b;
                }
            }
        }
        return null;
    }

    /**
     * Phase 5b: discipline pass. BASIC tier delegates to DisciplineDispatcher
     * (score-banded picks with stacking-cap + per-action cooldowns).
     * AI tier handles discipline directly in chat replies via {@code <PUNISH:...>}
     * tags — skipped here.
     */
    private void tryDisciplineActions(NannyEntity entity, NannyData data, Player ward) {
        com.storynook.PlayerStatsManagement.PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null) return;

        if (!isWithinActionRange(entity, ward)) {
            tryApproachWard(entity, data, ward);
            return;
        }

        // BASIC tier delegates to DisciplineDispatcher (which uses behavior score + stacking cap).
        // AI tier handles discipline directly in chat replies via <PUNISH:...> tags — skip here.
        if (data.getChatTier() == NannyData.ChatTier.AI) return;

        DisciplineDispatcher dispatcher = manager.getDisciplineDispatcher();
        if (dispatcher == null) return;
        dispatcher.pickAndEnactFromScore(data, ward);
    }

    /**
     * Finds the nearest fillable water source in a {@code radius}-block box around
     * {@code origin}. Accepts WATER_CAULDRON blocks and source-level WATER blocks.
     * Skips toilet cauldrons — identified by an IRON_TRAPDOOR placed directly above
     * the cauldron block (see Toilet.java for placement).
     *
     * <p>Public so {@link com.storynook.nanny.tasks.RefillBottleTask} can call it
     * during evaluate(). Task 24 (Phase F) reviews whether this can be re-privatised.
     */
    public org.bukkit.block.Block findWaterRefillSource(Location origin, int radius) {
        World w = origin.getWorld();
        if (w == null) return null;
        int cx = origin.getBlockX(), cy = origin.getBlockY(), cz = origin.getBlockZ();
        // Restrict vertical scan to [-1, +1] — water at her feet or just above/below.
        // Scanning deeper used to find cave pools and send her on long underground
        // expeditions she couldn't get back from.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material type = b.getType();
                    if (type == Material.WATER_CAULDRON) {
                        if (isToiletCauldron(b)) continue;
                        return b;
                    }
                    if (type == Material.WATER) {
                        // Accept any water block — source OR flowing — players might
                        // bring her near a stream/river/sea where source-level blocks
                        // are mixed with flowing edges. We just need water nearby.
                        return b;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Punishment-mode overdose, triggered by a misbehavior signal (punch /
     * naughty chat) from a ward who is currently serving a diaper-punishment
     * sentence. Pushes one water bottle and one laxative per call, each gated
     * by their own cooldown so rapid-fire signals don't cascade into dozens of
     * doses. No-op when the ward isn't in punishment, the Nanny isn't in
     * range, or the cooldown is active. Returns true if anything fired.
     */
    public boolean triggerPunishmentOverdose(NannyData data, Player ward) {
        if (ward == null || data == null) return false;
        PlayerStats stats = plugin.getPlayerStats(ward.getUniqueId());
        if (stats == null || !stats.isDiaperPunishment()) return false;
        NannyEntity entity = manager.getActiveNannies().get(data.getNannyUUID());
        if (entity == null || !entity.isSpawned()) return false;
        if (!isWithinActionRange(entity, ward)) return false;

        long now = System.currentTimeMillis();
        boolean fired = false;

        Long lastWater = lastOverdoseWater.get(ward.getUniqueId());
        if (lastWater == null || now - lastWater >= OVERDOSE_WATER_COOLDOWN_MS) {
            int bottles = inventoryManager.countAvailable(data, NannyInventoryManager::isWaterBottle);
            int melons  = inventoryManager.countAvailable(data, NannyInventoryManager::isMelonSlice);
            if (bottles > 0 || melons > 0) {
                // Punishment overdose ignores the threshold gate — call act() directly
                // rather than re-routing through the arbiter.
                hydrateTask.act(entity, data, ward);
                lastOverdoseWater.put(ward.getUniqueId(), now);
                fired = true;
            }
        }

        Long lastLax = lastOverdoseLax.get(ward.getUniqueId());
        if (lastLax == null || now - lastLax >= OVERDOSE_LAX_COOLDOWN_MS) {
            if (NannyPolicy.allows(data, Capability.FORCE_FEED_LAXATIVE)) {
                doForceFeedLaxative(entity, data, ward);
                lastOverdoseLax.put(ward.getUniqueId(), now);
                fired = true;
            }
        }
        return fired;
    }

    /**
     * Scan for the nearest Pail_ ArmorStand within radius. Returns its location or null.
     *
     * <p>Public so {@link com.storynook.nanny.tasks.DepositSoiledTask} can call it
     * during evaluate(). Task 24 (Phase F) reviews whether this can be re-privatised.
     */
    public Location findNearestPail(Location origin, double radius) {
        if (origin == null || origin.getWorld() == null) return null;
        org.bukkit.entity.ArmorStand nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(e instanceof org.bukkit.entity.ArmorStand)) continue;
            org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) e;
            String name = stand.getCustomName();
            if (name == null || !name.startsWith("Pail_")) continue;
            double d = stand.getLocation().distanceSquared(origin);
            if (d < nearestSq) { nearestSq = d; nearest = stand; }
        }
        return nearest == null ? null : nearest.getLocation();
    }

    /**
     * Finds a walkable block adjacent to a water source. Walkable = the candidate
     * block is air or passable, the block above it is air or passable, and the
     * block below it is solid (so she has somewhere to stand). Returns null when
     * no neighbor satisfies — the source is fully submerged / surrounded by water.
     *
     * <p>Public so {@link com.storynook.nanny.tasks.RefillBottleTask} can call it
     * during evaluate(). Task 24 (Phase F) reviews whether this can be re-privatised.
     */
    public org.bukkit.block.Block findAdjacentStandable(org.bukkit.block.Block water) {
        org.bukkit.block.BlockFace[] horizontals = {
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,  org.bukkit.block.BlockFace.WEST
        };
        for (org.bukkit.block.BlockFace face : horizontals) {
            org.bukkit.block.Block candidate = water.getRelative(face);
            org.bukkit.block.Block above = candidate.getRelative(org.bukkit.block.BlockFace.UP);
            org.bukkit.block.Block below = candidate.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (!candidate.isPassable()) continue;        // need to stand IN this block
            if (!above.isPassable()) continue;             // and need head clearance
            if (below.getType() == Material.AIR) continue; // need something to stand ON
            if (below.getType() == Material.WATER) continue;
            return candidate;
        }
        return null;
    }

    /**
     * Public so {@link com.storynook.nanny.tasks.RefillBottleTask}'s water-scan
     * filter can call it. Task 24 (Phase F) reviews whether this can be re-privatised.
     */
    public boolean isToiletCauldron(org.bukkit.block.Block b) {
        Material type = b.getType();
        if (type != Material.CAULDRON && type != Material.WATER_CAULDRON) return false;
        return b.getRelative(org.bukkit.block.BlockFace.UP).getType() == Material.IRON_TRAPDOOR;
    }

    /**
     * Mood-keyed local broadcast warning the ward and owner that the Nanny ran out
     * of something. Public so extracted *Task classes can call it when their act()
     * bails for lack of supplies. Task 24 (Phase F) reviews whether this can be
     * re-privatised.
     */
    public void warnLowSupplies(NannyData data, Player ward, String missing) {
        // Mood-keyed local broadcast (throttled per-(nanny, missing, ward) inside speakIfNearby)
        NannyChatEngine ce = manager.getChatEngine();
        if (ce != null && ward != null) {
            ce.speakIfNearby(ward, "low_supplies",
                    "low_supplies:" + missing,
                    LOW_SUPPLY_COOLDOWN_MS,
                    NannyChatEngine.PRI_LIFECYCLE);
        }

        // Quiet out-of-range ping for the owner — uses a separate (nanny, missing) cooldown
        String key = data.getNannyUUID() + ":" + missing;
        long now = System.currentTimeMillis();
        Long last = lastLowSupplyWarn.get(key);
        if (last != null && now - last < LOW_SUPPLY_COOLDOWN_MS) return;
        lastLowSupplyWarn.put(key, now);

        Player owner = Bukkit.getPlayer(data.getOwnerUUID());
        if (owner != null && owner.isOnline() && (ward == null || !owner.equals(ward))) {
            owner.sendMessage(ChatColor.YELLOW + "[" + data.getName() + "] running low on " + missing + ".");
        }
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.LOW_SUPPLIES,
                    ward != null ? ward.getUniqueId() : null,
                    "missing=" + missing);
        }
    }


    /**
     * Ward-approach gate. When the Nanny needs to move toward a ward (for care,
     * discipline, or crib placement), this is the single check site that
     * decides if she's allowed to. Semantics:
     *
     * <ul>
     *   <li>Cross-world: never approach. PlayerChangedWorldEvent brings her over.</li>
     *   <li>{@code followMode == true}: Citizens entity-follow handles approach.
     *       Don't issue a competing fixed-target nav.</li>
     *   <li>{@code seekEnabled == true}: issue navigateTo to her current location.</li>
     *   <li>both off: she stays put — ward must come within action range.</li>
     * </ul>
     */
    private void tryApproachWard(NannyEntity entity, NannyData data, Player ward) {
        Location here = entity.getLocation();
        if (here != null && !here.getWorld().equals(ward.getWorld())) return;
        NannyNavigator navigator = manager.getNavigator(data.getNannyUUID());
        if (navigator == null) return;
        if (data.isFollowMode()) return;
        if (!data.isSeekEnabled()) return;
        navigator.navigateTo(ward.getLocation());
    }

    /**
     * Returns true if the Nanny is within 3 blocks of the ward (close enough
     * to act without moving). Returns true when Citizens2 is absent.
     */
    private boolean isWithinActionRange(NannyEntity entity, Player ward) {
        if (!plugin.citizensEnabled) return true;
        Location here = entity.getLocation();
        if (here == null) return true;
        if (!here.getWorld().equals(ward.getWorld())) return false;
        return here.distanceSquared(ward.getLocation()) <= 9.0;
    }

    private void checkHomeEnforcement(NannyEntity entity, NannyData data) {
        if (data.isFollowMode()) return;
        if (data.getHomeWorld() == null || data.getHomeWorld().isEmpty()) return;
        Location here = entity.getLocation();
        if (here == null) return;
        World homeWorld = Bukkit.getWorld(data.getHomeWorld());
        if (homeWorld == null) return;
        Location home = new Location(homeWorld, data.getHomeX(), data.getHomeY(), data.getHomeZ());

        if (!here.getWorld().equals(homeWorld)) {
            entity.teleportTo(home);
            return;
        }
        double r = data.getHomeRadius();
        if (here.distanceSquared(home) > r * r) {
            NannyNavigator navigator = manager.getNavigator(data.getNannyUUID());
            if (navigator != null) {
                navigator.navigateTo(home);
            } else {
                entity.teleportTo(home);
            }
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) log.log(NannyEventLog.NannyEventType.RETURNED_HOME, null, "home_enforcement");
        }
    }

    private void checkFollowBoundary(NannyEntity entity, NannyData data) {
        if (!data.isFollowMode()) return;
        if (data.getHomeWorld() == null || data.getHomeWorld().isEmpty()) return;
        World homeWorld = Bukkit.getWorld(data.getHomeWorld());
        if (homeWorld == null) return;
        Location home = new Location(homeWorld, data.getHomeX(), data.getHomeY(), data.getHomeZ());
        Player owner = Bukkit.getPlayer(data.getOwnerUUID());
        if (owner == null || !owner.isOnline()) return;
        if (!owner.getWorld().equals(homeWorld)) return;

        double maxDist = data.getHomeRadius() * 2.0;
        if (owner.getLocation().distanceSquared(home) > maxDist * maxDist) {
            data.setFollowMode(false);
            data.save(plugin.getDataFolder());
            NannyNavigator navigator = manager.getNavigator(data.getNannyUUID());
            if (navigator != null) {
                navigator.cancelNavigation();
                navigator.navigateTo(home);
            } else {
                entity.teleportTo(home);
            }
            NannyChatEngine ce = manager.getChatEngine();
            if (ce != null) {
                ce.speakIfNearby(owner, "arrived_home",
                        "lifecycle_arrived_home", 60_000L,
                        NannyChatEngine.PRI_LIFECYCLE);
            }
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) log.log(NannyEventLog.NannyEventType.RETURNED_HOME, owner.getUniqueId(), "follow_boundary");
        }
    }

    private void checkSeekArrival(NannyEntity entity, NannyData data, Player ward) {
        NannyNavigator navigator = manager.getNavigator(data.getNannyUUID());
        if (navigator == null) return;
        if (!ward.getUniqueId().equals(navigator.getSeekingWardUUID())) return;

        Location here = entity.getLocation();
        if (here == null) return;

        if (here.getWorld().equals(ward.getWorld())
                && here.distanceSquared(ward.getLocation()) < 9.0) {
            NannyChatEngine ce = manager.getChatEngine();
            if (ce != null) {
                ce.speakIfNearby(ward, "found_ward",
                        "lifecycle_found_ward", 60_000L,
                        NannyChatEngine.PRI_LIFECYCLE);
            }
            navigator.clearSeekingWard();
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) log.log(NannyEventLog.NannyEventType.FOUND_WARD, ward.getUniqueId(), "seek_arrival");
        } else if (!navigator.isNavigating()) {
            // Don't cross-world teleport from the tick — PlayerChangedWorldEvent handles that.
            if (!here.getWorld().equals(ward.getWorld())) return;
            navigator.seekTo(ward);
        }
    }
}
