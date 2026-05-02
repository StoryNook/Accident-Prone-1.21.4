package com.storynook.Integrations;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Thin façade over PlaceholderAPI's static placeholder resolver.
 * Callers MUST gate every invocation on Plugin.PlaceholderAPI being true,
 * otherwise this class will fail to load when PlaceholderAPI isn't installed.
 */
public class PlaceholderAPIHook {
    public static String setPlaceholders(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
