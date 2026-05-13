package com.storynook.furniture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of all currently-loaded (new-system) cribs, plus the
 * reverse map of "which crib is each ward contained in." Source of truth
 * for crib data lives on the Interaction entity's PDC; this registry is
 * a session-only cache rebuilt from chunk-load events.
 */
public class CribRegistry {

    private final Map<UUID, Crib> byId = new ConcurrentHashMap<>();
    private final Map<String, Crib> byFloorBlockKey = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byChunkKey = new ConcurrentHashMap<>();

    private final Map<UUID, UUID> wardToCrib = new ConcurrentHashMap<>();

    public void register(Crib c) {
        Objects.requireNonNull(c, "crib");
        byId.put(c.id(), c);
        byFloorBlockKey.put(floorKey(c.worldName(), c.floorBlockX(), c.floorBlockY(), c.floorBlockZ()), c);
        byChunkKey
            .computeIfAbsent(chunkKey(c.worldName(), c.originX() >> 4, c.originZ() >> 4),
                             k -> ConcurrentHashMap.newKeySet())
            .add(c.id());
    }

    public void unregister(UUID cribId) {
        Crib c = byId.remove(cribId);
        if (c == null) return;
        byFloorBlockKey.remove(floorKey(c.worldName(), c.floorBlockX(), c.floorBlockY(), c.floorBlockZ()));
        Set<UUID> chunkSet = byChunkKey.get(chunkKey(c.worldName(), c.originX() >> 4, c.originZ() >> 4));
        if (chunkSet != null) {
            chunkSet.remove(cribId);
            if (chunkSet.isEmpty()) {
                byChunkKey.remove(chunkKey(c.worldName(), c.originX() >> 4, c.originZ() >> 4));
            }
        }
        wardToCrib.entrySet().removeIf(e -> e.getValue().equals(cribId));
    }

    public Crib findById(UUID cribId) {
        return byId.get(cribId);
    }

    public Crib findByFloorBlock(String worldName, int x, int y, int z) {
        return byFloorBlockKey.get(floorKey(worldName, x, y, z));
    }

    public Collection<Crib> findByChunk(String worldName, int chunkX, int chunkZ) {
        Set<UUID> ids = byChunkKey.get(chunkKey(worldName, chunkX, chunkZ));
        if (ids == null) return Collections.emptyList();
        List<Crib> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Crib c = byId.get(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    public void unregisterChunk(String worldName, int chunkX, int chunkZ) {
        Set<UUID> ids = byChunkKey.remove(chunkKey(worldName, chunkX, chunkZ));
        if (ids == null) return;
        for (UUID id : ids) {
            Crib c = byId.remove(id);
            if (c != null) {
                byFloorBlockKey.remove(floorKey(c.worldName(), c.floorBlockX(), c.floorBlockY(), c.floorBlockZ()));
            }
        }
    }

    public void containWard(UUID wardUuid, UUID cribId) {
        wardToCrib.put(wardUuid, cribId);
    }

    public void releaseWard(UUID wardUuid) {
        wardToCrib.remove(wardUuid);
    }

    public UUID cribIdForWard(UUID wardUuid) {
        return wardToCrib.get(wardUuid);
    }

    public Set<UUID> containedWards() {
        return new HashSet<>(wardToCrib.keySet());
    }

    public Map<UUID, UUID> containmentSnapshot() {
        return new HashMap<>(wardToCrib);
    }

    public int size() {
        return byId.size();
    }

    /**
     * Finds the nearest registered new-system crib to the given coordinates,
     * within {@code radius} blocks. Does NOT search legacy armor-stand cribs;
     * see {@code CribListener.findNearestCrib} (added later) for the unified
     * version that also scans nearby entities for the legacy path.
     */
    public CribLookupResult findNearestNewCrib(String worldName, double x, double y, double z, double radius) {
        Crib best = null;
        double bestDistSq = radius * radius;
        for (Crib c : byId.values()) {
            if (!c.worldName().equals(worldName)) continue;
            double dx = c.originX() + 0.5 - x;
            double dy = c.originY() + 0.5 - y;
            double dz = c.originZ() + 0.5 - z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = c;
            }
        }
        return best == null ? CribLookupResult.None.INSTANCE : new CribLookupResult.NewCribResult(best);
    }

    /**
     * Unified lookup: search new-system cribs first, then fall back to
     * scanning nearby invisible armor stands named "Crib" for the legacy
     * path. Used by NannyCareEngine + CarryDropListener to handle both
     * crib generations.
     */
    public CribLookupResult findNearestCrib(org.bukkit.Location origin, double radius) {
        if (origin == null || origin.getWorld() == null) return CribLookupResult.None.INSTANCE;
        CribLookupResult newResult = findNearestNewCrib(
            origin.getWorld().getName(), origin.getX(), origin.getY(), origin.getZ(), radius);
        if (newResult instanceof CribLookupResult.NewCribResult) return newResult;
        org.bukkit.entity.ArmorStand bestLegacy = null;
        double bestDistSq = radius * radius;
        for (org.bukkit.entity.Entity e : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!(e instanceof org.bukkit.entity.ArmorStand as)) continue;
            if (as.getCustomName() == null || !"Crib".equals(as.getCustomName())) continue;
            double d2 = as.getLocation().distanceSquared(origin);
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestLegacy = as;
            }
        }
        if (bestLegacy != null) return new CribLookupResult.LegacyCribResult(bestLegacy);
        return CribLookupResult.None.INSTANCE;
    }

    private static String floorKey(String worldName, int x, int y, int z) {
        return worldName + ':' + x + ':' + y + ':' + z;
    }

    private static String chunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ':' + chunkX + ':' + chunkZ;
    }
}
