package com.storynook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.storynook.items.Paci;

public final class PaciRegistry {

    public static final class PaciDef {
        public final int designId;
        public final int cmd;
        public final String giveKey;
        public final String displayName;
        public final String equippableKey;
        PaciDef(int designId, int cmd, String giveKey, String displayName, String equippableKey) {
            this.designId = designId;
            this.cmd = cmd;
            this.giveKey = giveKey;
            this.displayName = displayName;
            this.equippableKey = equippableKey;
        }
    }

    private static final Map<Integer, PaciDef> byCmd = new HashMap<>();
    private static final Map<String, PaciDef> byGiveKey = new HashMap<>();
    private static final List<PaciDef> all = new ArrayList<>();

    public static void register(int designId, int cmd, String giveKey,
                                String displayName, String equippableKey) {
        PaciDef def = new PaciDef(designId, cmd, giveKey, displayName, equippableKey);
        byCmd.put(cmd, def);
        byGiveKey.put(giveKey.toLowerCase(), def);
        all.add(def);
    }

    public static PaciDef findByGiveKey(String giveKey) {
        if (giveKey == null) return null;
        return byGiveKey.get(giveKey.toLowerCase());
    }

    public static PaciDef findByCmd(int cmd) {
        return byCmd.get(cmd);
    }

    public static boolean isPaciCmd(int cmd) {
        return byCmd.containsKey(cmd);
    }

    public static List<PaciDef> getAll() {
        return Collections.unmodifiableList(all);
    }

    public static ItemStack createItem(PaciDef def) {
        return Paci.createPaci(def);
    }

    public static Optional<PaciDef> getWornFromHelmet(ItemStack helmet) {
        if (helmet == null || !helmet.hasItemMeta()) return Optional.empty();
        if (!helmet.getItemMeta().hasCustomModelData()) return Optional.empty();
        PaciDef def = byCmd.get(helmet.getItemMeta().getCustomModelData());
        return Optional.ofNullable(def);
    }

    public static void init() {
        register( 1, 627100, "birthday",   "Birthday Paci",   "paci_birthday");
        register( 2, 627101, "black",      "Black Paci",      "paci_black");
        register( 3, 627102, "blue",       "Blue Paci",       "paci_blue");
        register( 4, 627103, "brown",      "Brown Paci",      "paci_brown");
        register( 5, 627104, "bunny",      "Bunny Paci",      "paci_bunny");
        register( 6, 627105, "christmas",  "Christmas Paci",  "paci_christmas");
        register( 7, 627106, "cyan",       "Cyan Paci",       "paci_cyan");
        register( 8, 627107, "discord",    "Discord Paci",    "paci_discord");
        register( 9, 627108, "easter",     "Easter Paci",     "paci_easter");
        register(10, 627109, "eevee",      "Eevee Paci",      "paci_eevee");
        register(11, 627110, "gray",       "Gray Paci",       "paci_gray");
        register(12, 627111, "green",      "Green Paci",      "paci_green");
        register(13, 627112, "halloween",  "Halloween Paci",  "paci_halloween");
        register(14, 627113, "light_blue", "Light Blue Paci", "paci_light_blue");
        register(15, 627114, "light_gray", "Light Gray Paci", "paci_light_gray");
        register(16, 627115, "lime",       "Lime Paci",       "paci_lime");
        register(17, 627116, "magenta",    "Magenta Paci",    "paci_magenta");
        register(18, 627117, "orange",     "Orange Paci",     "paci_orange");
        register(19, 627118, "patreon",    "Patreon Paci",    "paci_patreon");
        register(20, 627119, "pink",       "Pink Paci",       "paci_pink");
        register(21, 627120, "prince",     "Prince Paci",     "paci_prince");
        register(22, 627121, "princess",   "Princess Paci",   "paci_princess");
        register(23, 627122, "purple",     "Purple Paci",     "paci_purple");
        register(24, 627123, "red",        "Red Paci",        "paci_red");
        register(25, 627124, "royal",      "Royal Paci",      "paci_royal");
        register(26, 627125, "white",      "White Paci",      "paci_white");
        register(27, 627126, "yellow",     "Yellow Paci",     "paci_yellow");
    }
}
