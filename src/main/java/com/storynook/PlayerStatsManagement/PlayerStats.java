package com.storynook.PlayerStatsManagement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

import com.storynook.Plugin;

public class PlayerStats {
    private UUID playerUUID;
    private UUID DiaperBondedTo;
    private double bladder = 0;
    private double bowels = 0;
    private double diaperWetness = 0;
    private double diaperFullness = 0;
    private double bladderIncontinence = 1; // 1-10
    private double bowelIncontinence = 1; // 1-10
    private double bladderFillRate, bowelFillRate; // Default fill rate
    private double hydration = 100; // Starts fully hydrated
    private int urgeToGo = 1;
    private int UnderwearType, UnderwearDesign, layers, ParticleEffects, UI, Bedwetting, LaxEffectDuration, LaxEffectDelay, LaxEffectIntensity, DurEffectDuration, DurEffectDelay, canhear, lethear = 0;
    private float rashPoints = 0;
    private int minFill = 30;
    private boolean optin, messing, hardcore, BladderLockIncon, BowelLockIncon, AllCaregiver, specialCG, visableUnderwear, fillbar, showfill, showunderwear;
    private long hardcoreEnabledTime = -1;
    private Map<String, Map<String, Boolean>> StoredSounds = new HashMap<>();
    private List<UUID> caregivers = new ArrayList<>();
    private List<UUID> MyDiaperBindees = new ArrayList<>();
    private List<HypnoTrigger> hypnoTriggers = new ArrayList<>();
    private int hypnoPermission = 0;
    private boolean diaperPunishment = false;
    private long    diaperPunishmentExpiresAtTick = 0L;
    private int     diaperPunishmentRemainingViolations = 3;
    private int     diaperPunishmentScoreAtStart = 0;
    private UUID    diaperPunishmentNannyUUID = null;
    private boolean diaperPunishmentEscalated = false;
    private String nannyMembershipProvider = "";
    private String nannyMembershipEmail = "";
    private String nannyMembershipRefreshToken = "";
    private String nannyMembershipTier = "";
    private String nannyMembershipStatus = "UNLINKED";
    private String nannyMembershipLastCheck = "";
    private UUID containedInCribId = null;

    private static final int MAX_VALUE = 100;

    /**
     * Test-only no-arg constructor. Leaves {@code plugin} null. Methods
     * that depend on the plugin reference (notably {@link #isCaregiver})
     * are guarded against this; do not call other plugin-dependent methods
     * on a no-arg-constructed instance.
     */
    public PlayerStats() {}

    public PlayerStats(UUID playerUUID, Plugin plugin) {
        this.playerUUID = playerUUID;
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();
        this.bladderFillRate = config.getDouble("Bladder_Fill_Rate", 0.2);
        this.bowelFillRate = config.getDouble("Bowel_Fill_Rate", 0.14);
    }
    private Plugin plugin;

    public Map<String, Map<String, Boolean>> getStoredSounds() {
        return StoredSounds;
    }
    public void setStoredSounds(Map<String, Map<String, Boolean>> sounds) {
        this.StoredSounds = sounds;
    }
    
    public void toggleSound(String categoryName, String soundName) {
        Map<String, Boolean> sounds = StoredSounds.get(categoryName);
        if (sounds != null && sounds.containsKey(soundName)) {
            boolean currentStatus = sounds.get(soundName);
            sounds.put(soundName, !currentStatus);  // Toggle the status
        }
    }
    public String getRandomSoundFromCategory(String categoryName) {
        Map<String, Boolean> sounds = StoredSounds.get(categoryName);
        if (sounds == null || sounds.isEmpty()) {
            return null;  // No sounds in this category
        }
        
        List<String> enabledSounds = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : sounds.entrySet()) {
            if (entry.getValue()) {  // Only add enabled sounds
                enabledSounds.add(entry.getKey());
            }
        }
        
        if (enabledSounds.isEmpty()) {
            return null;  // No enabled sounds in this category
        }
        
