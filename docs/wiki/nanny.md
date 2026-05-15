---
title: Nanny NPC system
description: Phases 1–6 component reference for the Nanny NPC subsystem.
---

# Nanny NPC system

The largest feature in the plugin. Citizens2-backed NPC caregiver that watches over wards: changes their diapers, feeds them, navigates to find them, chats, applies discipline, refills water bottles, places tired wards in cribs, and (optionally) gates an AI chat tier behind Patreon/Subscribestar membership **or** a LuckPerms-assignable permission node. Phases 1–6 plus the post-Phase-6 work below are all shipped; the next unbuilt item is autonomous washer use (see *Pending work*).

**Design spec (read this first when starting any Nanny work):**
`docs/superpowers/specs/2026-04-30-nanny-npc-design.md`

**Membership integration spec:**
`docs/superpowers/specs/2026-04-30-membership-integration-design.md`

**Dialogue expansion spec:**
`docs/superpowers/specs/2026-04-30-nanny-dialogue-expansion.md`

---

## Phase 1 — Core NPC (shipped)

- Component layout under `com.storynook.nanny`:
  - `NannyData` — pure data + per-Nanny YAML at `<dataFolder>/nannies/{nannyUUID}.yml`. **Not part of `PlayerStats`** — separate file format like `DiaperPails`. Inner enums: `MoodTier {SWEET, CARING, STRICT, WARDEN, CUSTOM}`, `ChestMode`, `CraftingMode`, `ChatRespondTo`, `ChatTier`. Adding a field touches only `save()` + `load()` (single class, no LoadStats/SavePlayerStats/CreateDefaultStats triplet).
  - `NannyEntity` — Citizens2 NPC wrapper. Uses `CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name)` + `SkinTrait.setSkinName(playerName)` to copy another player's skin (URL-based skins via `setTexture(value, signature)` are a future enhancement). All public methods early-return when `!plugin.citizensEnabled`.
  - `NannyManager` — orchestrator + `Listener`. Owns `allNannies` (UUID→NannyData), `activeNannies` (UUID→NannyEntity), `wardToNannies` index, and `pendingNannyCreations` (player UUID → spawn location, used to bridge the Nanny-Egg-prompts-for-name flow). Handles `PlayerJoinEvent`/`PlayerQuitEvent` lifecycle, `PlayerInteractEvent` for the Nanny Egg, and `EntityDamageEvent`/`EntityTargetEvent` to make Nannies invincible and mob-aggro-immune. Public API: `getNannyForOwner`, `addWard`, `removeWard`, `deleteNanny`, `setFollowMode`, `updateNannyName`, `updateNannySkin`, `setHome`, `summonToPlayer`, `createNanny`. `Plugin.getNannyManager()` is the canonical accessor.
  - `NannyEventLog` — Phase-1 stub: `log(NannyEventType, wardUUID, details)` appends to in-memory list (max 100), async YAML write. Full event types and AI context wiring is Phase 4.
- Lifecycle: Nanny spawns when *any* ward or owner is online; despawns when *all* relevant players are offline. If owner has not logged in within `Nanny_Owner_Timeout_Days` (default 14), the Nanny goes dormant — won't spawn even for wards — until the owner returns. Server-wide cap via `Nanny_Max_Server_Nannies` (default 50).
- Multi-ward model: one owner per Nanny; owners add wards via `NannyMenu`'s Wards tab. One Nanny per owner.
- Nanny Egg (`items/Nanny.createNannyEgg(String displayName)`): admin-granted only via `/nanny give <player> [name]`. `Material.ZOMBIE_SPAWN_EGG`, `CustomModelData 629001`, PDC key `nanny_egg` (`PersistentDataType.BYTE`). **Not craftable** — sold in the server shop. If the egg has been renamed (display name ≠ "Nanny Egg"), that name is used at spawn; otherwise the player is prompted via `awaitingInput` type `nannyName`.
- `NannyMenu` (in `com.storynook.menus`) follows the `SettingsMenu` static-opener + `InventoryClickEvent` pattern. Three tabs in Phase 1: General (rename / skin / follow / sethome / summon), Wards (paginated player list), Behavior (mood tier — visual only in Phase 1, chat enabled, chat scope cycle). Tab navigation reads stripped display-name `"Tab: General"` etc.
- `NannyCommand` (in `com.storynook.Commands`) — Phase-1 subcommands `give | list | remove | info | settings | sethome | summon | reload`. Routed through `CommandHandler.onCommand` via the `nanny` entry in `dualCommands`; `CommandHandler` delegates to `plugin.getNannyCommand().handle(sender, args)` (and `tabComplete` similarly).
- `PlayerEventListener.onPlayerChat` handles two awaiting-input types: `nannyName` (creates a Nanny if `pendingNannyCreations` has a location for the player; otherwise renames the owned Nanny) and `nannySkinUrl` (`"default"` clears the skin). Both branches re-open the Nanny menu's General tab on success.
- Config: `Nanny:` block in `config.yml` is **nested**, not flat. Loaded into `globalConfig` as `Nanny` (boolean from `Nanny.enabled`), `Nanny_Owner_Timeout_Days`, `Nanny_Max_Server_Nannies`, `Nanny_Default_Home_Radius`, `Nanny_Default_Mood`, `Nanny_Default_Chest_Mode`, `Nanny_Default_Crafting_Mode`, and a flattened `Nanny_Chat_*` family (including the `Nanny_Chat_AI_*` keys reserved for Phase 4). The old root-level `Nanny: true` flag and `NannyNPCskin:` setting were removed from `config.yml`.
- `nanny_messages.yml` ships a stub with `care_reminder`, `keyword_wet`, `found_ward` keyed by mood tier (SWEET/CARING/STRICT/WARDEN). Wired in Phase 4.
- `CustomModelData` allocation: `629001` is now Nanny Egg. Add this to the resource pack's font/item JSON if changing.

---

## Phase 2 — Care Behaviors (shipped)

