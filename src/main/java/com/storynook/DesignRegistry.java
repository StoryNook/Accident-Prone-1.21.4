package com.storynook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Registry for underwear/diaper design variants. Each design is identified by
 * (category, designId) and carries:
 *   - sprite stage arrays for all 3 sizes (sidebar + action bar)
 *   - 4 in-world leggings CustomModelData values (clean, wet, dirty, wet+dirty)
 *   - giveKey (variation name used by /debug give) and displayName
 *
 * Codepoint block formula for custom designs (designId >= 1):
 *   codepoint = SIZE_BASE[size] + CAT_OFFSET[category] + designId * STAGE_BLOCK[category] + stageIndex
 *
 * Adding a new design: see docs/wiki/design-registry.md.
 * In short: drop PNGs in assets/.../special/<name>/, run tools/generate_design_json.py,
 * then /add-design (which appends a single register(...) call below).
 */
public class DesignRegistry {

    /** Public read-only struct describing one registered design. */
    public static final class DesignDef {
        public final int category;
        public final int designId;
        public final int cleanCmd;
        public final int wetCmd;
        public final int dirtyCmd;
        public final int wetDirtyCmd;
        public final String giveKey;       // variation key for /debug give
        public final String displayName;   // shown on the item
        DesignDef(int category, int designId,
                  int cleanCmd, int wetCmd, int dirtyCmd, int wetDirtyCmd,
                  String giveKey, String displayName) {
            this.category = category;
            this.designId = designId;
            this.cleanCmd = cleanCmd;
            this.wetCmd = wetCmd;
            this.dirtyCmd = dirtyCmd;
            this.wetDirtyCmd = wetDirtyCmd;
            this.giveKey = giveKey;
            this.displayName = displayName;
        }
    }

    private static final int[] SIZE_BASE   = { 0xED00, 0xE200, 0xE780 };
    private static final int[] CAT_OFFSET  = { 0x000,  0x080,  0x180,  0x380 };
    private static final int[] STAGE_BLOCK = { 4,      8,      16,     32    };
    private static final int[] STAGE_COUNT = { 4,      8,      15,     25    };

    private static final Map<Integer, char[][]> stageMap   = new HashMap<>();
    private static final Map<Integer, DesignDef> defMap    = new HashMap<>(); // (cat<<16|designId) -> def
    private static final Map<Integer, DesignDef> cmdIndex  = new HashMap<>(); // cleanCmd -> def
    private static final List<DesignDef> all                = new ArrayList<>();

    /**
     * Register a custom design (designId >= 1). Sprite codepoints are computed
     * automatically from the block formula. Call from Plugin.onEnable().
     */
    public static void register(int category, int designId,
                                int cleanCmd, int wetCmd, int dirtyCmd, int wetDirtyCmd,
                                String giveKey, String displayName) {
        char[][] sized = new char[3][];
        for (int size = 0; size < 3; size++) {
            int n = STAGE_COUNT[category];
            char[] stages = new char[n];
            for (int i = 0; i < n; i++) {
                stages[i] = (char)(SIZE_BASE[size] + CAT_OFFSET[category]
                                   + designId * STAGE_BLOCK[category] + i);
            }
            sized[size] = stages;
        }
        DesignDef def = new DesignDef(category, designId, cleanCmd, wetCmd, dirtyCmd, wetDirtyCmd, giveKey, displayName);
        int key = key(category, designId);
        stageMap.put(key, sized);
        defMap.put(key, def);
        cmdIndex.put(cleanCmd, def);
        all.add(def);
    }

    /**
     * Register designId=0 (the default design) with explicit legacy char arrays.
     * giveKey/displayName let the default design also be requested via /debug give.
     */
    public static void registerLegacy(int category,
                                      char[] small, char[] normal, char[] big,
                                      int cleanCmd, int wetCmd, int dirtyCmd, int wetDirtyCmd,
                                      String giveKey, String displayName) {
        DesignDef def = new DesignDef(category, 0, cleanCmd, wetCmd, dirtyCmd, wetDirtyCmd, giveKey, displayName);
        int key = key(category, 0);
        stageMap.put(key, new char[][]{ small, normal, big });
        defMap.put(key, def);
        cmdIndex.put(cleanCmd, def);
        all.add(def);
    }

    /**
     * Return the stage char array for (category, designId, size).
     * Falls back to designId=0 if the requested design is not registered.
     */
    public static char[] getStages(int category, int designId, int size) {
        char[][] sized = stageMap.get(key(category, designId));
        if (sized == null) sized = stageMap.get(key(category, 0));
        if (sized == null || size < 0 || size >= sized.length) return new char[0];
        return sized[size];
    }

    /**
     * Return in-world leggings CustomModelData for the current wetness/fullness state.
     * Falls back to designId=0 if the requested design is not registered.
     */
    public static int getVisibleCmd(int category, int designId, double wetness, double fullness) {
        DesignDef def = defMap.get(key(category, designId));
        if (def == null) def = defMap.get(key(category, 0));
        if (def == null) return 0;
        boolean wet  = wetness  > 0;
        boolean mess = fullness > 0;
        if (wet && mess) return def.wetDirtyCmd;
        if (wet)         return def.wetCmd;
        if (mess)        return def.dirtyCmd;
        return def.cleanCmd;
    }

    /** Lookup a design by its clean-state CustomModelData (used on equip). */
    public static DesignDef findByCleanCmd(int cleanCmd) {
        return cmdIndex.get(cleanCmd);
    }

    /**
     * True if the given CustomModelData is the clean CMD of any registered
     * design. Used by held-item checks (e.g. "is this slime ball a wearable
     * underwear/diaper?").
     */
    public static boolean isAnyCleanCmd(int cmd) {
        return cmdIndex.containsKey(cmd);
    }

