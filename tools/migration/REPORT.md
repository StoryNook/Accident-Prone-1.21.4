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

## Verdict

**PASS — proceed with plan as written.**

Migration plan §6.2 is accurate; the equippable component API is fully
exposed on the public Spigot 1.21.4 Bukkit API (no Paper-only fallback
needed); no plan adjustments required.
