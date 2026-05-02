package com.storynook.Event_Listeners;

import com.storynook.Plugin;
import com.storynook.items.CustomItemCoolDown;
import com.storynook.items.CustomItemCheck;

import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChangingTable implements Listener{
    @SuppressWarnings("unused")
    private JavaPlugin plugin;
    public ChangingTable(Plugin plugin) {
        this.plugin = plugin;
    }
}