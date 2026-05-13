# Integrations — Jobs / Quests bridge

Single canonical reference for every integration that bridges Accident-Prone gameplay events to a third-party plugin (Jobs Reborn, AdvancedJobs, BeautyQuests, future hooks).

## Overview

Internally, Accident-Prone publishes a Bukkit custom event `AccidentProneActionEvent` whenever a payable / progression-worthy gameplay action completes. Three integration hooks ship in this plugin and listen for that event:

- `JobsRebornHook` — translates events to Jobs Reborn payouts (pure reflection on `com.gamingmesh.jobs.Jobs`).
- `AdvancedJobsHook` — translates events to AdvancedJobs progress (`net.advancedplugins.jobs.Core` → `JobsPipeline.handle(ActionExecution)`).
- `BeautyQuestsHook` — advances BeautyQuests stages (BQ 2.x: `QuestsAPIProvider.getAPI().getQuestsManager().getQuest(id).getBranchesManager().getBranch(b).getRegularStage(s).finishStage(quester)`).

All three are independently feature-flagged in `integrations.yml`. Each is registered only if the corresponding plugin is detected at server startup (probed in `Plugin.onEnable` after Citizens / PlaceholderAPI / VentureChat).

The bus does all gating centrally before publishing the event: master flag → per-action feature flag → state predicate → caregiver predicate → cooldown → publish. Subscribers (the three hooks) only ever see events that already passed every check. Adding a new integration plugin is a single new `Listener` class and one config block — no anti-farm logic in the hook.

## Action catalog

The 16 firing points, their preconditions, and the `ctx` keys each event carries.

| Action ID | Fires when | Predicate (state + caregiver) | Cooldown | `ctx` keys |
|---|---|---|---|---|
| `accidentprone:change` | Caregiver changes a soiled little (Changing.java) | wetness ≥ Min_Wetness OR fullness ≥ Min_Fullness; worker ≠ target; `target.isCaregiver(worker, true)` | per-pair, 60s | `wetness`, `fullness`, `bladder`, `bowels` |
| `accidentprone:feed` | Caregiver feeds a thirsty little (FeedingAction.java) | hydration < Feed_Below; worker ≠ target; `target.isCaregiver(worker, true)` | per-pair, 120s | `hydration` |
| `accidentprone:pail_fill` | Diaper pail auto-empties dirty items on close (DiaperPail.java) | item was a dirty CMD variant | per-worker, 30s | `dirtyConsumed`, `cmd` |
| `accidentprone:wash_pants` | Washer recipe consumes dirty pants (washer.java) | item was a dirty pants CMD | per-worker, 15s | `dirtyPantsConsumed`, `cmd` |
| `accidentprone:equip_armor_on_little` | Caregiver equips armor on a little (EquipArmor.java) | slot was empty; `target.isCaregiver(worker, true)` | per-pair-slot, 300s | `slot`, `slotWasEmpty` |
| `accidentprone:craft_underwear` | Underwear recipe completes (PantsCrafting.java) | result CMD = 626002 | none | `cmd` |
| `accidentprone:craft_pullup` | Pull-up recipe completes | result CMD = 626003 | none | `cmd` |
| `accidentprone:craft_diaper` | Diaper recipe completes | result CMD = 626009 | none | `cmd` |
| `accidentprone:craft_thick_diaper` | Thick-diaper recipe completes | result CMD = 626001 | none | `cmd` |
| `accidentprone:craft_crib` | Crib recipe completes | result CMD ∈ 627000–627009 | none | `cmd` |
| `accidentprone:craft_washer` | Washer recipe completes | result CMD = 626014 | none | `cmd` |
| `accidentprone:toilet_relief` | Successful relief on a toilet (Toilet.java + CommandHandler /pee /poop on toilet) | bladder ≥ 50 OR bowels ≥ 50 | per-worker-and-stat, 90s | `bladder`, `bowels`, `stat` |
| `accidentprone:accident_handled` | Involuntary accident processed (HandleAccident.java) | underwearType > 0; `isVoluntary == false` | per-worker, 180s | `isVoluntary`, `underwearType`, `isBladder` |
| `accidentprone:hydrate_threshold` | Drinking pushes hydration over threshold (PlayerEventListener.onPlayerDrink) | hydration ≥ Hydrate_Threshold | per-worker, 600s | `hydration` |
| `accidentprone:carry_pickup` | Caregiver successfully picks up their explicitly-listed ward (3s saddle gesture) | `target.isCaregiver(worker, true)` | per-pair, 10s | — |
| `accidentprone:carry_drop` | Caregiver drops a carried ward (in crib or in place) | `target.isCaregiver(worker, true)` | per-pair, 10s | `dropped_in_crib`, `crib_id` |

