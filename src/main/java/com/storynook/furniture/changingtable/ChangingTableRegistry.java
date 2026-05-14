package com.storynook.furniture.changingtable;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static colour registry for changing tables. Mirrors HighchairRegistry exactly.
 * Adding a new colour: drop a model JSON, append a range_dispatch entry to
 * slime_ball.json, then add one register(...) line below.
 *
 * <p>CMD = 630100 + colorIndex. Range reserved: 630100–630199 (100 slots).
 */
public final class ChangingTableRegistry {

    public static final int CMD_BASE = 630100;
    public static final int CMD_MAX  = 630199;

    private static final List<String> COLOR_KEYS = new ArrayList<>();
    private static final List<String> DISPLAY_KEYS = new ArrayList<>();
    private static final Map<String, Integer> COLOR_KEY_TO_INDEX = new HashMap<>();

    public ChangingTableRegistry() {}

    public static void init() {
        register("white",       "changingtable_white");
        register("light_gray",  "changingtable_light_gray");
        register("gray",        "changingtable_gray");
        register("black",       "changingtable_black");
        register("brown",       "changingtable_brown");
        register("red",         "changingtable_red");
        register("orange",      "changingtable_orange");
        register("yellow",      "changingtable_yellow");
        register("lime",        "changingtable_lime");
        register("green",       "changingtable_green");
        register("light_blue",  "changingtable_light_blue");
        register("cyan",        "changingtable_cyan");
        register("blue",        "changingtable_blue");
        register("purple",      "changingtable_purple");
        register("magenta",     "changingtable_magenta");
        register("pink",        "changingtable_pink");
    }

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

    /** Read-only view of registered colour keys in registration order. */
    public static List<String> colorKeys() {
        return Collections.unmodifiableList(COLOR_KEYS);
    }

    /** Look up a colorIndex from a carpet Material (e.g. LIGHT_BLUE_CARPET -> 10). */
    public static int colorIndexFromCarpet(Material carpet) {
        if (carpet == null) return -1;
        String name = carpet.name();
        if (!name.endsWith("_CARPET")) return -1;
        String colorKey = name.substring(0, name.length() - "_CARPET".length()).toLowerCase(Locale.ROOT);
        return colorIndex(colorKey);
    }

    /** Test-only — clears all registrations so individual tests start fresh. */
    static void resetForTest() {
        COLOR_KEYS.clear();
        DISPLAY_KEYS.clear();
        COLOR_KEY_TO_INDEX.clear();
    }

    // ---- Runtime instance state (one instance held by Plugin) ----

    private final Map<UUID, ChangingTable> byId = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byChunk = new ConcurrentHashMap<>();          // "world|cx|cz" → tableIds
    private final Map<String, UUID> byBarrierBlock = new ConcurrentHashMap<>();        // "world|x|y|z" → tableId
    private final Map<UUID, UUID> wardToTable = new ConcurrentHashMap<>();             // ward → table
    private final Map<UUID, UUID> wardToPlacer = new ConcurrentHashMap<>();            // ward → caregiver (nullable)
    private final Map<UUID, UUID> wardToArmorStand = new ConcurrentHashMap<>();        // ward → seat AS
    private final Map<UUID, UUID> armorStandToWard = new ConcurrentHashMap<>();        // seat AS → ward (reverse)

    public void register(ChangingTable table) {
        byId.put(table.id(), table);
        String chunkKey1 = chunkKey(table.worldName(),
            table.anchorX() >> 4, table.anchorZ() >> 4);
        byChunk.computeIfAbsent(chunkKey1, k -> ConcurrentHashMap.newKeySet()).add(table.id());
        String chunkKey2 = chunkKey(table.worldName(),
            table.footX() >> 4, table.footZ() >> 4);
        if (!chunkKey2.equals(chunkKey1)) {
            byChunk.computeIfAbsent(chunkKey2, k -> ConcurrentHashMap.newKeySet()).add(table.id());
        }
        byBarrierBlock.put(blockKey(table.worldName(), table.anchorX(), table.anchorY() + 1, table.anchorZ()), table.id());
        byBarrierBlock.put(blockKey(table.worldName(), table.footX(),   table.footY() + 1, table.footZ()),   table.id());
    }

    public void unregister(UUID tableId) {
        ChangingTable t = byId.remove(tableId);
        if (t == null) return;
        for (Set<UUID> set : byChunk.values()) set.remove(tableId);
        byBarrierBlock.values().removeIf(id -> id.equals(tableId));
    }

    public ChangingTable findById(UUID tableId) {
        return byId.get(tableId);
    }

    public ChangingTable findByBarrierBlock(String worldName, int x, int y, int z) {
        UUID id = byBarrierBlock.get(blockKey(worldName, x, y, z));
        return id == null ? null : byId.get(id);
    }

    public Set<UUID> tablesInChunk(String worldName, int cx, int cz) {
        Set<UUID> ids = byChunk.get(chunkKey(worldName, cx, cz));
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    public void unregisterChunk(String worldName, int cx, int cz) {
        Set<UUID> ids = byChunk.remove(chunkKey(worldName, cx, cz));
        if (ids == null) return;
        for (UUID id : ids) unregister(id);
    }

    // ---- Occupancy ----

    public boolean isWardOnTable(UUID wardUuid) {
        return wardToTable.containsKey(wardUuid);
    }

    public UUID tableIdForWard(UUID wardUuid) {
        return wardToTable.get(wardUuid);
    }

    public UUID armorStandForWard(UUID wardUuid) {
        return wardToArmorStand.get(wardUuid);
    }

    public UUID wardForArmorStand(UUID armorStandUuid) {
        return armorStandToWard.get(armorStandUuid);
    }

    public Set<UUID> occupiedWards() {
        return Set.copyOf(wardToTable.keySet());
    }

    public void recordOccupancy(UUID wardUuid, UUID tableId, UUID placerUuid, UUID armorStandUuid) {
        wardToTable.put(wardUuid, tableId);
        if (placerUuid != null) wardToPlacer.put(wardUuid, placerUuid);
        if (armorStandUuid != null) {
            wardToArmorStand.put(wardUuid, armorStandUuid);
            armorStandToWard.put(armorStandUuid, wardUuid);
        }
    }

    public void clearOccupancy(UUID wardUuid) {
        UUID as = wardToArmorStand.remove(wardUuid);
        wardToTable.remove(wardUuid);
        wardToPlacer.remove(wardUuid);
        if (as != null) armorStandToWard.remove(as);
    }

    /** Release a ward: despawn their seat ArmorStand and clear all occupancy entries. */
    public void releaseWard(UUID wardUuid) {
        UUID as = wardToArmorStand.get(wardUuid);
        if (as != null) {
            org.bukkit.entity.Entity asEntity = org.bukkit.Bukkit.getEntity(as);
            if (asEntity != null) asEntity.remove();
        }
        clearOccupancy(wardUuid);
    }

    // ---- Helpers ----

    private static String chunkKey(String world, int cx, int cz) {
        return world + "|" + cx + "|" + cz;
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + "|" + x + "|" + y + "|" + z;
    }
}
