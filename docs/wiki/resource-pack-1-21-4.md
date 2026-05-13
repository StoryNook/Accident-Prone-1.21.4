---
title: Resource pack — 1.21.4 format
description: Pack format 46, range_dispatch items, equipment definitions, leather_leggings trim model.
---

# Resource pack — 1.21.4 format reference

Technical reference for the post-migration resource pack layout. The
companion pack lives under `src/main/resources/StoryNook1.2.4/` and is
re-zipped into `StoryNook1.2.4.zip` on every Maven build via the antrun
hook in `pom.xml`. This page documents the file shapes, conventions, and
the reasoning behind a few non-obvious choices.

For the design-author workflow ("drop PNGs → run script → run /add-design"),
see [`design-registry.md`](design-registry.md). This page is the underlying
format reference.

## Pack format

```jsonc
// pack.mcmeta
{
  "pack": {
    "pack_format": 46,
    "description": "StoryNook resource pack"
  }
}
```

`pack_format: 46` matches Minecraft 1.21.4. Older formats (e.g. 8/9 used
under 1.19) are not accepted by 1.21.4 clients and will pop a "this pack
is too old" warning.

## Item-model dispatch — `assets/minecraft/items/<base>.json`

In the 1.21.4 format, the per-item dispatch logic moves out of
`models/item/<base>.json` and into a new top-level
`assets/minecraft/items/<base>.json` document. Each such document has a
single `model` field whose `type` selects how the renderer chooses a
concrete model:

- `minecraft:model` — direct, no dispatch
- `minecraft:range_dispatch` — pick by a numeric property (we use
  `minecraft:custom_model_data`)
- `minecraft:select` — pick by a discrete property (e.g. `minecraft:trim_material`)
- `minecraft:composite` — combine multiple sub-models into one render

### Why `range_dispatch` and not `select` for CMDs

In Spigot, `ItemMeta.setCustomModelData(int)` writes the integer into the
`minecraft:custom_model_data` component's `floats` array at index `0`.
The 1.21.4 `range_dispatch` resolver reads exactly that — a float — and
matches by threshold. The `select` resolver matches against discrete
strings/ints and does not see the float-array shape, so plugin-set integer
CMDs do not match. **All custom-model dispatch in this pack uses
`range_dispatch` on `minecraft:custom_model_data` for that reason.**

If you ever switch to `setCustomModelDataComponent(...)` to write a String,
Boolean, or full float-array yourself, you can use `select` — but the
default `setCustomModelData(int)` path requires `range_dispatch`.

### Example: `slime_ball.json`

```jsonc
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "scale": 1.0,
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/slime_ball"
    },
    "entries": [
      { "threshold": 625000.0, "model": { "type": "minecraft:model", "model": "minecraft:item/sound_enable" } },
      { "threshold": 626060.0, "model": { "type": "minecraft:model", "model": "minecraft:item/goodnite_stars_clean" } }
      // ... more, sorted ascending by threshold
    ]
  }
}
```

The `entries` array MUST be sorted ascending by `threshold`. The
generator (`tools/generate_design_json.py`) keeps it sorted whenever it
splices a new entry. The fallback model is rendered when the item has no
matching `custom_model_data` value.

### Example: `leather_leggings.json` (mixed vanilla trim + custom CMDs)

The leather leggings file is special because it has to support both:
- vanilla armor-trim variants (no custom CMD, but a smithing trim applied)
- custom plugin items keyed by CMD (no trim, plugin-set CMD)

We achieve this with a **nested `select` inside the `range_dispatch`'s
`fallback`**. The `range_dispatch` fires first on CMD; if no entry
matches, it falls through to the `select` which checks
`minecraft:trim_material`; if no trim case matches, the plain
`item/leather_leggings` model is returned.

```jsonc
{
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "scale": 1.0,
    "fallback": {
      "type": "minecraft:select",
      "property": "minecraft:trim_material",
      "cases": [
        { "when": "minecraft:netherite", "model": { "type": "minecraft:model", "model": "minecraft:item/leather_leggings_netherite_trim" } },
        { "when": "minecraft:diamond",   "model": { "type": "minecraft:model", "model": "minecraft:item/leather_leggings_diamond_trim" } }
        // ... all 10 vanilla trim materials
      ],
      "fallback": {
        "type": "minecraft:model",
        "model": "minecraft:item/leather_leggings"
      }
    },
    "entries": [
      { "threshold": 626001.0, "model": { "type": "minecraft:model", "model": "minecraft:item/diaper_thick" } },
      { "threshold": 626009.0, "model": { "type": "minecraft:model", "model": "minecraft:item/diaper" } }
      // ... custom plugin CMDs
    ]
  }
}
```

Resolution order at runtime:

1. Item has CMD `626009` → `range_dispatch` finds the entry → renders
   `item/diaper`. (Trim component is ignored even if set.)
2. Item has no CMD but has `Trim{material: minecraft:netherite}` →
   `range_dispatch` falls through → `select` matches → renders the trim
   variant.
