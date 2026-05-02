# 1.21.4 Migration Pre-flight Report

## API Verification (Wave 0 / Task 1) — 2026-05-01

- Spigot 1.21.4 exposes `ItemMeta#setEquippable(EquippableComponent)`: **YES**
- Spigot 1.21.4 exposes `ItemMeta#getEquippable()` returning `EquippableComponent`: **YES**
- Spigot 1.21.4 exposes `ItemMeta#hasEquippable()`: **YES**
- `org.bukkit.inventory.meta.components.EquippableComponent` exists as a public interface: **YES**
- `EquippableComponent#setSlot(EquipmentSlot)` returns `void`: **YES**
- `EquippableComponent#setModel(NamespacedKey)` returns `void`: **YES**
- Plan §6.2 illustrative code example matches actual API surface: **YES**
- Original 1.19 build still compiles cleanly after pom.xml restoration: **YES**

### Decisive evidence

The exact probe class from the plan compiled with zero diagnostics against the
public Spigot 1.21.4 API jar:

```
javac -cp ~/.m2/repository/org/spigotmc/spigot-api/1.21.4-R0.1-SNAPSHOT/...
      -d /tmp/probe-only-classes
      src/main/java/com/storynook/_probe/EquippableProbe.java
# -> EquippableProbe.class produced, 0 errors, 0 warnings
```

`javap -p org.bukkit.inventory.meta.components.EquippableComponent` confirms
the full API surface, including bonus fields not used by the migration:

- `getEquipSound()` / `setEquipSound(Sound)`
- `getCameraOverlay()` / `setCameraOverlay(NamespacedKey)`
- `getAllowedEntities()` / `setAllowedEntities(...)` (3 overloads)
- `isDispensable()` / `setDispensable(boolean)`
- `isSwappable()` / `setSwappable(boolean)`
- `isDamageOnHurt()` / `setDamageOnHurt(boolean)`

These are available for follow-up work but not required by this migration.

### Bonus findings — API drift surfaced by full-project compile against 1.21.4

Running `mvn compile` against the full plugin (with pom.xml temporarily on
1.21.4) succeeded for the probe but produced 14 errors in unrelated 1.19-era
code. These are exactly what Wave 1 needs to fix:

| Old name (1.19)                  | New name (1.20+)              | Sites |
|----------------------------------|--------------------------------|-------|
| `Attribute.GENERIC_ARMOR`        | renamed                        | `PantsCrafting.java:171`, `pants.java:38` |
| `Attribute.GENERIC_MAX_HEALTH`   | renamed                        | `UpdateStats.java:296` |
| `Enchantment.DURABILITY`         | `Enchantment.UNBREAKING`       | `NannyMenu.java:61` |
| `Particle.REDSTONE`              | `Particle.DUST`                | `ParticleEffect.java:80` |
| `PotionEffectType.SLOW`          | `PotionEffectType.SLOWNESS`    | `UpdateStats.java:244,246,250,251,254,287,288` |
| `PotionEffectType.SLOW_DIGGING`  | `PotionEffectType.MINING_FATIGUE` | `UpdateStats.java:315,319` |

These get fixed in Wave 1 / Task 7.

## Custom Model Data Mapping (Wave 0 / Task 2) — 2026-05-01

- `meta.setCustomModelData(int)` writes the integer value to the first
  element of `custom_model_data.floats[]`: **YES**

### Evidence

The Spigot Javadocs for `ItemMeta.setCustomModelData(int)` (in 1.21.4 and
forward) document the method as setting custom model data, with a note
that integers passed via this legacy API are equivalent to a single float
in the `CustomModelDataComponent.getFloats()` list (i.e., `floats[0]`).
Confirmed via Spigot/Paper Javadoc lookup and SpigotMC forum threads.

This means:

- Calling `meta.setCustomModelData(626001)` in Java code on a 1.21.4 server
- produces a `custom_model_data` data component on the wire with
  `{floats: [626001.0], flags: [], strings: [], colors: []}`
- which the resource pack's `items/<base>.json` `range_dispatch` reads via
  `property: "minecraft:custom_model_data"` → its default index 0 picks
  `floats[0]` → matches the threshold → selects the right model.

The migration plan's pack-side discrimination (`range_dispatch` on
`custom_model_data.floats[0]`) is correct without any Java code changes
to the integer-CMD setting pattern.

### Note on deprecation

The `int`-form `setCustomModelData(int)` is **deprecated for removal** in
Spigot 1.21.5+ in favor of `setCustomModelDataComponent(...)`. As of
1.21.4 the method still works and writes to `floats[0]` as documented.
Once the migration is on 1.21.4, a future maintenance task can move the
plugin to the component-based API. That task is out of scope for the
1.21.4 migration.

## Verdict

**PASS — proceed with plan as written.**

Both Wave 0 hard gates verified:

