package com.storynook.PlayerStatsManagement;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

import com.storynook.Plugin;

// import com.storynook.PlayerStatsManagement.LoadSelectedSounds;

public class LoadStats {
    private static Plugin plugin;
    
    public static void setPlugin(Plugin plugin) {
        LoadStats.plugin = plugin;
    }
    
    public static void loadPlayerStatsFromConfig(PlayerStats stats, FileConfiguration config) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<String, Object> Globalconfig = plugin.getGlobalConfig();
        // Load basic stats
        if (Globalconfig.get("Messing") != null && (Boolean) Globalconfig.get("Messing")){
            stats.setMessing(config.getBoolean("Messing"));
            stats.setBowels(config.getDouble("bowels", 0));
            stats.setBowelFillRate(config.getDouble("bowelFillRate", 0.07));
            
            if (Globalconfig.get("Incontinence") != null && (Boolean) Globalconfig.get("Incontinence")){
                stats.setBowelIncontinence(config.getDouble("bowelIncontinence", 1));
                stats.setBowelLockIncon(config.getBoolean("BowelLockIncon"));
            }
            else{
                stats.setBowelIncontinence( 1);
                stats.setBowelLockIncon(true);
            }
        }
        else{
            stats.setMessing(false);
            stats.setBowels(0);
            stats.setBowelFillRate(0.07);
            stats.setBowelIncontinence(1);
            stats.setBowelLockIncon(true);
        }
        if (Globalconfig.get("Accidents") != null && (Boolean) Globalconfig.get("Accidents")){
            stats.setDiaperWetness(config.getDouble("diaperWetness", 0));
            stats.setDiaperFullness(config.getDouble("diaperFullness", 0));
            stats.applyUnderwear(config.getInt("UnderwearType", 0), config.getInt("UnderwearDesign", 0));
            stats.setLayers(config.getInt("Layers", 0));
            stats.setParticleEffects(config.getInt("Stinklines", 0));
            stats.setBedwetting(config.getInt("Bedwetting", 0));
            stats.setRashPoints((float) config.getDouble("RashPoints", 0));
            stats.setshowunderwear(config.getBoolean("showunderwear", true));
            if (Globalconfig.get("Incontinence") != null && (Boolean) Globalconfig.get("Incontinence")){
                stats.setBladderIncontinence(config.getDouble("bladderIncontinence", 1));
                stats.setBladderLockIncon(config.getBoolean("BladderLockIncon"));
            }
            else{
                stats.setBladderIncontinence(1);
                stats.setBladderLockIncon(true);
            }
        }
        else{
            stats.setDiaperWetness(0);
            stats.setDiaperFullness(0);
            stats.applyUnderwear(0, 0);
            stats.setLayers(0);
            stats.setParticleEffects(0);
            stats.setBedwetting(0);
            stats.setRashPoints(0);
            stats.setshowunderwear( false);
            stats.setBladderIncontinence( 1);
            stats.setBladderLockIncon(true);
        }
        if (Globalconfig.get("ShowUndies") != null && (Boolean) Globalconfig.get("ShowUndies")){
            stats.setvisableUnderwear(config.getBoolean("visableUnderwear", false));
        }
        else{
            stats.setvisableUnderwear(false);
        }
        if (Globalconfig.get("MinFilltoggle") != null && (Boolean) Globalconfig.get("MinFilltoggle")){
            stats.setMinFill(config.getInt("MinFill", 30));
        }
        else{
            stats.setMinFill(30);
        }
        if (Globalconfig.get("Hardcore") != null && (Boolean) Globalconfig.get("Hardcore")){
            stats.setHardcore(config.getBoolean("Hardcore", false));
            stats.setHardcoreEnabledTime(config.getLong("hardcoreEnabledTime", -1L));
        }
        else{
            stats.setHardcore(false);
            stats.setHardcoreEnabledTime(-1L);
        }
        if (Globalconfig.get("Hypno") != null && (Boolean) Globalconfig.get("Hypno")) {
            stats.setHypnoPermission(config.getInt("hypnoPermission", 0));

            // Load new-format trigger list
            List<String> serializedTriggers = config.getStringList("hypnoTriggers");
            for (String s : serializedTriggers) {
                HypnoTrigger t = HypnoTrigger.deserialize(s);
                if (t != null && !t.isExpired()) {
                    stats.addHypnoTrigger(t);
                }
            }

            // Migration: convert old String fields if present
            long durationDays = 3L;
            Object durationObj = Globalconfig.get("Hypno_Duration_Days");
            if (durationObj != null) {
                durationDays = ((Number) durationObj).longValue();
            }
            String oldMessing = config.getString("messingHypnoWord", null);
            String oldWetting = config.getString("wettingHypnoWord", null);
            if (oldMessing != null && !oldMessing.isEmpty()) {
                stats.addHypnoTrigger(new HypnoTrigger(oldMessing, "messing",
                    LocalDateTime.now().plusDays(durationDays), "unknown"));
            }
            if (oldWetting != null && !oldWetting.isEmpty()) {
                stats.addHypnoTrigger(new HypnoTrigger(oldWetting, "wetting",
                    LocalDateTime.now().plusDays(durationDays), "unknown"));
            }
        }