3. Item has no CMD and no trim → `range_dispatch` falls through →
   `select` falls through → renders plain `item/leather_leggings`.

This is the only place in this pack where two dispatch types are nested.

## Worn-armor textures — equipment definitions

In the legacy 1.19 setup, OptiFine CIT was used to swap the worn-armor
texture per CMD via `.properties` files under
`assets/minecraft/optifine/cit/...`. OptiFine support was dropped in
1.21.4 and the same problem is now solved by **equipment definitions**
plus the vanilla `minecraft:equippable` item component.

### File shape

```jsonc
// assets/minecraft/equipment/<equippableKey>.json
{
  "layers": {
    "humanoid_leggings": [
      { "texture": "minecraft:<equippableKey>" }
    ]
  }
}
```

The `texture` value is a namespaced ID resolved against
`assets/<namespace>/textures/entity/equipment/humanoid_leggings/<id>.png`.
For vanilla, `humanoid_leggings` is the layer slot for leggings — there
are parallel slots `humanoid` (helmet/chestplate/boots), `wings` (elytra),
`horse_body`, `wolf_body`, etc.

### Where the texture lives

```
assets/minecraft/textures/entity/equipment/humanoid_leggings/<equippableKey>.png
```

This is the body texture rendered on the player when the leggings are
worn. Different from the held-item model (which is still controlled by
`leather_leggings.json`'s `range_dispatch`).

### Why it replaced OptiFine CIT

| Concern        | OptiFine CIT (legacy)                                   | Vanilla equippable (1.21.4)                          |
| -------------- | ------------------------------------------------------- | ---------------------------------------------------- |
| Selection key  | `components.custom_model_data=<n>` in `.properties`     | `equippable.asset_id` component on the ItemStack     |
| File location  | `assets/minecraft/optifine/cit/.../<name>.properties`   | `assets/minecraft/equipment/<id>.json`               |
| Texture path   | `<same dir>/<state>.png`                                | `textures/entity/equipment/humanoid_leggings/<id>.png` |
| Mod required   | OptiFine                                                | none — vanilla                                       |
| Java side      | rely on OptiFine reading the CMD                        | call `meta.setEquippable(EquippableComponent)` and set `setAssetId(NamespacedKey)` |

The vanilla path is set on the ItemStack by Java code at item-creation
time (see `PantsCrafting.equipDiaperArmor` and `Changing.applyChange`)
rather than inferred from CMD by the resource pack. The trade-off: every
custom design needs ONE `setAssetId(...)` call in the matching `register(...)`
or factory, but the renderer is now native and runs in any 1.21.4 client.

## How the generator script wires everything up

`tools/generate_design_json.py <folder>` writes:

| Output                                                                          | Source                                            | Required input              |
| ------------------------------------------------------------------------------- | ------------------------------------------------- | --------------------------- |
| `assets/minecraft/font/images.json` (3*N spliced font entries)                  | `<folder>/*_<stage>.png`                          | always                      |
| `assets/minecraft/items/slime_ball.json` (range_dispatch entries, sorted)       | from generated `models/item/<design>_<state>.json` | `<folder>/icons/clean.png`  |
| `assets/minecraft/models/item/<design>_<state>.json` (item-model JSON)          | `<folder>/icons/<state>.png`                      | `<folder>/icons/clean.png`  |
| `assets/minecraft/textures/item/<design>_<state>.png` (texture copy)            | `<folder>/icons/<state>.png`                      | `<folder>/icons/clean.png`  |
| `assets/minecraft/equipment/<equippableKey>.json` (equipment definition)        | (synthesized — single-layer humanoid_leggings)    | `<folder>/armor/clean.png`  |
| `assets/minecraft/textures/entity/equipment/humanoid_leggings/<eq>.png`         | `<folder>/armor/clean.png`                        | `<folder>/armor/clean.png`  |
| `tools/pending_design.json` (manifest, includes `equippableKey` field)          | inferred from inputs + cmd_registry               | always                      |

The icon and armor folders are independent: a design can have inventory
icons without worn-armor textures, or vice versa, or both. Missing folders
just produce a `note:` and skip the corresponding outputs.

`/add-design` then:
1. Reads `tools/pending_design.json` (including `equippableKey`).
2. Appends a `register(...)` line to `DesignRegistry.init()` that includes
   the `equippableKey` so item creation can call
   `meta.setAssetId(NamespacedKey.minecraft(equippableKey))`.
3. Builds the plugin and archives the manifest under `tools/applied/`.

## Migration artifacts (read-only reference)

The one-time migration from the 1.19 pack format to 1.21.4 ran via
`tools/migrate_to_1_21_4.py` and dropped a few JSON manifests under
`tools/migration/` (legacy parser outputs, equipment-definition synthesis,
trim-variant routing). They are kept on disk for traceability but are
**not** read by the build — they exist only as a record of what the
migration produced. New designs added after the migration use the
generator script described above.
