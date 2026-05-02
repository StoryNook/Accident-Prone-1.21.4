package com.storynook.Integrations;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.storynook.Plugin;
import com.storynook.Event_Listeners.Hypno;

import mineverse.Aust1n46.chat.api.events.VentureChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VentureChatHook implements Listener {
    private static Plugin plugin;
    public static Map<UUID, String> playerCurrentRoom = new HashMap<>();

    public VentureChatHook(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVentureChat(VentureChatEvent e) {
        Hypno.fireTriggers(e.getChat());
    }
}