        if (Globalconfig.get("Membership") != null && (Boolean) Globalconfig.get("Membership")) {
            com.storynook.nanny.crypto.CryptoService crypto = plugin.getCryptoService();
            stats.setNannyMembershipProvider(config.getString("nanny_membership_provider", ""));
            stats.setNannyMembershipEmail(crypto.decrypt(config.getString("nanny_membership_email", "")));
            stats.setNannyMembershipRefreshToken(crypto.decrypt(config.getString("nanny_membership_refresh_token", "")));
            stats.setNannyMembershipTier(config.getString("nanny_membership_tier", ""));
            stats.setNannyMembershipStatus(config.getString("nanny_membership_status", "UNLINKED"));
            stats.setNannyMembershipLastCheck(config.getString("nanny_membership_last_check", ""));
        }

        stats.setBladder(config.getDouble("bladder", 0));
        stats.setBladderFillRate(config.getDouble("bladderFillRate", 0.2));
        stats.setHydration(config.getInt("hydration", 100));
        stats.setUrgeToGo(config.getInt("urgeToGo", 1));
        stats.setOptin(config.getBoolean("Optin"));
        
        stats.setUI(config.getInt("UI", 1));
        stats.setfillbar(config.getBoolean("FillBar", false));
        stats.setshowfill(config.getBoolean("ShowFill", false));
        
        stats.setLaxEffectDuration(config.getInt("LaxEffectDuration", 0));
        stats.setLaxEffectDelay(config.getInt("LaxEffectDelay", 0));
        stats.setLaxEffectIntensity(config.getInt("LaxEffectIntensity", 0));

        stats.setcanhear(config.getInt("CanHear", 0));
        stats.setlethear(config.getInt("LetHear", 0));

        if (Globalconfig.get("Binding_Diapers") != null && (Boolean) Globalconfig.get("Binding_Diapers")){
            String diaperBondedTo = config.getString("DiaperBondedTo");
            if (diaperBondedTo != null && !diaperBondedTo.isEmpty()) {
                stats.setDiaperBondedTo(UUID.fromString(diaperBondedTo));
            } else {
                stats.setDiaperBondedTo(null);
            }
            // Load MyDiaperBindees
            if (config.contains("MyDiaperBindees")) {
                for (String uuid : config.getStringList("MyDiaperBindees")) {
                    stats.addMyDiaperBindee(UUID.fromString(uuid));
                }
            }
        }
        
        if (Globalconfig.get("Caregivers") != null && (Boolean) Globalconfig.get("Caregivers")){
            // Load Caregivers
            if (config.contains("Caregivers")) {
                for (String uuid : config.getStringList("Caregivers")) {
                    stats.addCaregiver(UUID.fromString(uuid));
                }
            }
            stats.setspecialCG(config.getBoolean("specialCG", false));
            stats.setAllCaregiver(config.getBoolean("AllCaregiver", false));
            if (config.contains("containedInCribId")) {
                String s = config.getString("containedInCribId");
                if (s != null && !s.isEmpty()) {
                    try {
                        stats.setContainedInCribId(UUID.fromString(s));
                    } catch (IllegalArgumentException ex) {
                        // corrupted UUID — leave null
                    }
                }
            }
        }
        else{
            stats.setspecialCG(false);
            stats.setAllCaregiver(false);
            stats.setContainedInCribId(null);
        }

        // Load StoredSounds
        LoadSelectedSounds loader = new LoadSelectedSounds(); // Create instance
        loader.loadStoredSounds(stats, config);
    }
}
