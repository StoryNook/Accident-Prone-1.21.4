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
