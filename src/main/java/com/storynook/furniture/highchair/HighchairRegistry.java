package com.storynook.furniture.highchair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static colour registry + (later) runtime instance registry for highchairs.
 *
 * <p>The colour side mirrors {@code DesignRegistry}'s philosophy: one
 * {@link #register(String, String)} call per colour in {@link #init()},
 * which assigns a sequential colorIndex starting at 0. CMD = 630000 +
 * colorIndex.
 *
 * <p>The runtime instance side (chunk-indexed Maps + occupancy maps) is
 * created on instantiation; one registry instance is created in
 * {@code Plugin.onEnable} and held for the server session.
 */
public final class HighchairRegistry {

    private static final List<String> COLOR_KEYS = new ArrayList<>();
    private static final List<String> DISPLAY_KEYS = new ArrayList<>();
    private static final Map<String, Integer> COLOR_KEY_TO_INDEX = new HashMap<>();

    public static final int CMD_BASE = 630000;
    public static final int CMD_MAX  = 630099;

    /**
     * Populates the static colour registry. Called once from
     * {@code Plugin.onEnable} before any highchair listener / item factory runs.
     * Adding a new colour: drop a model JSON into the resource pack, append a
     * {@code range_dispatch} entry to {@code slime_ball.json}, then add one
     * {@code register(...)} line below.
     */
    public static void init() {
        register("light_blue", "light_blue_highchair");
        register("pink",       "pink_highchair");
    }

    /**
     * Registers a colour. Returns its colorIndex (idempotent — repeat calls
     * for the same colorKey return the existing index without changing state).
     */
    public static int register(String colorKey, String displayKey) {
        Integer existing = COLOR_KEY_TO_INDEX.get(colorKey);
        if (existing != null) return existing;
        int idx = COLOR_KEYS.size();
        COLOR_KEYS.add(colorKey);
        DISPLAY_KEYS.add(displayKey);
        COLOR_KEY_TO_INDEX.put(colorKey, idx);
        return idx;
    }

    public static int colorIndex(String colorKey) {
        Integer v = COLOR_KEY_TO_INDEX.get(colorKey);
        return v == null ? -1 : v;
    }

    public static String colorKeyFor(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= COLOR_KEYS.size()) return null;
        return COLOR_KEYS.get(colorIndex);
    }

    public static String displayKeyFor(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= DISPLAY_KEYS.size()) return null;
        return DISPLAY_KEYS.get(colorIndex);
    }

    public static int cmdFor(int colorIndex) {
        return CMD_BASE + colorIndex;
    }

    public static int registeredCount() {
        return COLOR_KEYS.size();
    }

    /**
     * Maps a Bukkit Material name like {@code "LIGHT_BLUE_CARPET"} to the
     * registered colorIndex, or -1 if the carpet's colour isn't registered.
     */
    public static int carpetMaterialNameToColorIndex(String materialName) {
        if (materialName == null || !materialName.endsWith("_CARPET")) return -1;
        String colorPart = materialName.substring(0, materialName.length() - "_CARPET".length())
            .toLowerCase(Locale.ROOT);
        return colorIndex(colorPart);
    }

    /** Test-only — clears the static state between unit tests. */
    public static void resetForTesting() {
        COLOR_KEYS.clear();
        DISPLAY_KEYS.clear();
        COLOR_KEY_TO_INDEX.clear();
    }

    public enum LockMode { SELF, LOCKED }

    // --- runtime instance side ---
    private final Map<UUID, Highchair> byId = new ConcurrentHashMap<>();
    private final Map<String, Highchair> byOriginBlockKey = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byChunkKey = new ConcurrentHashMap<>();

    private final Map<UUID, UUID> wardToHighchair = new ConcurrentHashMap<>();
    private final Map<UUID, LockMode> wardToLockMode = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> wardToPlacer = new ConcurrentHashMap<>();

    public void register(Highchair h) {
        Objects.requireNonNull(h, "highchair");
        byId.put(h.id(), h);
        byOriginBlockKey.put(originKey(h.worldName(), h.originX(), h.originY(), h.originZ()), h);
        byChunkKey
            .computeIfAbsent(chunkKey(h.worldName(), h.originX() >> 4, h.originZ() >> 4),
                             k -> ConcurrentHashMap.newKeySet())
            .add(h.id());
    }

    public void unregister(UUID highchairId) {
        Highchair h = byId.remove(highchairId);
        if (h == null) return;
        byOriginBlockKey.remove(originKey(h.worldName(), h.originX(), h.originY(), h.originZ()));
        Set<UUID> chunkSet = byChunkKey.get(chunkKey(h.worldName(), h.originX() >> 4, h.originZ() >> 4));
        if (chunkSet != null) {
            chunkSet.remove(highchairId);
            if (chunkSet.isEmpty()) {
                byChunkKey.remove(chunkKey(h.worldName(), h.originX() >> 4, h.originZ() >> 4));
            }
        }
        // Clear any seated wards in this highchair
        wardToHighchair.entrySet().removeIf(e -> {
            if (highchairId.equals(e.getValue())) {
                wardToLockMode.remove(e.getKey());
                wardToPlacer.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public Highchair findById(UUID highchairId) {
        return byId.get(highchairId);
    }

    public Highchair findByOriginBlock(String worldName, int x, int y, int z) {
        return byOriginBlockKey.get(originKey(worldName, x, y, z));
    }

    public Collection<Highchair> findByChunk(String worldName, int chunkX, int chunkZ) {
        Set<UUID> ids = byChunkKey.get(chunkKey(worldName, chunkX, chunkZ));
        if (ids == null) return Collections.emptyList();
        java.util.List<Highchair> out = new java.util.ArrayList<>(ids.size());
        for (UUID id : ids) {
            Highchair found = byId.get(id);
            if (found != null) out.add(found);
        }
        return out;
    }

    public void unregisterChunk(String worldName, int chunkX, int chunkZ) {
        Set<UUID> ids = byChunkKey.remove(chunkKey(worldName, chunkX, chunkZ));
        if (ids == null) return;
        for (UUID id : ids) {
            Highchair h = byId.remove(id);
            if (h != null) {
                byOriginBlockKey.remove(originKey(h.worldName(), h.originX(), h.originY(), h.originZ()));
                wardToHighchair.entrySet().removeIf(e -> {
                    if (id.equals(e.getValue())) {
                        wardToLockMode.remove(e.getKey());
                        wardToPlacer.remove(e.getKey());
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    /**
     * Records that {@code wardUuid} is now seated in {@code highchairId} with the
     * given lock mode and (for LOCKED) the placer's UUID. Pure bookkeeping —
     * does NOT spawn the seat ArmorStand or fire integration events; the listener
     * does that. {@code placerUuid} may be null for SELF mode.
     */
    public void recordSeating(UUID wardUuid, UUID highchairId, LockMode mode, UUID placerUuid) {
        wardToHighchair.put(wardUuid, highchairId);
        wardToLockMode.put(wardUuid, mode);
        if (placerUuid != null) wardToPlacer.put(wardUuid, placerUuid);
    }

    public void clearSeating(UUID wardUuid) {
        wardToHighchair.remove(wardUuid);
        wardToLockMode.remove(wardUuid);
        wardToPlacer.remove(wardUuid);
    }

    public UUID highchairIdForWard(UUID wardUuid) {
        return wardToHighchair.get(wardUuid);
    }

    public LockMode lockModeForWard(UUID wardUuid) {
        return wardToLockMode.get(wardUuid);
    }

    public UUID placerForWard(UUID wardUuid) {
        return wardToPlacer.get(wardUuid);
    }

    public Set<UUID> containedWards() {
        return new HashSet<>(wardToHighchair.keySet());
    }

    public int size() {
        return byId.size();
    }

    private static String originKey(String worldName, int x, int y, int z) {
        return worldName + ':' + x + ':' + y + ':' + z;
    }

    private static String chunkKey(String worldName, int chunkX, int chunkZ) {
        return worldName + ':' + chunkX + ':' + chunkZ;
    }

    public HighchairRegistry() {}
}
