package com.storynook.Integrations;

import org.bukkit.Server;

import java.util.Map;
import java.util.UUID;

/**
 * Thin host interface used by IntegrationsBus so it can be tested without
 * mocking the concrete Plugin / JavaPlugin class hierarchy.
 */
public interface IIntegrationsBusHost {
    /** Returns the flattened integrations config map (never null after onEnable). */
    Map<String, Object> getIntegrationsConfig();

    /** Returns the flattened global feature-flag map (never null after onEnable). */
    Map<String, Object> getGlobalConfig();

    /** Returns the Bukkit server instance. */
    Server getServer();

    /** Returns the per-player stats for the given UUID, or null if absent. */
    com.storynook.PlayerStatsManagement.PlayerStats getPlayerStats(UUID uuid);
}