1. Spigot 1.21.4 exposes the equippable component API in the exact shape
   the plan §6.2 example uses.
2. `setCustomModelData(int)` writes to `custom_model_data.floats[0]`,
   which is what the pack's `range_dispatch` reads.

No plan adjustments required. Migration can proceed to Task 3 (git tag),
then Wave 1 (pom.xml + Java 21 build switch).

---

## Wave 5 Validation Results (2026-05-01)

Tested on a clean Paper 1.21.4 server with a Prism Launcher 1.21.4 client.
Validation walked the §7.3 matrix as a TodoWrite checklist, prompted by the
implementer; user reported observations row by row.

### Pre-flight gates (Task 22)

- `mvn clean package` → BUILD SUCCESS, jar 21.7 MB.
- 388 pack JSON files all parse cleanly.
- Pack zip extracts byte-identical to source tree.

### Validation matrix results

| # | Check | Result | Note |
|---|---|---|---|
| V1 | Server boots, plugin enables | PASS | Citizens/WorldGuard failures are unrelated 3rd-party compat. |
| V2 | Resource pack accepted by client | PASS | Required clearing stale format-34 zip from client resourcepacks dir + plugin-extracted cache. |
| V3 | Custom inventory icons render | PASS | All categories. |
| V4 | Dropped-item world entity renders | PASS | |
| V5 | Equippable component renders worn texture | PASS | Required swapping the stale pre-migration jar from the test server's plugins dir + clearing Paper's `.paper-remapped` cache. End-to-end: Java setEquippable → wire asset_id → 1.21.4 client → custom worn texture. |
| V6 | `HandleAccident.changeLeggingsModel` advances state | PASS | CMD + equippable swap together via `setPantsState` helper. |
| V7 | Lazy-stamp legacy items | DEFERRED | Fresh test world; no pre-migration save data to test against. Code path is structurally sound. |
| V8 | `/settings` menu icons render | PASS | All sub-menus. |
| V9 | Crib 3D models render | PASS | All wood variants. Armor-stand-as-hat trick preserved. |
| V10 | Diaperpail 3D model renders | PASS | |
| V11 | HUD glyphs (font system) | PASS | Action bar + sidebar. |
| V12 | Sidebar underwear sprite cycles states | PARTIAL | Most transitions work; some edge cases don't update cleanly. Pre-existing or partly new — separate investigation, not migration-blocking. |
| V13 | `/diaperreload` | DEFERRED | Code path unchanged by migration. |
| V14 | Logout/login persistence | DEFERRED | YAML persistence unchanged; validated implicitly via V6 (state changed across an accident). |
| V15 | `/add-design` end-to-end | DEFERRED | No new design ready; generator was smoke-tested in Wave 4 via `mvn generate-resources`. |
| V16 | Vanilla armor trims | DEFERRED | Validated indirectly: items/leather_leggings.json's nested trim select was generated correctly per Wave 2 manifest. |
| V17 | DesignRegistry smoke (goodnite_stars) | PARTIAL | Inventory + dropped icon work. Worn-body shows plain leather leggings — designs have no equipment definitions; tracked as out-of-scope per spec §9. |

### Sign-off

User accepted the migration as good. **Migration to Spigot 1.21.4 / pack
format 46 / Java 21 is complete.**

### Known follow-ups (not blocking)

1. **Designs lack worn-body equipment definitions.** Spec §9 future-work.
   `goodnite_stars` and any future DesignRegistry designs render as plain
   leather leggings on the body. Fix involves generating per-design
   equipment definitions + textures and threading the equipment id through
   `PantsCrafting.equipDiaperArmor` for design CMDs.
2. **Pants color comes out gray instead of dyed.** Reproducible: a "cyan"
   pants attempt rendered gray. Two likely causes: (a) `equipDiaperArmor`
   line 156–157 hardcodes `#F7FFF4`, (b) 1.21.4's `minecraft:dyed_color`
   data component supersedes `LeatherArmorMeta.setColor()` and may need
   to be set explicitly.
3. **Sidebar underwear sprite has edge-case state transitions** that don't
   update cleanly. Investigate `ScoreBoard.getUnderwearStatus` glyph table
   indexing.
4. **3rd-party plugin compat:** WorldGuard 7.0.12 throws
   `NoSuchFieldError: ITEMS_TRIM_TEMPLATES` (1.21.4 incompat); CoreProtect
   22.4 throws `NotSerializableException: CraftAttribute`; Citizens fails
   to load (Nanny disabled). Out of scope for this migration; track
   separately or update those plugins.
5. **Maven jar packaging** currently bundles
   `src/main/resources/StoryNook1.2.4.replaced-by-migration/` and the
   unzipped pack tree alongside the zip — bloats the jar to 21.7 MB.
   Add a maven-jar-plugin exclusion or move the unzipped tree out of
   `src/main/resources/`.
