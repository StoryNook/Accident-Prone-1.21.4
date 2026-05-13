package com.storynook.PlayerStatsManagement;

import java.time.LocalDateTime;
import java.util.Map;

import org.bukkit.entity.Player;

import com.storynook.Plugin;

public class CreateDefaultStats {
    private static Plugin plugin;

    public static void setPlugin(Plugin plugin) {
        CreateDefaultStats.plugin = plugin;
    }
    public static void createDefaultPlayerStats(PlayerStats stats, Player player) {
        Map<String, Object> config = plugin.getGlobalConfig();
        double defaultBladderRate = plugin.getConfig().getDouble("Bladder_Fill_Rate", 0.2);
        double defaultBowelRate = plugin.getConfig().getDouble("Bowel_Fill_Rate", 0.14);
        
        // Set default values
        if (config.get("Messing") != null && (Boolean) config.get("Messing")){
            stats.setMessing(false);
            stats.setBowels(0);
            stats.setBowelFillRate(defaultBowelRate);
            if (config.get("Incontinence") != null && (Boolean) config.get("Incontinence")){
                stats.setBowelIncontinence(1);
                stats.setBowelLockIncon(false);
            }
        }  

        if (config.get("Accidents") != null && (Boolean) config.get("Accidents")){
            stats.setDiaperWetness(0);
            stats.setDiaperFullness(0);
            stats.applyUnderwear(0, 0);
            stats.setLayers(0);
            stats.setParticleEffects(0);
            stats.setBedwetting(0);
            stats.setRashPoints(0);
            stats.setshowunderwear(true);
            if (config.get("Incontinence") != null && (Boolean) config.get("Incontinence")){
                stats.setBladderIncontinence(1);
                stats.setBladderLockIncon(false);
            }
        }
        if (config.get("ShowUndies") != null && (Boolean) config.get("ShowUndies")){
            stats.setvisableUnderwear(false);
        }
        if (config.get("Diapers") != null && (Boolean) config.get("Diapers")){
            stats.setHardcore(false);
        }

        if (config.get("Hypno") != null && (Boolean) config.get("Hypno")) {
            stats.setHypnoPermission(0);
            // hypnoTriggers defaults to empty ArrayList in PlayerStats
        }

        if (config.get("Membership") != null && (Boolean) config.get("Membership")) {
            stats.setNannyMembershipProvider("");
            stats.setNannyMembershipEmail("");
            stats.setNannyMembershipRefreshToken("");
            stats.setNannyMembershipTier("");
            stats.setNannyMembershipStatus("UNLINKED");
            stats.setNannyMembershipLastCheck("");
        }

        stats.setBladder(0);
        
        stats.setBladderFillRate(defaultBladderRate); 
        stats.setHydration(100);
        stats.setUrgeToGo(1);
        stats.setOptin(false);
        stats.setAllCaregiver(false);
        stats.setspecialCG(false);
        stats.setContainedInCribId(null);
        stats.setLaxEffectDuration(0);
        stats.setLaxEffectDelay(0);
        stats.setLaxEffectIntensity(0);
        stats.setMinFill(30);
        
        stats.setHardcoreEnabledTime(-1);
        
        stats.setfillbar(false);
        stats.setshowfill(false);
        stats.setUI(1);
        stats.setStoredSounds(plugin.soundConfig);
        stats.setlethear(0);
        stats.setcanhear(0);
        
        SavePlayerStats savePlayerStats = new SavePlayerStats();
        // Save the default stats to a new file
        savePlayerStats.savePlayerStats(player);
    }
}
