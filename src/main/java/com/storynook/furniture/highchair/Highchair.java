package com.storynook.furniture.highchair;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Immutable record of one placed highchair in the world.
 *
 * <p>Source of truth is the PDC on the paired {@link Interaction} entity
 * (vanilla-persisted). This record is the in-memory view; see
 * {@code HighchairRegistry} for the index and {@code HighchairListener}
 * for placement.
 *
 * <p>The highchair occupies one cell. The visual {@code ItemDisplay}
 * extends upward to roughly two blocks tall (chair back); the BARRIER
 * lives only in the origin cell so a player can stand on top by jumping.
 */
public record Highchair(
    UUID id,
    String worldName,
    int originX, int originY, int originZ,
    float yaw,
    int colorIndex,
    UUID displayUuid,
    UUID interactionUuid,
    UUID ownerUuid
) {

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    /** Centre of the seat cell (origin.x+0.5, origin.y, origin.z+0.5). */
    public Location originCenter() {
        World w = world();
        if (w == null) return null;
        return new Location(w, originX + 0.5, originY, originZ + 0.5);
    }

    /**
     * Reads a Highchair from the PDC of an Interaction entity.
     * Returns null if any required key is missing.
     */
    public static Highchair fromPdc(Interaction interaction, HighchairPdcKeys keys) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        if (!pdc.has(keys.id, PersistentDataType.STRING)) return null;
        try {
            UUID id = UUID.fromString(pdc.get(keys.id, PersistentDataType.STRING));
            int colorIndex = pdc.getOrDefault(keys.colorIndex, PersistentDataType.INTEGER, 0);
            float yaw = pdc.getOrDefault(keys.yaw, PersistentDataType.FLOAT, 0.0f);
            UUID displayUuid = UUID.fromString(pdc.get(keys.displayUuid, PersistentDataType.STRING));
            UUID ownerUuid = UUID.fromString(pdc.get(keys.ownerUuid, PersistentDataType.STRING));
            int originX = pdc.get(keys.originX, PersistentDataType.INTEGER);
            int originY = pdc.get(keys.originY, PersistentDataType.INTEGER);
            int originZ = pdc.get(keys.originZ, PersistentDataType.INTEGER);
            Location loc = interaction.getLocation();
            String worldName = loc.getWorld() == null ? "" : loc.getWorld().getName();
            return new Highchair(
                id, worldName,
                originX, originY, originZ,
                yaw, colorIndex,
                displayUuid, interaction.getUniqueId(), ownerUuid
            );
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    /** Writes this highchair's data to the given Interaction entity's PDC and adds the scoreboard tag. */
    public void writeToPdc(Interaction interaction, HighchairPdcKeys keys) {
        PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        pdc.set(keys.id,          PersistentDataType.STRING,  id.toString());
        pdc.set(keys.colorIndex,  PersistentDataType.INTEGER, colorIndex);
        pdc.set(keys.yaw,         PersistentDataType.FLOAT,   yaw);
        pdc.set(keys.displayUuid, PersistentDataType.STRING,  displayUuid.toString());
        pdc.set(keys.ownerUuid,   PersistentDataType.STRING,  ownerUuid.toString());
        pdc.set(keys.originX,     PersistentDataType.INTEGER, originX);
        pdc.set(keys.originY,     PersistentDataType.INTEGER, originY);
        pdc.set(keys.originZ,     PersistentDataType.INTEGER, originZ);
        interaction.addScoreboardTag(HighchairPdcKeys.SCOREBOARD_TAG);
    }
}
