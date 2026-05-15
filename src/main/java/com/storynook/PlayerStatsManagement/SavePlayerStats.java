package com.storynook.PlayerStatsManagement;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.storynook.Plugin;

public class SavePlayerStats {
    
    private static Plugin plugin;
    public static void setPlugin(Plugin plugin)
    {
        SavePlayerStats.plugin = plugin;
    }
    public static void savePlayerStats(Player player) {
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    String formattedDateTime = now.format(formatter);
    Map<String, Object> Globalconfig = plugin.getGlobalConfig();
    // getLogger().info("Save Player Stats loaded");
    UUID playerUUID = player.getUniqueId();
    PlayerStats stats = plugin.playerStatsMap.get(playerUUID);
    if (stats != null) {
        // Save stats to a file or database (not implemented in this example)
        File playerFile = plugin.getPlayerFile(playerUUID);
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        // Save stats to the YAML file
        config.set("PlayerName", player.getName());
        config.set("LastLogin", formattedDateTime);
        config.set("Optin", stats.getOptin());
        config.set("bladder", stats.getBladder());

        if (Globalconfig.get("Messing") != null && (Boolean) Globalconfig.get("Messing")){
            config.set("Messing", stats.getMessing());
            config.set("bowels", stats.getBowels());
            config.set("bowelIncontinence", stats.getBowelIncontinence());
            config.set("bowelFillRate", stats.getBowelFillRate());
            
        }
        if (Globalconfig.get("Accidents") != null && (Boolean) Globalconfig.get("Accidents")){
            config.set("diaperWetness", stats.getDiaperWetness());
            config.set("diaperFullness", stats.getDiaperFullness());
            config.set("UnderwearType", stats.getUnderwearType());
            config.set("UnderwearDesign", stats.getUnderwearDesign());
            config.set("Layers", stats.getLayers());
            config.set("Stinklines", stats.getParticleEffects());
            config.set("Bedwetting", stats.getBedwetting());
            config.set("RashPoints", stats.getRashPoints());
            config.set("showunderwear", stats.getshowunderwear());
        }
        if (Globalconfig.get("ShowUndies") != null && (Boolean) Globalconfig.get("ShowUndies")){
            config.set("visableUnderwear", stats.getvisableUnderwear());
        }

        config.set("bladderIncontinence", stats.getBladderIncontinence());
        config.set("bladderFillRate", stats.getBladderFillRate());
        config.set("hydration", stats.getHydration());
        config.set("urgeToGo", stats.getUrgeToGo());
        
        config.set("specialCG", stats.getspecialCG());
        config.set("AllCaregiver", stats.getAllCaregiver());
        config.set("UI", stats.getUI());
        
        config.set("LaxEffectDuration", stats.getLaxEffectDuration());
        config.set("LaxEffectDelay", stats.getLaxEffectDelay());
        config.set("LaxEffectIntensity", stats.getLaxEffectIntensity());
        
        config.set("MinFill", stats.getMinFill());
        config.set("Hardcore", stats.getHardcore());
        config.set("hardcoreEnabledTime", stats.getHardcoreEnabledTime());
        
        config.set("BladderLockIncon", stats.getBladderLockIncon());
        config.set("BowelLockIncon", stats.getBowelLockIncon());
        config.set("ShowFill", stats.getshowfill());
        config.set("FillBar", stats.getfillbar());
        
        config.set("CanHear", stats.getcanhear());
        config.set("LetHear", stats.getlethear());

        if (Globalconfig.get("Binding_Diapers") != null && (Boolean) Globalconfig.get("Binding_Diapers")){
            if (stats.getDiaperBondedTo() != null) {config.set("DiaperBondedTo", stats.getDiaperBondedTo().toString());}
            else if (stats.getDiaperBondedTo() == null) {config.set("DiaperBondedTo", "");}
            List<String> uuidMyDiaperBindees = (stats.getMyDiaperBindees() != null) ? 
                stats.getMyDiaperBindees().stream()
                .map(UUID::toString) // Convert UUID to string
                .collect(Collectors.toList()) : new ArrayList<>();
            config.set("MyDiaperBindees", uuidMyDiaperBindees);
        }

        if (Globalconfig.get("Hypno") != null && (Boolean) Globalconfig.get("Hypno")) {
            config.set("hypnoPermission", stats.getHypnoPermission());
            List<String> serializedTriggers = new ArrayList<>();
            for (HypnoTrigger t : stats.getHypnoTriggers()) {
                serializedTriggers.add(t.serialize());
            }
            config.set("hypnoTriggers", serializedTriggers);
        }

        if (Globalconfig.get("Membership") != null && (Boolean) Globalconfig.get("Membership")) {
            com.storynook.nanny.crypto.CryptoService crypto = plugin.getCryptoService();
            config.set("nanny_membership_provider", stats.getNannyMembershipProvider());
            config.set("nanny_membership_email",
                    crypto.encrypt(stats.getNannyMembershipEmail() == null ? "" : stats.getNannyMembershipEmail()));
            config.set("nanny_membership_refresh_token",
                    crypto.encrypt(stats.getNannyMembershipRefreshToken() == null ? "" : stats.getNannyMembershipRefreshToken()));
            config.set("nanny_membership_tier", stats.getNannyMembershipTier());
            config.set("nanny_membership_status", stats.getNannyMembershipStatus());
            config.set("nanny_membership_last_check", stats.getNannyMembershipLastCheck());
        }

        if (Globalconfig.get("Nanny") != null && (Boolean) Globalconfig.get("Nanny")) {
            config.set("diaperPunishment", stats.isDiaperPunishment());
            config.set("diaperPunishmentExpiresAtTick", stats.getDiaperPunishmentExpiresAtTick());
            config.set("diaperPunishmentRemainingViolations", stats.getDiaperPunishmentRemainingViolations());
            config.set("diaperPunishmentScoreAtStart", stats.getDiaperPunishmentScoreAtStart());
            config.set("diaperPunishmentNannyUUID",
                    stats.getDiaperPunishmentNannyUUID() == null ? "" : stats.getDiaperPunishmentNannyUUID().toString());
            config.set("diaperPunishmentEscalated", stats.isDiaperPunishmentEscalated());
        }

        if (Globalconfig.get("Caregivers") != null && (Boolean) Globalconfig.get("Caregivers")) {
            List<String> uuidCaregiver = (stats.getCaregivers() != null) ?
                stats.getCaregivers().stream()
                .map(UUID::toString) // Convert UUID to string
                .collect(Collectors.toList()) : new ArrayList<>();
            config.set("Caregivers", uuidCaregiver);
            UUID cribId = stats.getContainedInCribId();
            config.set("containedInCribId", cribId == null ? null : cribId.toString());
        }

        ConfigurationSection storedSoundsConfig = config.createSection("StoredSounds");

        // Iterate through all categories in StoredSounds
        for (Map.Entry<String, Map<String, Boolean>> categoryEntry : stats.getStoredSounds().entrySet()) {
            String categoryName = categoryEntry.getKey();
            ConfigurationSection categoryConfig = storedSoundsConfig.createSection(categoryName);
            
            // Get the sounds map for the current category
            Map<String, Boolean> sounds = categoryEntry.getValue();
            
            // Add each sound to the category configuration
            for (Map.Entry<String, Boolean> soundEntry : sounds.entrySet()) {
                String soundName = soundEntry.getKey();
                boolean isEnabled = soundEntry.getValue();
                categoryConfig.set(soundName, isEnabled);
            }
        }
      
      try {
        config.save(playerFile);
        // getLogger().info("Saved stats for player " + player.getName());
      } catch (IOException e) {
        // getLogger().warning("Failed to save stats for player " + player.getName());
        e.printStackTrace();
      }
    }
  }
}
