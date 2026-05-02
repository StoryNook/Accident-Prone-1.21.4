# Hypnosis subsystem

Fully shipped. Trigger-word system that fires accidents on chat messages.

## Lifecycle

- **Crafting:** cursed clock recipe (`Hypno.onPrepareCraft`) — clock + book of Curse of Binding → Hypnosis Clock with PDC keys `hypnosis` (byte), `crafted_by` (name), `crafted_by_uuid` (UUID string), and a default `HypnoTriggerWord` (random from `HyponosisWords.yml`).
- **Programming:** `/hypno <word> <type>` (type = `wetting`|`messing`) writes both `HypnoTriggerWord` and `HypnoType` PDC keys onto the held clock. The clock's lore shows `Trigger: <word>` + `Type: <type>` to the crafter only (UUID-matched, follows the laxative pattern via `InventoryOpenEvent` / `InventoryCloseEvent` / `EntityPickupItemEvent`); lore is null for everyone else.
- **Application:** caster right-clicks target with the clock; 5-second boss-bar cast (`handleRightClickHold`); on success `handleInteraction` enforces opt-in (`hypnoPermission` 0/1/2 = Off/CaregiversOnly/Anyone), caregiver gating, and the `Hypno_Max_Triggers` cap, then adds a `HypnoTrigger(word, type, expiry, casterUUID)` to the target's `PlayerStats.hypnoTriggers` list.
- **Triggering:** `Hypno.fireTriggers(message)` is the single entry point for chat triggers; called from `Hypno.onPlayerChat` (sync-scheduled from `AsyncPlayerChatEvent`, only when VentureChat is absent) and from `VentureChatHook.onVentureChat`. For each online opted-in player it cleans expired triggers, then for each trigger whose word appears in the message it resets the expiry to `now + Hypno_Duration_Days`, rolls a 20% silent chance, and calls `HandleAccident.handleAccident` with `MessageType` `Hypno_Wetting` / `Hypno_Messing` / `SILENT`. Wetting and messing flags are tracked separately so the same word configured for both types fires both accidents simultaneously, but a duplicated word of the same type fires only once per message.

## Persistence and config

- **Persistence:** `hypnoPermission` (int) and `hypnoTriggers` (`List<String>` of pipe-delimited `word|type|yyyy-MM-dd HH:mm:ss|casterUUID`) under the `Hypno` feature gate. `LoadStats` migrates legacy `messingHypnoWord` / `wettingHypnoWord` String fields into triggers with default duration on first load.
- **Settings UI:** `SettingsMenu` shows a CLOCK item (gated on global `Hypno` flag) — cycles `0 → 1 → 2 → 0` on click, but is locked while `stats.hasActiveHypnoTriggers()` returns true (lore shows max-expiry remaining as `Xd Yh`).
- **Config:** `Secret_Menu.Hypno_Duration_Days` (default 3) and `Secret_Menu.Hypno_Max_Triggers` (default 0 = unlimited) live under `Secret_Menu` and are loaded into `globalConfig` as `Hypno_Duration_Days` (Long) and `Hypno_Max_Triggers` (Integer).

## Phase 5b — Nanny integration

`Hypno.applyHypnosis(Player target, ItemStack clock)` is the static helper that lets a Nanny apply a hypnosis trigger autonomously. The 4th `HypnoTrigger` arg (`casterUUID`) is a **String**, not UUID — Nanny passes `null` since it isn't a player. See `docs/wiki/nanny.md` Phase 5b for the dispatch path.

## Cursed items

`BindingDiaper` builds a cursed binding diaper (NamespacedKey `cursed`, `PersistentDataType.BYTE`, lore "Cursed: Binding Enchantment") gated by `Secret_Menu.Binding_Diapers`. `Hypno` adds the cursed clock recipe and rejects ingredients tagged with the `cursed` key. The `TutorialBook` references "Cursed Diapers" as a hidden setting.
