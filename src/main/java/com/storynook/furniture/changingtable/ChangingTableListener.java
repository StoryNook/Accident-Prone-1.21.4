package com.storynook.furniture.changingtable;

import com.storynook.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * All Bukkit event handlers for changing-table placement. Right-click dispatch
 * (recolor, lay-down, inventory) is added in later tasks.
 */
public final class ChangingTableListener implements Listener {

    private final Plugin plugin;
    private final ChangingTableRegistry registry;
    private final ChangingTablePdcKeys keys;

    /** Per-axis ItemDisplay scale. The changingtable model's native bounds are
     *  1.125 × 0.625 × 0.578 blocks (X × Y × Z), so these factors stretch it to
     *  a 2-wide × 1-tall × 1-deep table. Non-uniform scale is fine — Bukkit's
     *  Transformation takes a Vector3f. Tune per-axis here if the footprint
     *  looks off; no model-file edit needed. */
    private static final float SCALE_X = 1.778f;   // 1.125 → 2.0 blocks wide
    private static final float SCALE_Y = 1.600f;   // 0.625 → 1.0 block tall
    private static final float SCALE_Z = 1.440f;   // tuned: depth read ~1.2 at 1.73

    /** Vertical lift (blocks) added to the display translation. Tuned in QA so
     *  the table base sits on the ground rather than sunk in or floating. */
    private static final float TABLE_Y_LIFT = 0.75f;

    /** Clickable Interaction-entity hitbox. Bukkit's Interaction box is square
     *  (width × width) × height, so a single one can't be 2×1 — instead we
     *  spawn TWO interactions, one per barrier cell. Slightly larger than 1×1
     *  so they're easy to click without a visible overhang into other cells. */
    private static final float INTERACTION_WIDTH  = 1.1f;
    private static final float INTERACTION_HEIGHT = 1.1f;

