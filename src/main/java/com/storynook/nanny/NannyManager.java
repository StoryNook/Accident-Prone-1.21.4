package com.storynook.nanny;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.storynook.Plugin;

import net.citizensnpcs.api.CitizensAPI;

/**
 * Central orchestrator for the Nanny NPC system.
 *
 * <p>Handles the full Nanny lifecycle: loading data on startup,
 * spawning/despawning NPCs on player join/quit, handling Nanny Egg use,
 * and protecting NPCs from damage/mob targeting.
 *
 * <p>Register this class as a Bukkit listener in {@code Plugin.onEnable}.
 * Call {@link #init()} after the Citizens2 presence check.
 */
public class NannyManager implements Listener {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Plugin plugin;

    /** nannyUUID → NannyData */
    private final Map<UUID, NannyData> allNannies;

    /** nannyUUID → NannyEntity */
    private final Map<UUID, NannyEntity> activeNannies;

    /** wardUUID → List of nannyUUIDs whose ward list (or owner) includes that player */
    private final Map<UUID, List<UUID>> wardToNannies;

    /**
     * Stores the spawn location chosen when a player uses an unnamed Nanny Egg.
     * Keyed by player UUID; cleared once the name is entered (see PlayerEventListener).
     */
    private final Map<UUID, Location> pendingNannyCreations;

    /** Per-Nanny event log instances. Lazily populated. */
    private final Map<UUID, NannyEventLog> eventLogs = new HashMap<>();

    private NannyCareEngine careEngine;
    private NannyInventoryManager inventoryManager;

    /** nannyUUID → NannyNavigator for each currently spawned Nanny. */
    private final Map<UUID, NannyNavigator> navigators = new HashMap<>();

