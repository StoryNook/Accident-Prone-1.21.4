# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spigot/Bukkit Minecraft plugin "Accident-Prone" (`com.storynook:Accident-Prone`), targeting **Spigot API 1.21.4** on **Java 21**. The plugin tracks per-player bladder/bowel/hydration stats, layered underwear/diapers, accidents, caregivers, and a custom HUD. Built with Maven; output goes to `target/Accident-Prone-<version>.jar`, intended to be dropped into a Paper/Spigot 1.21.4 server's `plugins/` folder. Pre-1.21.4 client and server support is explicitly dropped.

## Wiki

Subsystem-level reference docs live in [`docs/wiki/`](docs/wiki/README.md). Open the relevant page when working on that subsystem:

- [Nanny NPC system](docs/wiki/nanny.md) — Phases 1–6 component reference (feature-complete; includes Phase 6 membership integration).
- [Hypnosis subsystem](docs/wiki/hypnosis.md) — trigger words, persistence, cursed items.
- [Toilet & Warning system](docs/wiki/toilet-warnings.md) — intent-driven toilet relief eligibility, probability-scaled warnings, session-only Plugin maps, `/pee`/`/poop` context branching.
- [Rash system](docs/wiki/rash.md) — RP accumulation, threshold effects, config modes, persistence.
- [Design Registry](docs/wiki/design-registry.md) — adding visual design variants per category (e.g. Goodnite Stars). One `register(...)` line per design; auto-detect script + `/add-design` skill handle the rest.
- [Resource pack 1.21.4 layout](docs/wiki/resource-pack-1-21-4.md) — pack format 46, `assets/minecraft/items/<base>.json` `range_dispatch` on `custom_model_data.floats[0]`, equipment definitions for worn armor (replaces OptiFine CIT), nested trim_material `select` for vanilla armor trims.
- [Plugin dependencies](docs/wiki/dependencies.md) — Maven + softdepends + runtime detection.
- [Integrations](docs/wiki/integrations.md) — Jobs Reborn / AdvancedJobs / BeautyQuests bridge; event-bus action catalog (`AccidentProneActionEvent` + 14 action IDs).

Design specs live in `docs/superpowers/specs/`. Admin guides live in `docs/security/` and `docs/membership-setup.md`.

## Build & test

```bash
mvn package          # build the plugin jar in target/
mvn test             # run JUnit tests
mvn -Dtest=PluginTest#shouldAnswerWithTrue test   # run a single test
mvn clean package    # clean rebuild
```

The current Java test suite (`src/test/java/com/storynook/PluginTest.java`) is a placeholder (`assertTrue(true)`); there is no real Java-side test coverage. The migration tooling at `tools/migrate_to_1_21_4/` has its own pytest suite at `tests/test_migrate_to_1_21_4.py` (run with `python3 -m pytest tests/`). Manual end-to-end testing is done by copying the built jar into a live Paper/Spigot 1.21.4 server and using the in-game commands declared in `plugin.yml`.

There is no linter/formatter configured.

## Architecture

### Plugin entry point
`com.storynook.Plugin` (declared as `main` in `src/main/resources/plugin.yml`) is the `JavaPlugin` and the central hub:

- `onEnable()` first probes for VentureChat and registers `VentureChatHook` if present (sets `plugin.VentureChat = true/false`). It then instantiates every listener/menu/item helper, registers them via a `listeners` array loop into `getServer().getPluginManager()`, registers all commands against a single `CommandHandler`, and starts two repeating tasks.
- Holds the canonical in-memory state: `playerStatsMap` (UUID → `PlayerStats`), `armorStandTracker`, `rightclickCount`, `soundConfig` (static), `globalConfig`, `hypnoWords`, `messagesConfig`, awaiting-input map, etc. Most subsystems reach into `Plugin` via a constructor-injected reference to read/write this state.
- Two scheduled loops drive the gameplay tick:
  - `UpdateStats` runs every **40 ticks (2s)** — fills bladder/bowels, applies hydration/incontinence, triggers warnings/accidents.
  - `UpdateStatsBar` runs every **20 ticks (1s)** — repaints the action-bar/scoreboard UI based on `PlayerStats.getUI()` (mode 1 = sidebar scoreboard, mode 2 = action-bar with custom glyph chars).

