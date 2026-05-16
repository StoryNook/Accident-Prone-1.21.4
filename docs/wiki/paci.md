---
title: Paci system
description: Cosmetic head-slot pacifier items + optional Curse of Binding.
---

# Paci system

Cosmetic head-slot pacifier items. Give-only (no crafting recipe; 27 designs), wearable on `LEATHER_HELMET` base via the 1.21.4 equippable component. Mirrors the worn-armor pattern used by `pants`. Optionally combinable with a Curse of Binding enchanted book — at a crafting table or anvil — to produce a cursed paci that sticks once worn and is invisible to non-crafters.

## Design list

| giveKey | displayName | CMD | equippableKey |
|---|---|---|---|
| birthday | Birthday Paci | 627100 | paci_birthday |
| black | Black Paci | 627101 | paci_black |
| blue | Blue Paci | 627102 | paci_blue |
| brown | Brown Paci | 627103 | paci_brown |
| bunny | Bunny Paci | 627104 | paci_bunny |
| christmas | Christmas Paci | 627105 | paci_christmas |
| cyan | Cyan Paci | 627106 | paci_cyan |
| discord | Discord Paci | 627107 | paci_discord |
| easter | Easter Paci | 627108 | paci_easter |
| eevee | Eevee Paci | 627109 | paci_eevee |
| gray | Gray Paci | 627110 | paci_gray |
| green | Green Paci | 627111 | paci_green |
| halloween | Halloween Paci | 627112 | paci_halloween |
| light_blue | Light Blue Paci | 627113 | paci_light_blue |
| light_gray | Light Gray Paci | 627114 | paci_light_gray |
| lime | Lime Paci | 627115 | paci_lime |
| magenta | Magenta Paci | 627116 | paci_magenta |
| orange | Orange Paci | 627117 | paci_orange |
| patreon | Patreon Paci | 627118 | paci_patreon |
| pink | Pink Paci | 627119 | paci_pink |
| prince | Prince Paci | 627120 | paci_prince |
| princess | Princess Paci | 627121 | paci_princess |
| purple | Purple Paci | 627122 | paci_purple |
| red | Red Paci | 627123 | paci_red |
| royal | Royal Paci | 627124 | paci_royal |
| white | White Paci | 627125 | paci_white |
| yellow | Yellow Paci | 627126 | paci_yellow |

CMD range `627100–627126` is reserved for pacis; cribs occupy `627000–627010` in the same `627xxx` block.

## Commands

- `/debug give <amount> <player> Paci <giveKey>` — only way to obtain a paci. Tab-completion lists all 27 giveKeys.
- `/equiparmor <player>` — a caregiver holding a paci can equip it on a ward within 10 blocks. The ward's existing paci, if any, is returned to the caregiver's inventory (subject to the existing inventory-fit check).

## Detection (for gameplay code)

Single point of detection:

```java
if (com.storynook.items.PaciCheck.isWearingPaci(player)) { ... }

com.storynook.items.PaciCheck.getWornPaci(player).ifPresent(def -> {
    // def.giveKey, def.cmd, def.displayName, def.equippableKey
});
```

`CustomItemCheck.isPaci(ItemStack)` checks an arbitrary ItemStack (held, dropped, inventory slot — not necessarily worn).

There is **no PlayerStats field** for the worn paci; the helmet slot is the source of truth, persisted by Spigot's vanilla inventory save.

## Curse of Binding

Gated on `Secret_Menu.Binding_Diapers` (shared with cursed diapers; one admin switch turns on both). When on:

- **Crafting:** paci + enchanted book carrying Curse of Binding in a 3×3 grid → cursed paci with the same display CMD as the input. PDC markers stamped: `crafted_by` (STRING name), `crafted_by_uuid` (STRING uuid), `cursed` (BYTE 1). Real `BINDING_CURSE` enchant is added (so vanilla enforces stuck-on-head). `HIDE_ENCHANTS` flag set.
- **Anvil:** paci in slot 1 + Curse of Binding book in slot 2 → same result, XP cost charged by vanilla.

**Lore visibility:** three listeners (`onInventoryOpen`, `onInventoryClose`, `onItemPickup`) rewrite the lore for the current viewer:
- Viewer is the crafter (UUID match) AND not in Hardcore mode → lore = `§4Cursed: Binding Enchantment (Crafted by you)`.
- Otherwise → lore is cleared. Combined with `HIDE_ENCHANTS`, non-crafters see what looks like a plain paci.

**Removal:** vanilla Curse of Binding blocks the player from taking the helmet off. The caregiver `/equiparmor` swap path is also blocked while the curse is in effect (caregiver receives the vanilla rejection).

## Resource pack layout

- Inventory icons: `assets/minecraft/textures/item/paci/<color>_paci.png`.
- Worn-armor textures (2 layers per design, both non-dyed): `assets/minecraft/textures/entity/equipment/humanoid/<color>_paci_layer_1.png` and `<color>_paci_layer_2.png`.
- Equipment definitions: `assets/minecraft/equipment/paci_<color>.json` — references both layers under the `humanoid` layer (helmets, chestplates, and boots all share the `humanoid` layer in 1.21.4; `humanoid_head` is not a valid layer type and renders nothing).
- Item models: `assets/minecraft/models/item/paci_<color>.json` — `minecraft:item/generated` over the inventory icon.
- Dispatch: `assets/minecraft/items/leather_helmet.json` — `range_dispatch` on `custom_model_data.floats[0]` threshold-routes each paci CMD to its model; vanilla `minecraft:item/leather_helmet` as fallback.

## Bug-prone areas

- The `Binding_Diapers` admin flag is also the gate for cursed pacis. Toggling it off does NOT remove the recipe from already-registered recipes; restart or `/diaperreload`.
- Anvil with the flag off still produces a vanilla-cursed leather_helmet (just without our PDC + lore). Acceptable known leakage.
- New paci designs are added by appending one `register(...)` line to `PaciRegistry.init()` plus four pack-side files (equipment JSON, model JSON, two PNG layers + one inventory icon) plus one `range_dispatch` entry in `items/leather_helmet.json`.