    private NannyChatEngine chatEngine;
    private NannyReactiveListener reactiveListener;
    private MembershipProvider membershipProvider;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Stores references and initialises empty maps.
     * Does NOT load data — call {@link #init()} from {@code Plugin.onEnable}
     * after the Citizens2 check.
     */
    public NannyManager(Plugin plugin) {
        this.plugin = plugin;
        this.allNannies = new HashMap<>();
        this.activeNannies = new HashMap<>();
        this.wardToNannies = new HashMap<>();
        this.pendingNannyCreations = new HashMap<>();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Creates the {@code nannies/} data folder if needed, loads all persisted
     * Nanny data files, and builds the ward-lookup index.
     */
    public void init() {
        File nanniesDir = new File(plugin.getDataFolder(), "nannies");
        if (!nanniesDir.exists()) {
            nanniesDir.mkdirs();
        }
        loadAllNannies();
        buildWardIndex();
        this.inventoryManager = new NannyInventoryManager(plugin);
        this.careEngine = new NannyCareEngine(plugin, this, this.inventoryManager);
        for (UUID id : allNannies.keySet()) {
            eventLogs.put(id, NannyEventLog.loadFromData(allNannies.get(id), plugin));
        }
        this.membershipProvider = plugin.buildMembershipProvider();
        this.chatEngine = new NannyChatEngine(plugin, this);
        this.chatEngine.startReminderTask();
        this.chatEngine.startAmbientTask();
    }

    /**
     * Scans all {@code .yml} files in {@code <dataFolder>/nannies/}, parses
     * each filename as a UUID, and loads the corresponding {@link NannyData}.
     */
    private void loadAllNannies() {
        File nanniesDir = new File(plugin.getDataFolder(), "nannies");
        File[] files = nanniesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().info("[NannyManager] No nanny data files found.");
            return;
        }
        for (File file : files) {
            String fileName = file.getName();
            String uuidStr = fileName.substring(0, fileName.length() - 4); // strip ".yml"
            UUID nannyUUID;
            try {
                nannyUUID = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[NannyManager] Skipping invalid nanny file: " + fileName);
                continue;
            }
            NannyData data = NannyData.load(nannyUUID, plugin.getDataFolder(), plugin);
            if (data != null) {
                allNannies.put(nannyUUID, data);
            }
        }
        plugin.getLogger().info("[NannyManager] Loaded " + allNannies.size() + " nanny data file(s).");
    }

    /**
     * Clears and rebuilds {@link #wardToNannies} from the current
     * {@link #allNannies} map.
     */
    private void buildWardIndex() {
        wardToNannies.clear();
        for (NannyData data : allNannies.values()) {
            UUID nannyUUID = data.getNannyUUID();

            // Index owner
            wardToNannies
                    .computeIfAbsent(data.getOwnerUUID(), k -> new ArrayList<>())
                    .add(nannyUUID);

            // Index each ward
            for (UUID wardUUID : data.getWards()) {
                wardToNannies
                        .computeIfAbsent(wardUUID, k -> new ArrayList<>())
                        .add(nannyUUID);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * On player join:
     * <ol>
     *   <li>If the player is an owner, mark {@code lastOwnerSeen} and clear dormancy.</li>
     *   <li>Spawn any non-dormant Nanny whose owner or ward list includes this player.</li>
     *   <li>Notify the player of dormant Nannies where they are a ward (not owner)
     *       if the owner has been absent too long.</li>
     * </ol>
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        membershipProvider.refresh(event.getPlayer().getUniqueId());
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Object timeoutObj = plugin.getGlobalConfig().getOrDefault("Nanny_Owner_Timeout_Days", 14L);
        long timeoutDays = (timeoutObj instanceof Number) ? ((Number) timeoutObj).longValue() : 14L;

        for (NannyData data : allNannies.values()) {
            boolean isOwner = data.getOwnerUUID().equals(playerUUID);
            boolean isWard = data.getWards().contains(playerUUID);

            // Step 1: Update owner presence and clear dormancy
            if (isOwner) {
                data.setLastOwnerSeen(LocalDateTime.now());
                data.setDormant(false);
                data.save(plugin.getDataFolder());
            }

            // Step 2: Spawn if the player is owner or ward and Nanny is not dormant
            if ((isOwner || isWard) && !data.isDormant()) {
                spawnNanny(data, player.getLocation());

                // Phase 3: seek the joining player if they are far from the Nanny
                if (data.isSeekEnabled()) {
                    UUID joinNannyUUID = data.getNannyUUID();
                    NannyEntity nannyEntity = activeNannies.get(joinNannyUUID);
                    if (nannyEntity != null && nannyEntity.isSpawned()) {
                        Location nannyLoc = nannyEntity.getLocation();
                        if (nannyLoc != null) {
                            boolean differentWorld = !nannyLoc.getWorld().equals(player.getWorld());
                            double distSq = differentWorld ? Double.MAX_VALUE
                                    : nannyLoc.distanceSquared(player.getLocation());
                            if (distSq > 100.0) {
                                if (differentWorld) {
                                    nannyEntity.teleportTo(player.getLocation());
                                }
                                NannyNavigator nav = navigators.get(joinNannyUUID);
                                if (nav != null) nav.seekTo(player);
                            }
                        }
                    }
                }
            }

            // Step 3: Dormancy warning for wards (not owner)
            if (isWard && !isOwner) {
                long daysSinceOwner = ChronoUnit.DAYS.between(
                        data.getLastOwnerSeen(), LocalDateTime.now());
                if (daysSinceOwner > timeoutDays) {
                    data.setDormant(true);
                    data.save(plugin.getDataFolder());
                    player.sendMessage(ChatColor.YELLOW + "[Nanny] " + data.getName()
                            + " is inactive — her owner has been away too long.");
                }
            }
        }

        // Step 4: greeting line — fire once Nanny has settled and player is past the join screen
        final Player joinPlayer = player;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joinPlayer.isOnline() && chatEngine != null) {
                chatEngine.speakIfNearby(joinPlayer, "greeting",
                        "lifecycle_greeting", 60_000L,
                        NannyChatEngine.PRI_LIFECYCLE);
            }
        }, 20L);
    }

