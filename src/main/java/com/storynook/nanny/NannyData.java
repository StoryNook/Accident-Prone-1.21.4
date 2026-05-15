package com.storynook.nanny;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;

public class NannyData {

    // -------------------------------------------------------------------------
    // Inner enums
    // -------------------------------------------------------------------------

    public enum MoodTier { SWEET, CARING, STRICT, WARDEN, CUSTOM }
    public enum ChestMode { ALL, SELECTED, INVENTORY_ONLY }
    public enum CraftingMode { ALL, BASIC, NONE, EVIL }
    public enum ChatRespondTo { OWNER, LISTED, ANYONE }
    public enum ChatTier { BASIC, AI }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UUID nannyUUID;
    private UUID ownerUUID;
    private String name;
    private String skinUrl;
    private int citizensNpcId;
    private String homeWorld;
    private double homeX;
    private double homeY;
    private double homeZ;
    private int homeRadius;
    private String lastWorld;
    private double lastX;
    private double lastY;
    private double lastZ;
    private LocalDateTime lastOwnerSeen;
    private boolean dormant;
    private MoodTier moodTier;
    private MoodTier customTone = MoodTier.CARING;
    private List<UUID> wards;
    private ChestMode chestMode;
    private List<String> selectedChests;
    private CraftingMode craftingMode;
    private boolean blockOtherCaregivers;
    private boolean chatEnabled;
    private ChatRespondTo chatRespondTo;
    private List<UUID> chatListedPlayers;
    private ChatTier chatTier;
    private boolean followMode;
    private boolean seekEnabled;
    private int changeThreshold;
    private int feedThreshold;
    private int hydrationThreshold;
    private ItemStack[] personalInventory;
    private Map<String, Boolean> customSettings;
    private Map<UUID, Boolean> lockedArmor;
    private List<String> lockedRoomBlocks;
    /** Per-ward bidirectional behavior score, range -100..+100. */
    private Map<UUID, Integer> behaviorScore = new HashMap<>();
    /** Per-ward fast-decaying streak (recent intent), range -50..+50. */
    private Map<UUID, Integer> behaviorStreak = new HashMap<>();
    /** Per-ward epoch-millis of last decay tick — lazy-decay marker. */
    private Map<UUID, Long> behaviorLastDecay = new HashMap<>();
    /** Per-ward epoch-millis after which "came when called" reward expires. */
    private Map<UUID, Long> pendingSummonExpiresAt = new HashMap<>();
    /** Per-ward per-action cooldown timestamps (action name → cooldown-until millis). */
    private Map<UUID, Map<String, Long>> disciplineCooldowns = new HashMap<>();
    /** Per-ward epoch-millis until which the next queued Java punishment is suppressed by recent AI <REWARD:praise>. */
    private Map<UUID, Long> praiseGraceUntil = new HashMap<>();
    /** Per-ward list of currently-active persistent punishment action names. */
    private Map<UUID, List<String>> activePersistentPunishments = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public NannyData(UUID nannyUUID, UUID ownerUUID, String name, Plugin plugin) {
        this.nannyUUID = nannyUUID;
        this.ownerUUID = ownerUUID;
        this.name = name;

        Map<String, Object> globalConfig =
                (plugin != null) ? plugin.getGlobalConfig() : new HashMap<>();

        this.citizensNpcId = -1;
        this.homeWorld = "";
        this.homeX = 0;
        this.homeY = 0;
        this.homeZ = 0;
        this.lastWorld = "";
        this.lastX = 0;
        this.lastY = 0;
        this.lastZ = 0;

        Object radiusObj = globalConfig.getOrDefault("Nanny_Default_Home_Radius", 50);
        this.homeRadius = (radiusObj instanceof Number) ? ((Number) radiusObj).intValue() : 50;

        this.lastOwnerSeen = LocalDateTime.now();
        this.dormant = false;

        MoodTier parsedMood = MoodTier.CARING;
        try {
            Object moodObj = globalConfig.getOrDefault("Nanny_Default_Mood", "CARING");
            parsedMood = MoodTier.valueOf(moodObj.toString());
        } catch (IllegalArgumentException e) {
            parsedMood = MoodTier.CARING;
        }
        this.moodTier = parsedMood;

        this.wards = new ArrayList<>();

        ChestMode parsedChestMode = ChestMode.INVENTORY_ONLY;
        try {
            Object chestObj = globalConfig.getOrDefault("Nanny_Default_Chest_Mode", "INVENTORY_ONLY");
            parsedChestMode = ChestMode.valueOf(chestObj.toString());
        } catch (IllegalArgumentException e) {
            parsedChestMode = ChestMode.INVENTORY_ONLY;
        }
        this.chestMode = parsedChestMode;

        this.selectedChests = new ArrayList<>();

        CraftingMode parsedCraftingMode = CraftingMode.BASIC;
        try {
            Object craftObj = globalConfig.getOrDefault("Nanny_Default_Crafting_Mode", "BASIC");
            parsedCraftingMode = CraftingMode.valueOf(craftObj.toString());
        } catch (IllegalArgumentException e) {
            parsedCraftingMode = CraftingMode.BASIC;
        }
        this.craftingMode = parsedCraftingMode;

        this.blockOtherCaregivers = false;

        Object chatEnabledObj = globalConfig.getOrDefault("Nanny_Chat_enabled", true);
        this.chatEnabled = (chatEnabledObj instanceof Boolean) ? (Boolean) chatEnabledObj : true;

        this.chatRespondTo = ChatRespondTo.OWNER;
        this.chatListedPlayers = new ArrayList<>();
        this.chatTier = ChatTier.BASIC;
        this.followMode = false;
        this.seekEnabled = true;
        this.skinUrl = "";

        Object changeObj = globalConfig.getOrDefault("Nanny_Default_Change_Threshold", 70);
        this.changeThreshold = (changeObj instanceof Number) ? ((Number) changeObj).intValue() : 70;

        Object feedObj = globalConfig.getOrDefault("Nanny_Default_Feed_Threshold", 14);
        this.feedThreshold = (feedObj instanceof Number) ? ((Number) feedObj).intValue() : 14;

        Object hydObj = globalConfig.getOrDefault("Nanny_Default_Hydration_Threshold", 30);
        this.hydrationThreshold = (hydObj instanceof Number) ? ((Number) hydObj).intValue() : 30;

        this.personalInventory = new ItemStack[18];
        this.customSettings = new HashMap<>();
        this.lockedArmor = new HashMap<>();
        this.lockedRoomBlocks = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // save
    // -------------------------------------------------------------------------

    public void save(File dataFolder) {
        File nanniesDir = new File(dataFolder, "nannies");
        if (!nanniesDir.exists()) {
            nanniesDir.mkdirs();
        }

        File nannyFile = new File(nanniesDir, nannyUUID.toString() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(nannyFile);

        config.set("nannyUUID", nannyUUID.toString());
        config.set("ownerUUID", ownerUUID.toString());
        config.set("name", name);
        config.set("skinUrl", skinUrl);
        config.set("citizensNpcId", citizensNpcId);
        config.set("homeWorld", homeWorld);
        config.set("homeX", homeX);
        config.set("homeY", homeY);
        config.set("homeZ", homeZ);
        config.set("homeRadius", homeRadius);
        config.set("lastWorld", lastWorld);
        config.set("lastX", lastX);
        config.set("lastY", lastY);
        config.set("lastZ", lastZ);
        config.set("lastOwnerSeen", lastOwnerSeen != null ? lastOwnerSeen.format(DATE_FMT) : "");
        config.set("dormant", dormant);
        config.set("moodTier", moodTier != null ? moodTier.name() : MoodTier.CARING.name());
        config.set("customTone", customTone != null ? customTone.name() : MoodTier.CARING.name());

        List<String> wardStrings = (wards != null) ?
                wards.stream().map(UUID::toString).collect(Collectors.toList()) :
                new ArrayList<String>();
        config.set("wards", wardStrings);

        config.set("chestMode", chestMode != null ? chestMode.name() : ChestMode.INVENTORY_ONLY.name());
        config.set("selectedChests", selectedChests != null ? selectedChests : new ArrayList<String>());
        config.set("craftingMode", craftingMode != null ? craftingMode.name() : CraftingMode.BASIC.name());
        config.set("blockOtherCaregivers", blockOtherCaregivers);
        config.set("chatEnabled", chatEnabled);
        config.set("chatRespondTo", chatRespondTo != null ? chatRespondTo.name() : ChatRespondTo.OWNER.name());

        List<String> chatListedStrings = (chatListedPlayers != null) ?
                chatListedPlayers.stream().map(UUID::toString).collect(Collectors.toList()) :
                new ArrayList<String>();
        config.set("chatListedPlayers", chatListedStrings);

        config.set("chatTier", chatTier != null ? chatTier.name() : ChatTier.BASIC.name());
        config.set("followMode", followMode);
        config.set("seekEnabled", seekEnabled);
        config.set("changeThreshold", changeThreshold);
        config.set("feedThreshold", feedThreshold);
        config.set("hydrationThreshold", hydrationThreshold);

        List<ItemStack> invList = new ArrayList<>();
        for (ItemStack stack : getPersonalInventory()) {
            invList.add(stack);
        }
        config.set("personalInventory", invList);

        Map<String, Object> cs = new HashMap<>();
        for (Map.Entry<String, Boolean> e : getCustomSettings().entrySet()) {
            cs.put(e.getKey(), e.getValue());
        }
        config.set("customSettings", cs);

        // behavior maps — UUID keys serialized as strings
        Map<String, Integer> bsOut = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : behaviorScore.entrySet()) bsOut.put(e.getKey().toString(), e.getValue());
        config.set("behaviorScore", bsOut);

        Map<String, Integer> bstrOut = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : behaviorStreak.entrySet()) bstrOut.put(e.getKey().toString(), e.getValue());
        config.set("behaviorStreak", bstrOut);

        Map<String, Long> bldOut = new HashMap<>();
        for (Map.Entry<UUID, Long> e : behaviorLastDecay.entrySet()) bldOut.put(e.getKey().toString(), e.getValue());
        config.set("behaviorLastDecay", bldOut);

        Map<String, Long> pseOut = new HashMap<>();
        for (Map.Entry<UUID, Long> e : pendingSummonExpiresAt.entrySet()) pseOut.put(e.getKey().toString(), e.getValue());
        config.set("pendingSummonExpiresAt", pseOut);

        Map<String, Map<String, Long>> dcOut = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Long>> e : disciplineCooldowns.entrySet()) dcOut.put(e.getKey().toString(), e.getValue());
        config.set("disciplineCooldowns", dcOut);

