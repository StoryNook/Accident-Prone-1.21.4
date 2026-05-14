package com.storynook.nanny;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.storynook.Plugin;

import net.citizensnpcs.api.npc.NPC;

/**
 * Per-Nanny Citizens2 Navigator wrapper.
 *
 * <p>Runs a 10-tick scan task that:
 * <ol>
 *   <li>Detects when Citizens2 cancels navigation (stuck via stationaryTicks)
 *       and teleports to the destination as a fallback.</li>
 *   <li>Scans a 2-block radius for closed doors/levers while navigating and
 *       opens them; restores them after 60 ticks.</li>
 * </ol>
 *
 * <p>All Citizens2 API calls are guarded by {@code plugin.citizensEnabled}.
 */
public class NannyNavigator {

    private static final Set<Material> DOOR_TYPES = new HashSet<>(Arrays.asList(
        Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
        Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
        Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR
    ));

    private final Plugin plugin;
    private final NannyEntity entity;
    private final NannyManager manager;
    private final NannyData data;

    private BukkitTask scanTask;

    /** Non-null while navigating to a fixed Location. Null during entity-follow or idle. */
    private Location currentTarget;

    /** Non-null while seeking a specific ward — cleared on arrival or ward going offline. */
    private UUID seekingWardUUID;

    /** Block keys already scheduled for restore; prevents double-toggling. */
    private final Set<String> pendingRestore = new HashSet<>();