    /**
     * When a player crosses into a new world, follow them across with active Nannies
     * that have follow or seek mode on. One teleport per world change avoids the
     * tick-loop flicker that came from the care engine retrying navigation cross-world.
     */
    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        for (NannyData data : allNannies.values()) {
            if (data.isDormant()) continue;
            boolean isOwner = data.getOwnerUUID().equals(playerUUID);
            boolean isWard = data.getWards().contains(playerUUID);
            if (!isOwner && !isWard) continue;
            if (!data.isFollowMode() && !data.isSeekEnabled()) continue;

            NannyEntity entity = activeNannies.get(data.getNannyUUID());
            if (entity == null || !entity.isSpawned()) continue;
            Location here = entity.getLocation();
            if (here == null || here.getWorld().equals(player.getWorld())) continue;
            entity.teleportTo(player.getLocation());
            // Re-engage entity-follow so Citizens resumes walking with the player
            // after the cross-world teleport (Citizens may drop the target).
            if (data.isFollowMode()) {
                NannyNavigator nav = navigators.get(data.getNannyUUID());
                if (nav != null) nav.setFollowTarget(player);
            }
        }
    }

    /**
     * On player quit: despawn any active Nanny whose owner AND all wards are
     * now fully offline.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID quittingUUID = event.getPlayer().getUniqueId();

        // Collect UUIDs still online after this player leaves
        // (PlayerQuitEvent fires before the player is removed from online list
        //  on some versions, so we explicitly exclude the quitting player)
        List<UUID> stillOnline = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(quittingUUID)) {
                stillOnline.add(online.getUniqueId());
            }
        }

        // Work on a snapshot of the keys to avoid ConcurrentModificationException
        for (UUID nannyUUID : new ArrayList<>(activeNannies.keySet())) {
            NannyData data = allNannies.get(nannyUUID);
            if (data == null) {
                continue;
            }

            boolean anyRelevantOnline = false;

            // Check owner
            if (stillOnline.contains(data.getOwnerUUID())) {
                anyRelevantOnline = true;
            }

            // Check wards
            if (!anyRelevantOnline) {
                for (UUID wardUUID : data.getWards()) {
                    if (stillOnline.contains(wardUUID)) {
                        anyRelevantOnline = true;
                        break;
                    }
                }
            }

            if (!anyRelevantOnline) {
                despawnNanny(nannyUUID);
            }
        }
    }

    /**
     * Handles right-clicking with a Nanny Egg item to create a new Nanny.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack heldItem = event.getItem();
        if (heldItem == null || !heldItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = heldItem.getItemMeta();
        NamespacedKey nannyEggKey = new NamespacedKey(plugin, "nanny_egg");
        if (!meta.getPersistentDataContainer().has(nannyEggKey, PersistentDataType.BYTE)) {
            return;
        }

        // It's a Nanny Egg — take control from here
        event.setCancelled(true);
        Player player = event.getPlayer();

        // Check: player already owns a Nanny
        boolean alreadyOwns = allNannies.values().stream()
                .anyMatch(d -> d.getOwnerUUID().equals(player.getUniqueId()));
        if (alreadyOwns) {
            player.sendMessage(ChatColor.RED + "You already have a Nanny.");
            return;
        }

        // Check: server Nanny cap
        Object capObj = plugin.getGlobalConfig().getOrDefault("Nanny_Max_Server_Nannies", 50);
        int cap = (capObj instanceof Number) ? ((Number) capObj).intValue() : 50;
        if (allNannies.size() >= cap) {
            player.sendMessage(ChatColor.RED + "The server's Nanny limit has been reached.");
            return;
        }

        // Determine spawn location (block face if RIGHT_CLICK_BLOCK, else player location)
        Location spawnLocation = (event.getClickedBlock() != null)
                ? event.getClickedBlock().getLocation().add(0.5, 1, 0.5)
                : player.getLocation();

        // Determine Nanny name from item display name
        String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (displayName != null && !displayName.isEmpty() && !displayName.equals("Nanny Egg")) {
            // Named egg — create immediately
            createNanny(player, displayName, spawnLocation);
        } else {
            // Unnamed egg — prompt player for a name
            pendingNannyCreations.put(player.getUniqueId(), spawnLocation);
            plugin.setAwaitingInput(player.getUniqueId(), "nannyName");
            player.sendMessage(ChatColor.GOLD + "What would you like to name your Nanny? Type in chat.");
        }
    }

    /**
     * Cancels damage to any of our active Nanny NPCs.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.citizensEnabled) {
            return;
        }
        if (!CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) {
            return;
        }
        boolean isOurNanny = activeNannies.values().stream()
                .anyMatch(e -> event.getEntity().equals(e.getNpcEntity()));
        if (isOurNanny) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents mobs from targeting any of our active Nanny NPCs.
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!plugin.citizensEnabled) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        if (!CitizensAPI.getNPCRegistry().isNPC(event.getTarget())) {
            return;
        }
        boolean isOurNanny = activeNannies.values().stream()
                .anyMatch(e -> event.getTarget().equals(e.getNpcEntity()));
        if (isOurNanny) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Spawn / despawn helpers
    // -------------------------------------------------------------------------

    /**
     * Spawns a Citizens2 NPC for the given Nanny at the specified location.
     * No-ops if the Nanny is already active or if Citizens2 is unavailable.
     */
    private void spawnNanny(NannyData data, Location location) {
        UUID nannyUUID = data.getNannyUUID();
        if (activeNannies.containsKey(nannyUUID)) {
            return; // already spawned
        }
        if (!plugin.citizensEnabled) {
            return;
        }

        NannyEntity entity = new NannyEntity(data, plugin);
        entity.spawn(location);
        activeNannies.put(nannyUUID, entity);

        // Phase 3: create and start a navigator for this Nanny
        NannyNavigator navigator = new NannyNavigator(plugin, entity, this, data);
        navigators.put(nannyUUID, navigator);
        navigator.start();

        // Ensure an event log instance exists for this Nanny
        eventLogs.computeIfAbsent(nannyUUID, k -> NannyEventLog.loadFromData(data, plugin));

        // Start the care engine on the first active Nanny
        if (activeNannies.size() == 1 && careEngine != null) {
            careEngine.start();
        }

        plugin.getLogger().info("[NannyManager] Spawned Nanny: " + data.getName());
    }

    /**
     * Despawns the Citizens2 NPC for the given Nanny UUID and removes it
     * from the active map.
     */
    private void despawnNanny(UUID nannyUUID) {
        NannyEntity entity = activeNannies.remove(nannyUUID);
        NannyNavigator navigator = navigators.remove(nannyUUID);
        if (navigator != null) {
            navigator.stop();
        }
        if (entity == null) {
            return;
        }
        entity.despawn();

        // Stop the care engine when no Nannies are left active
        if (activeNannies.isEmpty() && careEngine != null) {
            careEngine.stop();
        }

        NannyData data = allNannies.get(nannyUUID);
        String name = (data != null) ? data.getName() : nannyUUID.toString();
        plugin.getLogger().info("[NannyManager] Despawned Nanny: " + name);
    }

    // -------------------------------------------------------------------------
    // Public API — called by NannyCommand, NannyMenu, PlayerEventListener
    // -------------------------------------------------------------------------

    /**
     * Creates a brand-new Nanny owned by {@code owner}, saves it, and spawns it.
     * Also consumes one Nanny Egg from the owner's main hand.
     *
     * @param owner         the player who used the egg
     * @param name          the chosen Nanny name
     * @param spawnLocation where to spawn the Nanny
     */
    public void createNanny(Player owner, String name, Location spawnLocation) {
        UUID nannyUUID = UUID.randomUUID();
        NannyData data = new NannyData(nannyUUID, owner.getUniqueId(), name, plugin);

        // Store home location
        if (spawnLocation.getWorld() != null) {
            data.setHomeWorld(spawnLocation.getWorld().getName());
        }
        data.setHomeX(spawnLocation.getX());
        data.setHomeY(spawnLocation.getY());
        data.setHomeZ(spawnLocation.getZ());

        data.save(plugin.getDataFolder());
        allNannies.put(nannyUUID, data);

        // Update ward index for owner
        wardToNannies
                .computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>())
                .add(nannyUUID);

        spawnNanny(data, spawnLocation);
        owner.sendMessage(ChatColor.GOLD + "Your Nanny " + name + " has been summoned!");

        // Consume one egg from main hand
        ItemStack held = owner.getInventory().getItemInMainHand();
        if (held != null && held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            owner.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * Returns the {@link NannyData} whose owner UUID matches, or {@code null}.
     */
    public NannyData getNannyForOwner(UUID ownerUUID) {
        for (NannyData data : allNannies.values()) {
            if (data.getOwnerUUID().equals(ownerUUID)) {
                return data;
            }
        }
        return null;
    }

    /**
     * Adds a ward to the specified Nanny, updates the index, and saves.
     */
    public void addWard(UUID nannyUUID, UUID wardUUID) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) {
            return;
        }
        if (!data.getWards().contains(wardUUID)) {
            data.getWards().add(wardUUID);
        }
        wardToNannies
                .computeIfAbsent(wardUUID, k -> new ArrayList<>())
                .add(nannyUUID);
        data.save(plugin.getDataFolder());
    }

    /**
     * Removes a ward from the specified Nanny, updates the index, and saves.
     */
    public void removeWard(UUID nannyUUID, UUID wardUUID) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) {
            return;
        }
        data.getWards().remove(wardUUID);

        List<UUID> nannyList = wardToNannies.get(wardUUID);
        if (nannyList != null) {
            nannyList.remove(nannyUUID);
        }
        data.save(plugin.getDataFolder());
    }

    /**
     * Despawns the Nanny, removes it from all in-memory maps, deletes its
     * YAML file, and rebuilds the ward index.
     */
    public void deleteNanny(UUID nannyUUID) {
        despawnNanny(nannyUUID);
        NannyData data = allNannies.remove(nannyUUID);
        if (data != null) {
            File nannyFile = new File(plugin.getDataFolder(),
                    "nannies/" + nannyUUID.toString() + ".yml");
            if (nannyFile.exists()) {
                nannyFile.delete();
            }
        }
        buildWardIndex();
    }

    /**
     * Delegates to the active {@link NannyEntity} to enable or disable
     * follow mode for the given ward.
     */
    public void setFollowMode(UUID nannyUUID, boolean follow, Player ward) {
        NannyData data = allNannies.get(nannyUUID);
        NannyNavigator navigator = navigators.get(nannyUUID);

        if (follow && ward != null && data != null && inventoryManager != null) {
            inventoryManager.prepareFollowSupplies(data);
        }

        if (navigator != null) {
            if (follow && ward != null) {
                navigator.setFollowTarget(ward);
            } else {
                navigator.cancelNavigation();
            }
        } else {
            // Fallback: Citizens2 absent or Nanny not yet spawned
            NannyEntity entity = activeNannies.get(nannyUUID);
            if (entity != null) {
                entity.setFollowMode(follow, ward);
            }
        }
    }

    /**
     * Updates the Nanny's display name, saves, and notifies the active entity.
     */
    public void updateNannyName(UUID nannyUUID, String name) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) {
            return;
        }
        data.setName(name);
        data.save(plugin.getDataFolder());
        NannyEntity entity = activeNannies.get(nannyUUID);
        if (entity != null) {
            entity.updateName(name);
        }
    }

    /**
     * Updates the Nanny's skin, saves, and notifies the active entity.
     */
    public void updateNannySkin(UUID nannyUUID, String skinName) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) {
            return;
        }
        data.setSkinUrl(skinName);
        data.save(plugin.getDataFolder());
        NannyEntity entity = activeNannies.get(nannyUUID);
        if (entity != null) {
            entity.updateSkin(skinName);
        }
    }

    /**
     * Updates the Nanny's home location and saves.
     */
    public void setHome(UUID nannyUUID, Location location) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) {
            return;
        }
        if (location.getWorld() != null) {
            data.setHomeWorld(location.getWorld().getName());
        }
        data.setHomeX(location.getX());
        data.setHomeY(location.getY());
        data.setHomeZ(location.getZ());
        data.save(plugin.getDataFolder());
    }

    /**
     * Teleports the active Nanny NPC to the given player's location.
     */
    public void summonToPlayer(UUID nannyUUID, Player player) {
        NannyEntity entity = activeNannies.get(nannyUUID);
        if (entity != null) {
            entity.teleportTo(player.getLocation());
            if (chatEngine != null && player != null) {
                // 10-tick delay so the teleport settles and the line broadcasts from her new spot
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        chatEngine.speakIfNearby(player, "nanny_summoned",
                                "lifecycle:summoned", 30_000L,
                                NannyChatEngine.PRI_LIFECYCLE);
                    }
                }, 10L);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Map accessors
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of all loaded Nanny data. */
    public Map<UUID, NannyData> getAllNannies() {
        return Collections.unmodifiableMap(allNannies);
    }

    /** Returns an unmodifiable view of currently active (spawned) Nannies. */
    public Map<UUID, NannyEntity> getActiveNannies() {
        return Collections.unmodifiableMap(activeNannies);
    }

    /** Returns the ward-to-nannies lookup map. */
    public Map<UUID, List<UUID>> getWardToNannies() {
        return wardToNannies;
    }

    /**
     * Returns the pending-creation map so that {@code PlayerEventListener}
     * can complete a Nanny creation after the player types a name.
     */
    public Map<UUID, Location> getPendingNannyCreations() {
        return pendingNannyCreations;
    }

    public NannyCareEngine getCareEngine() {
        return careEngine;
    }

    public NannyInventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public NannyNavigator getNavigator(UUID nannyUUID) {
        return navigators.get(nannyUUID);
    }

    /**
     * Lazily fetches (or loads) the per-Nanny event log. Returns {@code null}
     * if no NannyData exists for the given UUID.
     */
    public NannyEventLog getEventLog(UUID nannyUUID) {
        NannyData data = allNannies.get(nannyUUID);
        if (data == null) return null;
        return eventLogs.computeIfAbsent(nannyUUID, k -> NannyEventLog.loadFromData(data, plugin));
    }

    public NannyChatEngine getChatEngine() {
        return chatEngine;
    }

    public NannyReactiveListener getReactiveListener() {
        return reactiveListener;
    }

    public void setReactiveListener(NannyReactiveListener listener) {
        this.reactiveListener = listener;
    }

    public MembershipProvider getMembershipProvider() {
        return membershipProvider;
    }

    public void setMembershipProvider(com.storynook.nanny.MembershipProvider mp) {
        this.membershipProvider = mp;
    }
}