        Map<String, Long> pgOut = new HashMap<>();
        for (Map.Entry<UUID, Long> e : praiseGraceUntil.entrySet()) pgOut.put(e.getKey().toString(), e.getValue());
        config.set("praiseGraceUntil", pgOut);

        Map<String, List<String>> appOut = new HashMap<>();
        for (Map.Entry<UUID, List<String>> e : activePersistentPunishments.entrySet()) appOut.put(e.getKey().toString(), e.getValue());
        config.set("activePersistentPunishments", appOut);

        Map<String, Object> la = new HashMap<>();
        for (Map.Entry<UUID, Boolean> e : getLockedArmor().entrySet()) {
            la.put(e.getKey().toString(), e.getValue());
        }
        config.set("lockedArmor", la);
        config.set("lockedRoomBlocks", lockedRoomBlocks != null ? lockedRoomBlocks : new ArrayList<String>());

        try {
            config.save(nannyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // static load
    // -------------------------------------------------------------------------

    public static NannyData load(UUID nannyUUID, File dataFolder, Plugin plugin) {
        File nannyFile = new File(dataFolder, "nannies/" + nannyUUID.toString() + ".yml");
        if (!nannyFile.exists()) {
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(nannyFile);

        UUID ownerUUID;
        try {
            ownerUUID = UUID.fromString(config.getString("ownerUUID", ""));
        } catch (IllegalArgumentException e) {
            ownerUUID = nannyUUID; // fallback — should not happen on a valid file
        }

        String name = config.getString("name", "Nanny");
        NannyData data = new NannyData(nannyUUID, ownerUUID, name, plugin);

        data.skinUrl = config.getString("skinUrl", "");
        data.citizensNpcId = config.getInt("citizensNpcId", -1);
        data.homeWorld = config.getString("homeWorld", "");
        data.homeX = config.getDouble("homeX", 0);
        data.homeY = config.getDouble("homeY", 0);
        data.homeZ = config.getDouble("homeZ", 0);
        data.homeRadius = config.getInt("homeRadius", 50);
        data.lastWorld = config.getString("lastWorld", "");
        data.lastX = config.getDouble("lastX", 0);
        data.lastY = config.getDouble("lastY", 0);
        data.lastZ = config.getDouble("lastZ", 0);

        String lastSeenStr = config.getString("lastOwnerSeen", "");
        if (lastSeenStr != null && !lastSeenStr.isEmpty()) {
            try {
                data.lastOwnerSeen = LocalDateTime.parse(lastSeenStr, DATE_FMT);
            } catch (Exception e) {
                data.lastOwnerSeen = LocalDateTime.now();
            }
        } else {
            data.lastOwnerSeen = LocalDateTime.now();
        }

        data.dormant = config.getBoolean("dormant", false);

        try {
            data.moodTier = MoodTier.valueOf(config.getString("moodTier", "CARING"));
        } catch (IllegalArgumentException e) {
            data.moodTier = MoodTier.CARING;
        }

        try {
            data.customTone = MoodTier.valueOf(config.getString("customTone", "CARING"));
        } catch (IllegalArgumentException e) {
            data.customTone = MoodTier.CARING;
        }

        List<String> wardStrings = config.getStringList("wards");
        List<UUID> wards = new ArrayList<>();
        for (String s : wardStrings) {
            try {
                wards.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                // skip malformed entries
            }
        }
        data.wards = wards;

        try {
            data.chestMode = ChestMode.valueOf(config.getString("chestMode", "INVENTORY_ONLY"));
        } catch (IllegalArgumentException e) {
            data.chestMode = ChestMode.INVENTORY_ONLY;
        }

        data.selectedChests = config.getStringList("selectedChests");

        try {
            data.craftingMode = CraftingMode.valueOf(config.getString("craftingMode", "BASIC"));
        } catch (IllegalArgumentException e) {
            data.craftingMode = CraftingMode.BASIC;
        }

        data.blockOtherCaregivers = config.getBoolean("blockOtherCaregivers", false);
        data.chatEnabled = config.getBoolean("chatEnabled", true);

        try {
            data.chatRespondTo = ChatRespondTo.valueOf(config.getString("chatRespondTo", "OWNER"));
        } catch (IllegalArgumentException e) {
            data.chatRespondTo = ChatRespondTo.OWNER;
        }

        List<String> chatListedStrings = config.getStringList("chatListedPlayers");
        List<UUID> chatListedPlayers = new ArrayList<>();
        for (String s : chatListedStrings) {
            try {
                chatListedPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                // skip malformed entries
            }
        }
        data.chatListedPlayers = chatListedPlayers;

        try {
            data.chatTier = ChatTier.valueOf(config.getString("chatTier", "BASIC"));
        } catch (IllegalArgumentException e) {
            data.chatTier = ChatTier.BASIC;
        }

        data.followMode = config.getBoolean("followMode", false);
        data.seekEnabled = config.getBoolean("seekEnabled", true);

        data.changeThreshold = config.getInt("changeThreshold", 70);
        data.feedThreshold = config.getInt("feedThreshold", 14);
        data.hydrationThreshold = config.getInt("hydrationThreshold", 30);

        @SuppressWarnings("unchecked")
        List<ItemStack> savedInv = (List<ItemStack>) config.getList("personalInventory");
        ItemStack[] inv = new ItemStack[18];
        if (savedInv != null) {
            for (int i = 0; i < Math.min(18, savedInv.size()); i++) {
                inv[i] = savedInv.get(i);
            }
        }
        data.personalInventory = inv;

        Map<String, Boolean> cs = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection csSec = config.getConfigurationSection("customSettings");
        if (csSec != null) {
            for (String key : csSec.getKeys(false)) {
                cs.put(key, csSec.getBoolean(key, false));
            }
        }
        data.customSettings = cs;

        // behavior maps — string keys deserialized back to UUID
        org.bukkit.configuration.ConfigurationSection bsSec = config.getConfigurationSection("behaviorScore");
        if (bsSec != null) {
            for (String k : bsSec.getKeys(false)) {
                try { data.behaviorScore.put(UUID.fromString(k), bsSec.getInt(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection bstrSec = config.getConfigurationSection("behaviorStreak");
        if (bstrSec != null) {
            for (String k : bstrSec.getKeys(false)) {
                try { data.behaviorStreak.put(UUID.fromString(k), bstrSec.getInt(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection bldSec = config.getConfigurationSection("behaviorLastDecay");
        if (bldSec != null) {
            for (String k : bldSec.getKeys(false)) {
                try { data.behaviorLastDecay.put(UUID.fromString(k), bldSec.getLong(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection pseSec = config.getConfigurationSection("pendingSummonExpiresAt");
        if (pseSec != null) {
            for (String k : pseSec.getKeys(false)) {
                try { data.pendingSummonExpiresAt.put(UUID.fromString(k), pseSec.getLong(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection dcSec = config.getConfigurationSection("disciplineCooldowns");
        if (dcSec != null) {
            for (String wardKey : dcSec.getKeys(false)) {
                try {
                    UUID wardUuid = UUID.fromString(wardKey);
                    org.bukkit.configuration.ConfigurationSection inner = dcSec.getConfigurationSection(wardKey);
                    if (inner == null) continue;
                    Map<String, Long> wardMap = new HashMap<>();
                    for (String act : inner.getKeys(false)) wardMap.put(act, inner.getLong(act));
                    data.disciplineCooldowns.put(wardUuid, wardMap);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection pgSec = config.getConfigurationSection("praiseGraceUntil");
        if (pgSec != null) {
            for (String k : pgSec.getKeys(false)) {
                try { data.praiseGraceUntil.put(UUID.fromString(k), pgSec.getLong(k)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        org.bukkit.configuration.ConfigurationSection appSec = config.getConfigurationSection("activePersistentPunishments");
        if (appSec != null) {
            for (String wardKey : appSec.getKeys(false)) {
                try {
                    UUID wardUuid = UUID.fromString(wardKey);
                    List<String> list = appSec.getStringList(wardKey);
                    if (list != null) data.activePersistentPunishments.put(wardUuid, new ArrayList<>(list));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        Map<UUID, Boolean> la = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection laSec = config.getConfigurationSection("lockedArmor");
        if (laSec != null) {
            for (String key : laSec.getKeys(false)) {
                try {
                    la.put(UUID.fromString(key), laSec.getBoolean(key, false));
                } catch (IllegalArgumentException e) { /* skip malformed */ }
            }
        }
        data.lockedArmor = la;

        data.lockedRoomBlocks = config.getStringList("lockedRoomBlocks");
        if (data.lockedRoomBlocks == null) data.lockedRoomBlocks = new ArrayList<>();

        return data;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public UUID getNannyUUID() { return nannyUUID; }
    public void setNannyUUID(UUID nannyUUID) { this.nannyUUID = nannyUUID; }

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }

    public int getCitizensNpcId() { return citizensNpcId; }
    public void setCitizensNpcId(int citizensNpcId) { this.citizensNpcId = citizensNpcId; }

    public String getHomeWorld() { return homeWorld; }
    public void setHomeWorld(String homeWorld) { this.homeWorld = homeWorld; }

    public double getHomeX() { return homeX; }
    public void setHomeX(double homeX) { this.homeX = homeX; }

    public double getHomeY() { return homeY; }
    public void setHomeY(double homeY) { this.homeY = homeY; }

    public double getHomeZ() { return homeZ; }
    public void setHomeZ(double homeZ) { this.homeZ = homeZ; }

    public int getHomeRadius() { return homeRadius; }
    public void setHomeRadius(int homeRadius) { this.homeRadius = homeRadius; }

    public String getLastWorld() { return lastWorld; }
    public void setLastWorld(String lastWorld) { this.lastWorld = lastWorld; }

    public double getLastX() { return lastX; }
    public void setLastX(double lastX) { this.lastX = lastX; }

    public double getLastY() { return lastY; }
    public void setLastY(double lastY) { this.lastY = lastY; }

    public double getLastZ() { return lastZ; }
    public void setLastZ(double lastZ) { this.lastZ = lastZ; }

    public LocalDateTime getLastOwnerSeen() { return lastOwnerSeen; }
    public void setLastOwnerSeen(LocalDateTime lastOwnerSeen) { this.lastOwnerSeen = lastOwnerSeen; }

    public boolean isDormant() { return dormant; }
    public void setDormant(boolean dormant) { this.dormant = dormant; }

    public MoodTier getMoodTier() { return moodTier; }
    public void setMoodTier(MoodTier moodTier) { this.moodTier = moodTier; }

    public MoodTier getCustomTone() {
        if (customTone == null || customTone == MoodTier.CUSTOM) return MoodTier.CARING;
        return customTone;
    }

    public void setCustomTone(MoodTier t) {
        this.customTone = (t == null || t == MoodTier.CUSTOM) ? MoodTier.CARING : t;
    }

    public List<UUID> getWards() { return wards; }
    public void setWards(List<UUID> wards) { this.wards = wards; }

    public ChestMode getChestMode() { return chestMode; }
    public void setChestMode(ChestMode chestMode) { this.chestMode = chestMode; }

    public List<String> getSelectedChests() { return selectedChests; }
    public void setSelectedChests(List<String> selectedChests) { this.selectedChests = selectedChests; }

    public CraftingMode getCraftingMode() { return craftingMode; }
    public void setCraftingMode(CraftingMode craftingMode) { this.craftingMode = craftingMode; }

    public boolean isBlockOtherCaregivers() { return blockOtherCaregivers; }
    public void setBlockOtherCaregivers(boolean blockOtherCaregivers) { this.blockOtherCaregivers = blockOtherCaregivers; }

    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }

    public ChatRespondTo getChatRespondTo() { return chatRespondTo; }
    public void setChatRespondTo(ChatRespondTo chatRespondTo) { this.chatRespondTo = chatRespondTo; }

    public List<UUID> getChatListedPlayers() { return chatListedPlayers; }
    public void setChatListedPlayers(List<UUID> chatListedPlayers) { this.chatListedPlayers = chatListedPlayers; }

    public ChatTier getChatTier() { return chatTier; }
    public void setChatTier(ChatTier chatTier) { this.chatTier = chatTier; }

    public boolean isFollowMode() { return followMode; }
    public void setFollowMode(boolean followMode) { this.followMode = followMode; }

    public int getChangeThreshold() { return changeThreshold; }
    public void setChangeThreshold(int v) { this.changeThreshold = v; }

    public int getFeedThreshold() { return feedThreshold; }
    public void setFeedThreshold(int v) { this.feedThreshold = v; }

    public int getHydrationThreshold() { return hydrationThreshold; }
    public void setHydrationThreshold(int v) { this.hydrationThreshold = v; }

    public ItemStack[] getPersonalInventory() {
        if (personalInventory == null) personalInventory = new ItemStack[18];
        return personalInventory;
    }
    public void setPersonalInventory(ItemStack[] inv) { this.personalInventory = inv; }

    public boolean isSeekEnabled() { return seekEnabled; }
    public void setSeekEnabled(boolean v) { this.seekEnabled = v; }

    public Map<String, Boolean> getCustomSettings() {
        if (customSettings == null) customSettings = new HashMap<>();
        return customSettings;
    }
    public void setCustomSettings(Map<String, Boolean> v) { this.customSettings = v; }

    public Map<UUID, Integer> getBehaviorScore() { return behaviorScore; }
    public Map<UUID, Integer> getBehaviorStreak() { return behaviorStreak; }
    public Map<UUID, Long> getBehaviorLastDecay() { return behaviorLastDecay; }
    public Map<UUID, Long> getPendingSummonExpiresAt() { return pendingSummonExpiresAt; }
    public Map<UUID, Map<String, Long>> getDisciplineCooldowns() { return disciplineCooldowns; }
    public Map<UUID, Long> getPraiseGraceUntil() { return praiseGraceUntil; }
    public Map<UUID, List<String>> getActivePersistentPunishments() { return activePersistentPunishments; }

    public Map<UUID, Boolean> getLockedArmor() {
        if (lockedArmor == null) lockedArmor = new HashMap<>();
        return lockedArmor;
    }
    public void setLockedArmor(Map<UUID, Boolean> v) { this.lockedArmor = v; }

    public List<String> getLockedRoomBlocks() {
        if (lockedRoomBlocks == null) lockedRoomBlocks = new ArrayList<>();
        return lockedRoomBlocks;
    }
    public void setLockedRoomBlocks(List<String> v) { this.lockedRoomBlocks = v; }
}