    public NannyNavigator(Plugin plugin, NannyEntity entity, NannyManager manager, NannyData data) {
        this.plugin = plugin;
        this.entity = entity;
        this.manager = manager;
        this.data = data;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void start() {
        if (scanTask != null) return;
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        currentTarget = null;
        seekingWardUUID = null;
        pendingRestore.clear();
    }

    private void tick() {
        if (!plugin.citizensEnabled || !entity.isSpawned()) return;
        checkNavigationCompleted();
        doBlockInteractionScan();
    }

    // -----------------------------------------------------------------------
    // Navigation API
    // -----------------------------------------------------------------------

    /**
     * Navigates the NPC to a fixed location. Falls back to teleport when
     * Citizens2 is absent.
     */
    public void navigateTo(Location dest) {
        if (dest == null) return;
        if (!plugin.citizensEnabled || !entity.isSpawned()) {
            entity.teleportTo(dest);
            return;
        }
        NPC npc = entity.getNpc();
        if (npc == null) return;
        currentTarget = dest;
        npc.getNavigator().getLocalParameters()
            .range(128)
            .speedModifier(1.0f)
            .stationaryTicks(600);
        npc.getNavigator().setTarget(dest);
    }

    /**
     * Enters entity-follow mode (NPC walks beside the player continuously).
     * Does not set {@code currentTarget} — there is no fixed destination.
     * Falls back to teleport when Citizens2 is absent.
     *
     * <p>A {@code distanceMargin} of 2.5 keeps her ~2-3 blocks back from the
     * ward instead of standing on top of them. Still within the 3-block action
     * range used by {@link NannyCareEngine#isWithinActionRange} so care actions
     * still trigger.
     */
    public void setFollowTarget(Player ward) {
        if (ward == null) return;
        if (!plugin.citizensEnabled || !entity.isSpawned()) {
            entity.teleportTo(ward.getLocation());
            return;
        }
        NPC npc = entity.getNpc();
        if (npc == null) return;
        currentTarget = null;
        seekingWardUUID = null;
        npc.getNavigator().getLocalParameters()
            .range(128)
            .speedModifier(1.0f)
            .stationaryTicks(600)
            .distanceMargin(2.5);
        npc.getNavigator().setTarget(ward, false);
    }

    /**
     * Navigates to the ward's current location and marks the Nanny as seeking
     * that ward. {@link NannyCareEngine} detects arrival and sends the
     * "Found you!" message.
     */
    public void seekTo(Player ward) {
        if (ward == null) return;
        seekingWardUUID = ward.getUniqueId();
        navigateTo(ward.getLocation());
        NannyEventLog log = manager.getEventLog(data.getNannyUUID());
        if (log != null) {
            log.log(NannyEventLog.NannyEventType.SEEKING_WARD, ward.getUniqueId(), "seekTo");
        }
    }

    public void cancelNavigation() {
        if (!plugin.citizensEnabled || !entity.isSpawned()) return;
        NPC npc = entity.getNpc();
        if (npc == null) return;
        npc.getNavigator().cancelNavigation();
        currentTarget = null;
        seekingWardUUID = null;
    }

    public boolean isNavigating() {
        if (!plugin.citizensEnabled || !entity.isSpawned()) return false;
        NPC npc = entity.getNpc();
        return npc != null && npc.getNavigator().isNavigating();
    }

    public UUID getSeekingWardUUID() { return seekingWardUUID; }

    public void clearSeekingWard() { seekingWardUUID = null; }

    // -----------------------------------------------------------------------
    // Stuck-teleport fallback
    // -----------------------------------------------------------------------

    /**
     * If {@code currentTarget} is set but Citizens2 is no longer navigating
     * (cancelled due to stationaryTicks or arrival), checks whether the NPC
     * is within 3 blocks of the target. If not, teleports (stuck fallback) and
     * logs STUCK_TELEPORT.
     */
    private void checkNavigationCompleted() {
        if (currentTarget == null || isNavigating()) return;

        Location here = entity.getLocation();
        if (here == null) {
            currentTarget = null;
            return;
        }

        boolean sameWorld = currentTarget.getWorld() != null
                && here.getWorld().equals(currentTarget.getWorld());

        if (!sameWorld || here.distanceSquared(currentTarget) > 9.0) {
            entity.teleportTo(currentTarget);
            NannyEventLog log = manager.getEventLog(data.getNannyUUID());
            if (log != null) {
                log.log(NannyEventLog.NannyEventType.STUCK_TELEPORT, null,
                        "dest=" + currentTarget.getBlockX() + ","
                                + currentTarget.getBlockY() + ","
                                + currentTarget.getBlockZ());
            }
        }
        currentTarget = null;

        currentTarget = null;
    }

    // -----------------------------------------------------------------------
    // Door / lever interaction
    // -----------------------------------------------------------------------

    private void doBlockInteractionScan() {
        if (!isNavigating()) return;
        Location loc = entity.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = loc.getWorld().getBlockAt(x + dx, y + dy, z + dz);
                    if (DOOR_TYPES.contains(block.getType())) {
                        handleDoor(block);
                    } else if (block.getType() == Material.LEVER) {
                        handleLever(block);
                    }
                }
            }
        }
    }

    private void handleDoor(Block block) {
        BlockData bd = block.getBlockData();
        if (!(bd instanceof Openable)) return;
        if (((Openable) bd).isOpen()) return;

        String key = blockKey(block);
        if (pendingRestore.contains(key)) return;

        if (block.getType() == Material.IRON_DOOR && !hasAdjacentLever(block)) {
            // Iron door with no lever — can't open; teleport past after 100 ticks
            pendingRestore.add(key);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingRestore.remove(key);
                if (currentTarget != null) entity.teleportTo(currentTarget);
            }, 100L);
            return;
        }

        ((Openable) bd).setOpen(true);
        block.setBlockData(bd);
        pendingRestore.add(key);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRestore.remove(key);
            BlockData current = block.getBlockData();
            if (current instanceof Openable) {
                ((Openable) current).setOpen(false);
                block.setBlockData(current);
            }
        }, 60L);
    }

    private void handleLever(Block block) {
        BlockData bd = block.getBlockData();
        if (!(bd instanceof Powerable)) return;

        String key = blockKey(block);
        if (pendingRestore.contains(key)) return;

        boolean wasPowered = ((Powerable) bd).isPowered();
        ((Powerable) bd).setPowered(!wasPowered);
        block.setBlockData(bd);
        pendingRestore.add(key);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRestore.remove(key);
            BlockData current = block.getBlockData();
            if (current instanceof Powerable) {
                ((Powerable) current).setPowered(wasPowered);
                block.setBlockData(current);
            }
        }, 60L);
    }

    private boolean hasAdjacentLever(Block block) {
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            if (block.getRelative(face).getType() == Material.LEVER) return true;
        }
        return false;
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ","
                + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