### State & persistence
- `PlayerStats` is the per-player domain object (~30 fields: `bladder`, `bowels`, `diaperWetness`, `diaperFullness`, layered `underwearType` + `layers`, incontinence, sounds, caregivers, hypno triggers (`List<HypnoTrigger>` + `hypnoPermission` int), diaper-binding lists, etc.).
- Persistence is **YAML-per-player** at `<plugin data folder>/players/<uuid>.yml`, hand-rolled across four classes in `com.storynook.PlayerStatsManagement`:
  - `Plugin.loadPlayerStats(player)` — orchestrator. If a YAML file exists, calls `LoadStats.loadPlayerStatsFromConfig(stats, config)`. Otherwise calls `CreateDefaultStats.createDefaultPlayerStats(stats, player)` and gives the player a welcome book.
  - `LoadStats.loadPlayerStatsFromConfig` — explicit `stats.setX(config.getX(...))` per field.
  - `SavePlayerStats.savePlayerStats(player)` — explicit `config.set(...)` per field, then writes the YAML.
  - `CreateDefaultStats.createDefaultPlayerStats` — explicit defaults per field.
- **There is no shared schema.** Adding a field to `PlayerStats` means touching all three of `LoadStats`, `SavePlayerStats`, and `CreateDefaultStats`, or it will silently not persist / be wrong on first join.
- **Persistence is feature-gated.** Both `LoadStats` and `SavePlayerStats` consult `plugin.getGlobalConfig()` and only read/write whole groups of fields when the corresponding flag (`Accidents`, `Messing`, `ShowUndies`, `Caregivers`, `Binding_Diapers`, `Hypno`, `Membership`, `MinFilltoggle`, `Hardcore`, `Incontinence`) is on. If a flag is off when stats save and on when they next load, the gated fields fall back to defaults — be careful flipping flags on a server with existing player data.
- `LoadStats`, `SavePlayerStats`, `CreateDefaultStats`, and `LoadSelectedSounds` all use the static-plugin pattern (`private static Plugin plugin;` + `setPlugin(...)` called from `Plugin.onEnable`). See *Bug-prone areas* below.
- Diaper-pail inventories live in `<plugin data folder>/DiaperPails/`. Nanny YAML lives in `<plugin data folder>/nannies/`. The encryption key file lives at `<plugin data folder>/.crypto.key` (overridable via `Crypto.Key_Path`).
- `Plugin.mergeConfigFiles(name)` is the config-update strategy: it copies a resource into the data folder on first run, then on reload merges in **only keys missing from the existing file** (preserves user edits, adds new defaults). It is called at startup for `config.yml`, `sounds.yml`, `messages.yml`, `HyponosisWords.yml`, `welcomebook.yml`, `nanny_messages.yml`, and `StoryNook1.2.4.zip` (the resource pack bundle).
- `/diaperreload` saves all online players, calls `mergeConfigFiles` again, reloads sounds, rebuilds the membership provider, and re-loads each online player's stats.

### Accident pipeline
`UpdateStats` (tick) → `Warnings` (urgency thresholds) → `HandleAccident.handleAccident(isBladder, player, playSound, messageType)`. `HandleAccident` is the single source of truth for "the player just had an accident": it converts current bladder/bowel level into diaper wetness/fullness based on `underwearType` (0 = nothing, 1–3 = increasing absorbency) and `layers` (0–4), zeros the source stat, and ticks incontinence up by 0.5 unless the corresponding lock is set. The `/pee` and `/poop` commands **when not on a toilet** call into it directly; when the player is seated on a toilet they route through `Toilet.relieveOnToilet` instead (zeroes stat, *decreases* incon by 0.2, no diaper transfer). Other triggers funnel through `handleAccident`: `AccidentsRandom` (random accidents, including bedwetting via `getBedwetting()` 1/2 modes), `TickleAccidents`, `Laxative`, and the binding-diaper redirect logic inside `HandleAccident` itself (when `Binding_Diapers` is on, an accident on a "bound" player can be re-targeted to a randomly selected online bindee). After the stat math, `HandleAccident.changeLeggingsModel` swaps the `LEATHER_LEGGINGS` `CustomModelData` between `626015`/`626016`/`626017`/`626018` so worn pants visually reflect the wet/dirty state. Any new accident trigger should funnel through `handleAccident` rather than mutating stats directly.

