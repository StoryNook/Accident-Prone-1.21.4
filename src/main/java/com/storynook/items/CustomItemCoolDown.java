package com.storynook.items;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitRunnable;

public class CustomItemCoolDown {
    public static Set<UUID> cooldown = new HashSet<>();

    public static void Cooldown(final Player actor, final int cooldownTime) {
        if (cooldown.contains(actor.getUniqueId())) {
            return;
        }

        // Apply cooldown
        cooldown.add(actor.getUniqueId());
        BukkitRunnable task = new BukkitRunnable() {
            private int ticksLeft = 20 * cooldownTime; // Convert seconds to ticks

            @Override
            public void run() {
                if (ticksLeft <= 0) {
                    cooldown.remove(actor.getUniqueId());
                    this.cancel();
                } else {
                    ticksLeft--;
                }
            }
        };

        task.runTaskTimer(JavaPlugin.getProvidingPlugin(CustomItemCoolDown.class), 0L, 1L); // Run every tick
    }
}