Cooldowns and thresholds are tunable in `plugins/Accident-Prone/integrations.yml` under `Events.Caregiver`, `Events.Crafter`, and `Events.Little`.

## Setting up Jobs (Reborn or AdvancedJobs)

1. Install Jobs Reborn (Spigot plugin name `Jobs`) or AdvancedJobs on your server.
2. Open `plugins/Accident-Prone/integrations.yml` and set `enabled: true` and `Jobs.enabled: true`.
3. In your Jobs plugin's own config, define a custom job for each action you want to pay out. Set the job's `type:` to the value in `integrations.yml`'s `Jobs.Action_Map` (e.g. `AccidentProne-Change`, `AccidentProne-CraftDiaper`).
4. Run `/diaperreload` — re-reads `integrations.yml`, clears in-memory cooldowns.

### Example — AdvancedJobs job (`plugins/AdvancedJobs/jobs/caregiver.yml`)

```yaml
actions-version: 2

type: AccidentProne-Change

variable:
  root: none

name: 'Caregiver'
required-progress: "5 * (%level% ^ 2)"
points-rewarded: "1 * %level%"
required-points: 0
premium: false
default-rewards: [ 1 ]
both-rewards: false
level-rewards: []
notify-at-percentages: [ 10, 25, 50, 75, 100 ]
whitelisted-worlds: []
blacklisted-worlds: []
blacklisted-regions: []
whitelisted-regions: []

item:
  material: white_wool
  name: '&f&lCaregiver Job &7[Lvl. %level%] '
  lore:
    - '&7 Change &f%required_progress% &7soiled littles.'
    - '&f• &lCurrent Level &8» &7Lvl. %level% '
```

AdvancedJobs's daily-rotation system (`config.yml: jobs-reset.enabled`) only activates a subset of jobs each day. If your custom job doesn't appear in `/jobs`, set `jobs-reset.enabled: false` (and bump `amount:` higher than your total job count).

### Troubleshooting

- **Payouts don't fire** — confirm `Integrations.enabled` AND `Jobs.enabled` are both `true`. Confirm the action name matches between `integrations.yml` and the Jobs config exactly. Check server log for `[JobsRebornHook]` or `[AdvancedJobsHook]` warnings.
- **AdvancedJobs payouts disabled at startup** — log shows `[AdvancedJobsHook] reflection probe failed: ...`. The `net.advancedplugins.jobs.Core` FQCN is pinned to AdvancedJobs 1.6.x; if a major version bump renames the main class, update the candidate list in `AdvancedJobsHook.initIfNeeded()`.
- **Wrong amount paid** — amount is set in the Jobs plugin's own config, not Accident-Prone. Adjust there.
- **Cooldown too aggressive** — tune `Events.Caregiver.Cooldown_*_Seconds` etc. in `integrations.yml`.

## Setting up BeautyQuests

BeautyQuests 2.x's stage YAML schema is incompatible with the legacy 1.x format. **Always create quests via the in-game GUI** (`/quests create`), not by hand-writing YAML. Then wire the trigger map to the quest the GUI created.

1. Install BeautyQuests on your server.
2. Set `BeautyQuests.enabled: true` in `integrations.yml`.
3. In-game: `/quests create`. Walk through the wizard. For each stage, pick any stage type — the description text is what matters; our hook calls `finishStage(quester)` directly when the matching action fires, bypassing the stage's built-in completion check.
4. Save the quest. Note its ID — visible in the editor, or check `plugins/BeautyQuests/data.yml` (`lastID:`).
5. Wire `Quest_Trigger_Map` in `integrations.yml`:

```yaml
BeautyQuests:
  enabled: true
  Quest_Trigger_Map:
    accidentprone:hydrate_threshold: '3:0:0'    # quest 3, branch 0, stage 0
    accidentprone:toilet_relief:     '3:0:1'
    accidentprone:pail_fill:         '3:0:2'
```

