package com.storynook.Integrations;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 *
 * <p>VentureChat fires its event on an async scheduler thread. fireTriggers
 * touches NPC entity state (locations, scheduler tasks) which is main-thread
 * only — we bounce via {@code Bukkit.getScheduler().runTask} to avoid silent
 * async-state-access exceptions that VentureChat's handler wrapper swallows.
 *
 * <p>Also strips chat color codes from the message body so keyword and
 * name-mention matching in NannyChatEngine.pickCategory works against the
 * plain text the user actually typed.
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
        String chat = ChatColor.stripColor(e.getChat());
        if (chat == null) return;
        chat = chat.trim();
        if (chat.isEmpty()) return;
        final String finalChat = chat;
        Bukkit.getScheduler().runTask(plugin,
                () -> engine.fireTriggers(speaker, finalChat));
    }
}
