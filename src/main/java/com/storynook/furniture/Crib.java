package com.storynook.furniture;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.util.BoundingBox;

/**
 * Immutable record of one placed (new-system) crib in the world.
 *
 * <p>Source of truth is the PDC on the paired {@link org.bukkit.entity.Interaction}
 * entity (vanilla-persisted). This record is the in-memory view; see
 * {@code CribRegistry} for the index and {@code CribListener} for placement.
 *
 * <p>The bed occupies two cells; only the head cell is stored. The foot
 * cell is derived from {@link #yaw()}.
 */
public record Crib(
    UUID id,
    String worldName,
    int originX, int originY, int originZ,
    float yaw,
    int woodVariant,
    int floorBlockX, int floorBlockY, int floorBlockZ,
    int bedHeadX, int bedHeadY, int bedHeadZ,
    UUID displayUuid,
    UUID interactionUuid,
    UUID ownerUuid
) {

    /** Foot cell X derived from {@link #bedHeadX()} and {@link #yaw()}. */
    public int bedFootX() {
        if (yaw == 90.0f)  return bedHeadX - 1;
        if (yaw == 270.0f) return bedHeadX + 1;
        return bedHeadX;
    }

    public int bedFootY() {
        return bedHeadY;
    }

    public int bedFootZ() {
        if (yaw == 0.0f)   return bedHeadZ + 1;
        if (yaw == 180.0f) return bedHeadZ - 1;
        return bedHeadZ;
    }

    /** Returns the world this crib lives in, or null if unloaded. */
    public World world() {
        return Bukkit.getWorld(worldName);
    }

    /** Bed head Bukkit Location with +0.5 centering, used for teleporting wards. */
    public Location bedHeadLocation() {
        World w = world();
        if (w == null) return null;
        return new Location(w, bedHeadX + 0.5, bedHeadY + 0.5, bedHeadZ + 0.5);
    }

    /**
     * True iff both the head and the derived foot cells contain a vanilla bed.
     * Returns false if either half is missing or the world is unloaded.
     */
    public boolean hasBed() {
        World w = world();
        if (w == null) return false;
        Block head = w.getBlockAt(bedHeadX, bedHeadY, bedHeadZ);
        Block foot = w.getBlockAt(bedFootX(), bedFootY(), bedFootZ());
        return head.getBlockData() instanceof Bed && foot.getBlockData() instanceof Bed;
    }

    /**
     * Bounding box covering both bed cells with a small horizontal expansion,
     * used by the soft-containment task to detect "ward outside the crib."
     * Roughly 1.4w × 2h × 1.9L oriented to the crib yaw.
     */
    public BoundingBox containmentBox() {
        double minX = Math.min(bedHeadX, bedFootX());
        double minY = bedHeadY;
        double minZ = Math.min(bedHeadZ, bedFootZ());
        double maxX = Math.max(bedHeadX, bedFootX()) + 1.0;
        double maxY = bedHeadY + 2.0;
        double maxZ = Math.max(bedHeadZ, bedFootZ()) + 1.0;
        return new BoundingBox(minX - 0.2, minY, minZ - 0.2, maxX + 0.2, maxY, maxZ + 0.2);
    }

    /**
     * Reads a Crib from the PDC of an Interaction entity.
     * Returns null if any required key is missing (corrupted save).
     */
    public static Crib fromPdc(org.bukkit.entity.Interaction interaction, CribPdcKeys keys) {
        org.bukkit.persistence.PersistentDataContainer pdc = interaction.getPersistentDataContainer();
        if (!pdc.has(keys.id, org.bukkit.persistence.PersistentDataType.STRING)) return null;
        try {
            UUID id = UUID.fromString(pdc.get(keys.id, org.bukkit.persistence.PersistentDataType.STRING));
            int variant = pdc.getOrDefault(keys.woodVariant, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            float yaw = pdc.getOrDefault(keys.yaw, org.bukkit.persistence.PersistentDataType.FLOAT, 0.0f);
            UUID displayUuid = UUID.fromString(pdc.get(keys.displayUuid, org.bukkit.persistence.PersistentDataType.STRING));
            UUID ownerUuid = UUID.fromString(pdc.get(keys.ownerUuid, org.bukkit.persistence.PersistentDataType.STRING));
            int floorX = pdc.get(keys.floorX, org.bukkit.persistence.PersistentDataType.INTEGER);
            int floorY = pdc.get(keys.floorY, org.bukkit.persistence.PersistentDataType.INTEGER);
            int floorZ = pdc.get(keys.floorZ, org.bukkit.persistence.PersistentDataType.INTEGER);
            int bedHeadX = pdc.get(keys.bedHeadX, org.bukkit.persistence.PersistentDataType.INTEGER);
            int bedHeadY = pdc.get(keys.bedHeadY, org.bukkit.persistence.PersistentDataType.INTEGER);
            int bedHeadZ = pdc.get(keys.bedHeadZ, org.bukkit.persistence.PersistentDataType.INTEGER);
            Location loc = interaction.getLocation();
            return new Crib(
                id,
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                yaw, variant,
                floorX, floorY, floorZ,
                bedHeadX, bedHeadY, bedHeadZ,
                displayUuid, interaction.getUniqueId(), ownerUuid
            );
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    /** Writes this crib's data to the given Interaction entity's PDC and adds the scoreboard tag. */
    public void writeToPdc(org.bukkit.entity.Interaction interaction, CribPdcKeys keys) {
        var pdc = interaction.getPersistentDataContainer();
        pdc.set(keys.id, org.bukkit.persistence.PersistentDataType.STRING, id.toString());
        pdc.set(keys.woodVariant, org.bukkit.persistence.PersistentDataType.INTEGER, woodVariant);
        pdc.set(keys.yaw, org.bukkit.persistence.PersistentDataType.FLOAT, yaw);
        pdc.set(keys.displayUuid, org.bukkit.persistence.PersistentDataType.STRING, displayUuid.toString());
        pdc.set(keys.ownerUuid, org.bukkit.persistence.PersistentDataType.STRING, ownerUuid.toString());
        pdc.set(keys.floorX, org.bukkit.persistence.PersistentDataType.INTEGER, floorBlockX);
        pdc.set(keys.floorY, org.bukkit.persistence.PersistentDataType.INTEGER, floorBlockY);
        pdc.set(keys.floorZ, org.bukkit.persistence.PersistentDataType.INTEGER, floorBlockZ);
        pdc.set(keys.bedHeadX, org.bukkit.persistence.PersistentDataType.INTEGER, bedHeadX);
        pdc.set(keys.bedHeadY, org.bukkit.persistence.PersistentDataType.INTEGER, bedHeadY);
        pdc.set(keys.bedHeadZ, org.bukkit.persistence.PersistentDataType.INTEGER, bedHeadZ);
        interaction.addScoreboardTag(CribPdcKeys.SCOREBOARD_TAG);
    }
}