**Warning probability scaling:** `Warnings.java` gates every warning message behind a `ThreadLocalRandom` roll whose probability scales linearly from `Warnings.Probability_Max` (default 1.0) at incon 1 to `Warnings.Probability_Min` (default 0.001) at incon 10. A suppressed roll does not stamp the warning timestamp — a warning the player never saw cannot enable toilet relief.

**Session-only toilet state:** `Plugin` holds three in-memory fields (not persisted to YAML) used by the toilet and warning systems: `lastBladderWarningMillis` and `lastBowelWarningMillis` (`ConcurrentHashMap<UUID, Long>`) stamp when each warning last fired; `playersOnToilet` (`ConcurrentHashMap.newKeySet()`) tracks who is currently seated. These survive log-out/log-in within the same server session but are cleared on server restart. The on-toilet set is also cleaned on `PlayerQuitEvent` (stale entries would misroute `/pee` after a disconnect-while-sitting). See [`docs/wiki/toilet-warnings.md`](docs/wiki/toilet-warnings.md) for full detail.

### Custom items & recipes
`com.storynook.items` defines custom items using **`CustomModelData`** in two ranges:
- `626xxx` — most items (underwear `626001/2/3/9`, pull-up `626004/5/10/11`, thick diaper sets, pants states `626015–626018`, washed-pants variants `626022–626033`, washer `626014`, laxative `626012`, plus several others).
- `628xxx` — Stuffies (`628000`).
- `629xxx` — Nanny Egg (`629001`).

The plugin relies on a paired resource pack to render them; without it, items appear as plain vanilla materials. `ItemManager` plus per-item classes (`pants`, `underwear`, `cribs`, `Stuffies`, `Nanny`, etc.) register `ShapedRecipe` / `ShapelessRecipe` instances during `onEnable`. `CustomItemCheck` identifies them at runtime by `CustomModelData`.

### Commands
All commands declared in `plugin.yml` are routed to a single `CommandHandler` instance. In `Plugin.onEnable` they are split into `singleCommands` (executor only — `settings`, `pee`, `poop`, `stats`, `nightvision`, `nv`, `diaperreload`, `welcomebook`) and `dualCommands` (executor + tab completer — `debug`, `check`, `caregiver`, `lockincon`, `unlockincon`, `minfill`, `equiparmor`, `change`, `hypno`, `nanny`). Sub-command classes in `com.storynook.Commands` (`Give`, `Laydown`, `EquipArmor`, `NannyCommand`) are invoked from inside `CommandHandler.onCommand`.

### Event listeners
`com.storynook.Event_Listeners` contains one `Listener` per gameplay system. They are all instantiated in `onEnable` and registered through the same `listeners` array loop — **to add a new listener, add it to that array** so it gets registered. `LeashControl` exists in source but is currently commented out and not registered. `VentureChatHook` and `NannyVentureChatHook` are registered separately, only when VentureChat is detected. `ArmorLockListener` and `RoomLockListener` (Phase 5) are registered alongside the array.

### Menus
`com.storynook.menus` (`SettingsMenu`, `Caregivermenu`, `HUDMenu`, `IncontinenceMenu`, `SoundEffectsMenu`, `NannyMenu`) are inventory-based GUIs. `SettingsMenu.OpenSettings(player, plugin)` is the entry point opened by `/settings`; it dispatches into the sub-menus. Some menus prompt the player for chat input via `Plugin.setAwaitingInput(uuid, type)` / `getAwaitingInputType` / `clearAwaitingInput`, which `PlayerEventListener` reads in its chat handler.

### Sounds
Categorized sound names live in `src/main/resources/sounds.yml`, loaded into `Plugin.soundConfig` (category → sound → enabled). Each player's `PlayerStats.StoredSounds` is initialized from this map and persisted under `StoredSounds:` in their YAML, so new sounds added to `sounds.yml` automatically appear (default-enabled) for existing players on next load. `PlaySounds.playsounds(player, category, ...)` picks a random enabled sound from a category.

## UI rendering

The HUD has two surfaces (`PlayerStats.getUI()` selects between them: 1 = sidebar, 2 = action bar), both **fully dependent on the companion resource pack font**. Both surfaces are repainted every 20 ticks by `Plugin.UpdateStatsBar`.