    /**
     * True if the given CustomModelData is any of clean/wet/dirty/wetDirty for
     * any registered design. Used by worn-leggings checks (e.g. "is this
     * leggings currently a diaper?").
     */
    public static boolean isAnyDesignCmd(int cmd) {
        for (DesignDef def : all) {
            if (cmd == def.cleanCmd || cmd == def.wetCmd
                    || cmd == def.dirtyCmd || cmd == def.wetDirtyCmd) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the given CustomModelData is a wet, dirty, or wetDirty state of
     * any registered design — i.e. any "soiled" state, excluding clean. New
     * designs registered later are picked up automatically. Pair with the
     * legacy hardcoded set for items produced by the pre-registry static
     * factories in {@code com.storynook.items.underwear}.
     */
    public static boolean isAnySoiledCmd(int cmd) {
        for (DesignDef def : all) {
            if (cmd == def.wetCmd || cmd == def.dirtyCmd || cmd == def.wetDirtyCmd) {
                return true;
            }
        }
        return false;
    }

    /** Lookup a design by category + giveKey (used by /debug give). */
    public static DesignDef findByGiveKey(int category, String giveKey) {
        if (giveKey == null) return null;
        for (DesignDef def : all) {
            if (def.category == category && giveKey.equalsIgnoreCase(def.giveKey)) {
                return def;
            }
        }
        return null;
    }

    /** All designs registered for a category (used by tab completion). */
    public static List<DesignDef> getDesignsForCategory(int category) {
        List<DesignDef> out = new ArrayList<>();
        for (DesignDef def : all) {
            if (def.category == category) out.add(def);
        }
        return Collections.unmodifiableList(out);
    }

    /** Build a clean ItemStack for this design (used by Give and applyEquip paths). */
    public static ItemStack createItem(DesignDef def) {
        ItemStack item = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(def.displayName);
        meta.setCustomModelData(def.cleanCmd);
        item.setItemMeta(meta);
        return item;
    }

    private static int key(int category, int designId) {
        return (category << 16) | (designId & 0xFFFF);
    }

    /** Called once from Plugin.onEnable() before any player joins. */
    public static void init() {

        // --- Default designs (designId=0) using existing legacy codepoints ---

        registerLegacy(0, // Underwear
            new char[]{'\uE134', '\uE136', '\uE135', '\uE137'},
            new char[]{'\uE018', '\uE01A', '\uE019', '\uE01B'},
            new char[]{'\uE034', '\uE036', '\uE035', '\uE037'},
            626002, 626031, 626032, 626033,
            "default", "Underwear"
        );
        registerLegacy(1, // Pull-up
            new char[]{'\uE11C', '\uE124', '\uE12C', '\uE130', '\uE11F', '\uE127', '\uE12F', '\uE133'},
            new char[]{'\uE000', '\uE008', '\uE010', '\uE014', '\uE003', '\uE00B', '\uE013', '\uE017'},
            new char[]{'\uE01C', '\uE024', '\uE02C', '\uE030', '\uE01F', '\uE027', '\uE02F', '\uE033'},
            626003, 626028, 626029, 626030,
            "default", "Pullup"
        );
        registerLegacy(2, // Diaper
            new char[]{'\uE11C', '\uE120', '\uE124', '\uE12C', '\uE130', '\uE11D', '\uE11F', '\uE121', '\uE125', '\uE12D', '\uE131', '\uE123', '\uE127', '\uE12F', '\uE133'},
            new char[]{'\uE000', '\uE004', '\uE008', '\uE010', '\uE014', '\uE001', '\uE003', '\uE005', '\uE009', '\uE011', '\uE015', '\uE007', '\uE00B', '\uE013', '\uE017'},
            new char[]{'\uE01C', '\uE020', '\uE024', '\uE02C', '\uE030', '\uE01D', '\uE01F', '\uE021', '\uE025', '\uE02D', '\uE031', '\uE023', '\uE027', '\uE02F', '\uE033'},
            626009, 626022, 626023, 626024,
            "default", "Diaper"
        );
        registerLegacy(3, // Thick Diaper
            new char[]{'\uE11C', '\uE120', '\uE124', '\uE128', '\uE12C', '\uE130', '\uE11D', '\uE11E', '\uE11F', '\uE121', '\uE125', '\uE129', '\uE12D', '\uE131', '\uE122', '\uE126', '\uE12A', '\uE12E', '\uE132', '\uE123', '\uE127', '\uE12B', '\uE12F', '\uE133'},
            new char[]{'\uE000', '\uE004', '\uE008', '\uE00C', '\uE010', '\uE014', '\uE001', '\uE002', '\uE003', '\uE005', '\uE009', '\uE00D', '\uE011', '\uE015', '\uE006', '\uE00A', '\uE00E', '\uE012', '\uE016', '\uE007', '\uE00B', '\uE00F', '\uE013', '\uE017'},
            new char[]{'\uE01C', '\uE020', '\uE024', '\uE028', '\uE02C', '\uE030', '\uE01D', '\uE01E', '\uE01F', '\uE021', '\uE025', '\uE029', '\uE02D', '\uE031', '\uE022', '\uE026', '\uE02A', '\uE02E', '\uE032', '\uE023', '\uE027', '\uE02B', '\uE02F', '\uE033'},
            626001, 626025, 626026, 626027,
            "default", "Thick Diaper"
        );

        // --- Custom designs (added by /add-design) ---
        // Format: register(category, designId, cleanCmd, wetCmd, dirtyCmd, wetDirtyCmd, giveKey, displayName);
        register(1, 1, 626060, 626061, 626062, 626063, "goodnite_stars", "Girls Goodnite Pull-Up (Stars)");
    }
}
