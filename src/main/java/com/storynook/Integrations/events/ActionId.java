package com.storynook.Integrations.events;

public final class ActionId {
    public static final String CHANGE = "accidentprone:change";
    public static final String FEED = "accidentprone:feed";
    public static final String PAIL_FILL = "accidentprone:pail_fill";
    public static final String WASH_PANTS = "accidentprone:wash_pants";
    public static final String EQUIP_ARMOR_ON_LITTLE = "accidentprone:equip_armor_on_little";
    public static final String CRAFT_UNDERWEAR = "accidentprone:craft_underwear";
    public static final String CRAFT_PULLUP = "accidentprone:craft_pullup";
    public static final String CRAFT_DIAPER = "accidentprone:craft_diaper";
    public static final String CRAFT_THICK_DIAPER = "accidentprone:craft_thick_diaper";
    public static final String CRAFT_CRIB = "accidentprone:craft_crib";
    public static final String CRAFT_WASHER = "accidentprone:craft_washer";
    public static final String TOILET_RELIEF = "accidentprone:toilet_relief";
    public static final String ACCIDENT_HANDLED = "accidentprone:accident_handled";
    public static final String HYDRATE_THRESHOLD = "accidentprone:hydrate_threshold";
    public static final String CARRY_PICKUP = "accidentprone:carry_pickup";
    public static final String CARRY_DROP = "accidentprone:carry_drop";

    public static final String[] ALL = new String[] {
        CHANGE, FEED, PAIL_FILL, WASH_PANTS, EQUIP_ARMOR_ON_LITTLE,
        CRAFT_UNDERWEAR, CRAFT_PULLUP, CRAFT_DIAPER, CRAFT_THICK_DIAPER,
        CRAFT_CRIB, CRAFT_WASHER, TOILET_RELIEF, ACCIDENT_HANDLED, HYDRATE_THRESHOLD,
        CARRY_PICKUP, CARRY_DROP
    };

    /**
     * Maps a crafted result-item CustomModelData to the corresponding craft ActionId, or null.
     *
     * <p>Base craft-result CMDs (clean, unworn items produced by the registered recipes):
     * <ul>
     *   <li>626002 — Underwear  ({@link #CRAFT_UNDERWEAR})</li>
     *   <li>626003 — Pullup     ({@link #CRAFT_PULLUP})</li>
     *   <li>626009 — Diaper     ({@link #CRAFT_DIAPER})</li>
     *   <li>626001 — ThickDiaper ({@link #CRAFT_THICK_DIAPER})</li>
     *   <li>626014 — Washer     ({@link #CRAFT_WASHER})</li>
     *   <li>627000–627009 — Crib wood variants ({@link #CRAFT_CRIB})</li>
     * </ul>
     *
     * <p>Sources: {@code underwear.java} (Underwear/Pullup/Diaper/ThickDiaper factory methods),
     * {@code ItemManager.java} (Washer), {@code cribs.java} ({@code getCribModelNumber}).
     *
     * @param cmd the {@code CustomModelData} value of the crafted result item
     * @return the matching {@code ActionId} constant, or {@code null} if not a tracked craft
     */
    public static String mapCmdToCraftAction(int cmd) {
        // Underwear: base clean CMD produced by createUnderwearRecipe()
        if (cmd == 626002) return CRAFT_UNDERWEAR;
        // Pull-up: base clean CMD produced by createPullupRecipe()
        if (cmd == 626003) return CRAFT_PULLUP;
        // Diaper: base clean CMD produced by createDiaperRecipe()
        if (cmd == 626009) return CRAFT_DIAPER;
        // Thick Diaper: base clean CMD produced by createThickDiaperRecipe()
        if (cmd == 626001) return CRAFT_THICK_DIAPER;
        // Washer: CMD 626014 produced by createWasherRecipe()
        if (cmd == 626014) return CRAFT_WASHER;
        // Crib: wood-variant range 627000–627009 produced by getCribModelNumber()
        if (cmd >= 627000 && cmd <= 627009) return CRAFT_CRIB;
        return null;
    }

    private ActionId() {}
}