- **Action bar** (mode `UI=2`) — sent each tick via `player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(s))`. The string is hand-concatenated from glyph chars (e.g. hydration droplets ``/``, bladder ``/``, bowels ``/``) plus **invisible / negative-width spacing chars** in the `\uF8xx` range that the resource-pack font uses to position the next glyph (``, ``, ``, ``, ``, ``, ``, ``). Reordering or removing one `\uF8xx` char shifts everything to its right. The mode-2 string is conditionally extended with bowels and the underwear sprite based on `stats.getMessing()` and `stats.getshowunderwear()`.
- **Sidebar scoreboard** (mode `UI=1`, `ScoreBoard.java`) — standard Bukkit `Scoreboard`/`Objective` with `DisplaySlot.SIDEBAR`. Bars use bladder glyphs ``/`` and bowel glyphs ``/``. The underwear sprite is built by `getUnderwearStatus(wetness, fullness, type, size)` from a 3D `char[][]` table: `smallStages` (size=0) / `normalStages` (size=1) / `bigStages` (size=2), each indexed by underwear type 0–3. The stage-index calculation in `getUnderwearStatus` (around lines 160–177) combines `wetStage` and `messStage` into a single index — easy to miscount when adding stages. Mode 1 *also* sends a hydration-only action bar in addition to the sidebar.
- The action bar is **only sent when `player.getRemainingAir() == player.getMaximumAir()`** so we don't overwrite Minecraft's drowning indicator.
- When a player's `getUI()` is 0 (or `getOptin()` is false), `UpdateStatsBar` clears the scoreboard with `player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard())`.

Adding a sprite means adding a glyph to the resource-pack font JSON **and** referencing its codepoint in code.

## Resource pack as a hard runtime dependency

The plugin is **unusable without its companion resource pack**, which it bundles and merges as `StoryNook1.2.4.zip` (also kept unpacked under `src/main/resources/StoryNook1.2.4/`). The pack supplies:

1. **Item textures** keyed by the `CustomModelData` numbers in the `626xxx` / `628xxx` / `629xxx` ranges listed in *Custom items & recipes*. Without the pack, custom items render as their base vanilla material. Item-model resolution uses the **1.21.4 `assets/minecraft/items/<base>.json` system** with `range_dispatch` on `custom_model_data.floats[0]` (the legacy integer CMD is auto-mapped to `floats[0]` by the client). The pack ships `items/slime_ball.json` (37 entries) and `items/leather_leggings.json` (27 CMD entries + a nested `select` on `minecraft:trim_material` reproducing all 10 vanilla armor trims). The legacy `models/item/<base>.json` `overrides[]` system is gone.
2. **Worn-armor textures** for leather_leggings-based wearables, served via the **vanilla `equippable` data component** pointing at `assets/minecraft/equipment/<id>.json` definitions (with textures under `assets/minecraft/textures/entity/equipment/humanoid_leggings/<id>.png`). 20 equipment IDs ship out of the box (one per pre-migration OptiFine CIT properties file). DesignRegistry-driven designs (e.g. `goodnite_stars`) currently have inventory icons but no equipment definitions — designs render as plain dyed leather leggings on the body until per-design equipment defs are added. **OptiFine CIT is no longer used**; the entire `optifine/` tree was removed in the 1.21.4 migration.
3. **Bitmap font** that turns the `\uE0xx`–`\uE1xx` ranges into HUD/sidebar glyphs (hydration, bladder, bowels, underwear sprite stages — see the tables in `ScoreBoard.getUnderwearStatus`) and the `\uF8xx` range into invisible / negative-width spacing characters used to position those glyphs.

Because the pack lives alongside this repo as a bundled zip, **any new codepoint allocation in code must be mirrored in the pack** (and vice versa) or the player will see tofu/blank glyphs. Same rule applies to new CMDs — they must appear as a `range_dispatch` entry in the appropriate `items/<base>.json`, or the item renders as the base vanilla material.

## Bug-prone areas

