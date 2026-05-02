package com.storynook.nanny.membership;

import com.storynook.nanny.MembershipProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Unlocks AI tier when the (online) player has a configured permission node.
 * Designed for admins using LuckPerms + DiscordSRV role-sync.
 */
public class PermissionMembershipProvider implements MembershipProvider {

    private final String node;

    public PermissionMembershipProvider(String node) {
        this.node = node;
    }

    @Override
    public boolean isUnlocked(UUID playerUUID) {
        Player p = Bukkit.getPlayer(playerUUID);
        return p != null && p.hasPermission(node);
    }

    @Override
    public void refresh(UUID playerUUID) {
        // No-op: hasPermission is live.
    }
}
