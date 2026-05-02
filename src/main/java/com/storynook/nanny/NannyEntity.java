package com.storynook.nanny;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import com.storynook.Plugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;

/**
 * Wraps a Citizens2 NPC for a single Nanny instance.
 *
 * <p>All Citizens2 API calls are guarded by {@code plugin.citizensEnabled}.
 * When Citizens2 is absent every method silently no-ops so the plugin
 * never crashes on servers that do not have Citizens2 installed.
 */
public class NannyEntity {

    private final NannyData data;
    private final Plugin plugin;
    /** Null when Citizens2 is absent or the NPC has not been created yet. */
    private NPC npc;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Stores references. Does NOT spawn the NPC.
     *
     * @param data   persistent nanny data (provides name, skin, NPC id, etc.)
     * @param plugin plugin instance used for the citizensEnabled guard
     */
    public NannyEntity(NannyData data, Plugin plugin) {
        this.data = data;
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Spawns the Citizens2 NPC at the given location.
     *
     * <p>If an NPC id is already stored in {@code data}, the existing NPC is
     * looked up and re-spawned. Otherwise a new NPC is created, the skin is
     * applied, the NPC is spawned, and the new id is stored back into
     * {@code data}.
     *
     * @param location the world location at which to spawn the NPC
     * @return {@code true} on success; {@code false} when Citizens2 is absent
     *         or an exception is thrown
     */
    public boolean spawn(Location location) {
        if (!plugin.citizensEnabled) {
            return false;
        }
        try {
            // Re-use an existing NPC if we already have an id for one.
            if (data.getCitizensNpcId() != -1) {
                NPC existing = CitizensAPI.getNPCRegistry().getById(data.getCitizensNpcId());
                if (existing != null) {
                    npc = existing;
                    enableLookClose(npc);
                    if (!npc.isSpawned()) {
                        npc.spawn(location);
                    }
                    return true;
                }
            }

            // Create a brand-new NPC.
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, data.getName());

            // Apply skin if one has been set.
            if (data.getSkinUrl() != null && !data.getSkinUrl().isEmpty()) {
                npc.getOrAddTrait(SkinTrait.class).setSkinName(data.getSkinUrl());
            }

            enableLookClose(npc);
            npc.spawn(location);
            data.setCitizensNpcId(npc.getId());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(
                    "[NannyEntity] Failed to spawn NPC for nanny '"
                            + data.getName() + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Despawns the NPC with a PLUGIN reason and clears the local reference.
     * Silently no-ops when the NPC is absent or not currently spawned.
     */
    public void despawn() {
        if (npc != null && npc.isSpawned()) {
            npc.despawn(DespawnReason.PLUGIN);
            npc = null;
        }
    }

    /**
     * Enables or disables follow mode for the NPC.
     *
     * @param follow {@code true} to make the NPC follow {@code ward};
     *               {@code false} to cancel navigation
     * @param ward   the player to follow (ignored when {@code follow} is false)
     */
    public void setFollowMode(boolean follow, Player ward) {
        if (!plugin.citizensEnabled || npc == null || !npc.isSpawned()) {
            return;
        }
        if (follow && ward != null) {
            npc.getNavigator().setTarget(ward, false);
        } else if (!follow) {
            npc.getNavigator().cancelNavigation();
        }
    }

    /**
     * Updates the skin displayed by the NPC.
     *
     * @param skinName skin name (Mojang account name) or identifier to apply
     */
    public void updateSkin(String skinName) {
        if (!plugin.citizensEnabled || npc == null) {
            return;
        }
        npc.getOrAddTrait(SkinTrait.class).setSkinName(skinName);
    }

    /**
     * Updates the display name of the NPC.
     *
     * @param name new name to show above the NPC's head
     */
    public void updateName(String name) {
        if (!plugin.citizensEnabled || npc == null) {
            return;
        }
        npc.setName(name);
    }

    /**
     * Enables Citizens' built-in LookClose trait so the Nanny turns her head
     * toward nearby players. Range matches Citizens defaults.
     */
    private void enableLookClose(NPC npc) {
        try {
            LookClose lc = npc.getOrAddTrait(LookClose.class);
            lc.lookClose(true);
            lc.setRange(8);
        } catch (Throwable ignored) {
            // Trait API can vary across Citizens versions — best effort.
        }
    }

    /**
     * Snaps the NPC's head/body to face the given location. Used for polish
     * around care actions (e.g. face the ward you just changed).
     */
    public void faceLocation(Location target) {
        if (!plugin.citizensEnabled || npc == null || !npc.isSpawned() || target == null) return;
        try {
            npc.faceLocation(target);
        } catch (Throwable ignored) {
            // No-op if Citizens API doesn't expose faceLocation in this build.
        }
    }

    /**
     * Teleports the NPC to the given location.
     *
     * @param location destination location
     */
    public void teleportTo(Location location) {
        if (!plugin.citizensEnabled || npc == null || !npc.isSpawned()) {
            return;
        }
        npc.teleport(location, TeleportCause.PLUGIN);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the NPC exists and is currently spawned.
     */
    public boolean isSpawned() {
        return npc != null && npc.isSpawned();
    }

    /**
     * Returns the underlying Bukkit {@link org.bukkit.entity.Entity} for the
     * NPC, or {@code null} if the NPC does not exist or is not spawned.
     */
    public org.bukkit.entity.Entity getNpcEntity() {
        return (npc != null && npc.isSpawned()) ? npc.getEntity() : null;
    }

    /**
     * Returns the current world location of the spawned NPC, or {@code null}
     * if the NPC does not exist or is not spawned.
     */
    public Location getLocation() {
        if (npc == null || !npc.isSpawned()) return null;
        return npc.getEntity().getLocation();
    }

    /**
     * Returns the underlying Citizens2 {@link NPC}, or {@code null} if not yet
     * created. Used by {@link NannyNavigator} to access the Navigator API.
     */
    public NPC getNpc() {
        return npc;
    }

    /**
     * Returns the persistent data object for this nanny.
     */
    public NannyData getData() {
        return data;
    }
}
