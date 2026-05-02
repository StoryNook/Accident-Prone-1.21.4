# Design Registry — design variants for underwear, pull-ups, and diapers

Adds named visual variants per absorbency category (e.g. "Girls Goodnite
Pull-Up Stars" looks different but uses pull-up mechanics). Mechanics are
unchanged; visuals are picked by a `(category, designId)` pair.

This page is the operator's manual. The implementation lives in
`com.storynook.DesignRegistry`; the workflow is automated by
`tools/generate_design_json.py` and the `/add-design` Claude skill.

## Mental model

Two independent integers per player:

| Field            | Purpose                                | Where set                           |
| ---------------- | -------------------------------------- | ----------------------------------- |
| `UnderwearType`  | Mechanics: bladder/bowel absorbency    | `applyUnderwear(type, designId)`    |
| `UnderwearDesign`| Visuals: which design within category  | `applyUnderwear(type, designId)`    |

`UnderwearType` is unchanged at all ~25 mechanics call sites. `UnderwearDesign`
only matters for two visual systems: the sidebar/action-bar sprite (via
`ScoreBoard.getUnderwearStatus`) and the in-world leggings model (via
`PantsCrafting.equipDiaperArmor`). Both call into `DesignRegistry`.

## Categories

| Category | Name           | Stages | STAGE_BLOCK |
| -------- | -------------- | ------ | ----------- |
| 0        | Underwear      | 4      | 4           |
| 1        | Pull-up        | 8      | 8           |
| 2        | Diaper         | 15     | 16          |
| 3        | Thick Diaper   | 25     | 32          |

`STAGE_BLOCK` is rounded up to the next power of two so `designId` blocks are
addressable by simple shift/add. Each design reserves a contiguous block of
codepoints per size variant.

## Codepoint formula

```
codepoint = SIZE_BASE[size] + CAT_OFFSET[category] + designId * STAGE_BLOCK[category] + stageIndex
```

| Constant     | values                                |
| ------------ | ------------------------------------- |
| `SIZE_BASE`  | `[0xED00, 0xE200, 0xE780]`            |
| `CAT_OFFSET` | `[0x000, 0x080, 0x180, 0x380]`        |
| `STAGE_BLOCK`| `[4, 8, 16, 32]`                      |

`size`: `0` = small (action bar low), `1` = normal (sidebar), `2` = big
(action bar high).

Custom designs (`designId >= 1`) start at ``. Existing legacy sprites
(``–``) and HUD icons (``–``) are untouched and reserved
for `designId=0`.

## Folder naming convention (important)

Name your folder with a **category prefix** so the script knows what kind of
item it is — no `--category` flag needed:

| Prefix                                       | Category     | Stages |
| -------------------------------------------- | ------------ | ------ |
| `undies-...` or `underwear-...`              | Underwear    | 4      |
| `pullup-...` or `pull-up-...` / `pull_up-`   | Pull-up      | 8      |
| `diaper-...`                                 | Diaper       | 15     |
| `thick-...` or `thick-diaper-...` / `thick_diaper-` | Thick Diaper | 25     |

Example: `pullup-goodnite-stars/` — category 1, design name `goodnite_stars`
(prefix is stripped to form the giveKey). The PNG count just **validates**
the prefix; a mismatch warns loudly so you know your filenames are off.

If you don't use a prefix, the script falls back to PNG count and tells you
to rename the folder. If neither prefix nor PNG count gives a valid answer,
the script errors out and asks you to pass `--category` or rename.

## Adding a design (the easy path)

1. **Drop your stage PNGs** in
   `src/main/resources/StoryNook1.2.4/assets/minecraft/textures/custom/special/<prefix>-<name>/`
   following the suffix convention below.
2. **Run the auto-detect script:**
   ```
   python3 tools/generate_design_json.py src/main/resources/StoryNook1.2.4/assets/minecraft/textures/custom/special/<prefix>-<name>
   ```
   This:
   - reads the category from the folder prefix (validated against PNG count)
   - infers `designId` from the next free slot in that category
   - reserves the next free CMD block (tracked in `tools/cmd_registry.json`)
   - **directly edits `images.json`** to add 3*N font entries
   - if `icons/` is present, writes `models/item/<design>_<state>.json` per
     state and appends `range_dispatch` entries to
     `assets/minecraft/items/slime_ball.json` (sorted by threshold)
   - if `armor/clean.png` is present, writes
     `assets/minecraft/equipment/<equippableKey>.json` and copies the source
     PNG to `assets/minecraft/textures/entity/equipment/humanoid_leggings/<equippableKey>.png`
   - writes `tools/pending_design.json` (including an `equippableKey` field)
     with everything `/add-design` needs
3. **Run `/add-design`** in Claude Code. It appends one `register(...)` line
   to `DesignRegistry.init()`, runs `mvn package`, and archives the manifest
   to `tools/applied/`.
4. **Done.** `/debug give 1 <player> Pullup <name>` works.

If you prefer to review first, `--dry-run` prints the JSON block to stdout
and modifies nothing. Override any inferred value with
`--category` / `--design-id` / `--cmd` / `--name` / `--display-name`.

### What "I added new designs, add them to the plugin" looks like in a fresh window

You can drop one or more new prefix-named folders into the special textures
directory, then in a clean Claude Code session say something like
"I added new designs, please add them." The model will:

1. Read CLAUDE.md → "Adding a design variant" → this wiki page.
2. List `textures/custom/special/`, compare against `tools/applied/*.json`
   to find new folders.
3. For each new folder: run `python3 tools/generate_design_json.py <folder>`
   (prefix → category, no questions asked), then `/add-design` to append the
   register line.
4. Run `mvn package` once at the end to confirm the build is green.

If a folder has no prefix and an ambiguous PNG count, the model will stop
and ask you which category it should be — that's the only manual moment.

## Folder layout: three places, same convention

A complete design touches three folders. The script handles all three; each is
optional except the first.

```
1. textures/custom/special/<prefix>-<name>/        (HUD sprites + icons)
   ├── *_clean.png, *_wet1.png, *_mess1.png, ...   (HUD sprite stages — required)
   └── icons/                                       (inventory icons — optional)
       ├── clean.png      (required if icons/ exists)
       ├── wet.png        (optional — falls back to clean)
       ├── messy.png      (optional — falls back to wet→clean)
       └── wetmessy.png   (optional — falls back to messy→wet→clean)

2. <design-folder>/armor/                          (worn-armor — optional, 1.21.4 native)
   └── clean.png          (required if folder exists; wired into
                           assets/minecraft/equipment/<equippableKey>.json
                           and textures/entity/equipment/humanoid_leggings/<equippableKey>.png)

3. tools/applied/<name>.json                       (manifest archive — written by /add-design)
```

- **HUD sprite stages**: the small glyphs shown in the sidebar / action bar.
  Required. Follow the stage-suffix convention below.
- **Inventory icons** (`icons/` subfolder): the slime-ball textures shown when
  the item is in the hotbar / inventory. The script generates
  `models/item/<design>_<state>.json` for each icon PNG and appends entries
  to the `range_dispatch` model in `assets/minecraft/items/slime_ball.json`
  (one entry per state CMD; entries are kept sorted ascending by threshold).
  Only `clean.png` is required; missing states fall back through the chain
  (wetmessy → messy → wet → clean).
- **Worn-armor texture** (`armor/clean.png`): the body texture rendered on
  the player's legs while the design is equipped. The script writes a
  vanilla equipment definition at
  `assets/minecraft/equipment/<equippableKey>.json` (humanoid_leggings layer)
  and copies `clean.png` to
  `assets/minecraft/textures/entity/equipment/humanoid_leggings/<equippableKey>.png`.
  The `equippableKey` defaults to the design name (giveKey) and is recorded
  in `tools/applied/<name>.json` so `/add-design` can pass it to the
  `register(...)` call. Without an `armor/` folder, the worn item shows as a
  vanilla leather leggings (no custom rendering). OptiFine CIT is no longer
  used — see [resource-pack-1-21-4.md](resource-pack-1-21-4.md).

The script emits a `note:` for every folder it skips and tells you exactly
what's missing. Drop in additional files and re-run any time — it's
idempotent.

## Stage filename convention

Each PNG must end with `_<suffix>.png`. Suffixes per category:

- **Underwear** (4): `clean`, `wet1`, `mess1`, `wet1mess1`
- **Pull-up** (8): `clean`, `wet1`, `wet2`, `wet3`, `mess1`, `wet1mess1`, `wet2mess1`, `wet3mess1`
- **Diaper** (15): `clean`, `wet1`–`wet4`, `mess1`, `wet1mess1`–`wet4mess1`, `mess2`, `wet1mess2`–`wet4mess2`
- **Thick** (25): same pattern with `wet1`–`wet5` and `mess1`–`mess3`

Missing files fall back to the previous existing PNG and the script prints a
note. (Useful for placeholders during development — the `wet3mess1` slot is
commonly missing.)

## What `register(...)` does

```java
register(int category, int designId,
        int cleanCmd, int wetCmd, int dirtyCmd, int wetDirtyCmd,
        String giveKey, String displayName);
```

Stores:
- the formula-computed sprite stage arrays for all 3 sizes
- the 4 in-world leggings CMDs for clean / wet / dirty / wet+dirty
- the `giveKey` (variation argument for `/debug give`) and `displayName`
  (text shown on the item)

After registration, all of these work automatically:
- `/debug give <amount> <player> Pullup <giveKey>` — handled by `Give.java`
  via `DesignRegistry.findByGiveKey`.
- Right-click-to-equip the item — `Changing.applyChange` calls
  `DesignRegistry.findByCleanCmd` to set `(UnderwearType, UnderwearDesign)`.
- Sidebar sprite — `ScoreBoard.getUnderwearStatus` calls
  `DesignRegistry.getStages(type, designId, size)`.
- In-world leggings model — `PantsCrafting.equipDiaperArmor` calls
  `DesignRegistry.getVisibleCmd(type, designId, wetness, fullness)`.

There is **no per-design Java code anymore**. One `register(...)` line wires
up everything.

## Lookup API (read-only)

```java
DesignRegistry.DesignDef def = DesignRegistry.findByCleanCmd(cmd);
DesignRegistry.DesignDef def = DesignRegistry.findByGiveKey(category, key);
List<DesignDef> defs        = DesignRegistry.getDesignsForCategory(category);
ItemStack stack             = DesignRegistry.createItem(def);
char[] stages               = DesignRegistry.getStages(category, designId, size);
int cmd                     = DesignRegistry.getVisibleCmd(category, designId, wetness, fullness);
```

Falls back to `designId=0` (legacy sprites) when an unregistered design is
requested. No NPEs from missing designs.

## Files touched per new design

| File                                                                       | Edit                                                |
| -------------------------------------------------------------------------- | --------------------------------------------------- |
| `assets/minecraft/font/images.json`                                        | +3*N font entries (script)                          |
| `assets/minecraft/items/slime_ball.json`                                   | +1 to 4 `range_dispatch` entries (script, if icons) |
| `assets/minecraft/models/item/<design>_<state>.json`                       | new model file per icon (script, if icons)          |
| `assets/minecraft/equipment/<equippableKey>.json`                          | new equipment def (script, if `armor/clean.png`)    |
| `assets/minecraft/textures/entity/equipment/humanoid_leggings/<eq>.png`    | new worn-armor texture (script, if `armor/clean.png`) |
| `DesignRegistry.init()`                                                    | +1 `register(...)` line (`/add-design`)             |
| `tools/cmd_registry.json`                                                  | reserve CMD block (script)                          |
| `tools/applied/<name>.json`                                                | archived manifest with `equippableKey` (`/add-design`) |

That's it. No edits to `Give.java`, `Changing.java`, `underwear.java`,
`PantsCrafting.java`, `ScoreBoard.java`, `Plugin.java`, `plugin.yml`, or
`leather_leggings.json`.

## Backward compatibility

- `setUnderwearType(int)` still works; it now delegates to
  `applyUnderwear(type, 0)`. Direct admin overrides reset design to default.
- Player YAMLs without `UnderwearDesign` load with `0` — they look identical
  to before.
- Legacy sprite codepoints (``–``) are kept verbatim in
  `DesignRegistry.init()` via `registerLegacy(...)`. Existing font entries
  are not edited.

## Future: Patreon-gated designs

The intended split is a single config flag (e.g.
`Settings_Menu.Custom_Designs`). When off, `DesignRegistry.getStages` /
`getVisibleCmd` / `findByCleanCmd` short-circuit to the legacy `designId=0`
fall-back so custom-design items render as their default category
visuals (or refuse to equip). Build with the flag off = free build; build
with the flag on = members build. No code stripping needed; gating happens
at the registry boundary.

## Backups & legacy reference

- `docs/backups/ScoreBoard_pre_designregistry.java` — verbatim copy of the
  pre-refactor ScoreBoard for reference.
- `DesignRegistry.java` itself contains the original hardcoded char arrays
  as a `// LEGACY REFERENCE — DO NOT REMOVE` comment block at the top.
