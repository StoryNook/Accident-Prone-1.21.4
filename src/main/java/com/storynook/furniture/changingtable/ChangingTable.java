package com.storynook.furniture.changingtable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * One placed changing table. Anchor is the cell the player clicked; the foot
 * cell is derived from {@link #yaw()} at lookup time via
 * {@link ChangingTablePlacementService#footOffset(float)} so there is only one
 * source of truth.
 *
 * <p>Owner UUID is optional (only set on player placement, not on debug
 * `/give` spawns).
 */
public record ChangingTable(
        UUID id,
        String worldName,
        int anchorX, int anchorY, int anchorZ,
        float yaw,
        int colorIndex,
        UUID displayUuid,
        UUID interactionUuid,
        UUID ownerUuid
) {

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public int[] footOffset() {
        return ChangingTablePlacementService.footOffset(yaw);
    }

    public int footX() { return anchorX + footOffset()[0]; }
    public int footZ() { return anchorZ + footOffset()[1]; }
    public int footY() { return anchorY; }

    /** Centre point at the visible mat surface. With the NPC-clone approach
     *  the body renders horizontally at the NPC's Y, so Y is tuned to sit
     *  the body just above the barrier tops with a hair of clearance. */
    public Location matCentre() {
        World w = world();
        if (w == null) return null;
        double cx = (anchorX + footX()) / 2.0 + 0.5;
        double cz = (anchorZ + footZ()) / 2.0 + 0.5;
        // Barriers occupy anchorY+1. Teleporting the ward to Y = anchorY+1
        // puts feet INSIDE the barrier block, and Minecraft shoves them out
        // sideways ("standing off to the side" symptom). Y = anchorY+2 places
        // feet on TOP of the barriers and the SLEEPING body horizontally on
        // the mat surface.
        return new Location(w, cx, anchorY + 2.0, cz);
    }

    public void writeToPdc(Interaction interaction, ChangingTablePdcKeys keys) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        pdc.set(keys.id,          PersistentDataType.STRING,  id.toString());
        pdc.set(keys.colorIndex,  PersistentDataType.INTEGER, colorIndex);
        pdc.set(keys.yaw,         PersistentDataType.FLOAT,   yaw);
        pdc.set(keys.anchorX,     PersistentDataType.INTEGER, anchorX);
        pdc.set(keys.anchorY,     PersistentDataType.INTEGER, anchorY);
        pdc.set(keys.anchorZ,     PersistentDataType.INTEGER, anchorZ);
        pdc.set(keys.displayUuid, PersistentDataType.STRING,  displayUuid.toString());
        if (ownerUuid != null) {
            pdc.set(keys.ownerUuid, PersistentDataType.STRING, ownerUuid.toString());
        }
    }

    public static ChangingTable fromPdc(Interaction interaction, ChangingTablePdcKeys keys) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        if (!pdc.has(keys.id, PersistentDataType.STRING)) return null;
        try {
            UUID id = UUID.fromString(pdc.get(keys.id, PersistentDataType.STRING));
            int colorIndex = pdc.getOrDefault(keys.colorIndex, PersistentDataType.INTEGER, 0);
            float yaw = pdc.getOrDefault(keys.yaw, PersistentDataType.FLOAT, 0f);
            int ax = pdc.getOrDefault(keys.anchorX, PersistentDataType.INTEGER, 0);
            int ay = pdc.getOrDefault(keys.anchorY, PersistentDataType.INTEGER, 0);
            int az = pdc.getOrDefault(keys.anchorZ, PersistentDataType.INTEGER, 0);
            String dispStr = pdc.get(keys.displayUuid, PersistentDataType.STRING);
            UUID disp = dispStr == null ? null : UUID.fromString(dispStr);
            String ownStr = pdc.get(keys.ownerUuid, PersistentDataType.STRING);
            UUID own = ownStr == null ? null : UUID.fromString(ownStr);
            return new ChangingTable(
                id, interaction.getWorld().getName(),
                ax, ay, az, yaw, colorIndex,
                disp, interaction.getUniqueId(), own
            );
        } catch (Exception e) {
            return null;
        }
    }
}