- `NannyCareEngine` (in `com.storynook.nanny`) — single repeating `BukkitTask` (40 ticks). For each active Nanny × each online ward (and the owner if not also a ward), evaluates four priority-ordered conditions and dispatches one task at a time: (1) wetness/fullness > `changeThreshold` → change, (2) `underwearType == 0` → equip, (3) hunger < `feedThreshold` → feed, (4) hydration < `hydrationThreshold` → hydrate (melon slice). No real navigation in Phase 2 — Nanny `entity.teleportTo(ward.getLocation())` to act. The engine starts on the first Nanny spawn and stops when the last despawns. Citizens2-guarded (no-op if `plugin.citizensEnabled == false`). Started/stopped from `NannyManager.spawnNanny`/`despawnNanny`.
- `NannyInventoryManager` (in `com.storynook.nanny`) — supply chain abstraction. 18-slot per-Nanny inventory stored on `NannyData.personalInventory` (ItemStack[]) and serialised via the YAML `personalInventory:` list. Smart filter (`isUsableItem`) accepts 626xxx CustomModelData diaper-family items, a small food whitelist (bread/cookie/melon/apple/golden_apple/cooked meats/baked_potato/carrot), and any leather leggings. `takeOne(NannyData, predicate)` checks personal inventory first, then chests according to `chestMode` (`INVENTORY_ONLY`/`SELECTED`/`ALL`). `tryCraftFood` (BASIC: bread or cookies) and `tryCraftDiaper` (ALL/EVIL: stuffer 626007 + tape 626008 + leather leggings → 626006) are simulated — they consume ingredients and return a fresh ItemStack without opening a real crafting GUI, gated on a crafting table block existing within `homeRadius` of `homeLocation`. EVIL-specific recipes (Curse of Binding, laxative food) are deferred to Phase 5; in Phase 2 EVIL is treated equivalent to ALL. `prepareFollowSupplies(NannyData)` tops up the personal inventory to ≥ 3 clean diapers + ≥ 3 food before the Nanny departs home in follow mode.
- New `NannyData` fields: `changeThreshold` (default 70), `feedThreshold` (default 14), `hydrationThreshold` (default 30), `personalInventory` (`ItemStack[18]`). All persisted via `NannyData.save`/`load`.
- New static helpers (extracted to make the existing event-driven listeners callable from the autonomous Nanny path):
  - `Changing.applyChange(Player target, ItemStack cleanDiaper)` — performs the diaper-change stat mutation (delegates to existing private `resetAndUpdateStats`). Replaces the inline stat block in `handleInteraction`. **Why a non-actor signature:** `handleRightClickHold` reads the actor's mainhand every tick and runs a boss-bar UI on the actor — a Citizens2 NPC actor would NPE. The helper bypasses all actor-side UI.
  - `FeedingAction.applyFeed(Player target, ItemStack food)` — applies food/laxative/melon-slice stat side effects without the caregiver-auth gate. `FeedingAction.plugin` was made `static` to enable the static helper (matches the existing static-`plugin` anti-pattern).
  - `EquipArmor.applyEquip(Player target, ItemStack legging)` — thin delegation to `Changing.applyChange` (the equip-on-empty case is functionally a "change" in this domain). Existing `equipArmor(sender, target)` (player command for non-diaper armor) is unchanged.
  - `DiaperPail.deposit(Location origin, double radius, ItemStack soiled)` — finds the nearest pail ArmorStand (custom name prefix `Pail_<UUID>`) within `radius` blocks, loads its YAML via `loadInventory`, adds the item via `Inventory.addItem`, saves via `saveInventory`. Used by `NannyCareEngine.doChange` to dispose of the soiled diaper after a change.
- New menu tab: **Supplies** (`NannyMenu.openSupplies`, title `"Nanny Supplies"`). 4-row inventory: row 0 = tabs (4 tabs now), row 1 = chest mode cycler / crafting mode cycler / 3 threshold paper items (click to prompt via `awaitingInput`), rows 2–3 = the 18-slot personal-inventory editor (real Bukkit drag-and-drop slots). The personal inventory persists on `InventoryCloseEvent` for `TITLE_SUPPLIES`. Three new `awaitingInput` types in `PlayerEventListener.onPlayerChat`: `nannyChangeThreshold`, `nannyFeedThreshold`, `nannyHydrationThreshold` (clamped 0–100/0–20/0–100 respectively, save and re-open the Supplies tab on confirm).
- New `Nanny.Default_Change_Threshold` / `Default_Feed_Threshold` / `Default_Hydration_Threshold` config keys, loaded into `globalConfig` as `Nanny_Default_*`.
- `NannyManager` exposes `getCareEngine()`, `getInventoryManager()`, `getEventLog(UUID)`. `getEventLog` lazily instantiates a per-Nanny `NannyEventLog` and caches it in `eventLogs` (`Map<UUID, NannyEventLog>`).

---

## Phase 3 — Navigation (shipped)

- `NannyNavigator` (in `com.storynook.nanny`) — per-Nanny Citizens2 Navigator wrapper; one instance is created/started in `NannyManager.spawnNanny` and stopped/removed in `despawnNanny`. Exposes `navigateTo(Location)`, `setFollowTarget(Player)` (entity-follow, no fixed target), `seekTo(Player)` (navigate + mark seekingWardUUID + log SEEKING_WARD), `cancelNavigation()`, `isNavigating()`, `getSeekingWardUUID()`, `clearSeekingWard()`. Runs a 10-tick `BukkitTask` that does two things: (1) **stuck-teleport fallback** — if `currentTarget` is set but Citizens2 is no longer navigating (cancelled by `stationaryTicks(600)` or arrival) AND the NPC is > 3 blocks from target, teleport to target and log STUCK_TELEPORT; (2) **door/lever scan** — 2-block radius around the NPC for `Material.OAK_DOOR`/`SPRUCE_DOOR`/`BIRCH_DOOR`/`JUNGLE_DOOR`/`ACACIA_DOOR`/`DARK_OAK_DOOR`/`CRIMSON_DOOR`/`WARPED_DOOR`/`IRON_DOOR` and `LEVER`. Doors → `Openable.setOpen(true)`, auto-close after 60 ticks. Levers → toggle `Powerable.setPowered`, auto-restore after 60 ticks. Iron door without an adjacent lever → wait 100 ticks then teleport past `currentTarget`. `pendingRestore` set keys (`world,x,y,z`) prevent double-toggling. All Citizens2 calls behind `plugin.citizensEnabled`; `navigateTo`/`setFollowTarget` fall back to teleport when disabled.
- `NannyCareEngine` upgraded to navigate-then-act: `evaluateAndAct` now computes `needsChange/Equip/Feed/Hydrate` flags first, calls `navigator.navigateTo(ward.getLocation())` if `!isWithinActionRange(entity, ward)` (3-block threshold), and acts only on a later cycle once close. The four action methods (`doChange`/`doEquipDiaper`/`doFeed`/`doHydrate`) no longer teleport. Added per-tick `checkHomeEnforcement` (when not in follow mode and homeWorld is set, navigate back to home if outside `homeRadius`; teleport if in wrong world; logs RETURNED_HOME), `checkFollowBoundary` (when in follow mode and owner > `homeRadius × 2` from home, set `followMode=false`, navigate home, send "I'll wait here for you!" message, log RETURNED_HOME), `checkSeekArrival` (when ward UUID matches `navigator.getSeekingWardUUID()` and within 3 blocks, send "Found you! ♥" and log FOUND_WARD; if not navigating, re-seek; cross-world → teleport).
- `NannyManager.setFollowMode(uuid, follow, ward)` now calls `inventoryManager.prepareFollowSupplies(data)` before enabling follow, then dispatches to `navigator.setFollowTarget(ward)` or `navigator.cancelNavigation()`. Falls back to `entity.setFollowMode(follow, ward)` only when navigator is null (Citizens2 absent or Nanny not yet spawned).
- `NannyManager.onPlayerJoin` triggers `navigator.seekTo(player)` for every Nanny where `seekEnabled` is on AND the joining player is > 10 blocks (sq dist > 100) or in a different world from the Nanny. Cross-world: teleport Nanny to ward's world first, then `seekTo`. Owner timeout/dormancy logic from Phase 1 still runs first — seek only fires when the Nanny is being spawned for this player.
- `NannyEntity` exposes `getLocation()` (current world location, null if not spawned) and `getNpc()` (raw Citizens2 `NPC` reference, null if not created).
- `NannyData` adds `seekEnabled` (boolean, default `true`, persisted as `seekEnabled:` in YAML).
- `NannyEventLog.NannyEventType` adds `SEEKING_WARD` and `STUCK_TELEPORT`.
- New menu item: COMPASS "Hide and Seek" toggle in the Behavior tab (slot 25), toggles `data.setSeekEnabled(...)` on click and re-opens the tab.

