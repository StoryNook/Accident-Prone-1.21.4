package com.storynook.nanny;

import org.bukkit.entity.Player;
import com.storynook.Plugin;

public class DiaperPunishment {
    private final Plugin plugin;
    public DiaperPunishment(Plugin plugin) { this.plugin = plugin; }
    public void start(NannyData data, Player ward, int days) {
        plugin.getLogger().info("[DiaperPunishment] stub: would start " + days + "d for " + ward.getName());
    }
    public boolean isBlocked(Player p) { return false; }
    public void recordViolation(Player p) { }
    public void tick() { }
}
