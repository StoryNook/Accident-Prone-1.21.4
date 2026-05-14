package com.storynook.furniture.changingtable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-based GSit-style lay-down renderer for changing tables. Spawns
 * a packet-only fake {@code ServerPlayer} clone of the real player in
 * {@code Pose.SLEEPING}, and sends a client-only fake-bed block update so the
 * client's sleeping pose has an orientation anchor. The real player is hidden
 * via {@code setInvisible(true)} and stays at the mat surface for collision /
 * caregiver-distance checks.
 *
 * <p>All NMS access is via reflection so the plugin still builds against the
 * Bukkit API (no NMS jar) and remains tolerant of minor Paper revisions that
 * keep the same Mojang-mapped names. Falls back silently — if any reflection
 * step fails, the helper is a no-op for the rest of the JVM's lifetime and
 * logs once.
 *
 * <p>One instance per ward. {@link #spawn} must be called exactly once;
 * {@link #despawn} must be called exactly once and only after a successful
 * spawn. {@link #refreshViewers} is safe to call repeatedly.
 */
public final class LaydownPoseNpc {

    private static final Logger LOG = Bukkit.getLogger();
    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    // CraftBukkit handle access
    private static Method craftPlayer_getHandle;
    private static Method craftWorld_getHandle;
    private static Method craftServer_getServer;

    // NMS types we instantiate
    private static Class<?> serverPlayerCls;
    private static Constructor<?> serverPlayer_ctor;
    private static Constructor<?> gameProfile_ctor;
    private static Method clientInfo_createDefault;

    // BlockPos
    private static Constructor<?> blockPos_ctor;

    // Pose enum
    private static Object nmsPoseSleeping;

    // Entity / data
    private static Method nmsEntity_getId;
    private static Method nmsEntity_getEntityData;
    private static Method nmsEntity_setPos;
    private static Method nmsEntity_moveTo;
    private static Method nmsEntity_getUUID;
    private static Method nmsEntity_getX;
    private static Method nmsEntity_getY;
    private static Method nmsEntity_getZ;
    private static Method nmsEntity_getXRot;
    private static Method nmsEntity_getYRot;
    private static Method nmsEntity_getYHeadRot;
    private static Method nmsEntity_getType;
    private static Method nmsEntity_getDeltaMovement;
    private static Method nmsEntity_setInvisible;

    // SynchedEntityData
    private static Method synched_set;
    private static Method synched_get;
    private static Method synched_packDirty;
    private static Method synched_getNonDefaultValues;

    // EntityDataAccessor / serializers
    private static Object poseAccessor;          // index 6, EntityDataSerializers.POSE
    private static Object sleepingPosAccessor;   // index 14, EntityDataSerializers.OPTIONAL_BLOCK_POS

    // Packets
    private static Constructor<?> packetSetEntityData_ctor;
    private static Constructor<?> packetAddEntity_ctor;
    private static Constructor<?> packetRemoveEntities_ctor;
    private static Class<?> packetPlayerInfoUpdate_cls;
    private static Method packetPlayerInfoUpdate_static_create;   // unused after fix; kept for back-compat
    private static Constructor<?> packetPlayerInfoUpdate_entries_ctor;  // (EnumSet<Action>, Entry) — single-entry public ctor
    private static Constructor<?> playerInfoEntry_ctor;            // 9-arg Entry record ctor
    private static Object playerInfoActions;                       // EnumSet<Action> we send
    private static Object gameTypeSurvival;
    private static Constructor<?> packetPlayerInfoRemove_ctor;
    private static Constructor<?> packetBlockUpdate_ctor;
    private static Method nmsBlocks_WHITE_BED_defaultState;
    private static Method nmsBlockState_setValue;
    private static Object bedBlock_FACING_property;
    private static Object bedBlock_PART_property;
    private static Object bedPart_HEAD;

    // Post-spawn teleport — forces the client to render the SLEEPING body at
    // the entity's real position, not at sleepingPos's y.
    private static Constructor<?> packetTeleportEntity_ctor;
    private static Method positionMoveRotation_of;
    private static Object emptyRelativeSet;

    // Equipment hide
    private static Constructor<?> packetSetEquipment_ctor;
    private static Class<?> equipSlotCls;
    private static Object[] equipSlots;     // all 6 EquipmentSlot enum values
    private static Object itemStack_EMPTY;
    private static Method nmsEntity_getItemBySlot;
    private static Method pair_of;

    // Connection
    private static Field serverPlayer_connection;
    private static Method connection_send;

    // Nameplate-hiding team packets — direct NMS so we don't depend on Bukkit
    // scoreboard auto-sync timing (which is unreliable for mid-game team
    // membership changes sent to already-online viewers).
    private static Class<?> playerTeamCls;
    private static Constructor<?> playerTeam_ctor;
    private static Method playerTeam_setNameTagVisibility;
    private static Method playerTeam_setCollisionRule;
    private static Method playerTeam_getPlayers;
    private static Object teamVisibility_NEVER;
    private static Object teamCollision_NEVER;
    private static Object nmsScoreboard;
    private static Method packetSetPlayerTeam_createAddOrModify;
    private static Method packetSetPlayerTeam_createPlayerPacket;
    private static Class<?> setPlayerTeam_action_cls;
    private static Object teamAction_ADD;
    private static Object teamAction_REMOVE;
    private static Object hiddenTeamNmsHandle;       // shared, created once
    private static volatile boolean hiddenTeamReady;

    // -- Instance state --
    private final org.bukkit.plugin.Plugin plugin;
    private final Player template;
    private final Location seatLoc;
    private final BlockFace bedFacing;
    private Object npcHandle;
    private int npcId;
    private UUID npcUuid;
    private Object fakeBedPos;
    private Object npcProfile;
    private boolean spawned;
    /** Viewers who have already received the spawn bundle. Per-tick refresh
     *  only sends packets to NEW entries, so we don't re-add the entity every
     *  second (which would flicker the NPC). */
    private final java.util.Set<UUID> sentViewers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean isSpawned() { return spawned; }
    public boolean isAvailable() { return available; }

    /** NPC profile name. Remembered so despawn can drop the entry from the
     *  shared hidden-nameplate team's NMS players set. */
    private String npcName;

    /** Vertical offset (blocks) added to the seat location for the NPC's render
     *  Y. Tuned in QA so the lying body rests on the mat surface. */
    private static final double LAY_Y_OFFSET = -0.05;

    /** Horizontal offset (blocks) applied along the bed-facing direction.
     *  Sign convention: dx = bedFacing.modX * value (bedFacing points toward
     *  the head end). bedFacing.modX is -1 for a WEST-facing bed, so a POSITIVE
     *  value moves the entity toward the head end, a NEGATIVE value toward the
     *  foot end. Tuned empirically against the 2-block mat: the body otherwise
     *  drifts ~1 block toward the foot end, so +0.75 pulls it back to centre. */
    private static final double LAY_XZ_OFFSET = 0.75;

    public LaydownPoseNpc(org.bukkit.plugin.Plugin plugin, Player template, Location seatLoc, BlockFace bedFacing) {
        this.plugin = plugin;
        this.template = template;
        this.seatLoc = seatLoc.clone();
        this.bedFacing = bedFacing;
    }

    /** Creates the fake ServerPlayer NPC, configures pose + sleeping-pos, and
     *  sends the create-bundle to every player currently within the template
     *  player's simulation distance. Idempotent: returns immediately if already
     *  spawned or if init failed. */
    public void spawn() {
        if (spawned) return;
        if (!initialized) init(template);
        if (!available) return;
        try {
            // Build profile that mirrors the template's name. We DO NOT copy the
            // template's UUID — same UUID + duplicate ClientboundPlayerInfoUpdate
            // = client disconnects with "duplicate login" on some Paper builds.
            npcUuid = UUID.randomUUID();
            // Use a randomized short name distinct from the template's name —
            // Paper sanitizes/conflicts when a ServerPlayer is constructed with
            // a profile whose name matches a currently-online player. The
            // visible identity comes from the gameProfile we overwrite below
            // and the skin properties copied from the template.
            npcName = "ld_" + npcUuid.toString().substring(0, 8);
            Object profile = gameProfile_ctor.newInstance(npcUuid, npcName);
            // Add the NPC name to the shared hidden-name NMS PlayerTeam. The
            // team-create + member-add packets are broadcast per-viewer in
            // refreshViewers() — Bukkit's main-scoreboard auto-sync proved
            // unreliable for mid-game membership updates.
            if (hiddenTeamReady) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<String> members =
                        (java.util.Collection<String>) playerTeam_getPlayers.invoke(hiddenTeamNmsHandle);
                    members.add(npcName);
                } catch (Throwable teamErr) {
                    LOG.log(Level.WARNING, "[LaydownPoseNpc] team players-set add failed", unwrap(teamErr));
                }
            }
            // Copy template's GameProfile properties (skin textures, cape).
            try {
                Object templateHandle = craftPlayer_getHandle.invoke(template);
                Object templateProfile = serverPlayerCls.getMethod("getGameProfile").invoke(templateHandle);
                Object templateProps = templateProfile.getClass().getMethod("getProperties").invoke(templateProfile);
                Object newProps = profile.getClass().getMethod("getProperties").invoke(profile);
                newProps.getClass().getMethod("putAll", com.google.common.collect.Multimap.class)
                    .invoke(newProps, templateProps);
            } catch (Throwable propErr) {
                LOG.log(Level.WARNING, "[LaydownPoseNpc] profile property copy failed (using blank profile)", unwrap(propErr));
            }
            npcProfile = profile;
            Object clientInfo = clientInfo_createDefault.invoke(null);
            Object server = craftServer_getServer.invoke(Bukkit.getServer());
            Object serverLevel = craftWorld_getHandle.invoke(seatLoc.getWorld());

            // FancyNpcs trick: construct with a blank-name profile to dodge Paper
            // sanitization, then overwrite the gameProfile field directly with
            // the full one we built above.
            Object blankProfile = gameProfile_ctor.newInstance(npcUuid, "");
            npcHandle = serverPlayer_ctor.newInstance(server, serverLevel, blankProfile, clientInfo);
            try {
                java.lang.reflect.Field gameProfileField = serverPlayerCls.getField("gameProfile");
                gameProfileField.set(npcHandle, profile);
            } catch (Throwable gpErr) {
                LOG.log(Level.WARNING, "[LaydownPoseNpc] gameProfile overwrite failed", unwrap(gpErr));
            }
            npcId = (Integer) nmsEntity_getId.invoke(npcHandle);

            // Position at the seat with offsets:
            //   Y += LAY_Y_OFFSET so the body sits on top of the mat block
            //   XZ shifted along bed-facing direction by LAY_XZ_OFFSET so the
            //     sleeping-pose render translate lands the body centered on the
            //     2-block mat (it otherwise drifts toward the head end).
            int dxSign = bedFacing.getModX();
            int dzSign = bedFacing.getModZ();
            double dx = dxSign * LAY_XZ_OFFSET;
            double dz = dzSign * LAY_XZ_OFFSET;
            nmsEntity_moveTo.invoke(npcHandle,
                seatLoc.getX() + dx, seatLoc.getY() + LAY_Y_OFFSET, seatLoc.getZ() + dz,
                seatLoc.getYaw(), 0f);

            // SLEEPING pose: set Pose=SLEEPING + sleepingPos. The fake bed at
            // sleepingPos is what the client reads for orientation; without it,
            // the body renders standing. The post-spawn teleport packet below
            // overrides the sleepingPos.y so the body actually appears at the
            // NPC's real position rather than at world-minHeight.
            Object entityData = nmsEntity_getEntityData.invoke(npcHandle);
            synched_set.invoke(entityData, poseAccessor, nmsPoseSleeping);
            int bedX = seatLoc.getBlockX();
            int bedZ = seatLoc.getBlockZ();
            int bedY = seatLoc.getWorld().getMinHeight();
            fakeBedPos = blockPos_ctor.newInstance(bedX, bedY, bedZ);
            synched_set.invoke(entityData, sleepingPosAccessor, Optional.of(fakeBedPos));

            // Broadcast initial bundle to every player in range.
            refreshViewers();

            // Hide the real player's worn equipment for viewers — setInvisible
            // hides the body but not equipment, so without this packet helmets,
            // armor, mainhand items would float visibly.
            broadcastEquipmentVisibility(false);

            spawned = true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] spawn failed", unwrap(t));
            available = false;     // give up cleanly — calls become no-ops
        }
    }
    /** Sends the (re-)create bundle to every player within simulation-distance
     *  of the seat. Safe to call every tick. Sends:
     *    - ClientboundPlayerInfoUpdatePacket (adds the NPC to viewers' tab list)
     *    - ClientboundAddEntityPacket (spawns the entity)
     *    - ClientboundBlockUpdatePacket (client-only fake bed at minHeight)
     *    - ClientboundSetEntityDataPacket (initial pose + sleepingPos)
     *  Players already aware of the NPC ignore duplicate packets harmlessly. */
    public void refreshViewers() {
        if (!available || npcHandle == null) return;
        try {
            // Build PlayerInfoUpdate entry manually. The convenience factory
            // (`createPlayerInitializing`) and the (EnumSet, Collection<ServerPlayer>)
            // ctor both call `Entry::new(ServerPlayer)` which eagerly reads
            // `player.connection.latency()` — our packet-only NPC has no connection,
            // so that path NPEs. We bypass it by constructing the Entry record
            // directly with explicit latency=0, then use the package-private
            // (EnumSet, List<Entry>) ctor that doesn't read the ServerPlayer.
            // listOrder = -1 (FancyNpcs default), listed = false (don't pollute
            // tab list — pure-cosmetic NPC), showHat = true.
            Object infoEntry = playerInfoEntry_ctor.newInstance(
                npcUuid, npcProfile, false, 69,
                gameTypeSurvival, null, true, -1, null);
            Object infoPacket = packetPlayerInfoUpdate_entries_ctor.newInstance(
                playerInfoActions, infoEntry);

            Object addPacket = packetAddEntity_ctor.newInstance(
                npcId, npcUuid,
                nmsEntity_getX.invoke(npcHandle),
                nmsEntity_getY.invoke(npcHandle),
                nmsEntity_getZ.invoke(npcHandle),
                nmsEntity_getXRot.invoke(npcHandle),
                nmsEntity_getYRot.invoke(npcHandle),
                nmsEntity_getType.invoke(npcHandle),
                0,
                nmsEntity_getDeltaMovement.invoke(npcHandle),
                (double) (float) nmsEntity_getYHeadRot.invoke(npcHandle)
            );

            Object bedState = buildFakeBedState();
            Object blockUpdatePacket = packetBlockUpdate_ctor.newInstance(fakeBedPos, bedState);

            Object entityData = nmsEntity_getEntityData.invoke(npcHandle);
            @SuppressWarnings("unchecked")
            List<Object> nonDefaults = (List<Object>) synched_getNonDefaultValues.invoke(entityData);
            Object dataPacket = (nonDefaults == null || nonDefaults.isEmpty())
                ? null
                : packetSetEntityData_ctor.newInstance(npcId, nonDefaults);

            // Post-spawn teleport packet — the critical missing piece for
            // SLEEPING. Without this, the client uses sleepingPos.y (world min)
            // as the body render position. With this, the body renders at the
            // entity's actual location.
            Object posRot = positionMoveRotation_of.invoke(null, npcHandle);
            Object teleportPacket = packetTeleportEntity_ctor.newInstance(
                npcId, posRot, emptyRelativeSet, false);

            int range = template.getWorld().getSimulationDistance() * 16;
            int rangeSq = range * range;
            for (Player viewer : template.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(seatLoc) > rangeSq) continue;
                // Skip viewers who already received the spawn bundle — re-sending
                // ClientboundAddEntityPacket for an existing entity ID makes the
                // client tear it down and re-create it, producing visible flicker.
                if (!sentViewers.add(viewer.getUniqueId())) continue;
                // Create-or-modify the hidden-nameplate team on the client BEFORE
                // the entity is added — by the time the client renders the new
                // entity's first frame, it already knows the entity's profile
                // name belongs to a team with NameTagVisibility=NEVER and
                // skips the nameplate render entirely.
                if (hiddenTeamReady) {
                    try {
                        Object teamPacket = packetSetPlayerTeam_createAddOrModify
                            .invoke(null, hiddenTeamNmsHandle, true);
                        sendPacket(viewer, teamPacket);
                    } catch (Throwable ignored) {}
                }
                sendPacket(viewer, infoPacket);
                sendPacket(viewer, addPacket);
                sendPacket(viewer, blockUpdatePacket);
                if (dataPacket != null) sendPacket(viewer, dataPacket);
                sendPacket(viewer, teleportPacket);
                // GSit pattern: re-send teleport at tick+1 and tick+2 to
                // out-shout the client's LivingEntity.tick that re-snaps the
                // entity to sleepingPos.y after the bundle is applied. Without
                // this, the body briefly renders at world-min before the
                // entity-tick correction lands → visible clip-in/out.
                final Player vCapture = viewer;
                final Object tpCapture = teleportPacket;
                Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendPacket(vCapture, tpCapture), 1L);
                Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendPacket(vCapture, tpCapture), 2L);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] refreshViewers failed", unwrap(t));
        }
    }

    /** Per-tick maintenance: re-broadcast the empty-equipment packet so Paper's
     *  auto-equipment-resync doesn't make the helmet / paci / armor reappear
     *  on the (invisible) real player, and re-assert setInvisible(true) in
     *  case some other plugin's potion or interaction cleared it. Also calls
     *  refreshViewers() so newly-in-range players still get the spawn bundle. */
    public void perTick() {
        if (!available || npcHandle == null) return;
        refreshViewers();
        broadcastEquipmentVisibility(false);   // hide the real (invisible) player's gear
        broadcastNpcEquipment();               // mirror that gear onto the NPC clone
        try {
            Object templateHandle = craftPlayer_getHandle.invoke(template);
            nmsEntity_setInvisible.invoke(templateHandle, true);
        } catch (Throwable ignored) {}
    }

    private Object buildFakeBedState() throws Exception {
        Class<?> blocksCls = Class.forName("net.minecraft.world.level.block.Blocks");
        Object whiteBed = blocksCls.getField("WHITE_BED").get(null);
        Object state = nmsBlocks_WHITE_BED_defaultState.invoke(whiteBed);
        Class<?> dirCls = Class.forName("net.minecraft.core.Direction");
        Object dir;
        switch (bedFacing) {
            case NORTH: dir = dirCls.getField("NORTH").get(null); break;
            case SOUTH: dir = dirCls.getField("SOUTH").get(null); break;
            case EAST:  dir = dirCls.getField("EAST").get(null);  break;
            case WEST:  dir = dirCls.getField("WEST").get(null);  break;
            default:    dir = dirCls.getField("NORTH").get(null);
        }
        state = nmsBlockState_setValue.invoke(state, bedBlock_FACING_property, dir);
        state = nmsBlockState_setValue.invoke(state, bedBlock_PART_property, bedPart_HEAD);
        return state;
    }

    /** Sends ClientboundSetEquipmentPacket to all in-range viewers, targeting
     *  the REAL player's entity id. `visible=false` zeroes out every slot so
     *  helmet/chestplate/leggings/boots/mainhand/offhand stop rendering. */
    private void broadcastEquipmentVisibility(boolean visible) {
        try {
            Object templateHandle = craftPlayer_getHandle.invoke(template);
            int templateId = (Integer) nmsEntity_getId.invoke(templateHandle);

            java.util.List<Object> slots = new java.util.ArrayList<>();
            for (Object slot : equipSlots) {
                Object stack = visible
                    ? nmsEntity_getItemBySlot.invoke(templateHandle, slot)
                    : itemStack_EMPTY;
                slots.add(pair_of.invoke(null, slot, stack));
            }
            Object equipPacket = packetSetEquipment_ctor.newInstance(templateId, slots);

            int range = template.getWorld().getSimulationDistance() * 16;
            int rangeSq = range * range;
            for (Player viewer : template.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(seatLoc) > rangeSq) continue;
                sendPacket(viewer, equipPacket);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] broadcastEquipmentVisibility failed", unwrap(t));
        }
    }

    /** Sends ClientboundSetEquipmentPacket to all in-range viewers, targeting
     *  the NPC clone's entity id, mirroring the REAL player's live equipment
     *  (helmet/paci, chestplate, leggings, boots, main-hand, off-hand). Called
     *  every tick from {@link #perTick()}, so equipment changes mid-laydown —
     *  e.g. a caregiver removing the ward's leggings — propagate to the clone
     *  within a tick. Empty slots send ItemStack.EMPTY, so removed gear clears. */
    private void broadcastNpcEquipment() {
        try {
            Object templateHandle = craftPlayer_getHandle.invoke(template);

            java.util.List<Object> slots = new java.util.ArrayList<>();
            for (Object slot : equipSlots) {
                Object stack = nmsEntity_getItemBySlot.invoke(templateHandle, slot);
                slots.add(pair_of.invoke(null, slot, stack));
            }
            Object equipPacket = packetSetEquipment_ctor.newInstance(npcId, slots);

            int range = template.getWorld().getSimulationDistance() * 16;
            int rangeSq = range * range;
            for (Player viewer : template.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(seatLoc) > rangeSq) continue;
                sendPacket(viewer, equipPacket);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] broadcastNpcEquipment failed", unwrap(t));
        }
    }

    private static void sendPacket(Player viewer, Object packet) {
        try {
            Object viewerHandle = craftPlayer_getHandle.invoke(viewer);
            Object conn = serverPlayer_connection.get(viewerHandle);
            connection_send.invoke(conn, packet);
        } catch (Throwable ignored) {}
    }

    /** Unwrap reflection wrappers so the log surfaces the real cause + stack. */
    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur instanceof java.lang.reflect.InvocationTargetException
                && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    /** Sends the despawn bundle to every player in range AND restores the real
     *  block at the fake-bed position (sendBlockChange). Idempotent. */
    public void despawn() {
        if (!spawned || !available || npcHandle == null) return;
        try {
            Object removeEntityPacket = packetRemoveEntities_ctor.newInstance(new int[] { npcId });
            Object removeInfoPacket   = packetPlayerInfoRemove_ctor.newInstance(
                Collections.singletonList(npcUuid));

            int range = template.getWorld().getSimulationDistance() * 16;
            int rangeSq = range * range;
            org.bukkit.Location bedBukkit = new org.bukkit.Location(
                seatLoc.getWorld(), seatLoc.getBlockX(),
                seatLoc.getWorld().getMinHeight(), seatLoc.getBlockZ());
            org.bukkit.block.data.BlockData realBlock = bedBukkit.getBlock().getBlockData();

            // Only send despawn to viewers who actually received the spawn.
            for (Player viewer : template.getWorld().getPlayers()) {
                if (!sentViewers.contains(viewer.getUniqueId())) continue;
                sendPacket(viewer, removeEntityPacket);
                sendPacket(viewer, removeInfoPacket);
                viewer.sendBlockChange(bedBukkit, realBlock);
            }
            sentViewers.clear();
            // Restore real-player equipment visibility.
            broadcastEquipmentVisibility(true);
            // Drop the NPC's name from the shared NMS team's members set so it
            // doesn't grow unbounded across sessions. We don't need a per-viewer
            // team-remove packet — the entity itself is being despawned in the
            // same bundle, so the client immediately drops the team-membership
            // entry for the gone profile.
            if (hiddenTeamReady && npcName != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<String> members =
                        (java.util.Collection<String>) playerTeam_getPlayers.invoke(hiddenTeamNmsHandle);
                    members.remove(npcName);
                } catch (Throwable ignored) {}
            }
            spawned = false;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] despawn failed", unwrap(t));
        }
    }

    private static synchronized void init(Player exemplar) {
        if (initialized) return;
        initialized = true;
        try {
            craftPlayer_getHandle = exemplar.getClass().getMethod("getHandle");
            Class<?> craftServerCls = Bukkit.getServer().getClass();
            craftServer_getServer = craftServerCls.getMethod("getServer");
            Class<?> craftWorldCls = exemplar.getWorld().getClass();
            craftWorld_getHandle = craftWorldCls.getMethod("getHandle");

            serverPlayerCls = craftPlayer_getHandle.getReturnType();

            // GameProfile (com.mojang.authlib.GameProfile)
            Class<?> gameProfileCls = Class.forName("com.mojang.authlib.GameProfile");
            gameProfile_ctor = gameProfileCls.getConstructor(UUID.class, String.class);

            // ClientInformation.createDefault()
            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInfo_createDefault = clientInfoCls.getMethod("createDefault");

            // ServerPlayer ctor signature
            Class<?> serverLevelCls = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> minecraftServerCls = Class.forName("net.minecraft.server.MinecraftServer");
            serverPlayer_ctor = serverPlayerCls.getConstructor(
                minecraftServerCls, serverLevelCls, gameProfileCls, clientInfoCls);

            // BlockPos
            Class<?> blockPosCls = Class.forName("net.minecraft.core.BlockPos");
            blockPos_ctor = blockPosCls.getConstructor(int.class, int.class, int.class);

            // Pose enum
            Class<?> poseCls = Class.forName("net.minecraft.world.entity.Pose");
            nmsPoseSleeping = poseCls.getField("SLEEPING").get(null);

            // Entity API
            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            nmsEntity_getId          = entityCls.getMethod("getId");
            nmsEntity_getUUID        = entityCls.getMethod("getUUID");
            nmsEntity_getEntityData  = entityCls.getMethod("getEntityData");
            nmsEntity_moveTo         = entityCls.getMethod("moveTo",
                double.class, double.class, double.class, float.class, float.class);
            nmsEntity_getX           = entityCls.getMethod("getX");
            nmsEntity_getY           = entityCls.getMethod("getY");
            nmsEntity_getZ           = entityCls.getMethod("getZ");
            nmsEntity_getXRot        = entityCls.getMethod("getXRot");
            nmsEntity_getYRot        = entityCls.getMethod("getYRot");
            nmsEntity_getYHeadRot    = entityCls.getMethod("getYHeadRot");
            nmsEntity_getType        = entityCls.getMethod("getType");
            nmsEntity_getDeltaMovement = entityCls.getMethod("getDeltaMovement");
            nmsEntity_setInvisible   = entityCls.getMethod("setInvisible", boolean.class);
            nmsEntity_setPos         = entityCls.getMethod("setPos",
                double.class, double.class, double.class);

            // SynchedEntityData
            Class<?> synchedCls = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            Class<?> accessorCls = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            synched_set = synchedCls.getMethod("set", accessorCls, Object.class);
            synched_get = synchedCls.getMethod("get", accessorCls);
            synched_packDirty = synchedCls.getMethod("packDirty");
            synched_getNonDefaultValues = synchedCls.getMethod("getNonDefaultValues");

            // EntityDataSerializers — create the two accessors we need.
            // EntityDataSerializer is an interface with a default `createAccessor(int)`
            // method. Look it up on the interface itself — getMethod() then resolves
            // for any implementing class at invoke time.
            Class<?> serializersCls = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
            Class<?> serializerInterface = Class.forName("net.minecraft.network.syncher.EntityDataSerializer");
            Object serializerPose = serializersCls.getField("POSE").get(null);
            Object serializerOptBlockPos = serializersCls.getField("OPTIONAL_BLOCK_POS").get(null);
            Method createAccessor = serializerInterface.getMethod("createAccessor", int.class);
            // The pose accessor is at index 6 (Entity.DATA_POSE_ID).
            poseAccessor        = createAccessor.invoke(serializerPose, 6);
            // The sleeping_pos accessor is at index 14 (LivingEntity.SLEEPING_POS_ID).
            sleepingPosAccessor = createAccessor.invoke(serializerOptBlockPos, 14);

            // Packets
            Class<?> packetSetEntityData = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            packetSetEntityData_ctor = packetSetEntityData.getConstructor(int.class, List.class);

            Class<?> packetAddEntity = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> entityTypeCls = Class.forName("net.minecraft.world.entity.EntityType");
            Class<?> vec3Cls = Class.forName("net.minecraft.world.phys.Vec3");
            packetAddEntity_ctor = packetAddEntity.getConstructor(
                int.class, UUID.class, double.class, double.class, double.class,
                float.class, float.class, entityTypeCls, int.class, vec3Cls, double.class);

            Class<?> packetRemoveEntities = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            packetRemoveEntities_ctor = packetRemoveEntities.getConstructor(int[].class);

            packetPlayerInfoUpdate_cls = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            for (Method m : packetPlayerInfoUpdate_cls.getMethods()) {
                if (m.getName().equals("createPlayerInitializing") && m.getParameterCount() == 1) {
                    packetPlayerInfoUpdate_static_create = m;
                    break;
                }
            }

            // Inner Entry record + package-private (EnumSet, List<Entry>) ctor.
            // Used in refreshViewers() to bypass the convenience factory that
            // eagerly reads `player.connection.latency()` on a connection-less NPC.
            Class<?> playerInfoEntryCls = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> gameTypeCls = Class.forName("net.minecraft.world.level.GameType");
            Class<?> componentCls = Class.forName("net.minecraft.network.chat.Component");
            Class<?> chatSessionDataCls = Class.forName(
                "net.minecraft.network.chat.RemoteChatSession$Data");
            // Entry record ctor (Paper 1.21.4): 9 args — note `boolean showHat`
            // sits between `displayName` and `listOrder`. javap confirms order.
            playerInfoEntry_ctor = playerInfoEntryCls.getDeclaredConstructor(
                UUID.class, gameProfileCls, boolean.class, int.class,
                gameTypeCls, componentCls, boolean.class, int.class, chatSessionDataCls);
            playerInfoEntry_ctor.setAccessible(true);
            gameTypeSurvival = gameTypeCls.getField("SURVIVAL").get(null);

            // Match FancyNpcs's minimal action set. UPDATE_DISPLAY_NAME with a
            // null Component is the suspected culprit for "client rejects the
            // entity"; dropping it. ADD_PLAYER registers the profile (including
            // skin textures); UPDATE_LISTED applies the listed=false so the
            // NPC doesn't pollute the tab list.
            Class<?> actionCls = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            @SuppressWarnings({"unchecked", "rawtypes"})
            EnumSet actions = EnumSet.of(
                Enum.valueOf((Class<Enum>) actionCls, "ADD_PLAYER"),
                Enum.valueOf((Class<Enum>) actionCls, "UPDATE_LISTED"));
            playerInfoActions = actions;

            // Use the (EnumSet, Entry) single-entry public ctor — bypasses the
            // (EnumSet, Collection<ServerPlayer>) ctor that NPEs on no-connection NPCs.
            packetPlayerInfoUpdate_entries_ctor = packetPlayerInfoUpdate_cls.getConstructor(
                EnumSet.class, playerInfoEntryCls);

            Class<?> packetInfoRemove = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            packetPlayerInfoRemove_ctor = packetInfoRemove.getConstructor(List.class);

            Class<?> packetBlockUpdate = Class.forName("net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket");
            Class<?> blockStateCls = Class.forName("net.minecraft.world.level.block.state.BlockState");
            packetBlockUpdate_ctor = packetBlockUpdate.getConstructor(blockPosCls, blockStateCls);

            Class<?> blockCls = Class.forName("net.minecraft.world.level.block.Block");
            nmsBlocks_WHITE_BED_defaultState = blockCls.getMethod("defaultBlockState");
            Method setValueLookup = null;
            for (Method m : blockStateCls.getMethods()) {
                if (m.getName().equals("setValue") && m.getParameterCount() == 2) {
                    setValueLookup = m;
                    break;
                }
            }
            nmsBlockState_setValue = setValueLookup;
            Class<?> bedBlockCls = Class.forName("net.minecraft.world.level.block.BedBlock");
            bedBlock_FACING_property = bedBlockCls.getField("FACING").get(null);
            bedBlock_PART_property   = bedBlockCls.getField("PART").get(null);
            Class<?> bedPartCls = Class.forName("net.minecraft.world.level.block.state.properties.BedPart");
            bedPart_HEAD = bedPartCls.getField("HEAD").get(null);

            // ClientboundTeleportEntityPacket + PositionMoveRotation — sent
            // after SetEntityData so the SLEEPING-pose body renders at the
            // entity's actual Y, not at sleepingPos.y (which is world-min).
            Class<?> packetTeleportEntity = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            Class<?> positionMoveRotationCls = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            Class<?> relativeCls = Class.forName("net.minecraft.world.entity.Relative");
            packetTeleportEntity_ctor = packetTeleportEntity.getConstructor(
                int.class, positionMoveRotationCls, java.util.Set.class, boolean.class);
            positionMoveRotation_of = positionMoveRotationCls.getMethod("of", entityCls);
            emptyRelativeSet = java.util.Collections.emptySet();

            // ClientboundSetEquipmentPacket — used to hide / restore worn
            // armor on the real player for viewers during laydown.
            Class<?> packetSetEquipment = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            packetSetEquipment_ctor = packetSetEquipment.getConstructor(int.class, List.class);
            equipSlotCls = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            equipSlots = (Object[]) equipSlotCls.getEnumConstants();
            Class<?> itemStackCls = Class.forName("net.minecraft.world.item.ItemStack");
            itemStack_EMPTY = itemStackCls.getField("EMPTY").get(null);
            // getItemBySlot lives on LivingEntity, not Entity.
            Class<?> livingEntityCls = Class.forName("net.minecraft.world.entity.LivingEntity");
            nmsEntity_getItemBySlot = livingEntityCls.getMethod("getItemBySlot", equipSlotCls);
            Class<?> pairCls = Class.forName("com.mojang.datafixers.util.Pair");
            pair_of = pairCls.getMethod("of", Object.class, Object.class);

            // ServerPlayer.connection (public field) + connection.send
            serverPlayer_connection = serverPlayerCls.getField("connection");
            Class<?> packetCls = Class.forName("net.minecraft.network.protocol.Packet");
            connection_send = serverPlayer_connection.getType().getMethod("send", packetCls);

            // Nameplate-hide team: NMS PlayerTeam + ClientboundSetPlayerTeamPacket.
            // We use the server's main scoreboard as the team's scoreboard
            // (required by the PlayerTeam ctor) but otherwise treat the team as
            // a packet-only artifact — Bukkit's auto-sync of main-scoreboard
            // changes is unreliable for ad-hoc mid-game membership updates, so
            // we send the team-create + member-add packets ourselves per viewer.
            playerTeamCls = Class.forName("net.minecraft.world.scores.PlayerTeam");
            Class<?> scoreboardNmsCls = Class.forName("net.minecraft.world.scores.Scoreboard");
            playerTeam_ctor = playerTeamCls.getConstructor(scoreboardNmsCls, String.class);
            Class<?> teamVisibilityCls = Class.forName("net.minecraft.world.scores.Team$Visibility");
            Class<?> teamCollisionCls = Class.forName("net.minecraft.world.scores.Team$CollisionRule");
            teamVisibility_NEVER = teamVisibilityCls.getField("NEVER").get(null);
            teamCollision_NEVER  = teamCollisionCls.getField("NEVER").get(null);
            playerTeam_setNameTagVisibility = playerTeamCls.getMethod("setNameTagVisibility", teamVisibilityCls);
            playerTeam_setCollisionRule     = playerTeamCls.getMethod("setCollisionRule",   teamCollisionCls);
            playerTeam_getPlayers           = playerTeamCls.getMethod("getPlayers");
            // Obtain the server's main scoreboard handle once.
            Object craftScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Method getHandle = craftScoreboard.getClass().getMethod("getHandle");
            nmsScoreboard = getHandle.invoke(craftScoreboard);
            // The shared hidden-name team — one NMS PlayerTeam reused across
            // every laydown. We mutate its players() set per spawn/despawn.
            hiddenTeamNmsHandle = playerTeam_ctor.newInstance(nmsScoreboard, "ap_laydown_nameless");
            playerTeam_setNameTagVisibility.invoke(hiddenTeamNmsHandle, teamVisibility_NEVER);
            playerTeam_setCollisionRule.invoke(hiddenTeamNmsHandle, teamCollision_NEVER);
            Class<?> setPlayerTeamPacketCls = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
            packetSetPlayerTeam_createAddOrModify = setPlayerTeamPacketCls.getMethod(
                "createAddOrModifyPacket", playerTeamCls, boolean.class);
            packetSetPlayerTeam_createPlayerPacket = setPlayerTeamPacketCls.getMethod(
                "createPlayerPacket", playerTeamCls, String.class,
                Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Action"));
            setPlayerTeam_action_cls = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Action");
            teamAction_ADD    = Enum.valueOf((Class) setPlayerTeam_action_cls, "ADD");
            teamAction_REMOVE = Enum.valueOf((Class) setPlayerTeam_action_cls, "REMOVE");
            hiddenTeamReady = true;

            available = true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[LaydownPoseNpc] init failed — laydown disabled.", unwrap(t));
        }
    }
}