- **Static-`plugin`-field anti-pattern (very widespread).** The following classes declare `private static Plugin plugin;` and assign it from a constructor or `setPlugin(Plugin)`:
  `HandleAccident`, `Warnings`, `PlaySounds`, `PantsCrafting`, `Changing`, `Hypno`, `PlayerInteractWithEntity`, `VentureChatHook`, `EquipArmor`, `LoadStats`, `SavePlayerStats`, `CreateDefaultStats`, `LoadSelectedSounds`. Each `new` overwrites the static field — the methods are effectively static, and stale references survive `/reload` poorly. `PlayerEventListener` is the exception that uses `private final Plugin plugin` (the right pattern).
- **NPE risk on `getPlayerStats`**: ~73 call sites across the codebase, with very few immediate `!= null` guards. `getPlayerStats` returns `null` for events firing before `PlayerJoinEvent`, NPCs/Citizens with player UUIDs, async chat events, and post-`/diaperreload` races. New code should null-check before dereferencing.
- **YAML persistence drift**: every `PlayerStats` field has explicit `set`/`get` calls in *all three* of `LoadStats`, `SavePlayerStats`, and `CreateDefaultStats`. Adding a field and missing one of them → silent data loss or a mis-defaulted field on first join.
- **Persistence is feature-gated.** Save/load groups are only written/read when their `globalConfig` flag is on (see *State & persistence*). Toggling a flag off, having a player log out, then toggling it back on can stomp data with defaults.
- **Glyph table indexing** in `ScoreBoard.getUnderwearStatus` — out-of-range stage index falls through to a `Math.min(stageIndex, stages.length - 1)` clamp rather than crashing, so the symptom of a miscount is a blank/wrong glyph, not a stack trace.
- **At-rest encryption key loss is unrecoverable.** `<dataFolder>/.crypto.key` decrypts OAuth client secrets, AI API key, and per-player membership tokens. Replacing/losing the file makes existing encrypted data permanently unreadable — the plugin will throw `Decryption failed (key file may have been replaced)` and refuse rather than silently re-encrypting under a new key. See `docs/security/hardening.md`.
- **`MinFill` was missing from `loadGlobalConfig`.** The root `MinFill` key was never loaded into `globalConfig` — any code reading `globalConfig.get("MinFill")` was getting `null` and falling back to defaults. Fixed when the toilet-eligibility system was added. If you add new root config keys, check they are explicitly loaded in `loadGlobalConfig`; the `Settings_Menu.*` and nested-block keys are handled separately.

## Project goals (north star — not yet built)

1. **Open-source friendly**, public contributions. Implies: format/lint, no inscrutable single-letter classes, consistent naming, tests so contributors can refactor without fear.
2. **Real tests + CI** (replace the placeholder `PluginTest`). MockBukkit (whichever version tracks 1.21.4) runs headless without a real server. The Python migration tooling already has a pytest suite at `tests/test_migrate_to_1_21_4.py`; mirror that pattern for Java. High-value targets first:
   - `HandleAccident.handleAccident` arithmetic per `underwearType` × `layers`.
   - `LoadStats` / `SavePlayerStats` / `CreateDefaultStats` round-trip.
   - `ScoreBoard.getUnderwearStatus` stage-index math.
   - `Plugin.mergeConfigFiles` upgrade behavior.
   - `CryptoService` round-trip + key-file replacement detection.
3. **Code cleanup**: rename lower-case classes (`pants`/`underwear`/`cribs`/`washer`/`Stuffies`), fix `AccidentsANDWanrings` typo and the `Hyponsis`/`HyponosisWords` config-key typos, replace static-`plugin` pattern with injected references, extract per-field YAML I/O into one serializer, adopt Spotless + google-java-format.
4. **Server-admin config in `config.yml`** — admin should have lots of switches; default is the *mildest* version (bathroom simulator).

## Config structure

**Current `config.yml` (as shipped):**

