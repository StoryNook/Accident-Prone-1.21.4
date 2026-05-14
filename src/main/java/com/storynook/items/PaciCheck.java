package com.storynook.items;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.storynook.PaciRegistry;

public final class PaciCheck {
    private PaciCheck() {}

    public static boolean isWearingPaci(Player player) {
        if (player == null) return false;
        ItemStack helmet = player.getInventory().getHelmet();
        return PaciRegistry.getWornFromHelmet(helmet).isPresent();
    }

    public static Optional<PaciRegistry.PaciDef> getWornPaci(Player player) {
        if (player == null) return Optional.empty();
        return PaciRegistry.getWornFromHelmet(player.getInventory().getHelmet());
    }
}