---

## Phase 4 — Chat & Event Log (shipped)

- `NannyChatEngine` (in `com.storynook.nanny`) — Listener for `AsyncPlayerChatEvent` (skipped when `plugin.VentureChat` is true; `NannyVentureChatHook` delegates back via `fireTriggers(Player, String)`). Per-Nanny throttle (default 30s, key `Nanny_Chat_AI_Cooldown_Seconds`). Skips messages with < `Nanny_Chat_Min_Words` words. For each active Nanny in `Nanny_Chat_Local_Radius` of speaker: logs `WARD_CHAT` (always), then if `chatEnabled` + `chatRespondTo` permits + throttle clear, evaluates triggers (name mention → `greeting`; keyword stem `wet/messy/hungry/thirsty/tired/cute` → `keyword_<stem>`; `Nanny_Chat_Ambient_Chance` percent roll → `idle_ambient`). Picks a mood-keyed response from `nanny_messages.yml` (CUSTOM tier falls back to CARING; missing tier falls back to CARING). Broadcasts `[Name] line` to all players within local radius in same world. Logs `NANNY_CHAT`. AI tier path: gated on `MembershipProvider.isUnlocked(ownerUUID)` AND non-empty `Nanny_Chat_AI_Endpoint`. AI HTTP call uses `HttpURLConnection` on a single-thread `ExecutorService`; on success or any failure, returns to main thread via `runTask` and either broadcasts the AI text or falls back to BASIC silently. Provides `reload()` for `/diaperreload`.
- `MembershipProvider` interface (in `com.storynook.nanny`). Phase 4 shipped `AlwaysLockedProvider` as the always-locked stub; Phase 6 replaced it with the composite + Permission/Patreon/Subscribestar implementations. The provider is instantiated in `NannyManager.init` and exposed via `getMembershipProvider()`.
- `NannyVentureChatHook` (in `com.storynook.Integrations`) — registered in `Plugin.onEnable` only when `plugin.VentureChat` is true. Mirrors `VentureChatHook`. Resolves speaker via `e.getMineverseChatPlayer().getPlayer()` and delegates `VentureChatEvent` to `NannyChatEngine.fireTriggers`.
- `nanny_messages.yml` — fully populated: 14 categories (`care_reminder`, `keyword_wet/messy/hungry/thirsty/tired/cute`, `found_ward`, `arrived_home`, `low_supplies`, `discipline` (WARDEN-only), `greeting`, `farewell`, `idle_ambient`) × 4 mood tiers.
- `HandleAccident.handleAccident` now logs `WARD_HAD_ACCIDENT` for every active Nanny whose ward list (or owner) matches the affected player. Wrapped in `try/catch (Throwable)` so logging cannot break the accident pipeline.
- `NannyMenu` Behavior tab adds a "Chat Tier" toggle (slot 19): WRITABLE_BOOK when AI is unlocked for the menu opener (BOOK when locked). BASIC ↔ AI cycle on click; lore explains "AI tier requires membership" when locked. Click guard: only fires when stripped display name equals "Chat Tier" (avoids the "Tab: General" BOOK).
- New `globalConfig` keys: `Nanny_Chat_Min_Words` (3), `Nanny_Chat_Ambient_Chance` (1, percent), `Nanny_Chat_AI_Endpoint` ("" disables AI even when unlocked).
- `NannyEventLog` actively logs `WARD_CHAT`, `NANNY_CHAT`, `WARD_HAD_ACCIDENT`. Other defined event types (LOCKED_WARD, FORCE_FED, LEASHED_WARD, HYPNOTIZED_WARD) remain reserved for Phase 5b. (PLACED_IN_CRIB now active in Phase 5a.)

---

## Phase 5a — Strict-Tier Discipline (shipped)