Format: `"questId"` (defaults to branch 0 stage 0) or `"questId:branchId:stageId"`. Empty string = no quest progression. The hook only calls `finishStage(quester)` if the player is currently *on* that stage — wiring an action to a stage they're not on is a safe no-op until they reach it.

### Example — solo "Self-Care Basics" quest

3-stage quest where each stage auto-completes via Accident-Prone:
- Stage 0: drink water (auto-completes via `accidentprone:hydrate_threshold`)
- Stage 1: use a toilet (auto-completes via `accidentprone:toilet_relief`)
- Stage 2: dispose of dirty diaper in a pail (auto-completes via `accidentprone:pail_fill`)

Wire as `'3:0:0'`, `'3:0:1'`, `'3:0:2'` (assuming questId=3 from `/quests create`).

### Troubleshooting

- **Quest progression disabled at startup** — same FQCN-pinning issue as AdvancedJobs. Update candidates in `BeautyQuestsHook.initIfNeeded()` if a major version bump moves classes.
- **Stage didn't advance** — confirm the player is *on* that stage in the editor. Confirm the trigger map references the right `questId:branchId:stageId`. Confirm the bus's state predicate passed (e.g. for `hydrate_threshold`, hydration must be ≥ `Hydrate_Threshold` and not within the 600s cooldown).

## Anti-farm policy

Each action has a state predicate and (optionally) a cooldown. The state predicate verifies the action was meaningful — `change` only pays if the diaper was actually wet/messy, `toilet_relief` only pays if bladder/bowels was meaningfully full, `accident_handled` only pays for involuntary accidents (voluntary `/pee` and `/poop` are filtered out). The cooldown blocks farming the same action repeatedly. Cooldowns are in-memory only — they reset on server restart and on player quit (`PlayerEventListener.onPlayerQuit`), but persist across re-logs within a session.

The `change`, `feed`, and `equip_armor_on_little` actions also require the worker to be on the target's caregiver list (or have public-caregiver mode enabled, per `PlayerStats.isCaregiver(uuid, true)`). Players can only earn caregiver payouts for littles who have actually accepted them.

## When to add an integration action

Every new player-visible gameplay event is a candidate for a Jobs payout or a BeautyQuests stage. The rule:

> **For every new feature you build, explicitly decide whether it should fire an `AccidentProneActionEvent`.** Default answer is yes for anything with a clear success-state moment (an action completed, a state crossed a threshold, a recipe finished). If you decide no, write the reason somewhere durable (commit message, PR description) so future maintainers know it was considered.

Examples of what counts as a "success-state moment":

- A player completed an action (changed someone, fed someone, used a toilet).
- A player crafted a custom item.
- A player crossed a threshold (hydration ≥ 80, incon level reached).
- A timed effect resolved (a Hypnosis trigger fired, a curse ticked).
- A social interaction finished (a Nanny dialog completed, a caregiver was added).

What usually doesn't warrant one:

- Pure UI events (opening a menu, browsing settings).
- Pure persistence events (saving stats — already covered by other actions).
- Internal state mutations with no player-facing meaning.

Once you decide an action belongs, follow "Adding a new integration action" below.

## Adding a new integration

The architecture is plugin-agnostic. To add a new hook (e.g. BetonQuest, Skript bridge, custom plugin):

1. Create `src/main/java/com/storynook/Integrations/<plugin>/<Plugin>Hook.java` implementing `org.bukkit.event.Listener`. Add an `@EventHandler onAction(AccidentProneActionEvent event)`.
2. Probe in `Plugin.onEnable`: `if (getServer().getPluginManager().getPlugin("<PluginName>") != null) registerEvents(new <Plugin>Hook(this), this);`
3. Add a config block to `integrations.yml` (mirror the `Jobs:` or `BeautyQuests:` shape).
4. Add a `<Plugin>_enabled` flag load in `Plugin.loadIntegrationsConfig()`.
5. Add a row to this wiki page.

Use pure reflection inside the hook to avoid hard Maven deps unless necessary. Pin FQCNs explicitly and degrade gracefully (log + disable) on probe failure.

## Configuration reference

See `src/main/resources/integrations.yml` for the full annotated default. Every key has an inline comment.

## See also

- Spec: `docs/superpowers/specs/2026-05-02-jobs-integrations-design.md`
- Plan: `docs/superpowers/plans/2026-05-02-jobs-integrations.md`
