---
title: Toilet & warning system
description: Intent-driven toilet relief, probability-scaled warnings, and the relieveOnToilet vs handleAccident split.
---

# Toilet & Warning System

Reference for the intent-driven toilet relief system and the probability-scaled warning system. Both were introduced together because they are tightly coupled: the toilet checks whether a warning recently fired; the warning system decides whether to fire and stamps timestamps used by the toilet.

## Overview

Before this system, sitting on a toilet emptied any stat above 10 unconditionally. That made independent bladder/bowel playstyles (e.g. bowel-incontinent but bladder-strong) impossible — every routine toilet visit wiped both stats. The fix makes relief **intent-driven**: a stat is only emptied if the system believes the player genuinely needed to go for that stat.

## Toilet relief eligibility (`Toilet.java`)

`canRelieveOnToilet(player, stats, isBladder)` is evaluated independently for bladder and bowels each time the player sits. Both stats are evaluated; each may or may not relieve.

When `Accidents: false` in config, the method returns `true` unconditionally (legacy behavior preserved for non-accident servers).

### Condition A — Recent warning

A warning for this specific stat (`Need_To_Pee` / `Desperate_Pee` for bladder; their bowel equivalents for bowels) fired within the player's **warning window**. The window shrinks linearly with that stat's incontinence:

```
windowSec = maxSec - (incon - 1) / 9.0 * (maxSec - minSec)
```

| Incontinence | Window (default config) |
|---|---|
| 1 | 60 s |
| 5 | ~33 s |
| 10 | 5 s |

Config keys (under `Toilet:`):
- `Warning_Window_Max_Seconds` — window at incon 1 (default 60)
- `Warning_Window_Min_Seconds` — window at incon 10 (default 5)

### Condition B — Proactive override (high incontinence)

Because warnings become very rare at high incontinence (see below), highly incontinent players can use the toilet without a recent warning if the stat itself is above a threshold that scales down linearly:

```
proactiveThreshold = 100.0 - (incon - 1) / 9.0 * (100.0 - MinFill)
```

| Incontinence | Threshold (MinFill=30) |
|---|---|
| 1 | 100 (effectively disabled — can never reach 100%) |
| 5 | ~65 |
| 10 | 30 (= MinFill) |

At incon 10, the proactive threshold equals `MinFill` — the same level at which warnings *would* fire for a normal player. The player watches their own stats and uses the toilet when they approach the danger zone.

### False alarm message

If the player sits and at least one stat was above 10 but **neither condition was met** for it, the `Toilet_False_Alarm` message key fires:

```yaml
# messages.yml
Toilet_False_Alarm: §7You sit down... but you didn't actually have to go that badly.
```

Stats below 10 are silently skipped (they're near-empty; no message needed).

### `relieveOnToilet` vs `handleAccident`

These are sibling methods with opposite incontinence effects:

| | `relieveOnToilet` | `handleAccident` |
|---|---|---|
| Stat | zeroed | zeroed |
| Incontinence | **decreased** (scaled by underwear type, unless locked) | **increased** (scaled by underwear type, unless locked) |
| Diaper | no transfer | wetness/fullness added based on absorbency |
| Sound | flush (from sit handler) | accident sound |

`relieveOnToilet` is a `public static` method on `Toilet.java`, callable from `CommandHandler` when `/pee` or `/poop` is run while seated.

#### Incontinence scaling by underwear type

The amount changed scales with what the player is wearing. Underwear and pull-ups punish accidents and barely reward toilet use; diapers and thick diapers do the opposite.

| Underwear type | Accident (`handleAccident`) | Toilet success (`relieveOnToilet`) |
|---|---|---|
| 0 — underwear | +1.0 | -0.1 |
| 1 — pull-ups | +1.0 | -0.1 |
| 2 — diaper | +0.2 | -0.5 |
| 3 — thick diaper | +0.1 | -0.8 |
| none (default) | +0.5 | -0.2 |

Logic lives in `HandleAccident.accidentInconDelta(int)` and `Toilet.toiletInconDelta(int)`.

## `/pee` and `/poop` context awareness (`CommandHandler.java`)

Both commands branch on `plugin.isOnToilet(uuid)`:

- **On toilet** → `Toilet.relieveOnToilet(stats, isBladder)` — empties into toilet, decreases incontinence, no diaper wetness.
- **Off toilet** → `HandleAccident.handleAccident(...)` — accident as before.

The `> 10` floor check still applies: if the stat is already near-empty, the command does nothing in both branches.

## Warning probability scaling (`Warnings.java`)

Every warning message send is gated by a probability check **before** it fires. This models the unpredictability of high incontinence — a highly incontinent player cannot rely on consistent warnings and must watch their stats directly.

```
p = pMax - (incon - 1) / 9.0 * (pMax - pMin)
```

| Incontinence | Probability (default config) |
|---|---|
| 1 | 1.0 (always fires) |
| 5 | ~0.5 |
| 10 | 0.001 (~1 in 1000) |

Config keys (under `Warnings:`):
- `Probability_Max` — probability at incon 1 (default 1.0)
- `Probability_Min` — probability at incon 10 (default 0.001)

`Genaric_Desperate` uses `Math.max(bladderIncon, bowelIncon)` since both stats are critical.

**If the gate fails, no timestamp is stamped.** A suppressed warning does not open the toilet relief window. This is intentional: a warning the player never saw shouldn't give them a free toilet use.

## Session-only timestamp maps (`Plugin.java`)

Three in-memory fields on `Plugin` hold warning timestamps and the on-toilet set:

```java
private final Map<UUID, Long> lastBladderWarningMillis = new ConcurrentHashMap<>();
private final Map<UUID, Long> lastBowelWarningMillis   = new ConcurrentHashMap<>();
private final Set<UUID> playersOnToilet = ConcurrentHashMap.newKeySet();
```

**These are deliberately not persisted to YAML.** Logging out and back in does not reset or extend the warning window — the entry simply stays in the map for the server's uptime. A server restart clears the maps, which is the intended hard reset.

The on-toilet set is cleaned up on `PlayerQuitEvent` (in `Toilet.java`) so a player who disconnects while seated does not have `/pee` routed through the toilet path on their next login.

Accessors on `Plugin`:

```java
getLastBladderWarningMillis(UUID)  stampBladderWarning(UUID)
getLastBowelWarningMillis(UUID)    stampBowelWarning(UUID)
isOnToilet(UUID)  markOnToilet(UUID)  clearOnToilet(UUID)
getMessagesConfig()
```

## Config reference

```yaml
# config.yml

Toilet:
  # Seconds after a warning during which the toilet will relieve that stat.
  # This is the window at incontinence 1; scales linearly down to Min at incon 10.
  Warning_Window_Max_Seconds: 60
  Warning_Window_Min_Seconds: 5

Warnings:
  # Probability a warning message actually fires. 1.0 = always (incon 1).
  # Scales linearly to Min at incon 10.
  Probability_Max: 1.0
  Probability_Min: 0.001
```

The proactive threshold reuses `MinFill` (root key, default 30) — no separate knob.

**Note:** `MinFill` was not previously loaded into `globalConfig` in `loadGlobalConfig`. This was a pre-existing bug fixed when this system was added.

## Player-facing documentation

`welcomebook.yml` has a `toilet_smarts` section (gated `requires: Accidents`) that explains this behavior to players. Its `order:` entry sits between `toilets` and `accidents`.
