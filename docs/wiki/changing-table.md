---
title: Changing Table
description: Placeable 2-block furniture — lie-down via packet NPC clone, 9-slot storage, recolor, dual-interaction hitbox.
---

# Changing Table

A placed 2-block furniture item. Players right-click the **top** to lie down in a sleeping pose (for a diaper change), right-click any **side or bottom** to open a small storage inventory, and recolor it by right-clicking with a carpet. Left-click (punch) to break it down.

Gated behind the `Nursery_Items` global flag — when off, the recipe doesn't register, the listener no-ops, and `/debug give` errors gracefully.

## Package layout

All in `com.storynook.furniture.changingtable`:

| Class | Role |
|---|---|
| `ChangingTable` | Immutable record — one placed table. Anchor cell + yaw; foot cell is *derived* from yaw, never stored. |
| `ChangingTableRegistry` | Static color registry (16 colors → CMD) + per-world live-table index (`byBarrierBlock`, ward occupancy). |
| `ChangingTablePdcKeys` | `NamespacedKey` bundle for PDC + the `accidentprone.changing_table` scoreboard tag. |
| `ChangingTableListener` | All Bukkit event handlers — placement, right-click dispatch, break, explosion/chunk handling, lay-down seating, movement lock. |
| `ChangingTablePlacementService` | Pure-Java placement math: `footOffset(yaw)`, the validation predicate. |
| `ChangingTableItem` | Builds the slime_ball `CustomModelData` item for a given color. |
| `ChangingTableRecipes` / `ChangingTableRecipeMatrixValidator` / `ChangingTableCraftingListener` | Crafting: one shaped recipe per color, validated by a matrix check. |
| `ChangingTableInventoryManager` | Per-table 9-slot inventory, YAML-persisted at `<dataFolder>/ChangingTables/<tableUuid>.yml`. |
| `ChangingTableInventoryFilter` | `isStorable(ItemStack)` — only *clean* changing supplies are accepted; dirty/worn items rejected. |
| `LaydownPoseNpc` | GSit-style packet-only NPC clone that renders the lying-down body. See below. |

## CMD allocation

`CMD = 630100 + colorIndex`. Range reserved: **630100–630199** (100 slots; 16 used). The item base material is `SLIME_BALL` — it needs a `range_dispatch` entry in the resource pack's `items/slime_ball.json` or it renders as a plain slimeball.

## Placement

Right-click the **top face** of a solid block while holding a changing-table item:

1. Yaw is snapped to the nearest cardinal (`ChangingTablePlacementService.snapYaw`). `footOffset(yaw)` gives the foot cell — it sits to the **player's left** of the anchor cell.
2. Both cells above the clicked block must be air, the player must not be standing in them, and both base blocks must be solid (`validate(...)`).
3. A synthetic `BlockPlaceEvent` per cell lets protection plugins (WorldGuard etc.) veto — silent no-op on veto, item retained.
4. Two `BARRIER` blocks are placed (anchor cell + foot cell).
5. One `ItemDisplay` (the visible model) plus **two** `Interaction` entities (one per barrier cell) are spawned. All three are persistent and the interactions carry the full table PDC + scoreboard tag.

### Display transform tuning

The visible model is an `ItemDisplay` in `NONE` transform mode. Its placement is driven by named constants at the top of `ChangingTableListener` — tune these, no model-file edit needed:

| Constant | Purpose |
|---|---|
| `SCALE_X` / `SCALE_Y` / `SCALE_Z` | Per-axis scale. Non-uniform — stretches the model's native `1.125 × 0.625 × 0.578` block bounds to a 2-wide × 1-tall × 1-deep table. |
| `TABLE_Y_LIFT` | Vertical offset in the display translation so the base sits on the ground. |
| `TABLE_BACK_SHIFT` / `TABLE_RIGHT_SHIFT` | Empirical world-space nudge (relative to placer facing) to seat the model on the barrier cells. |

The model rotation is `(180 - yaw)` around +Y — correct for all four cardinal placements.

## Right-click dispatch

`onInteractEntity` fires on a tagged `Interaction`. `resolveClickedFace` determines which of the 6 box faces was hit (the click lands exactly on one face plane — it does **not** use largest-axis-magnitude, which would misread the upper half of a side as the top):