        Random random = new Random();
        int index = random.nextInt(enabledSounds.size());
        return enabledSounds.get(index);
    }

    // Caregiver Settings
    public List<UUID> getCaregivers() {return caregivers;}
    public void addCaregiver(UUID caregiverUUID) { if(!caregivers.contains(caregiverUUID)){caregivers.add(caregiverUUID);}}
    public void removeCaregiver(UUID caregiverUUID) { caregivers.remove(caregiverUUID);}
    public boolean isCaregiver(UUID uuid, Boolean Specified) {
        if (plugin == null) {
            // No-arg constructor was used (test-only path); fall back to
            // explicit-list-only check, no wildcard resolution.
            return caregivers != null && caregivers.contains(uuid);
        }
        PlayerStats triggerStats = plugin.getPlayerStats(uuid);
        if (caregivers != null && caregivers.contains(uuid)) {
            return true;
        } else if (Specified && AllCaregiver && triggerStats.getspecialCG()){
            return true;
        }
        else {return false;}
    }

    //Diaper Binding
    public List<UUID> getMyDiaperBindees() { return MyDiaperBindees; }
    public void addMyDiaperBindee(UUID playerUUID) {
        if (!MyDiaperBindees.contains(playerUUID)) {
            MyDiaperBindees.add(playerUUID);
        }
    }
    public void removeMyDiaperBindee(UUID playerUUID) {
        MyDiaperBindees.remove(playerUUID);
    }
    public boolean isMyDiaperBindeee(UUID uuid) {
        return MyDiaperBindees.contains(uuid);
    }

    public UUID getDiaperBondedTo() {return DiaperBondedTo;}
    public void setDiaperBondedTo(UUID BindTo) {DiaperBondedTo = BindTo;}

    public UUID getContainedInCribId() { return containedInCribId; }
    public void setContainedInCribId(UUID id) { this.containedInCribId = id; }

    //Quick Boolean Settings
    public int getlethear() {return lethear;}
    public void setlethear(int number) {lethear = number;}

    public int getcanhear() {return canhear;}
    public void setcanhear(int number) {canhear = number;}

    public boolean getOptin() {return optin;}
    public void setOptin(boolean bool) {optin = bool;}

    public boolean getshowfill() {return showfill;}
    public void setshowfill(boolean bool) {showfill = bool;}

    public boolean getshowunderwear() {return showunderwear;}
    public void setshowunderwear(boolean bool) {showunderwear = bool;}

    public boolean getfillbar() {return fillbar;}
    public void setfillbar(boolean bool) {fillbar = bool;}

    public boolean getvisableUnderwear() {return visableUnderwear;}
    public void setvisableUnderwear(boolean bool) {visableUnderwear = bool;}

    public int getParticleEffects() {return ParticleEffects;}
    public void setParticleEffects(int number) {ParticleEffects = number;}

    public boolean getspecialCG() {return specialCG;}
    public void setspecialCG(boolean bool) {specialCG = bool;}

    public boolean getAllCaregiver() {return AllCaregiver;}
    public void setAllCaregiver(boolean bool) {AllCaregiver = bool;}

    public boolean getBladderLockIncon() {return BladderLockIncon;}
    public void setBladderLockIncon(boolean bool) {BladderLockIncon = bool;}

    public boolean getBowelLockIncon() {return BowelLockIncon;}
    public void setBowelLockIncon(boolean bool) {BowelLockIncon = bool;}

    public boolean getMessing() {return messing;}
    public void setMessing(boolean bool) {messing = bool;}

    public boolean getHardcore() {return hardcore;}
    public void setHardcore(boolean bool) {hardcore = bool;}

    //Settings Menu Options
    public int getUI() {return UI;}
    public void setUI(int number) {UI = number;}

    public int getBedwetting() {return Bedwetting;}
    public void setBedwetting(int number) {Bedwetting = number;}

    //Hardcore timer
    public long getHardcoreEnabledTime() {return hardcoreEnabledTime;}
    public void setHardcoreEnabledTime(long time) {this.hardcoreEnabledTime = time;}

    //Bladder, Bowels, Diaper Fill, etc.
    public double getBladder() { return bladder; }
    public void setBladder(double amount) { bladder = Math.max(0, amount); }
    public void increaseBladder(double amount) { bladder = Math.min(bladder + amount, MAX_VALUE); }

    public double getBowels() { return bowels; }
    public void setBowels(double amount) { bowels = Math.max(0, amount); }
    public void increaseBowels(double amount) { bowels = Math.min(bowels + amount, MAX_VALUE); }

    public double getDiaperWetness() { return diaperWetness; }
    public void setDiaperWetness(double amount) { diaperWetness = Math.max(0, amount); }
    public void increaseDiaperWetness(double amount) { diaperWetness = diaperWetness + amount; }

    public double getDiaperFullness() { return diaperFullness; }
    public void setDiaperFullness(double amount) { diaperFullness = Math.max(0, amount); } 
    public void increaseDiaperFullness(double amount) { diaperFullness = diaperFullness + amount;}

    public double getBladderIncontinence() { return bladderIncontinence; }
    public void increaseBladderIncontinence(double amount) { bladderIncontinence = Math.min(bladderIncontinence + amount, 10); }
    public void decreaseBladderIncontinence(double amount) { bladderIncontinence = Math.max(bladderIncontinence - amount, 1); }
    public void setBladderIncontinence(double amount) { bladderIncontinence = Math.max(1, Math.min(10, amount)); }

    public double getBowelIncontinence() { return bowelIncontinence; }
    public void increaseBowelIncontinence(double amount) { bowelIncontinence = Math.min(bowelIncontinence + amount, 10); }
    public void decreaseBowelIncontinence(double amount) { bowelIncontinence = Math.max(bowelIncontinence - amount, 1); }
    public void setBowelIncontinence(double amount) { bowelIncontinence = Math.max(1, Math.min(10, amount)); }

    public int getLaxEffectDuration() { return LaxEffectDuration; }
    public void increaseLaxEffectDuration(int amount) { LaxEffectDuration = Math.min(LaxEffectDuration + amount, 1000); }
    public void decreaseLaxEffectDuration(int amount) { LaxEffectDuration = Math.max(LaxEffectDuration - amount, 0); }
    public void setLaxEffectDuration(int amount) { LaxEffectDuration = Math.max(0, amount); }

    public int getDurEffectDuration() { return DurEffectDuration; }
    public void increaseDurEffectDuration(int amount) { DurEffectDuration = Math.min(DurEffectDuration + amount, 1000); }
    public void decreaseDurEffectDuration(int amount) { DurEffectDuration = Math.max(DurEffectDuration - amount, 0); }
    public void setDurEffectDuration(int amount) { DurEffectDuration = Math.max(0, amount); }

    public int getLaxEffectDelay() { return LaxEffectDelay; }
    public void setLaxEffectDelay(int amount) { LaxEffectDelay = Math.max(0, amount); }

    public int getLaxEffectIntensity() { return LaxEffectIntensity; }
    public void increaseLaxEffectIntensity(int amount) { LaxEffectIntensity = Math.min(LaxEffectIntensity + amount, 10); }
    public void setLaxEffectIntensity(int amount) { LaxEffectIntensity = Math.max(0, Math.min(10, amount)); }

    public int getLDurEffectDelay() { return DurEffectDelay; }
    public void setDurEffectDelay(int amount) { DurEffectDelay = Math.max(0, amount); }
    
    public float getRashPoints() { return rashPoints; }
    public void setRashPoints(float amount) { rashPoints = Math.max(0, amount); }
    
    public double getBladderFillRate() { return bladderFillRate; }
    public void setBladderFillRate(double rate) { bladderFillRate = Math.max(rate, 0.001); }

    public double getBowelFillRate() { return bowelFillRate; }
    public void setBowelFillRate(double rate) { bowelFillRate = Math.max(rate, 0.001); }

    public double getMinFill() { return minFill; }
    public void setMinFill(int threshold) { minFill = Math.max(0, Math.min(100, threshold)); }

    public double getHydration() { return hydration; }
    public void setHydration(double amount) { hydration = amount; }
    public void increaseHydration(double amount) { hydration = hydration + amount; }
    public void decreaseHydration(double amount) { hydration = Math.max(hydration - amount, 0); }

    public int getUrgeToGo() { return urgeToGo; }
    public void setUrgeToGo(int amount) { urgeToGo = Math.min(amount, 100);}
    public void increaseUrgeToGo(int amount) { urgeToGo = Math.min(urgeToGo + amount, 100); }
    
    public int getUnderwearType() { return UnderwearType; }
    public int getUnderwearDesign() { return UnderwearDesign; }
    public void setUnderwearType(int type) { applyUnderwear(type, 0); }
    public void applyUnderwear(int type, int designId) {
        UnderwearType = Math.max(0, Math.min(type, 3));
        UnderwearDesign = Math.max(0, designId);
    }

    public int getLayers() { return layers; }
    public void setLayers(int type) { layers = type; }
    public void increaseLayers(int type) { layers = Math.min(layers + type, 4); }
    public void decreaseLayers(int type) { layers = Math.max(layers - type, 0);}

    public void unlockIncon(String type) {
        switch (type.toLowerCase()) {
            case "bladder":
                setBladderLockIncon(false);
                break;
            case "bowel":
                setBowelLockIncon(false);
                break;
            case "both":
                setBladderLockIncon(false);
                setBowelLockIncon(false);
                break;
            default:
                System.out.println("Invalid incontinence type specified.");
        }
    }
    // Hypnosis triggers
    public List<HypnoTrigger> getHypnoTriggers() { return hypnoTriggers; }

    public void addHypnoTrigger(HypnoTrigger trigger) {
        hypnoTriggers.add(trigger);
    }

    public void removeHypnoTrigger(HypnoTrigger trigger) {
        hypnoTriggers.remove(trigger);
    }

    public int getHypnoPermission() { return hypnoPermission; }
    public void setHypnoPermission(int permission) { hypnoPermission = permission; }
    public boolean isDiaperPunishment() { return diaperPunishment; }
    public void setDiaperPunishment(boolean v) { this.diaperPunishment = v; }
    public long getDiaperPunishmentExpiresAtTick() { return diaperPunishmentExpiresAtTick; }
    public void setDiaperPunishmentExpiresAtTick(long v) { this.diaperPunishmentExpiresAtTick = v; }
    public int getDiaperPunishmentRemainingViolations() { return diaperPunishmentRemainingViolations; }
    public void setDiaperPunishmentRemainingViolations(int v) { this.diaperPunishmentRemainingViolations = v; }
    public int getDiaperPunishmentScoreAtStart() { return diaperPunishmentScoreAtStart; }
    public void setDiaperPunishmentScoreAtStart(int v) { this.diaperPunishmentScoreAtStart = v; }
    public UUID getDiaperPunishmentNannyUUID() { return diaperPunishmentNannyUUID; }
    public void setDiaperPunishmentNannyUUID(UUID v) { this.diaperPunishmentNannyUUID = v; }
    public boolean isDiaperPunishmentEscalated() { return diaperPunishmentEscalated; }
    public void setDiaperPunishmentEscalated(boolean v) { this.diaperPunishmentEscalated = v; }
    public String getNannyMembershipProvider() { return nannyMembershipProvider; }
    public void setNannyMembershipProvider(String v) { nannyMembershipProvider = v == null ? "" : v; }
    public String getNannyMembershipEmail() { return nannyMembershipEmail; }
    public void setNannyMembershipEmail(String v) { nannyMembershipEmail = v == null ? "" : v; }
    public String getNannyMembershipRefreshToken() { return nannyMembershipRefreshToken; }
    public void setNannyMembershipRefreshToken(String v) { nannyMembershipRefreshToken = v == null ? "" : v; }
    public String getNannyMembershipTier() { return nannyMembershipTier; }
    public void setNannyMembershipTier(String v) { nannyMembershipTier = v == null ? "" : v; }
    public String getNannyMembershipStatus() { return nannyMembershipStatus; }
    public void setNannyMembershipStatus(String v) { nannyMembershipStatus = v == null ? "UNLINKED" : v; }
    public String getNannyMembershipLastCheck() { return nannyMembershipLastCheck; }
    public void setNannyMembershipLastCheck(String v) { nannyMembershipLastCheck = v == null ? "" : v; }

    public boolean hasActiveHypnoTriggers() {
        for (HypnoTrigger t : hypnoTriggers) {
            if (!t.isExpired()) return true;
        }
        return false;
    }

    public LocalDateTime getMaxHypnoExpiry() {
        LocalDateTime max = null;
        for (HypnoTrigger t : hypnoTriggers) {
            if (!t.isExpired()) {
                if (max == null || t.getExpiry().isAfter(max)) {
                    max = t.getExpiry();
                }
            }
        }
        return max;
    }

    public void cleanExpiredTriggers() {
        List<HypnoTrigger> toRemove = new ArrayList<>();
        for (HypnoTrigger t : hypnoTriggers) {
            if (t.isExpired()) toRemove.add(t);
        }
        hypnoTriggers.removeAll(toRemove);
    }
}

