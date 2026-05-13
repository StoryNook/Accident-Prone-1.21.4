---
title: Plugin dependencies
description: Maven dependencies, plugin.yml softdepends, and runtime detection.
---

# Plugin dependencies

## Maven (`pom.xml`)

- `org.spigotmc:spigot-api 1.19-R0.1-SNAPSHOT` — `provided` (never bundle).
- `mineverse.Aust1n46.chat:VentureChat 3.7.1` — `provided`. Used by `Integrations.VentureChatHook`.
- `net.citizensnpcs:citizens-main 2.0.33-SNAPSHOT` — `provided` (repo `https://repo.citizensnpcs.co/`). Used by the Nanny NPC subsystem.
- `junit 4.11` — `test`.
- VaultAPI is commented out in `pom.xml`; not currently used.

## `plugin.yml` softdepends

`[PlaceholderAPI, VentureChat, Citizens]`. The older `softdepend: [Vault]` line is commented out.

## Runtime detection

- `Plugin.onEnable` checks `getServer().getPluginManager().getPlugin("VentureChat") != null` and stores the result in `plugin.VentureChat` (boolean). `VentureChatHook` is registered only when VentureChat is present. `Hypno`'s `AsyncPlayerChatEvent` handler short-circuits when `plugin.VentureChat` is true (deduplication — `VentureChatHook.onVentureChat` delegates to `Hypno.fireTriggers(message)` instead). When VentureChat is absent, `Hypno` handles chat itself.
- `Plugin.onEnable` checks `getServer().getPluginManager().getPlugin("Citizens") != null` and stores the result in `plugin.citizensEnabled` (boolean). All `NannyEntity` calls into `CitizensAPI` are guarded by this flag — when Citizens2 is absent the Nanny system silently no-ops and `/nanny give` returns an error to the admin.
- PlaceholderAPI is softdepended in `plugin.yml` but there is no first-class hook class for it yet.