- Flat root keys for the always-on knobs: `Bladder_Fill_Rate`, `Bowel_Fill_Rate`, `MinFill`, `Accidents`, `Diapers`, `Lock_Optin_with_Hardcore`.
- `Settings_Menu:` block — `Messing`, `Caregivers`, `Show_Undies`, `Incontinence`, `Hardcore`, `MinFilltoggle`, `Membership`. These are surfaced through `Plugin.loadGlobalConfig` into `globalConfig` and gate the corresponding persistence/feature paths.
- `Secret_Menu:` block — `Binding_Diapers`, `Hyponsis` (note typo — the loader actually reads `Secret_Menu.Hypnosis`; verify before changing), `Hypno_Duration_Days` (Long, default 3), `Hypno_Max_Triggers` (Integer, default 0 = unlimited). These gate the adult/advanced subsystems.
- `Toilet:` block — `Warning_Window_Max_Seconds` (default 60), `Warning_Window_Min_Seconds` (default 5). Controls the window after a warning fires during which the toilet will relieve that stat. Only active when `Accidents: true`. See [`docs/wiki/toilet-warnings.md`](docs/wiki/toilet-warnings.md).
- `Warnings:` block — `Probability_Max` (default 1.0), `Probability_Min` (default 0.001). Controls how often warning messages actually fire at low vs. high incontinence. See [`docs/wiki/toilet-warnings.md`](docs/wiki/toilet-warnings.md).
- `Nanny:` block — nested. See [`docs/wiki/nanny.md`](docs/wiki/nanny.md) for the full key list.
- `Crypto:` block — `Key_Path` (empty = `<dataFolder>/.crypto.key`; supports `${ENV_VAR}` expansion).
- `Links:` block — `Discord_Link`, `Patreon_Link`, `Subscribestar_Link`. Surfaced in the settings menu.

**Aspirational structure (not yet implemented; treat as backlog):**

- Three UI areas inside the file: **main** (admin-visible defaults — the obvious knobs), **advanced** (admin-visible but in a separate, less-prominent section — opt-in tweaks), and **hidden** (not surfaced anywhere; an admin has to *know it exists* to set it; document them somewhere internal). The current `Settings_Menu` / `Secret_Menu` split is a partial early version of this idea.
- **Preset templates** that flip groups of individual settings at once. Templates are not section names in the file — they are shortcuts an admin can invoke to apply a recommended bundle:
  - `BASIC` (default): bathroom simulator only — bladder, bowels, hydration, toilets. No accidents.
  - `ACCIDENTS`: enables random accidents, urge warnings, bedwetting.
  - `PROTECTION`: enables underwear/diaper items, layered absorbency, diaper pails, washer.
  - `ADVANCED`: enables laxatives, hypnosis, cursed items.

## Adding a config setting

Whenever you add a new admin-configurable flag, follow these four steps in order. The welcome-book step is the one most likely to be forgotten — do not skip it.

1. **Add the key to `src/main/resources/config.yml`** in the appropriate block (root, `Settings_Menu`, `Secret_Menu`, or a feature block like `Nanny`). Include a `#` comment above the key explaining what it controls.
2. **Load it in `Plugin.loadGlobalConfig`** so it appears in the flattened `globalConfig` map. Use a short, flat key name (e.g. `Settings_Menu.Messing` is loaded as key `Messing`). For sensitive values (secrets, API keys), use `Plugin.readEncryptedConfigString(path)` instead of `getConfig().getString(...)` — this gives you at-rest encryption + plaintext-to-encrypted migration on first save.
3. **Gate the relevant code path** on `globalConfig.get("<flag>")`. If a per-player setting depends on this global flag, also make sure that per-player toggle is greyed out / hidden in the menu when the global flag is off (the symptom of forgetting this is "this toggle does nothing" reports).
4. **Update `src/main/resources/welcomebook.yml`**:
   - Add (or edit) a section under `sections:` whose `requires:` references the new flag. Use the *flattened* key name from step 2, not the dotted YAML path.
   - Add the section's id to the `order:` list at the appropriate point in the player-interaction flow (intro → core mechanics → optional features → hidden features → credits).
   - Write the `body:` prose explaining how a player will actually encounter the feature.
   - The merge logic in `Plugin.mergeConfigFiles` is non-destructive: existing servers keep their admin edits, and your new section is added on the next start. This only works if you actually add the section here.
5. **Decide if this feature deserves an integration action.** If the new flag gates a new player action (changing, crafting, drinking, accident, etc.), it should probably fire an `AccidentProneActionEvent` — see "Adding an integration action" below. Make the call explicitly; default to yes. If no, note the reason in the commit.

The Credits page is rendered after all YAML sections and lives in `TutorialBook.buildCreditsBody()` — update it there when adding contributors, not in `welcomebook.yml`.

## Adding an integration action

