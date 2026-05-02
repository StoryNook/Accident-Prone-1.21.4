package com.storynook.nanny;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.storynook.Plugin;

/**
 * Cancels door/lever interactions and block breaks on locked-room blocks
 * for any ward whose Nanny has {@link Capability#ROOM_LOCKDOWN} permission
 * AND the block is in {@code data.getLockedRoomBlocks()}.
 *
 * <p>Block keys are stored as {@code "world,x,y,z"} strings.
 */
public class RoomLockListener implements Listener {

    private final Plugin plugin;

    public RoomLockListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (isLockedForPlayer(event.getPlayer().getUniqueId(), event.getClickedBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "Nanny says no.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLockedForPlayer(event.getPlayer().getUniqueId(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "Nanny says no.");
        }
    }

    private boolean isLockedForPlayer(UUID playerUUID, Block block) {
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return false;
        String key = blockKey(block);
        for (NannyData data : mgr.getAllNannies().values()) {
            boolean isWard = data.getOwnerUUID().equals(playerUUID)
                    || data.getWards().contains(playerUUID);
            if (!isWard) continue;
            if (!NannyPolicy.allows(data, Capability.ROOM_LOCKDOWN)) continue;
            if (data.getLockedRoomBlocks().contains(key)) return true;
        }
        return false;
    }

    private String blockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ","
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
