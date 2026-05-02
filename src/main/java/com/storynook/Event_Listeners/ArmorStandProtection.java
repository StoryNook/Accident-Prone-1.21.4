package com.storynook.Event_Listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import com.storynook.Plugin;

public class ArmorStandProtection implements Listener {
    private final Plugin plugin;

    public ArmorStandProtection(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        
        // Check if the armor stand has a custom name
        if (stand.getCustomName() != null) {
            String name = stand.getCustomName();
            
            // Cancel the event if the armor stand has any of these names
            if (name.equals("ToiletArmor") || 
                name.equals("Crib") || 
                name.equals("Chair") || 
                name.contains("Pail_")) {
                
                event.setCancelled(true);
            }
        }
    }
}