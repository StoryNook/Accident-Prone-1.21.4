---
title: Rash system
description: RP accumulation tiers, threshold-driven effects, persistence, and config keys.
---

# Rash system

Tracks skin irritation caused by wearing a wet or soiled diaper over time. Rash points (RP) accumulate while the diaper is in a bad state and decay while it is clean and dry. When RP crosses a configurable threshold the server-chosen health effect fires. Getting changed resets RP to 0.

## RP accumulation

The tick runs every **2 seconds** (40-game-tick `UpdateStats` loop). Each tick a net `rpTick` is computed and applied to the player's `rashPoints` float, clamped to a floor of 0.

### Base recovery

`-5 RP/tick` always. This is the value when the player is perfectly clean and dry.

### Mess contribution (`diaperFullness`)

| Fullness | RP/tick added |
|---|---|
| 0–19 | 0 |
| 20–49 | +10 |
| 50–99 | +25 |
| 100+ (overloaded) | +40 |

### Wetness contribution (`diaperWetness`)

| Wetness | Effect |
|---|---|
| 0–39 | no modifier (recovery proceeds normally) |
| 40–79 | clamps net `rpTick` to ≥ 0 — stops recovery, adds no RP |
| 80–94 | +8 RP/tick |
| 95–100+ | +20 RP/tick |

### Underwear recovery bonus

If `underwearType == 1` (plain underwear/panties) **and** `diaperFullness < 20` **and** `diaperWetness < 40`, an additional **-20 RP/tick** is applied on top of the base. This means clean underwear recovers at -25 RP/tick total.

### Example net rates

| State | Net RP/tick |
|---|---|
| Clean underwear, dry | -25 |
| Clean diaper, dry | -5 |
| Wetness 40–79 | 0 |
| Wetness 80–94 | +3 (8 − 5) |
| Wetness 95+ | +15 (20 − 5) |
| Fullness 50–99, dry | +20 (25 − 5) |
| Fullness 100+, wetness 95+ | +55 (40 + 20 − 5) |

## Threshold crossing and effects

When `rashPoints` crosses from below to above `threshold`:
- The `Rash` message fires (defined in `messages.yml`).
- The health effect starts.

When `rashPoints` drops back below `threshold` (player was changed):
- The `Rash_Clear` message fires.
- The health effect stops; any active modifiers are removed.

### Modes

Configured by `Rash.mode` in `config.yml`.

| Mode | Behavior |
|---|---|
| `none` | No health effect. RP still accumulates and is visible in `/stats`. |
| `poison` | `POISON I` (`amplifier 0`, 50-tick duration) re-applied each tick. Cannot kill (existing Bukkit poison behavior). |
| `damage` | `player.damage(amount)` per tick. Goes through armor. Capped so the player cannot die: damage is min'd to `player.getHealth() − 0.5`. |
| `health_reduction` | Reduces max health by `amount` hearts (1 heart = 2 HP) via an `AttributeModifier` (`GENERIC_MAX_HEALTH`, `ADD_NUMBER`, value = `−amount * 2`). No direct damage; player just cannot heal above the reduced ceiling. Modifier is reconciled every tick so server restarts re-apply it automatically if RP is still above threshold. |

### Slowness

When `Rash.rash_slowness: true` (default) and rash is active, `SLOWNESS I` is applied **only if the player has no slowness already active**. This avoids downgrading the diaper-fullness-based slowness (which can reach Slowness IV). When rash clears, the fullness-slowness block naturally cleans up the residual Slowness I on the next tick.

## Config keys (`config.yml`)

```yaml
Rash:
  mode: poison          # none | poison | damage | health_reduction
  threshold: 250        # RP before effects start
  amount: 1             # damage mode: HP/tick  |  health_reduction: hearts removed
  rash_slowness: true   # apply Slowness I while above threshold
```

All four keys are loaded in `Plugin.loadGlobalConfig` as `Rash_Mode`, `Rash_Threshold`, `Rash_Amount`, `Rash_Slowness`.

## Persistence

`rashPoints` is a `float` field on `PlayerStats`. It is saved/loaded as `RashPoints` in the per-player YAML under the `Accidents` feature gate (same gate as `diaperWetness`, `diaperFullness`, etc.). It defaults to `0` for new players and for existing players whose YAML pre-dates this field.

Old saves that still have a `TimeWorn` key are harmlessly ignored — the field is no longer read.

## Resetting RP

`stats.setRashPoints(0)` is called in `Changing.java` when a player receives a fresh diaper. This immediately drops RP to 0 and, on the next tick, clears the health effect if it was active.

The debug command `/debug rash <value>` sets RP to any number, useful for testing threshold behavior.

## Item lore

Stinky/wet diapers and soiled underwear produced during a change display `Rash Points: X` in their lore, where X is the player's RP at the moment of changing (cast to int).

## Code locations

| Concern | File | Notes |
|---|---|---|
| RP field + accessors | `PlayerStatsManagement/PlayerStats.java` | `rashPoints` float, `getRashPoints()`, `setRashPoints()` |
| Accumulation + effects | `PlayerStatsManagement/UpdateStats.java` | Inside the 40-tick `Update()` loop |
| Persistence (load) | `PlayerStatsManagement/LoadStats.java` | Key `RashPoints` under `Accidents` gate |
| Persistence (save) | `PlayerStatsManagement/SavePlayerStats.java` | Same key and gate |
| Persistence (defaults) | `PlayerStatsManagement/CreateDefaultStats.java` | Default 0 |
| Reset on change | `Event_Listeners/Changing.java` | `stats.setRashPoints(0)` |
| Item lore | `items/underwear.java` | All `create*` methods |
| Config loading | `Plugin.java` → `loadGlobalConfig` | Four `Rash_*` keys |
| Debug command | `CommandHandler.java` | `/debug rash <n>` |
| Messages | `messages.yml` | `Rash`, `Rash_Clear` |

## Extension notes

- **Barrier cream / baby powder** — discussed during design but not implemented. Would require new `CustomModelData` slots, resource pack entries, and recipes. RP manipulation (`setRashPoints`) is the hook point.
- **Aftermess wetting multiplier** — skipped. Would require a state flag tracking whether mess was present before a wet accident.
- **Non-linear HP loss curve** — skipped in favor of a flat damage-on-interval model that admins can tune via `amount`.