> **Project rule:** every new player-visible gameplay event is an opportunity for a Jobs payout or a BeautyQuests stage. Whenever you add a new feature (a new gameplay event, a new way to interact with an item, a new toggle that triggers behavior), **explicitly decide** whether it should fire an `AccidentProneActionEvent`. Default answer is "yes" for anything that has a clear success-state moment. If you decide "no" — write the reason in the PR/commit so future maintainers know it was considered. The only place this lookup happens is `IntegrationsBus.TABLE`; if your action isn't there, no Jobs or BeautyQuests integration is possible without code changes.

When you add a new gameplay event that should be visible to integrations (Jobs / BeautyQuests / future hooks), follow these six steps:

1. **Add a constant** to `com.storynook.Integrations.events.ActionId` (e.g. `public static final String NEW_THING = "accidentprone:new_thing";`) and append it to `ActionId.ALL`.
2. **Fire the event** from the listener / command / handler at the point the action successfully completes:
   ```java
   plugin.getIntegrationsBus().fire(worker, ActionId.NEW_THING, target, ctx);
   ```
   `ctx` is a `Map<String,Object>` carrying pre-state values for the bus's state predicate (e.g. wetness/fullness/bladder/bowels/hydration).
3. **Add a descriptor** in `IntegrationsBus.TABLE` static block: feature-flag key (one of `Caregivers`, `Diapers`, `Accidents`, etc.), caregiver-relationship requirement, state predicate, cooldown key, scope (`worker`, `pair`, `pair_slot`, `worker_stat`).
4. **Add a config row** in `src/main/resources/integrations.yml` under both `Jobs.Action_Map` (default Jobs action name) and `BeautyQuests.Quest_Trigger_Map` (empty string).
5. **Add a row** to the action catalog table in `docs/wiki/integrations.md`.
6. **Tests:** `JobsActionMapTest` and `BeautyQuestsTriggerMapTest` automatically check the YAML coverage. Add a state-predicate / cooldown test in `IntegrationsBusTest` if the action has non-trivial gating.

If the action introduces a new feature flag, also follow "Adding a config setting" for that flag.

## Adding a design variant

For new visual variants of an existing absorbency category (e.g. a new pull-up that looks different but behaves identically), do **not** add a new factory method, recipe, give-case, equip-line, or sprite array. The system is fully data-driven through `DesignRegistry`. The full procedure is in [`docs/wiki/design-registry.md`](docs/wiki/design-registry.md); the short version:

1. Drop stage PNGs in `src/main/resources/StoryNook1.2.4/assets/minecraft/textures/custom/special/<prefix>-<name>/`. **Prefix the folder with the category** so detection is unambiguous: `undies-` / `underwear-` (4 stages), `pullup-` (8), `diaper-` (15), `thick-` / `thick_diaper-` (25). PNG suffix convention: `*_clean.png`, `*_wet1.png`, `*_wet1mess1.png`, … Optionally add an `icons/` subfolder with `clean.png` (required if subfolder present) and optional `wet.png` / `messy.png` / `wetmessy.png` for the inventory item textures. To give the design a distinct **worn-armor body texture** (instead of falling back to plain dyed leggings), place `clean.png` (and optional `wet.png` / `messy.png` / `wetmessy.png`) in an `armor/` subfolder of the design folder; the generator will write the matching `assets/minecraft/equipment/<design>.json` definitions and copy textures into `assets/minecraft/textures/entity/equipment/humanoid_leggings/`.
2. Run `python3 tools/generate_design_json.py <that-folder>`. The script reads the category from the prefix (PNG count just validates), strips the prefix to form the giveKey, picks the next free designId + CMD block, splices font entries into `images.json`, generates `models/item/<design>_<state>.json` + appends a `range_dispatch` entry to `assets/minecraft/items/slime_ball.json` if `icons/clean.png` exists, generates `assets/minecraft/equipment/<design>.json` definitions + copies textures to `assets/minecraft/textures/entity/equipment/humanoid_leggings/<design>.png` if the `armor/` folder + `clean.png` exist, and writes `tools/pending_design.json` (carrying an `equippableKey` for `/add-design`'s `register(...)` call). Folders without a prefix fall back to PNG count and emit a "rename me" hint; ambiguous cases error. Each missing optional folder produces a `note:` describing exactly what to add.
3. Run `/add-design` in Claude Code. It appends one `register(...)` line to `DesignRegistry.init()`, runs `mvn package`, and archives the manifest.

When the user says something like "I added new designs to the resource pack, add them" in a fresh context, follow this same flow: scan `textures/custom/special/` for folders not yet in `tools/applied/*.json`, run the script per new folder, then `/add-design` per manifest. Stop and ask the user only if a folder lacks a prefix AND has an ambiguous PNG count.

The new design is then immediately usable via `/debug give <amount> <player> <Pullup|Underwear|Diaper|Thick_Diaper> <giveKey>`, equip-on-right-click, sidebar sprite, and in-world model — all without touching any other Java file.

Two integers per player drive the visuals: `UnderwearType` (0..3, mechanics — unchanged at every existing call site) and `UnderwearDesign` (>= 0, visuals — set atomically with type via `applyUnderwear(type, designId)`). `setUnderwearType(type)` is preserved for backward compat and resets design to 0. Mechanics-only code (HandleAccident, Toilet, etc.) reads only `UnderwearType` and is design-agnostic.

## Cross-feature gating

Many features depend on other features being on. The current code expresses this implicitly by checking `globalConfig.get(...)` flags inside listeners and persistence; some gates are not enforced and a per-player toggle can visibly do nothing. When extending the plugin, route new gating through a single check site (a `FeatureGate` helper or equivalent) so we have one place to reason about it. Hard rules:

- `Bedwetting > 0` requires `Accidents` enabled.
- Laxatives require `Settings_Menu.Messing` (per-player) **and** `Diapers`/bowels feature globally enabled.
- Hypnosis requires `Secret_Menu.Hypnosis` on (VentureChat is optional — if present, `VentureChatHook` handles chat triggers and `Hypno`'s `AsyncPlayerChatEvent` short-circuits to avoid double-fire; if absent, `Hypno` handles chat directly). Recipe registration and the settings-menu CLOCK item are both gated on the `Hypno` global flag.
- Cursed binding diapers require `Secret_Menu.Binding_Diapers` on; the recipe should not register at all when off.
- Membership-gated AI tier requires `Settings_Menu.Membership` on AND `Nanny.Membership.enabled` on AND at least one sub-provider (`Permission`/`Patreon`/`Subscribestar`) enabled. Otherwise `AlwaysLockedProvider` is used and AI never fires.
- Per-player settings should **grey out / be hidden** when the underlying global setting is off, not silently no-op. The current symptom — a per-player toggle that visibly does nothing — is a frequent confusion source.
- `Integrations.enabled` (master switch in `integrations.yml`) is required on top of every per-action feature flag — `accidentprone:change` requires both `Integrations.enabled` AND `Settings_Menu.Caregivers`, etc. Per-action gating is centralised in `IntegrationsBus.TABLE`.

## Conventions to be aware of

- **Class names are inconsistent**: Java types `pants`, `underwear`, `cribs`, `washer`, `Stuffies` use lower-case names. Don't "fix" the casing on a whim — imports across the codebase reference these exact names.
- **Package name typo**: `com.storynook.AccidentsANDWanrings` (sic — "Wanrings", not "Warnings"). The directory and package name are spelled this way; preserve it unless you are doing a deliberate rename across all imports.
- **Config-key typos**: `HyponosisWords.yml` (resource filename) and `Secret_Menu.Hyponsis` (key in the shipped `config.yml`) both mis-spell "Hypnosis". The Java code reads `Secret_Menu.Hypnosis` (correctly spelled) — meaning the shipped key is currently inert. Don't silently rewrite either side without checking the other.
- The resource folder `src/main/java/com/storynook/JSON Files/` (with a space) holds non-Java assets (`font.json`); keep non-source assets out of `src/main/java` ideally, but be aware existing code/build config may assume current layout.
- Java 21 source/target — newer language features (`var`, records, switch expressions, pattern matching, sealed classes, text blocks) are now available. Existing code is not churned to use them; introduce them only where they improve clarity in new code.
- Spigot API and VentureChat are `provided` scope — never bundle them into the jar.
- Resource pack uses pack format 46 (1.21.4) with `supported_formats: [46, 99]` for forward-compat. Pre-1.21.4 client and server support is explicitly dropped — see `docs/wiki/resource-pack-1-21-4.md`.