- **Carpet in hand** → recolor (swap the `ItemDisplay`'s item, rewrite PDC on both interactions, return the old carpet). Same color = silent no-op.
- **Top face, empty hand** → self lay-down.
- **Top face, carrying a ward** → caregiver lay-down (the carried ward is laid; saddle returned to caregiver).
- **Any other face** → open the storage inventory.

### Interaction hitbox

Bukkit's `Interaction` hitbox is square (`width × width × height`), so a single entity can't be 2×1. Instead **two** interactions are spawned, one centered on each barrier cell — together they mirror the two barrier blocks. Size is set by `INTERACTION_WIDTH` / `INTERACTION_HEIGHT` (1.1 — slightly over 1.0 so they're easy to click).

## Lay-down — `LaydownPoseNpc`

The lying body is **not** the real player and **not** a real bed. The real player is teleported onto the mat, made invisible + no-gravity + movement-locked, and a **packet-only fake `ServerPlayer` clone** is broadcast to nearby viewers in `Pose.SLEEPING`. This is the GSit `/lay` approach — see [GSit's `mcv/v1_21_4/.../model/Pose.java`](https://github.com/Gecolay/GSit).

Why a clone instead of posing the real player: setting `Pose.SLEEPING` on the real player either fights the server's per-tick `updatePlayerPose()` (clip-in/out) or triggers the client-side "Leave Bed" overlay / day-skip. A separate packet NPC has none of those side effects — no chat, no screen darken, no phantom skip.

Key implementation details (all NMS via reflection, so the plugin still builds against the Bukkit API):

- **Fake bed packet** — a client-only `ClientboundBlockUpdatePacket` places a `WHITE_BED` at world-min under the mat, so the client's sleeping-pose renderer has an orientation anchor.
- **Triple teleport** — `ClientboundTeleportEntityPacket` is sent in the spawn bundle and again at tick+1 and tick+2, to out-shout the client's `LivingEntity` tick that re-snaps the entity to `sleepingPos`. Without this the body clips in and out.
- **Per-tick maintenance** (`perTick()`, driven by a 1-tick `BukkitTask`):
  - re-broadcasts an empty-equipment packet for the real (invisible) player so Paper's auto-resync doesn't make their gear reappear,
  - mirrors the real player's **live** equipment onto the clone (`broadcastNpcEquipment` — all 6 slots; removing the ward's leggings mid-laydown clears them from the clone within a tick),
  - re-asserts `setInvisible(true)` on the real player.
- **Nameplate hide** — the clone's random profile name (`ld_<uuid8>`) is added to a packet-only NMS `PlayerTeam` with `NameTagVisibility.NEVER`, broadcast per-viewer in the spawn bundle. Bukkit's main-scoreboard auto-sync proved unreliable for this.
- **Body alignment** — `LAY_Y_OFFSET` (vertical) and `LAY_XZ_OFFSET` (along the bed-facing axis) tune where the body lands on the mat.

Release is triggered by sneak, teleport, death, world-change, or quit. `onJoinStuckLaydown` defensively clears stuck invisible / no-gravity / sleep-ignored flags on join — and `onQuit` restores them *before* the NBT save so a disconnect mid-laydown doesn't persist a broken state.

## Storage inventory

9 slots (1 row), title "Changing Table". `ChangingTableInventoryFilter.isStorable` rejects anything that isn't a clean changing supply; a close-time sweep ejects violators back to the player. Persisted as YAML per table UUID; `loadOrCreate` guards `slot < SLOTS`, so a table whose YAML predates the 9-slot size silently drops the overflow slots.

## Teardown

`teardown(table)` is idempotent and reached from several paths:

- **Attack a tagged `Interaction`** (`onAttackInteraction`) — the normal break path. The interactions cover the barrier cells, so a left-click raycast hits an interaction before the block; we treat that attack as the break request.
- **`onLeftClickBarrier`** — fallback `LEFT_CLICK_BLOCK` path (still reachable from some angles / for the explosion + WorldEdit cases).
- **`onBarrierBreak`** (MONITOR `BlockBreakEvent`), **explosions**, **chunk load** orphan-cleanup.

Teardown releases any ward on the table, drops + deletes the inventory YAML, removes the `ItemDisplay` and **both** `Interaction` entities (found by scanning the two barrier cells, since only one UUID is stored in the record), clears the barriers, and drops the table item.

## Bug-prone areas

- **Two interactions, one stored UUID.** The record's `interactionUuid` is just whichever one `fromPdc` happened to read. Anything that needs to mutate or remove the interactions (teardown, recolor) must use `findInteractions(table)` to get both.
- **`LaydownPoseNpc` is reflection-heavy.** If a Paper revision renames an NMS symbol, `init()` logs once and the helper becomes a silent no-op for the JVM lifetime — lay-down just won't render.
- **Build hygiene.** Always `mvn clean package -Dmaven.test.skip=true` and verify the jar has no `Unresolved compilation problem` stubs before deploying — an IDE auto-build can poison `target/classes`.
