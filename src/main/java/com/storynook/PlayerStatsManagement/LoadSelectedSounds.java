package com.storynook.PlayerStatsManagement;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import com.storynook.Plugin;

public class LoadSelectedSounds {
    private static Plugin plugin;
    
        public static void setPlugin(Plugin plugin) {
            LoadSelectedSounds.plugin = plugin;
        }
    public void loadStoredSounds(PlayerStats stats, FileConfiguration config) {
    if (config.contains("StoredSounds")) {
        ConfigurationSection storedSounds = config.getConfigurationSection("StoredSounds");
        Map<String, Map<String, Boolean>> soundsMap = new HashMap<>();
        
        for (String category : plugin.soundConfig.keySet()) {
            Map<String, Boolean> soundMap = plugin.soundConfig.get(category);
            ConfigurationSection categorySection = storedSounds.getConfigurationSection(category);
            
            if (categorySection == null) {
                categorySection = storedSounds.createSection(category);
            }
            
            Map<String, Boolean> categorySounds = new HashMap<>();
            for (Map.Entry<String, Boolean> entry : soundMap.entrySet()) {
                String soundName = entry.getKey();
                Boolean defaultValue = entry.getValue();
                
                if (!categorySection.contains(soundName)) {
                    categorySection.set(soundName, defaultValue);
                    categorySounds.put(soundName, defaultValue);
                } else {
                    Object existingValue = categorySection.get(soundName);
                    categorySounds.put(soundName, plugin.parseBooleanValue(existingValue, defaultValue));
                }
            }
            soundsMap.put(category, categorySounds);
        }
        
        stats.setStoredSounds(soundsMap);
    } else {
        createDefaultStoredSounds(stats, config);
    }
}
private void createDefaultStoredSounds(PlayerStats stats, FileConfiguration config) {
    ConfigurationSection storedSounds = config.createSection("StoredSounds");
    Map<String, Map<String, Boolean>> soundsMap = new HashMap<>();
    
    for (String category : plugin.soundConfig.keySet()) {
        Map<String, Boolean> soundMap = plugin.soundConfig.get(category);
        ConfigurationSection categorySection = storedSounds.createSection(category);
        
        Map<String, Boolean> categorySounds = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : soundMap.entrySet()) {
            String soundName = entry.getKey();
            Boolean defaultValue = entry.getValue();
            
            categorySection.set(soundName, defaultValue);
            categorySounds.put(soundName, defaultValue);
        }
        soundsMap.put(category, categorySounds);
    }
    
    stats.setStoredSounds(soundsMap);
}
}
