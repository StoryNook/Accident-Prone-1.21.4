package com.storynook.Integrations;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.storynook.Plugin;
import com.storynook.nanny.NannyChatEngine;
import com.storynook.nanny.NannyManager;

import mineverse.Aust1n46.chat.api.events.VentureChatEvent;

/**
 * Bridges VentureChat to {@link NannyChatEngine#fireTriggers(Player, String)}.
 * Registered only when VentureChat is detected at startup
 * (see {@code Plugin.onEnable}).
 */
public class NannyVentureChatHook implements Listener {

    private final Plugin plugin;

    public NannyVentureChatHook(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVentureChat(VentureChatEvent e) {
        if (e == null) return;
        Player speaker = e.getMineverseChatPlayer() != null
                ? e.getMineverseChatPlayer().getPlayer() : null;
        if (speaker == null) return;
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return;
        NannyChatEngine engine = mgr.getChatEngine();
        if (engine == null) return;
        engine.fireTriggers(speaker, e.getChat());
    }
}