- `Capability` enum (in `com.storynook.nanny`) — keys for the mood-tier action matrix: `BASIC_CARE`, `POTTY_REMINDERS`, `ARMOR_LOCK`, `CRIB_PLACEMENT`, `BLOCK_CAREGIVERS`, plus reserved Phase 5b keys (FORCE_FEED_LAXATIVE, BINDING_LEGGINGS, LEASH_WARD, HYPNOSIS_USE, ROOM_LOCKDOWN, EVIL_CRAFTING).
- `NannyPolicy` (in `com.storynook.nanny`) — static gate. `allows(NannyData, Capability)` returns true based on mood tier (SWEET/CARING/STRICT/WARDEN have hard-coded sets) OR, when `data.getMoodTier() == CUSTOM`, reads `data.getCustomSettings().get(cap.name())`. `BLOCK_CAREGIVERS` is a special case: at STRICT/WARDEN it consults `data.isBlockOtherCaregivers()` (defaults false; toggle exposed via menu). `minTier(Capability)` returns the lowest tier that grants a capability for menu lore display.
- `NannyData` adds `customSettings` (`Map<String, Boolean>` — Capability name → enabled, used only when moodTier == CUSTOM) and `lockedArmor` (`Map<UUID, Boolean>` — wardUUID → leggings-slot locked). Both persisted as YAML config sections (`customSettings:` and `lockedArmor:`).
- `ArmorLockListener` (in `com.storynook.nanny`) — registered in `Plugin.onEnable`. At `EventPriority.LOWEST`, cancels `InventoryClickEvent` on raw slot 38 (leggings) and `MOVE_TO_OTHER_INVENTORY` shift-clicks of leggings items, but only when the clicker is a ward of an active Nanny with `ARMOR_LOCK` permission AND `lockedArmor.get(ward) == true`. All vanilla leggings types covered (leather/chainmail/iron/golden/diamond/netherite).
- `Changing.handleRightClickHold` — first statement: try-block scanning `mgr.getAllNannies()` for any Nanny watching the target with `BLOCK_CAREGIVERS` permission. If actor != owner, sends red `[Name] Only Name or her owner may change this ward.` and returns. Wrapped in `try/catch (Throwable)` so policy lookup never breaks the change pipeline.
- `NannyChatEngine.startReminderTask` — 1200-tick (60s) `BukkitTask` started from `NannyManager.init`, stopped via `shutdown()`. For each active Nanny with `POTTY_REMINDERS` permission and `chatEnabled`: scans wards within `Nanny_Chat_Local_Radius`, picks the first whose `bladder >= 70` OR `bowels >= 70`, broadcasts a `care_reminder.<TIER>` line via `broadcast()`. Reuses the per-Nanny `lastResponse` throttle map (default 30s). Also ties into the existing `shutdown()` so graceful disable cancels the task.
- `NannyCareEngine.doChange` now unlocks-around-change: if `ARMOR_LOCK` is permitted AND the ward is currently locked, the lock is temporarily cleared before `Changing.applyChange` and re-set after `DiaperPail.deposit`. State-restore + save happen only when the lock was actually flipped, so nothing changes for non-locked wards.
- `NannyCareEngine.doPlaceInCrib` — new action. Triggered from the no-care branch of `evaluateAndAct` when ward food level ≤ 6 (sleep proxy) AND `CRIB_PLACEMENT` is permitted AND ward is not already a vehicle passenger. Scans within `data.getHomeRadius()` for an `ArmorStand` whose custom name equals literal `"Crib"` (matches the name set by `CribPlacement.java`), teleports the ward, calls `addPassenger`. Logs `PLACED_IN_CRIB`. Navigator handoff: when out of range, the engine instead navigates the Nanny toward the ward and acts on the next cycle.

  **Dual crib-system lookup (added 2026-05-03):** `doPlaceInCrib` now uses
  `CribRegistry.findNearestCrib(location, radius)` which searches new
  display-entity cribs first and falls back to legacy invisible-armor-stand
  cribs. New cribs require a vanilla bed in the cavity (`crib.hasBed()`);
  bedless cribs are skipped. New cribs use soft containment (the
  `CribContainmentTask`) instead of `addPassenger`. Legacy cribs continue
  to use `addPassenger` exactly as before. See
  `docs/superpowers/specs/2026-05-03-crib-redesign-design.md`.
- `NannyMenu` Behavior tab: three new items (slots 27/29/31): **IRON_BARS "Armor Lock"** (toggles lock on every current ward + the owner; capability-gated; sends red message if mood tier is too low), **OAK_FENCE "Crib Placement"** (informational — placement runs automatically when permitted), **BARRIER "Block Other Caregivers"** (toggles `data.blockOtherCaregivers`; lore explains tier requirement when not yet effective). Each item's lore reflects current state and shows mood-tier requirement when not currently allowed.

---

## Phase 5b — Warden Discipline + Custom Tier (shipped)