    public ChangingTableListener(Plugin plugin, ChangingTableRegistry registry, ChangingTablePdcKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickPlace(PlayerInteractEvent event) {
        if (!isOn()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getBlockFace() != BlockFace.UP) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SLIME_BALL) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;
        int cmd = meta.getCustomModelData();
        if (cmd < ChangingTableRegistry.CMD_BASE || cmd > ChangingTableRegistry.CMD_MAX) return;
        int colorIndex = cmd - ChangingTableRegistry.CMD_BASE;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        float snappedYaw = ChangingTablePlacementService.snapYaw(player.getLocation().getYaw());
        int[] off = ChangingTablePlacementService.footOffset(snappedYaw);

        Block anchorBase = clicked;
        Block footBase   = clicked.getRelative(off[0], 0, off[1]);
        Block anchorOrigin = clicked.getRelative(0, 1, 0);
        Block footOrigin   = footBase.getRelative(0, 1, 0);

        boolean playerInAnchor = sameCell(player.getLocation(), anchorOrigin);
        boolean playerInFoot   = sameCell(player.getLocation(), footOrigin);
        boolean footInWorld    = footBase.getWorld() != null;

        ChangingTablePlacementService.Result result = ChangingTablePlacementService.validate(
            anchorBase.getType().isSolid(),
            footBase.getType().isSolid(),
            anchorOrigin.getType().isAir(),
            footOrigin.getType().isAir(),
            playerInAnchor, playerInFoot, footInWorld
        );
        if (result != ChangingTablePlacementService.Result.OK) {
            event.setCancelled(true);
            return;     // silent — no message, no sound
        }

        // Protection-plugin veto: synthetic BlockPlaceEvent per cell.
        for (Block target : List.of(anchorOrigin, footOrigin)) {
            BlockState replaced = target.getState();
            BlockPlaceEvent fake = new BlockPlaceEvent(
                target, replaced, clicked, item, player,
                player.hasPermission("worldguard.region.bypass.*"),
                EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(fake);
            if (fake.isCancelled() || !fake.canBuild()) {
                event.setCancelled(true);
                return;   // silent
            }
        }

        // Place barriers
        anchorOrigin.setType(Material.BARRIER);
        footOrigin.setType(Material.BARRIER);

        // Compute display location + transform BEFORE spawn so the entity is
        // born oriented (no client-side rotate-into-place interpolation).
        Location displayLoc = new Location(
            anchorBase.getWorld(),
            (anchorOrigin.getX() + footOrigin.getX()) / 2.0 + 0.5,
            anchorOrigin.getY() + 0.0,
            (anchorOrigin.getZ() + footOrigin.getZ()) / 2.0 + 0.5
        );

        // Translation: lift the model up by 1 block so it sits on the barrier
        // cell, plus an empirical world-space shift to seat it on the mat:
        //   TABLE_BACK_SHIFT  blocks "back" (away from player, their facing dir)
        //   TABLE_RIGHT_SHIFT blocks to the player's right
        //
        // Back direction (D)   = (-sin(yaw),  0,  cos(yaw))
        // Player's right (R)   = (-cos(yaw),  0, -sin(yaw))
        // Combined shift       = BACK*D + RIGHT*R
        // Tuned empirically against the mat surface.
        final double TABLE_BACK_SHIFT  = 0.25;
        final double TABLE_RIGHT_SHIFT = 0.10;
        double yawRad = Math.toRadians(snappedYaw);
        double sinY = Math.sin(yawRad);
        double cosY = Math.cos(yawRad);
        float shiftX = (float) (-sinY * TABLE_BACK_SHIFT - cosY * TABLE_RIGHT_SHIFT);
        float shiftZ = (float) ( cosY * TABLE_BACK_SHIFT - sinY * TABLE_RIGHT_SHIFT);
        Vector3f translation = new Vector3f(shiftX, TABLE_Y_LIFT, shiftZ);

        // Rotation formula: (180 - yaw). Works for all four cardinal yaws.
        org.joml.AxisAngle4f rotation = new org.joml.AxisAngle4f(
            (float) Math.toRadians(180f - snappedYaw), 0f, 1f, 0f);

        ItemDisplay display = anchorBase.getWorld().spawn(displayLoc, ItemDisplay.class, ed -> {
            ed.setItemStack(ChangingTableItem.make(colorIndex));
            ed.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            ed.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            ed.setTransformation(new Transformation(
                translation,
                rotation,
                new Vector3f(SCALE_X, SCALE_Y, SCALE_Z),
                new org.joml.AxisAngle4f(0f, 0f, 1f, 0f)
            ));
            ed.setBrightness(new Brightness(15, 15));
            ed.setPersistent(true);
        });

        // Two 1×1×1 Interaction entities, one centred on each barrier cell, so
        // the clickable region exactly mirrors the two barrier blocks. The
        // entity sits at the cell's bottom-centre; height 1.0 fills the cell.
        java.util.function.Consumer<Interaction> initIx = ix -> {
            ix.setInteractionWidth(INTERACTION_WIDTH);
            ix.setInteractionHeight(INTERACTION_HEIGHT);
            ix.setResponsive(true);
            ix.setPersistent(true);
            ix.addScoreboardTag(ChangingTablePdcKeys.SCOREBOARD_TAG);
        };
        Location anchorIxLoc = new Location(anchorBase.getWorld(),
            anchorOrigin.getX() + 0.5, anchorOrigin.getY(), anchorOrigin.getZ() + 0.5);
        Location footIxLoc = new Location(anchorBase.getWorld(),
            footOrigin.getX() + 0.5, footOrigin.getY(), footOrigin.getZ() + 0.5);
        Interaction interactionAnchor = anchorBase.getWorld().spawn(anchorIxLoc, Interaction.class, initIx);
        Interaction interactionFoot   = anchorBase.getWorld().spawn(footIxLoc,   Interaction.class, initIx);

        ChangingTable table = new ChangingTable(
            UUID.randomUUID(),
            anchorBase.getWorld().getName(),
            anchorOrigin.getX(), anchorOrigin.getY() - 1, anchorOrigin.getZ(),  // anchorY = floor Y (barrier is anchor.y + 1)
            snappedYaw, colorIndex,
            display.getUniqueId(), interactionAnchor.getUniqueId(), player.getUniqueId()
        );
        // Both interactions carry the full PDC so either one resolves the table
        // (fromPdc derives interactionUuid from whichever entity is read).
        table.writeToPdc(interactionAnchor, keys);
        table.writeToPdc(interactionFoot, keys);
        registry.register(table);

        if (player.getGameMode() != GameMode.CREATIVE) {
            decrementHand(player);
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (!isOn()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains(ChangingTablePdcKeys.SCOREBOARD_TAG)) return;

        ChangingTable table = lookupOrRebuild(interaction);
        if (table == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        BlockFace clickedFace = resolveClickedFace(event.getClickedPosition());

        // Protection-plugin veto on broad interaction.
        Block anchorBarrier = anchorBarrierBlock(table);
        if (anchorBarrier != null) {
            PlayerInteractEvent fake = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK,
                hand, anchorBarrier, BlockFace.UP, EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(fake);
            if (fake.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
                event.setCancelled(true);
                return;
            }
        }

        boolean handCarpet = hand != null && hand.getType().name().endsWith("_CARPET");
        boolean handEmpty = hand == null || hand.getType() == Material.AIR;
        boolean carrying = plugin.getCarryManager() != null
            && plugin.getCarryManager().isCarrying(player.getUniqueId());

        if (handCarpet) {
            handleRecolor(player, hand, table);
        } else if (carrying && clickedFace == BlockFace.UP) {
            handleCaregiverLay(player, table);
        } else if (handEmpty && clickedFace == BlockFace.UP) {
            handleSelfLay(player, table);
        } else {
            handleOpenInventory(player, table);
        }
        event.setCancelled(true);
    }

    /** Resolves a ChangingTable for the given Interaction. If the registry hasn't seen
     *  this Interaction yet (chunk-load race), rebuilds from PDC and registers. */
    private ChangingTable lookupOrRebuild(Interaction interaction) {
        // Try by Interaction UUID first via the byBarrierBlock index — actually we don't
        // have a byInteractionUuid lookup; rebuild from PDC every time as the cheapest path.
        ChangingTable t = ChangingTable.fromPdc(interaction, keys);
        if (t == null) return null;
        if (registry.findById(t.id()) == null) registry.register(t);
        return t;
    }

    /** Resolves which of the 6 box faces was clicked, from the Interaction-
     *  relative click position. The Interaction hitbox spans Y ∈ [0, HEIGHT]
     *  and X/Z ∈ [-WIDTH/2, +WIDTH/2] (entity origin at the bottom-centre), so
     *  the click — which lands exactly on the box surface — lies on whichever
     *  face plane it is closest to. Only BlockFace.UP triggers lay-down; every
     *  other face routes to the inventory. */
    private static BlockFace resolveClickedFace(org.bukkit.util.Vector clickedPos) {
        if (clickedPos == null) return BlockFace.UP;
        double halfW = INTERACTION_WIDTH / 2.0;
        double dTop    = Math.abs(clickedPos.getY() - INTERACTION_HEIGHT);
        double dBottom = Math.abs(clickedPos.getY());
        double dEast   = Math.abs(clickedPos.getX() - halfW);
        double dWest   = Math.abs(clickedPos.getX() + halfW);
        double dSouth  = Math.abs(clickedPos.getZ() - halfW);
        double dNorth  = Math.abs(clickedPos.getZ() + halfW);
        double min = Math.min(Math.min(Math.min(dTop, dBottom),
                                       Math.min(dEast, dWest)),
                              Math.min(dSouth, dNorth));
        if (min == dTop)    return BlockFace.UP;
        if (min == dBottom) return BlockFace.DOWN;
        if (min == dEast)   return BlockFace.EAST;
        if (min == dWest)   return BlockFace.WEST;
        if (min == dSouth)  return BlockFace.SOUTH;
        return BlockFace.NORTH;
    }

    /** Finds the changing table's Interaction entities by scanning the two
     *  barrier cells. There are two (one per cell); they aren't both tracked
     *  by UUID in the record, so callers that need to mutate or remove them
     *  (teardown, recolor) locate them this way. */
    private List<Interaction> findInteractions(ChangingTable table) {
        List<Interaction> result = new java.util.ArrayList<>();
        org.bukkit.World w = Bukkit.getWorld(table.worldName());
        if (w == null) return result;
        int[][] cells = {
            { table.anchorX(), table.anchorY() + 1, table.anchorZ() },
            { table.footX(),   table.footY()   + 1, table.footZ()   }
        };
        for (int[] cell : cells) {
            Location c = new Location(w, cell[0] + 0.5, cell[1] + 0.5, cell[2] + 0.5);
            for (org.bukkit.entity.Entity e : w.getNearbyEntities(c, 0.6, 0.6, 0.6)) {
                if (e instanceof Interaction ix
                        && ix.getScoreboardTags().contains(ChangingTablePdcKeys.SCOREBOARD_TAG)) {
                    result.add(ix);
                }
            }
        }
        return result;
    }

    private Block anchorBarrierBlock(ChangingTable t) {
        org.bukkit.World w = Bukkit.getWorld(t.worldName());
        if (w == null) return null;
        return w.getBlockAt(t.anchorX(), t.anchorY() + 1, t.anchorZ());
    }

    // ---- Sub-handler stubs (filled in by T15/T16/T17) ----

    private void handleRecolor(Player player, ItemStack carpet, ChangingTable table) {
        int newColor = ChangingTableRegistry.colorIndexFromCarpet(carpet.getType());
        if (newColor < 0) return;                  // unregistered colour → silent no-op
        if (newColor == table.colorIndex()) return; // same colour → silent no-op

        // Update live ItemDisplay's ItemStack (model swaps in place; transform unchanged).
        org.bukkit.entity.Entity displayEnt = Bukkit.getEntity(table.displayUuid());
        if (displayEnt instanceof org.bukkit.entity.ItemDisplay id) {
            id.setItemStack(ChangingTableItem.make(newColor));
        }

        // Update colorIndex PDC on BOTH Interaction entities so a chunk-reload
        // rebuild reads a consistent colour regardless of which one fromPdc hits.
        for (Interaction interaction : findInteractions(table)) {
            interaction.getPersistentDataContainer().set(
                keys.colorIndex,
                org.bukkit.persistence.PersistentDataType.INTEGER,
                newColor
            );
        }

        // Rewrite the registry entry (record is immutable).
        ChangingTable updated = new ChangingTable(
            table.id(), table.worldName(),
            table.anchorX(), table.anchorY(), table.anchorZ(),
            table.yaw(), newColor,
            table.displayUuid(), table.interactionUuid(), table.ownerUuid()
        );
        registry.register(updated);

        // Return the old-colour carpet.
        String oldColorKey = ChangingTableRegistry.colorKeyFor(table.colorIndex());
        if (oldColorKey != null) {
            try {
                Material oldCarpetMat = Material.valueOf(oldColorKey.toUpperCase(java.util.Locale.ROOT) + "_CARPET");
                ItemStack returned = new ItemStack(oldCarpetMat, 1);
                java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(returned);
                for (ItemStack stack : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                }
            } catch (IllegalArgumentException ignored) {
                // oldColorKey doesn't map to a vanilla carpet — shouldn't happen for the 16 registered
            }
        }

        // Consume the new carpet.
        if (carpet.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else carpet.setAmount(carpet.getAmount() - 1);

        // Soft sound; silent on category misconfiguration.
        try {
            com.storynook.PlaySounds.playsounds(player, "changing", 1, 0.4, 0.6, false);
        } catch (Throwable ignored) {}
    }

    private void handleSelfLay(Player player, ChangingTable table) {
        if (registry.isWardOnTable(player.getUniqueId())) return;   // already on a table
        if (anyoneAlreadyOn(table)) return;                          // this table occupied
        if (isContainedInCrib(player)) return;                       // crib trumps
        seat(player, table, null);
    }

    private void handleCaregiverLay(Player caregiver, ChangingTable table) {
        if (plugin.getCarryManager() == null) return;
        UUID wardUuid = plugin.getCarryManager().wardOf(caregiver.getUniqueId());
        if (wardUuid == null) return;
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward == null) return;
        if (registry.isWardOnTable(wardUuid)) return;
        if (anyoneAlreadyOn(table)) return;

        // Release the carry (CarryManager.onDismount returns a saddle to caregiver).
        plugin.getCarryManager().clearCarryByWard(wardUuid);
        // Defensive saddle return — match CarryManager's normal behavior if its
        // own dismount handler doesn't fire (we're bypassing the vehicle path).
        ItemStack saddle = new ItemStack(Material.SADDLE, 1);
        java.util.Map<Integer, ItemStack> leftover = caregiver.getInventory().addItem(saddle);
        for (ItemStack s : leftover.values()) {
            caregiver.getWorld().dropItemNaturally(caregiver.getLocation(), s);
        }

        seat(ward, table, caregiver.getUniqueId());
    }

    private boolean anyoneAlreadyOn(ChangingTable table) {
        for (UUID ward : registry.occupiedWards()) {
            UUID tid = registry.tableIdForWard(ward);
            if (tid != null && tid.equals(table.id())) return true;
        }
        return false;
    }

    private boolean isContainedInCrib(Player player) {
        com.storynook.PlayerStatsManagement.PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        return stats != null && stats.getContainedInCribId() != null;
    }

    /** Tokens marking "the next teleport for this UUID is one we initiated";
     *  the onTeleport handler ignores those so the per-tick pin doesn't
     *  trigger auto-release. */
    private final java.util.Set<UUID> selfTeleportTokens =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Per-table-seat anchor location, so onMove can snap the ward back to it
     *  without re-querying the registry/record. */
    private final java.util.Map<UUID, Location> seatAnchors =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Active LaydownPoseNpc per ward UUID. Replaces the hidden-bed approach
     *  entirely — the NPC is the only thing rendered laying down. */
    private final java.util.Map<UUID, LaydownPoseNpc> activeNpcs =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Common seat path. placerUuid is null for self-lay, the caregiver UUID for caregiver-lay. Spawns a LaydownPoseNpc clone in Pose.SLEEPING and hides the real player at the mat surface. */
    private void seat(Player ward, ChangingTable table, UUID placerUuid) {
        Location matCentre = table.matCentre();
        if (matCentre == null) return;
        if (matCentre.getWorld() == null) return;

        // Compute bed direction from the table's foot offset. With the body
        // laid out along (anchor → foot), the bed's "head" faces back toward the
        // anchor — i.e. opposite of the foot direction.
        int[] off = ChangingTablePlacementService.footOffset(table.yaw());
        BlockFace bedFacing = blockFaceForOffset(-off[0], -off[1]);

        float layYaw = table.yaw();
        Location seatLoc = matCentre.clone();
        seatLoc.setYaw(layYaw);
        seatLoc.setPitch(0f);

        selfTeleportTokens.add(ward.getUniqueId());
        ward.teleport(seatLoc);
        ward.setGravity(false);
        ward.setInvisible(true);
        ward.setSleepingIgnored(true);   // suppress night-skip vote while laid

        LaydownPoseNpc npc = new LaydownPoseNpc(plugin, ward, seatLoc, bedFacing);
        npc.spawn();
        activeNpcs.put(ward.getUniqueId(), npc);

        seatAnchors.put(ward.getUniqueId(), seatLoc);
        registry.recordOccupancy(ward.getUniqueId(), table.id(), placerUuid, null);
    }

    private static BlockFace blockFaceForOffset(int dx, int dz) {
        if (dx ==  1) return BlockFace.EAST;
        if (dx == -1) return BlockFace.WEST;
        if (dz ==  1) return BlockFace.SOUTH;
        if (dz == -1) return BlockFace.NORTH;
        return BlockFace.NORTH;   // shouldn't happen for snapped yaws
    }

    /** Force the SLEEPING pose via reflection (Paper exposes the 2-arg setPose;
     *  Spigot only has the 1-arg form). Silent on failure — cosmetic only. */
    private void applySleepingPose(Player ward) {
        try {
            java.lang.reflect.Method m = ward.getClass().getMethod(
                "setPose", org.bukkit.entity.Pose.class, boolean.class);
            m.invoke(ward, org.bukkit.entity.Pose.SLEEPING, true);
        } catch (NoSuchMethodException nsme) {
            try {
                java.lang.reflect.Method m = ward.getClass().getMethod(
                    "setPose", org.bukkit.entity.Pose.class);
                m.invoke(ward, org.bukkit.entity.Pose.SLEEPING);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /** Restore the natural STANDING pose. Calls the 2-arg form with
     *  {@code fixed=false} to clear the lock set by {@link #applySleepingPose}. */
    private void applyStandingPose(Player ward) {
        try {
            java.lang.reflect.Method m = ward.getClass().getMethod(
                "setPose", org.bukkit.entity.Pose.class, boolean.class);
            m.invoke(ward, org.bukkit.entity.Pose.STANDING, false);
        } catch (NoSuchMethodException nsme) {
            try {
                java.lang.reflect.Method m = ward.getClass().getMethod(
                    "setPose", org.bukkit.entity.Pose.class);
                m.invoke(ward, org.bukkit.entity.Pose.STANDING);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    /** Centralised release: tear down the NPC clone, restore real-player
     *  visibility and gravity, drop registry occupancy. */
    private void releaseLayDown(Player ward) {
        UUID id = ward.getUniqueId();
        if (!registry.isWardOnTable(id)) return;

        LaydownPoseNpc npc = activeNpcs.remove(id);
        if (npc != null) npc.despawn();

        ward.setInvisible(false);
        ward.setGravity(true);
        ward.setSleepingIgnored(false);
        seatAnchors.remove(id);
        registry.releaseWard(id);
    }

    /** Offline-safe release (quit / disable): tear down the NPC but skip
     *  player-state restoration since the Player handle may be invalid. */
    private void releaseLayDownByUuid(UUID id) {
        if (!registry.isWardOnTable(id)) return;
        LaydownPoseNpc npc = activeNpcs.remove(id);
        if (npc != null) npc.despawn();
        seatAnchors.remove(id);
        registry.releaseWard(id);
    }

    /** Sneak-to-exit when a ward is lying on a changing table (no ArmorStand,
     *  so {@link org.bukkit.event.entity.EntityDismountEvent} doesn't fire). */
    /** Defensive cleanup on join: if a previous session disconnected mid-laydown,
     *  setInvisible(true) and setGravity(false) were saved to the player's NBT
     *  and ride back on rejoin. Since {@link #releaseLayDownByUuid} clears the
     *  registry on quit, a player rejoining is by definition NOT on a table —
     *  so it's safe to reset both flags. */
    @EventHandler
    public void onJoinStuckLaydown(org.bukkit.event.player.PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        // Crash-disconnect during laydown can leave the registry pointing at a
        // table and the player's NBT carrying invisible+no-gravity+sleep-ignored.
        // Clear all of it, then defer one more pass at tick+1 so NBT load can't
        // race us.
        if (registry.isWardOnTable(id)) {
            LaydownPoseNpc npc = activeNpcs.remove(id);
            if (npc != null) npc.despawn();
            seatAnchors.remove(id);
            registry.releaseWard(id);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (p.isInvisible()) p.setInvisible(false);
            if (!p.hasGravity()) p.setGravity(true);
            if (p.isSleepingIgnored()) p.setSleepingIgnored(false);
        }, 1L);
    }

    /** Movement lock: while a ward is seated on a changing table, cancel any
     *  PlayerMoveEvent whose x/y/z changes — only rotation (look) passes.
     *  Sneak (handled by {@link #onSneakExit}) still releases. */
    @EventHandler(ignoreCancelled = true)
    public void onMoveWhileSeated(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!registry.isWardOnTable(event.getPlayer().getUniqueId())) return;
        if (selfTeleportTokens.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // pure rotation — allow
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onSneakExit(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        releaseLayDown(event.getPlayer());
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player ward)) return;
        UUID wardId = ward.getUniqueId();
        if (!registry.isWardOnTable(wardId)) return;
        UUID expectedAS = registry.armorStandForWard(wardId);
        if (expectedAS == null) return;
        if (!expectedAS.equals(event.getDismounted().getUniqueId())) return;

        try {
            java.lang.reflect.Method setPose = ward.getClass().getMethod("setPose", org.bukkit.entity.Pose.class, boolean.class);
            setPose.invoke(ward, org.bukkit.entity.Pose.STANDING, false);
        } catch (Throwable ignored) {}
        registry.releaseWard(wardId);
    }

    private void handleOpenInventory(Player player, ChangingTable table) {
        if (plugin.getChangingTableInventoryManager() != null) {
            plugin.getChangingTableInventoryManager().open(player, table);
        }
    }

    /** Read whether the Nursery_Items master flag is on. */
    private boolean isOn() {
        Object flag = plugin.getGlobalConfig().get("Nursery_Items");
        return Boolean.TRUE.equals(flag);
    }

    private static boolean sameCell(Location loc, Block b) {
        return loc.getBlockX() == b.getX()
            && loc.getBlockY() == b.getY()
            && loc.getBlockZ() == b.getZ();
    }

    private static void decrementHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;
        if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // The per-tick seat pin teleports the ward back to its anchor — skip
        // those (otherwise we'd release-and-re-seat every tick).
        if (selfTeleportTokens.remove(id)) return;
        releaseLayDown(event.getPlayer());
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        releaseLayDown(event.getEntity());
    }

    @EventHandler
    public void onChangeWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        releaseLayDown(event.getPlayer());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        // Restore player flags BEFORE NBT save fires later in the quit pipeline.
        // setInvisible(true), setGravity(false), and setSleepingIgnored(true) all
        // persist in player NBT — without this, rejoin re-applies them and the
        // player loads invisible + no-gravity + sleep-vote-ignored.
        if (registry.isWardOnTable(id)) {
            p.setInvisible(false);
            p.setGravity(true);
            p.setSleepingIgnored(false);
        }
        releaseLayDownByUuid(id);
    }

    /** Per-tick task that re-broadcasts the NPC packet bundle so players who
     *  enter render range mid-laydown receive the spawn packets. Idempotent
     *  per viewer — duplicate add packets are absorbed by the client.
     *
     *  <p>Started once at plugin enable time. */
    private org.bukkit.scheduler.BukkitTask viewerTask;

    public void startViewerTask(Plugin plugin) {
        if (viewerTask != null) return;
        // Per-tick: GSit pattern re-broadcasts equipment-hide every tick so
        // Paper's auto-equipment-resync doesn't bring the paci/helmet back.
        // perTick() also calls refreshViewers() internally for new in-range
        // players. The sentViewers set inside the NPC dedupes spawn bundles.
        viewerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (LaydownPoseNpc npc : activeNpcs.values()) {
                npc.perTick();
            }
        }, 1L, 1L);
    }

    public void stopViewerTask() {
        if (viewerTask != null) {
            viewerTask.cancel();
            viewerTask = null;
        }
    }

    /** Plugin disable: release every contained ward. */
    public void releaseAllOnDisable() {
        stopViewerTask();
        for (UUID ward : registry.occupiedWards()) releaseLayDownByUuid(ward);
    }

    /** Break path. The two Interaction entities cover the barrier cells, so a
     *  left-click raycast hits an Interaction (firing an attack event) before
     *  it can reach the barrier block — {@link #onLeftClickBarrier} never sees
     *  the click. We treat attacking a tagged Interaction as the break request,
     *  mirroring the protection-veto + teardown of the block-break path. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackInteraction(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!isOn()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains(ChangingTablePdcKeys.SCOREBOARD_TAG)) return;

        ChangingTable table = lookupOrRebuild(interaction);
        if (table == null) { event.setCancelled(true); return; }

        org.bukkit.World w = Bukkit.getWorld(table.worldName());
        if (w == null) { event.setCancelled(true); return; }
        Block anchorBarrier = w.getBlockAt(table.anchorX(), table.anchorY() + 1, table.anchorZ());
        Block footBarrier   = w.getBlockAt(table.footX(),   table.footY()   + 1, table.footZ());

        // Protection veto via synthetic BlockBreakEvent per cell.
        for (Block target : List.of(anchorBarrier, footBarrier)) {
            org.bukkit.event.block.BlockBreakEvent fake = new org.bukkit.event.block.BlockBreakEvent(target, player);
            Bukkit.getPluginManager().callEvent(fake);
            if (fake.isCancelled()) {
                event.setCancelled(true);
                return;   // silent
            }
        }

        teardown(table);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeftClickBarrier(PlayerInteractEvent event) {
        if (!isOn()) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BARRIER) return;

        ChangingTable table = registry.findByBarrierBlock(
            clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (table == null) return;

        Player player = event.getPlayer();
        org.bukkit.World w = clicked.getWorld();
        Block anchorBarrier = w.getBlockAt(table.anchorX(), table.anchorY() + 1, table.anchorZ());
        Block footBarrier   = w.getBlockAt(table.footX(),   table.footY() + 1,   table.footZ());

        // Protection veto via synthetic BlockBreakEvent per cell.
        for (Block target : List.of(anchorBarrier, footBarrier)) {
            org.bukkit.event.block.BlockBreakEvent fake = new org.bukkit.event.block.BlockBreakEvent(target, player);
            Bukkit.getPluginManager().callEvent(fake);
            if (fake.isCancelled()) {
                event.setCancelled(true);
                return;     // silent
            }
        }

        teardown(table);
        event.setCancelled(true);
    }

    /** Common teardown — invoked by left-click, BlockBreakEvent, and explosions.
     *  Idempotent: if the table is already unregistered, returns immediately so
     *  the synthetic BlockBreakEvent + MONITOR-priority onBarrierBreak chain
     *  doesn't double-tear-down (and double-drop the item). */
    private void teardown(ChangingTable table) {
        if (registry.findById(table.id()) == null) return;
        // Release any ward currently on the table.
        for (UUID ward : registry.occupiedWards()) {
            UUID tid = registry.tableIdForWard(ward);
            if (tid != null && tid.equals(table.id())) {
                registry.releaseWard(ward);
            }
        }
        // Drop inventory contents + delete YAML.
        if (plugin.getChangingTableInventoryManager() != null) {
            plugin.getChangingTableInventoryManager().dropAndDelete(table);
        }
        // Despawn paired entities — the ItemDisplay plus BOTH Interaction
        // entities (one per barrier cell).
        org.bukkit.entity.Entity disp = Bukkit.getEntity(table.displayUuid());
        if (disp != null) disp.remove();
        for (Interaction ix : findInteractions(table)) ix.remove();
        // Clear barriers + drop the table item.
        org.bukkit.World w = Bukkit.getWorld(table.worldName());
        if (w != null) {
            w.getBlockAt(table.anchorX(), table.anchorY() + 1, table.anchorZ()).setType(Material.AIR);
            w.getBlockAt(table.footX(),   table.footY() + 1,   table.footZ()).setType(Material.AIR);
            Location dropLoc = new Location(w, table.anchorX() + 0.5, table.anchorY() + 1.0, table.anchorZ() + 0.5);
            w.dropItemNaturally(dropLoc, ChangingTableItem.make(table.colorIndex()));
        }
        registry.unregister(table.id());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBarrierBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!isOn()) return;
        Block b = event.getBlock();
        if (b.getType() != Material.BARRIER) return;
        ChangingTable t = registry.findByBarrierBlock(
            b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (t != null) teardown(t);
    }

    @EventHandler
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (!isOn()) return;
        for (Block b : event.blockList()) tearDownIfBarrier(b);
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (!isOn()) return;
        for (Block b : event.blockList()) tearDownIfBarrier(b);
    }

    private void tearDownIfBarrier(Block b) {
        if (b == null || b.getType() != Material.BARRIER) return;
        ChangingTable t = registry.findByBarrierBlock(
            b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (t != null) teardown(t);
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!isOn()) return;
        for (org.bukkit.entity.Entity e : event.getChunk().getEntities()) {
            if (!(e instanceof Interaction interaction)) continue;
            if (!interaction.getScoreboardTags().contains(ChangingTablePdcKeys.SCOREBOARD_TAG)) continue;
            ChangingTable t = ChangingTable.fromPdc(interaction, keys);
            if (t == null) {
                plugin.getLogger().warning("Orphan changing-table Interaction at "
                    + interaction.getLocation() + "; removing.");
                interaction.remove();
                continue;
            }
            // Verify display exists.
            if (Bukkit.getEntity(t.displayUuid()) == null) {
                plugin.getLogger().warning("Changing table " + t.id() + " has missing display; removing interaction.");
                interaction.remove();
                continue;
            }
            // Verify both barriers exist.
            org.bukkit.World w = Bukkit.getWorld(t.worldName());
            if (w == null
                || w.getBlockAt(t.anchorX(), t.anchorY() + 1, t.anchorZ()).getType() != Material.BARRIER
                || w.getBlockAt(t.footX(),   t.footY() + 1,   t.footZ()).getType() != Material.BARRIER) {
                plugin.getLogger().warning("Changing table " + t.id() + " has missing barrier; removing entities.");
                org.bukkit.entity.Entity disp = Bukkit.getEntity(t.displayUuid());
                if (disp != null) disp.remove();
                interaction.remove();
                continue;
            }
            registry.register(t);
        }
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        if (!isOn()) return;
        // Defensive: release any ward whose seat ArmorStand is in the unloading
        // chunk. Chunk-tickets should normally keep this chunk loaded while a
        // player is inside, but we cover the edge case to avoid stale occupancy
        // entries pointing at despawned ArmorStands.
        String worldName = event.getWorld().getName();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        for (UUID wardId : registry.occupiedWards()) {
            UUID asUuid = registry.armorStandForWard(wardId);
            if (asUuid == null) continue;
            org.bukkit.entity.Entity asEntity = Bukkit.getEntity(asUuid);
            if (asEntity == null) continue;
            org.bukkit.Location loc = asEntity.getLocation();
            if (!worldName.equals(loc.getWorld().getName())) continue;
            if ((loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                registry.releaseWard(wardId);
            }
        }
        registry.unregisterChunk(worldName, cx, cz);
    }
}