- `Hypno.applyHypnosis(Player target, ItemStack clock)` — public static helper extracted from `handleInteraction`. Reads `hypnosis` / `HypnoTriggerWord` / `HypnoType` PDC keys from the clock; gates on `stats.getHypnoPermission() > 0`; calls `stats.cleanExpiredTriggers()`; respects `Hypno_Max_Triggers` cap; builds a `HypnoTrigger(word, type, expiry, null)` (4th arg is **String** casterUUID, Nanny passes null since it isn't a player); calls `stats.addHypnoTrigger(...)` and `SavePlayerStats.savePlayerStats(target)`. Returns true on success.
- `NannyInventoryManager.tryCraftDiaper` extended: when `craftingMode == EVIL`, the produced diaper has `Enchantment.BINDING_CURSE` applied with red "Curse of Binding" lore. Recipe unchanged (stuffer + tape + leather leggings).
- `NannyInventoryManager.tryCraftLaxative` — new method. EVIL-only. Recipe: 1 bread + 1 cocoa beans + 1 sugar (with crafting table in `homeRadius`). Produces a `Material.BREAD` ItemStack tagged with the existing `laxative_effect` PDC key (matches `ItemManager.Laxative` for `FeedingAction.applyFeed` pipeline compatibility), dark-purple "Laxative" name.
- `NannyData.lockedRoomBlocks` — `List<String>` of `"world,x,y,z"` keys. Persisted as `lockedRoomBlocks:` YAML list. Populated by `/nanny lockroom`, cleared by `/nanny unlockroom`.
- `RoomLockListener` (in `com.storynook.nanny`) — registered in `Plugin.onEnable` after `ArmorLockListener`. At `EventPriority.LOWEST`, cancels `PlayerInteractEvent` (door/lever right-click) and `BlockBreakEvent` for any ward whose Nanny has `ROOM_LOCKDOWN` permission AND the affected block's `world,x,y,z` is in `data.getLockedRoomBlocks()`. Sends red "Nanny says no." on cancel.
- `/nanny lockroom` — collects every door/trapdoor/lever within a 16-block cube (vertical ±4) around the issuing owner into the owner's Nanny's `lockedRoomBlocks` list (clears existing first). Requires `ROOM_LOCKDOWN`. `/nanny unlockroom` clears the list. Tab-completer updated.
- `NannyCareEngine` — four new action methods + `tryDisciplineActions` dispatcher. `doForceFeedLaxative` calls `tryCraftLaxative` then `FeedingAction.applyFeed`; logs `FORCE_FED`. `doEquipBindingLeggings` takes/crafts a diaper, force-applies `Enchantment.BINDING_CURSE`, calls `EquipArmor.applyEquip`; logs `EQUIPPED_WARD` with detail "binding". `doLeash` calls `target.setLeashHolder((LivingEntity) npcEntity)` (Citizens2 NPC entity is a Player-type LivingEntity); logs `LEASHED_WARD`. `doHypnotize` finds a hypnosis-PDC clock in the personal inventory, calls `Hypno.applyHypnosis`, returns the clock to inventory regardless of result; logs `HYPNOTIZED_WARD` only when the helper returned true. `tryDisciplineActions` runs in `evaluateAndAct`'s no-care branch *after* basic-care + crib placement; uses `isWithinActionRange` to navigate-then-act. Trigger thresholds: bowels < 30 → laxative, distance > `homeRadius/2` from home (same world) → leash, leggings without Curse of Binding → bind, otherwise → hypnotize (throttled to once per 5 minutes per ward via `lastHypnotize` map).
- `NannyMenu` Advanced tab (5th tab, slot 4 in tab strip, NETHER_STAR icon, title "Nanny Advanced") — visible always. For non-CUSTOM moods, shows yellow "CUSTOM tier required" info paper. For CUSTOM: shows 10 LIME_DYE/GRAY_DYE toggles (POTTY_REMINDERS, ARMOR_LOCK, CRIB_PLACEMENT, BLOCK_CAREGIVERS, FORCE_FEED_LAXATIVE, BINDING_LEGGINGS, LEASH_WARD, HYPNOSIS_USE, ROOM_LOCKDOWN, EVIL_CRAFTING) laid out across two rows skipping decorative slot 18. Click toggles `data.customSettings.<cap.name()>`, saves, re-opens.
- `NannyEventLog` actively logs `FORCE_FED`, `LEASHED_WARD`, `HYPNOTIZED_WARD` (in addition to the Phase 5a `PLACED_IN_CRIB`). LOCKED_WARD remains reserved — armor-lock state lives on `NannyData.lockedArmor` directly, no event firing for it.

---

## Phase 6 — Membership Integration (shipped)

- New packages: `com.storynook.nanny.crypto` (CryptoService) and `com.storynook.nanny.membership` (CompositeMembershipProvider, PermissionMembershipProvider, PatreonMembershipProvider, SubscribestarMembershipProvider, OAuthHelper).
- `CryptoService` — AES-256-GCM at-rest encryption. Key file at `<dataFolder>/.crypto.key` (32 random bytes), overridable via `Crypto.Key_Path` config (supports `${ENV_VAR}` expansion). Encrypted values use `enc:<base64>` prefix. Initialised in `Plugin.onEnable` immediately after `mergeConfigFiles("config.yml")` and before `loadGlobalConfig()`; failure disables the plugin.
- `Plugin.readEncryptedConfigString(path)` — single entry point that decrypts on read and migrates plaintext to encrypted in-place on first save. Used for `Nanny.Membership.{Patreon,Subscribestar}.Client_Secret` and `Nanny.Chat.AI.API_Key` (the existing Phase 4 AI key is now encrypted-at-rest via the same mechanism).
- `Plugin.buildMembershipProvider()` — assembles `CompositeMembershipProvider` from enabled sub-providers; returns `AlwaysLockedProvider` when `Nanny.Membership.enabled` is false or no sub-providers are enabled. Re-invoked by `/diaperreload` via `nannyManager.setMembershipProvider(buildMembershipProvider())` so config changes pick up without a restart.
- New `Settings_Menu.Membership` flag gates persistence of six new `PlayerStats` fields: `nannyMembershipProvider`, `nannyMembershipEmail` (encrypted), `nannyMembershipRefreshToken` (encrypted), `nannyMembershipTier`, `nannyMembershipStatus` (UNLINKED/ACTIVE/LAPSED), `nannyMembershipLastCheck` (ISO-8601). All three of LoadStats/SavePlayerStats/CreateDefaultStats updated.
- OAuth code-paste flow: player runs `/nanny link <patreon|subscribestar>`, clicks the chat link, signs in on the provider, copies the `code|state` string from the static redirect helper page (`docs/security/oauth-redirect.html`), runs `/nanny link <provider> <code|state>` in chat. CSRF state generated via `OAuthHelper.generateState(UUID)`, validated via `consumeState` (15-min TTL, single-use). Token exchange + tier query happen in async tasks; result message returned to main thread via `runTask`.
- `PermissionMembershipProvider` — checks `Player.hasPermission(node)` at call time. No persistence needed. For LuckPerms + DiscordSRV setups; `Allow_Linking: false` hides the OAuth flow when this is the only path.
- `MembershipProvider.refresh(UUID)` triggered on player join (`NannyManager.onPlayerJoin` first statement) and via admin command `/nanny refresh [player]`.
- New commands: `/nanny link <provider> [code|state]`, `/nanny unlink`, `/nanny refresh [player]` (admin-gated). Tab completer supports them and offers `patreon`/`subscribestar` for the second arg of `link`.
- Reference helper page (`docs/security/oauth-redirect.html`) — single static HTML, parses `?code=&state=` from URL + `#provider` from hash, displays `code|state` as a copyable string. Self-hosters can deploy to GitHub Pages / Cloudflare Pages / etc. and override `Nanny.Membership.Redirect_URI` (must also re-pin the redirect URI in their own OAuth client registration).
- Admin docs: `docs/security/hardening.md` (threat model, key path, backup advice), `docs/membership-setup.md` (per-provider setup guide).

### Membership providers — OR semantics

`CompositeMembershipProvider` wraps every *enabled* sub-provider and returns unlocked if **any** of them unlocks the player. The three sub-providers are independent and can be enabled in any combination:

- **`PermissionMembershipProvider`** — `isUnlocked` is a live `Player.hasPermission(node)` check (`refresh` is a no-op, no persistence). Config: `Nanny.Membership.Permission.{enabled,node}`. This is the LuckPerms / DiscordSRV-role path — no OAuth, no encrypted tokens. A player with the node gets AI chat; revoke the node and they lose it immediately.
- **`PatreonMembershipProvider`** / **`SubscribestarMembershipProvider`** — OAuth code-paste link flow, encrypted refresh tokens, tier checks.

`Plugin.buildMembershipProvider()` returns `AlwaysLockedProvider` when `Nanny.Membership.enabled` is false **or** no sub-provider is enabled — the master `Nanny.Membership.enabled` flag must be on for *any* provider (including Permission) to be built. So the permission-only setup is: `Nanny.Membership.enabled: true` + `Nanny.Membership.Permission.enabled: true` + a `node`. `Settings_Menu.Membership` is **not** required for the Permission path (it only gates persistence of the six OAuth-related `PlayerStats` fields).

---

## Dialogue Expansion (shipped)

Spec: `docs/superpowers/specs/2026-04-30-nanny-dialogue-expansion.md`. Makes the BASIC (non-AI) tier feel like a companion — she narrates her own actions, reacts to in-game events, and idles aloud on a timer. All content is YAML-only in `nanny_messages.yml` (57 categories × 4 mood tiers); adding lines never touches Java.

- **`NannyChatEngine.speakIfNearby(ward, category, throttleKey, throttleMs, priority)`** — single entry point for all event-driven Nanny speech. Per-Nanny `MIN_LINE_GAP_MS` (3s) floor prevents back-to-back lines; per-`(nanny, throttleKey, ward)` throttle prevents one trigger spamming. Priority constants (`PRI_DISCIPLINE` > `PRI_MOB`/`PRI_ACCIDENT` > `PRI_CARE` > `PRI_LIFECYCLE` > `PRI_DISCOVERY` > `PRI_IDLE`) are informational for now — the throttles already prevent overlap.
- **Post-action commentary** — `NannyCareEngine.speakPostAction` fires `changed_ward` / `equipped_diaper` / `fed_ward` / `hydrated_ward` / `tucked_in` after the matching care action. `speakDiscipline` fires `force_fed_lax` / `bound_in_diaper` / `leashed_ward` / `hypnotized_ward` (plus a generic `discipline` fallback) after Phase 5b actions.
- **Reactive scans** (`NannyCareEngine.tryReactiveScans`, runs in the no-care branch when the Nanny is within action range) — `ward_low_health` (HP < 6), `night_warning` (outdoors at night, 1h throttle), `lava_warning` (LAVA within 4 blocks, per-vein throttle), `mob_warning` / `mob_warning_creeper` (hostiles near a *busy* ward — SWEET/CARING/CUSTOM tiers only; STRICT/WARDEN stay quiet by design and let the hit land).
- **Ore spotting** (`tryOreSpotting`) — only when the Nanny is in follow mode following the **owner**. Narrates `ore_spotted_diamond` / `_emerald` / `_ancient_debris` / `_gold` (gold 50%-suppressed) for veins within 4 blocks, with a 30-min per-vein TTL cache.
- **Ambient idle** (`startAmbientTask`, 100-tick task) — each Nanny picks a random 4–6 min delay; any broadcast (from any source) resets it. Context-aware category pick: `idle_following` / `idle_raining` / `idle_night` / `idle_outdoors` / `idle_at_home`, falling back to `idle_ambient`.
- **Potty reminders** (`startReminderTask`, 1200-tick task) — Phase 5a; broadcasts `care_reminder` when a nearby ward's bladder/bowels ≥ 70 and the Nanny has `POTTY_REMINDERS` capability.
- **Placeholders** — `{ward}` / `{you}` / `{nanny}` / `{biome}` / `{time}` substituted in any line by `applyPlaceholders`.
- **Lifecycle lines** — `greeting` (on join + chat name-mention), `arrived_home` (follow-boundary return), `found_ward` (seek arrival), `nanny_summoned` (after `/nanny summon`), `respawn_after_death`, `low_supplies`.

---

## Autonomous behaviors beyond core care (shipped)

These run in the no-care branch of `NannyCareEngine.evaluateAndAct`, one interaction per tick, matching the engine's "one task per cycle" discipline.

- **Water-bottle refill** (`tryRefillBottles`) — if the Nanny holds empty glass bottles, is in the ward's world and within 32 blocks of the ward, she finds a water source within 8 blocks (`WATER_CAULDRON` or source-level `WATER`), navigates to it, and fills one bottle per tick (`GLASS_BOTTLE` → water `POTION`, decrementing cauldron level). **Toilet cauldrons are skipped** — identified by an `IRON_TRAPDOOR` directly above. `doHydrate` prefers a water bottle over a melon slice and returns the empty bottle to her inventory, closing the loop. Ungated by follow/seek — she's walking to *water*, not the ward.
- **Crib placement** — `doPlaceInCrib` now uses `CribRegistry.findNearestCrib` (new display-entity cribs first, legacy `"Crib"`-named ArmorStands as fallback). New cribs require a vanilla bed (`crib.hasBed()`) and use soft containment (`CribContainmentTask` + `PlayerStats.setContainedInCribId`); legacy cribs use `addPassenger`. Kill-switch: `Crib_New_System=false` forces the legacy-only path. See `docs/superpowers/specs/2026-05-03-crib-redesign-design.md`.

### Follow & seek — current behavior

- **Follow mode** (`NannyData.followMode`): `NannyManager.setFollowMode` tops up supplies via `prepareFollowSupplies`, then `NannyNavigator.setFollowTarget(ward)` (Citizens entity-follow, 2.5-block distance margin). Re-engaged on owner join and on `PlayerChangedWorldEvent`. `NannyCareEngine.checkFollowBoundary` auto-disables follow if the owner strays past `homeRadius × 2` from home — she navigates home and speaks `arrived_home`.
- **Seek mode** (`NannyData.seekEnabled`, default true, COMPASS toggle in the Behavior tab): on join, if seek is on and the player is > 10 blocks away or in another world, `NannyNavigator.seekTo` is issued (cross-world teleports the Nanny first). `checkSeekArrival` speaks `found_ward` within 3 blocks and clears the seek flag.
- **`tryApproachWard` gating**: when a ward needs care and the Nanny is out of action range, she only walks to them if `seekEnabled` is true and `followMode` is false. **If both follow and seek are off, she stays put — the ward must come within the 3-block action range.** Cross-world is never approached from the tick loop; `PlayerChangedWorldEvent` brings her over.

---

## Personality-driven AI prompt (shipped)

Spec: `docs/superpowers/specs/2026-05-15-nanny-personality-prompt-design.md`. Replaces the old single-adjective AI mood prompt (`"a caring caretaker"`) with a building-blocks system. Each Nanny has a per-call system prompt assembled from voice exemplars sampled live from `nanny_messages.yml` plus capability fragments from a new admin-editable resource, plus dynamic world context.

- **New resource:** `src/main/resources/nanny_personalities.yml` (merged into data folder by `Plugin.mergeConfigFiles` like other YAML resources; `/diaperreload` re-reads it). Two top-level keys: `always_on:` (capabilities every Nanny has: `BASIC_CARE`, `WATER_REFILL`, `ORE_SPOTTING`) and `capabilities:` (one fragment per `Capability` enum value the Nanny is permitted to use). Each entry is short first-person prose. Admins override any fragment by editing the data-folder copy.
- **New `NannyData` field:** `customTone` (`MoodTier` enum, default `CARING`). For CUSTOM-mood Nannies, the AI's voice samples are drawn from the `customTone` tier instead of the meaningless "CUSTOM" tier. Set via a `BOOK` cycler in the Advanced tab (visible only when mood = CUSTOM). Cycles `SWEET → CARING → STRICT → WARDEN → SWEET`. Persisted alongside `moodTier`. Both null and `CUSTOM` clamp to `CARING` defensively.
- **New config knob:** `Nanny.Chat.AI.Voice_Sample_Count` (default `6`). Number of example lines sampled from `nanny_messages.yml` and embedded as `Speak in this style — here are example lines you have said before:` in the system prompt. `0` disables the exemplar block. Sampled categories are voice-bearing only: prefixes `idle_` and `keyword_`, plus exact match `greeting`. Action-specific categories like `low_supplies`, `mob_warning`, `ore_spotted_*`, `discipline` are excluded — they're status reports, not voice tells.
- **`buildSystemPrompt` assembly order** (`NannyChatEngine.java`):
  1. Identity preamble (mood label uses `voiceTier`, not `mood`, so CUSTOM Nannies present as `"a sweet caregiver"` etc. — `mood.name()` of CUSTOM would be a meaningless adjective)
  2. Voice exemplars from `nanny_messages.yml` for the active tier
  3. `Your abilities:` — composed via `resolveFragmentList(data, mood, ward)`:
     - Always-on keys (`BASIC_CARE`, `WATER_REFILL`, `ORE_SPOTTING`) always prepended; deduped against the gated loop
     - Capability iteration: for fixed moods, `NannyPolicy.allows(data, cap)`; for CUSTOM, `data.getCustomSettings().get(cap.name())`
     - Cross-feature gate via `isFeatureGloballyEnabled` (HYPNOSIS_USE needs `Hypno`; FORCE_FEED_LAXATIVE needs `Messing` + `Diapers`; BINDING_LEGGINGS / EVIL_CRAFTING need `Messing` + `Diapers` + `Binding_Diapers`) — the AI never advertises tools the plugin will refuse to fire
  4. World/ward context — time of day, weather, soiled diaper, hungry, thirsty (phrased as "Your little..." not "the ward...")
  5. Optional admin override from `Nanny.Chat.AI.System_Prompt`
- **`{hypno_triggers}` placeholder:** substituted in any fragment with the comma-joined `word (type)` list from `PlayerStats.getHypnoTriggers()` for the current ward. Empty list → friendly fallback string. Lets the AI know the *actual* active words on this ward so she can weave them in.
- **`/diaperreload` rebuilds chat engine + re-merges YAML.** Edit `nanny_personalities.yml` or `nanny_messages.yml`, reload, see the change live. `CommandHandler` calls `mergeConfigFiles` for both files plus `nannyManager.getChatEngine().reload()` (which calls `loadMessages()` + `loadPersonalities()`).

---

## AI chat behavior (shipped)

Several refinements to how the AI tier actually decides when and how to reply:

- **AI tier bypasses the keyword/name gate.** `NannyChatEngine.fireTriggers` previously dropped any message that didn't mention the Nanny's name or contain a `KEYWORD_MAP` stem (`wet`, `messy`, `hungry`, etc.) — which made BASIC-tier sense (canned lines feel repetitive on every message) but broke AI-tier conversational flow. AI tier now responds to anything that clears the existing filters (`minWords`, `chatRespondTo`, `Local_Radius`, throttle). BASIC tier still uses the keyword gate.
- **`<SKIP>` sentinel for selective AI replies.** System prompt instructs the model: reply with only the literal `<SKIP>` (case-insensitive) for messages that don't need a response — server chatter, exclamations, single-word fragments, messages addressed to other players. The AI callback strips and respects it before broadcasting. Concrete examples are embedded in the prompt (`"oh dear"` → SKIP; `"Nanny what are you doing?"` → reply).
- **Inventory awareness.** A `Your supplies right now:` block is appended to the system prompt summarizing the Nanny's `personalInventory` (clean diapers, soiled diapers, food, water bottles, empty bottles, laxatives, hypno clocks, coal, cursed diapers). Stops the model from hallucinating `"I crafted one for you"` when the inventory is empty. Predicates reuse `NannyInventoryManager.isCleanDiaper / isWaterBottle / isAnyFood / isEmptyGlassBottle` plus new helpers for soiled/hypno/laxative detection.
- **Action-implying phrase ban.** System prompt explicitly forbids `"down the hatch"`, `"eat up"`, `"open wide"`, `"here you go"`, `"hold still"`, etc. — phrases that read as a promise of an action that doesn't actually fire (the care engine handles actions on its own clock; chat is decoupled). The little gets confused when the Nanny says these without follow-through.
- **VentureChat hook bounces to main thread.** `NannyVentureChatHook.onVentureChat` fires on `VentureChat`'s async scheduler thread. Calling `engine.fireTriggers` directly there meant `nanny.getLocation()` and friends ran async → silent `IllegalStateException` swallowed by VentureChat's handler wrapper → chat never reached the engine. The hook now wraps in `Bukkit.getScheduler().runTask(plugin, () -> ...)` mirroring the vanilla `AsyncPlayerChatEvent` path. Also strips chat color codes via `ChatColor.stripColor(e.getChat())` so keyword/name matching sees plain text.
- **`Max_Tokens` config + thinking-model awareness.** New `Nanny.Chat.AI.Max_Tokens` (default `4000`) replaces a hard-coded `200`. The 200 cap silently broke reasoning models like `qwen3.5:9b` — they fill the budget on internal reasoning before emitting `content`, so the OpenAI extractor saw an empty string and the plugin fell back to a non-existent `"general"` BASIC category, dropping the reply. Symptom: GPU spins on Ollama but the Nanny says nothing. Bump default to 4000 so reasoning models have room to think AND respond; reply brevity is controlled by the system-prompt instruction `"one or two sentences"`, not by token cap. On paid OpenAI you can drop this lower for a cost ceiling.
- **Empty-content warning log.** When `extractAssistantContent` returns empty after a `2xx` response, the AI callback now logs `[Nanny AI] empty content from <endpoint> — if using a reasoning/thinking model (qwen3, deepseek-r1, etc.) bump Nanny.Chat.AI.Max_Tokens, otherwise pick a non-thinking model (llama3.1:8b, dolphin-llama3).` No more silent debug.
- **Separate throttle for chat-initiated vs background replies.** Previously, `fireTriggers` and `speakIfNearby` (used by ambient idle, potty reminders, post-action commentary) both consumed and checked the same `lastResponse` map — so whenever the Nanny said anything autonomously, your next 30s of chat got silently dropped. There are now two maps: `lastChatReply` (touched only by `fireTriggers`) and `lastResponse` (touched by both). Background lines no longer steal the user-chat cooldown.
- **Configurable `Chat_Cooldown_Seconds` default lowered to `5`.** The 30s default was modeled on paid-OpenAI cost concerns; with local Ollama (the typical setup) it just blocked back-and-forth conversation. Admins on paid endpoints can bump it back up.
- **`Min_Words` default lowered to `1`.** The original 3-word minimum was a coarse anti-spam filter; the `<SKIP>` mechanism handles spam more intelligently. One-word replies (`"hey"`, `"ow"`) can now register with AI-tier Nannies. BASIC tier still benefits from the keyword gate.

### Recommended models for AI tier

| Model | Pulled size | Latency | Notes |
|---|---|---|---|
| `llama3.1:8b` | 4.9 GB | ~2-3s | Solid default for chat. Non-thinking. Recommended. |
| `dolphin-llama3:latest` | 4.7 GB | ~10-15s | RP-flavored fine-tune. Slower but better persona. |
| `gemma4:26b` | 17 GB | ~10-20s | Higher quality, slower. |
| `qwen3.5:9b` | ~6 GB | ~5-20s + reasoning | **Thinking model.** Works at `Max_Tokens: 4000+`. |

Avoid pure reasoning models (`deepseek-r1`, etc.) on the OpenAI-compatible endpoint unless you bump `Max_Tokens` high — they shove their answer into a `reasoning` field that the standard extractor doesn't read.

---

## UX improvements (shipped)

- **Right-click Nanny NPC → settings menu (owner only).** `NannyManager.onNannyRightClick` listens for Citizens `NPCRightClickEvent`. Matches the clicked NPC against `activeNannies`, verifies clicker UUID equals `data.getOwnerUUID()`, then opens `NannyMenu.open(player, plugin)` — the same UI as `/nanny settings`. Non-owners get no-op (existing Citizens default).
- **Custom Tone cycler in Advanced tab.** `Material.BOOK` item at slot 13 of the Advanced inventory, visible only when `mood == CUSTOM`. Click cycles voice tier. Slot-skip logic added to the capability loop so the BOOK never gets overwritten.

---

## Shared soiled-diaper item factory

`NannyCareEngine.doChange` previously hand-rolled a `LEATHER_LEGGINGS` stand-in with CMDs `626015-626018` — but those are the *worn-leggings* textures (`item/pants`, `pants_wet`, etc.), not inventory-icon textures, so the Nanny's pail-bound items showed as soiled pants icons instead of soiled diaper icons. The player-side change flow already had correct dispatch (the `underwear.createStinkyDiaper / createWet* / createDirty*` factories produce items with the right CMD + owner tag + soiling values + rash points).

Extracted that dispatch into `Changing.createDirtyDiaperItem(target, underwearType, wetness, fullness, rashPoints)` and call it from `NannyCareEngine.doChange`. AI-Nanny and player change now produce identical soiled items. Snapshot pre-change state (including `underwearType` and `rashPoints`) before calling `Changing.applyChange` since that resets stats.

---

## Behavior scoring & discipline

Spec: `docs/superpowers/specs/2026-05-15-naughty-nice-points-design.md`.
Plan: `docs/superpowers/plans/2026-05-15-naughty-nice-points.md`.

### Concept

Per-(Nanny, ward) bidirectional behavior score (-100 to +100) + fast-decaying streak (-50 to +50). Captures gameplay signals (punching the Nanny, chat sentiment, summon compliance, proactive hydration) and feeds them into discipline decisions for both BASIC and AI chat tiers.

### Components

- **`BehaviorScoreboard`** — owns score/streak maps. Clamps, sycophancy gate (score < 0 AND streak > +20 → halve positive deltas), lazy streak decay (1/2 per real-time minute), direction-reset on sign flip.
- **`BehaviorSignals`** — Bukkit listener. Translates events into score deltas + fires `AccidentProneActionEvent`. Punch detection (−15), chat sentiment with throttled +2/−3 deltas, proactive water/food consume +1.
- **`DisciplineDispatcher`** — shared decision/enactment surface. BASIC: score-band threshold ladder + random pick from eligible. AI: parses `<PUNISH:foo>` and `<REWARD:praise>` tags from chat replies.
- **`DiaperPunishment`** — new capability: lock ward in binding diaper, block toilet, escalate to cursed pants on 3 violations or score floor.

### BASIC threshold ladder

| Score | Persistent slot cap | Events allowed |
|---|---|---|
| > -20 | 0 | — |
| -20 to -40 | 0 | event (laxative, hypno) |
| -40 to -65 | 1 | + 1 persistent |
| -65 to -90 | 2 | + 2 persistent |
| -90 to -100 | 3 | full stack |
| ≤ -100 | 3 + auto-escalate diaper to cursed pants | full stack |

Persistent slot inventory: `{LEASH_WARD, BINDING_LEGGINGS, DIAPER_PUNISHMENT}`. Score recovery un-stacks the least-severe persistent first.

### AI tag syntax

```
<PUNISH:laxative>           - force-feed a laxative
<PUNISH:leash>              - leash to your hand
<PUNISH:binding>            - equip a binding-cursed diaper
<PUNISH:hypno>              - speak a hypno trigger word
<PUNISH:diaper:Nd>          - diaper-punishment for N Minecraft days (1-30)
<REWARD:praise>             - suppress your next queued punishment for 5 minutes
```

Tags appended at the end of the AI's chat reply on their own line. Stripped from the visible message before broadcast. Per-action cooldown (default 5 minutes) prevents tag spam.

### Diaper punishment

Initial state: binding-cursed thick diaper (CMD 626006 + Binding_Curse), `underwearType=3`, `layers=4`. Timer set to `world.getFullTime() + (days × 24000)`. `Toilet.canRelieveOnToilet` + `/pee` + `/poop` consult `DiaperPunishment.isBlocked` first.

Escalation (3 violations OR score ≤ -100): strip diaper, force-equip cursed pants (CMD 626015 + Binding_Curse + Unbreakable + DARK_RED display name). Stays until timer expires.

+20 behavior score earned during active punishment shaves 1 Minecraft day off the timer.

### Config knobs (`Nanny.Behavior:` in config.yml)

See spec for the full list of 16 knobs. Most-tuned in practice: `Discipline_Cooldown_Minutes`, `Diaper_Punishment_Max_Days`, `Praise_Grace_Seconds`.

---

## Pending work

- **Autonomous washer use** — spec `docs/superpowers/specs/2026-05-14-nanny-washer-design.md` (draft, **not yet implemented**). Would teach the Nanny to carry soiled cloth pants (`626015–626018`) to a player-placed washer, load it, top up coal fuel, and retrieve washed pants (`626022–626033`) — mirroring the existing `DiaperPail.deposit` flow for disposable diapers. Adds `NannyCareEngine.tryDoLaundry`, `NannyInventoryManager.isSoiledPants`, a `WasherRegistry`, and `LAUNDRY_STARTED` / `LAUNDRY_RETRIEVED` event types.
- **Naughty/nice points + AI-mediated discipline** — see "Behavior scoring & discipline" section above. Per-(Nanny, ward) behavior score, BASIC threshold ladder, AI tag emission, diaper-punishment subsystem with cursed-pants escalation. 11 new `AccidentProneActionEvent` action IDs.
