# Java item-creation patch report

For each site below, add an equippable component setter next to the existing setCustomModelData call. Skip sites whose CMD has no equipment_id (those are non-armor items — inventory icon only).

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/nanny/NannyInventoryManager.java:358 — CMD 626006

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = diaper.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(626006);
            meta.setDisplayName(net.md_5.bungee.api.ChatColor.WHITE + "Diaper");
            if (data.getCraftingMode() == NannyData.CraftingMode.EVIL) {
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/CustomFurnitureRemoval.java:103 — CMD 628000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                        }

                        meta.setCustomModelData(628000);
                        meta.setDisplayName("Diaper Pail");
                        CustomItem.setItemMeta(meta);  
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/Changing.java:306 — CMD 626003

**Add equippable:** `pull-up`

```java
// after meta.setCustomModelData(626003);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("pull-up"));
meta.setEquippable(equip);
```

Context:
```java
                    ItemMeta meta = diaper.getItemMeta();
                    meta.setDisplayName("Thick Diaper");
                    meta.setCustomModelData(626003);
                    diaper.setItemMeta(meta);
                    resetAndUpdateStats(stats, diaper, target, target);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/PantsCrafting.java:74 — CMD 626015

**Add equippable:** `pants`

```java
// after meta.setCustomModelData(626015);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("pants"));
meta.setEquippable(equip);
```

Context:
```java
                LeatherArmorMeta meta = (LeatherArmorMeta) coloredLeggings.getItemMeta();
                meta.setDisplayName("Pants");
                meta.setCustomModelData(626015);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/washer.java:72 — CMD 626014

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
            ItemStack furnaceItem = new ItemStack(Material.FURNACE);
            ItemMeta meta = furnaceItem.getItemMeta();
            meta.setCustomModelData(626014);
            meta.setDisplayName("");
            furnaceItem.setItemMeta(meta);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/washer.java:97 — CMD 626014

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                    ItemStack dropItem = new ItemStack(Material.FURNACE);
                    ItemMeta meta = dropItem.getItemMeta();
                    meta.setCustomModelData(626014);
                    meta.setDisplayName("Washing Machine");
                    dropItem.setItemMeta(meta);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/washer.java:233 — CMD 626015

**Add equippable:** `pants`

```java
// after meta.setCustomModelData(626015);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("pants"));
meta.setEquippable(equip);
```

Context:
```java
                        meta.getCustomModelData() == 626017 ||
                        meta.getCustomModelData() == 626018) {
                            meta.setCustomModelData(626015);
                            meta.setLore(null);
                        }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/Event_Listeners/washer.java:239 — CMD 626002

**Add equippable:** `undies`

```java
// after meta.setCustomModelData(626002);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("undies"));
meta.setEquippable(equip);
```

Context:
```java
                        meta.getCustomModelData() == 626020 || 
                        meta.getCustomModelData() == 626021) {
                            meta.setCustomModelData(626002);
                            meta.setDisplayName("Underwear");
                            meta.setLore(null);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SoundEffectsMenu.java:77 — CMD 626005

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                switch (categoryName) {
                    case "pee":
                        categoryMeta.setCustomModelData(626005);
                        break;
                    case "mess":
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SoundEffectsMenu.java:80 — CMD 626004

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                        break;
                    case "mess":
                        categoryMeta.setCustomModelData(626004);
                        break;
                    default:
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SoundEffectsMenu.java:83 — CMD 625000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                        break;
                    default:
                        categoryMeta.setCustomModelData(625000);
                        break;
                }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:84 — CMD 626009

**Add equippable:** `diaper`

```java
// after meta.setCustomModelData(626009);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("diaper"));
meta.setEquippable(equip);
```

Context:
```java
                OptinMeta.setLore(lore);
                OptinMeta.setDisplayName("Opt into plugin");
                OptinMeta.setCustomModelData(626009);
                Optin.setItemMeta(OptinMeta);   
            }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:96 — CMD 626004

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                MessingMeta.setLore(lore);
                MessingMeta.setDisplayName("Messing");
                MessingMeta.setCustomModelData(626004);
                Messing.setItemMeta(MessingMeta);
            }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:196 — CMD 626002

**Add equippable:** `undies`

```java
// after meta.setCustomModelData(626002);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("undies"));
meta.setEquippable(equip);
```

Context:
```java
                showundiesMeta.setLore(lore);
                showundiesMeta.setDisplayName("Show Undies");
                showundiesMeta.setCustomModelData(626002);
                showundies.setItemMeta(showundiesMeta);   
            }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:230 — CMD 627000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                ParticleEffectsMeta.setLore(lore);
                ParticleEffectsMeta.setDisplayName("Particle Effects");
                ParticleEffectsMeta.setCustomModelData(627000);
                ParticleEffects.setItemMeta(ParticleEffectsMeta);   
            }
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:269 — CMD 625000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
            ItemStack AudioSettings = new ItemStack(Material.SLIME_BALL); // Custom button
            ItemMeta AudioSettingsmeta = AudioSettings.getItemMeta();
            AudioSettingsmeta.setCustomModelData(625000);
            if (AudioSettingsmeta != null) {
                List<String> lore = Arrays.asList(
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:288 — CMD 629000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                    discordMeta.setDisplayName("Discord Link");
                    discordMeta.setLore(Arrays.asList("Join our Discord community"));
                    discordMeta.setCustomModelData(629000);
                    discord.setItemMeta(discordMeta);
                    urlItems.add(discord);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:300 — CMD 629001

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                    patreonMeta.setDisplayName("Patreon Link");
                    patreonMeta.setLore(Arrays.asList("Support us on Patreon"));
                    patreonMeta.setCustomModelData(629001);
                    patreon.setItemMeta(patreonMeta);
                    urlItems.add(patreon);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/SettingsMenu.java:312 — CMD 629002

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
                    subscribestarMeta.setDisplayName("Subscribestar Link");
                    subscribestarMeta.setLore(Arrays.asList("Support us on Subscribestar"));
                    subscribestarMeta.setCustomModelData(629002);
                    subscribestar.setItemMeta(subscribestarMeta);
                    urlItems.add(subscribestar);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/HUDMenu.java:61 — CMD 626005

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemStack toggleBar = new ItemStack(Material.SLIME_BALL); // Custom button
        ItemMeta toggleBarMeta = toggleBar.getItemMeta();
        toggleBarMeta.setCustomModelData(626005);
        if (toggleBarMeta != null) {
            
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/menus/HUDMenu.java:92 — CMD 626001

**Add equippable:** `diapers_thick`

```java
// after meta.setCustomModelData(626001);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("diapers_thick"));
meta.setEquippable(equip);
```

Context:
```java
        ItemStack showunderwear = new ItemStack(Material.SLIME_BALL); // Custom button
        ItemMeta showunderwearmeta = showunderwear.getItemMeta();
        showunderwearmeta.setCustomModelData(626001);
        if (toggleFillmeta != null) {
            List<String> lore = Arrays.asList(
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:59 — CMD 626014

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = Washer.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Washing Machine");
        meta.setCustomModelData(626014);// Custom Model Data for texture
        Washer.setItemMeta(meta);
        return Washer;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:67 — CMD 628000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = diaperPail.getItemMeta();
        meta.setDisplayName("Diaper Pail");
        meta.setCustomModelData(628000); // Custom Model Data for texture
        diaperPail.setItemMeta(meta);
        return diaperPail;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:75 — CMD 626006

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = toilet.getItemMeta();
        meta.setDisplayName("Toilet");
        meta.setCustomModelData(626006); // Custom Model Data for texture
        toilet.setItemMeta(meta);
        return toilet;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:117 — CMD 626012

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
    //     ItemMeta meta = item.getItemMeta();
    //     meta.setDisplayName("Laxative");
    //     meta.setCustomModelData(626012);
    //     item.setItemMeta(meta);
    //     lax = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:133 — CMD 626012

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta Laxmeta = Laxitem.getItemMeta();
        Laxmeta.setDisplayName("Laxative");
        Laxmeta.setCustomModelData(626012);// Custom Model Data for texture
        Laxitem.setItemMeta(Laxmeta);
        return Laxitem;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:142 — CMD 626012

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta Laxmeta = Laxitem.getItemMeta();
        Laxmeta.setDisplayName("Laxative");
        Laxmeta.setCustomModelData(626012);// Custom Model Data for texture
        Laxitem.setItemMeta(Laxmeta);
        lax = Laxitem;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/ItemManager.java:183 — CMD 626013

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
    //     ItemMeta meta = item.getItemMeta();
    //     meta.setDisplayName("Diuretic");
    //     meta.setCustomModelData(626013);
    //     item.setItemMeta(meta);
    //     dur = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/Nanny.java:30 — CMD 629001

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + (displayName == null || displayName.isEmpty() ? "Nanny Egg" : displayName));
            meta.setCustomModelData(629001);
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click to summon your Nanny.",
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:67 — CMD 626007

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Diaper Stuffer");
        meta.setCustomModelData(626007);// Custom Model Data for texture
        item.setItemMeta(meta);
        DiaperStuffer = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:82 — CMD 626008

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Tape");
        meta.setCustomModelData(626008);
        item.setItemMeta(meta);
        Tape = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:97 — CMD 626009

**Add equippable:** `diaper`

```java
// after meta.setCustomModelData(626009);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("diaper"));
meta.setEquippable(equip);
```

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Diaper");
        meta.setCustomModelData(626009);
        item.setItemMeta(meta);
        diaper = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:117 — CMD 626001

**Add equippable:** `diapers_thick`

```java
// after meta.setCustomModelData(626001);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("diapers_thick"));
meta.setEquippable(equip);
```

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Thick Diaper");
        meta.setCustomModelData(626001);
        item.setItemMeta(meta);
        thickdiaper = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:137 — CMD 626003

**Add equippable:** `pull-up`

```java
// after meta.setCustomModelData(626003);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("pull-up"));
meta.setEquippable(equip);
```

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Pullup");
        meta.setCustomModelData(626003);
        item.setItemMeta(meta);
        pullup = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:157 — CMD 626002

**Add equippable:** `undies`

```java
// after meta.setCustomModelData(626002);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("undies"));
meta.setEquippable(equip);
```

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Underwear");
        meta.setCustomModelData(626002);
        item.setItemMeta(meta);
        underwear = item;
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:198 — CMD 626004

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Stinky Diaper");
        meta.setCustomModelData(626004);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:212 — CMD 626005

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Wet Diaper");
        meta.setCustomModelData(626005);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:226 — CMD 626010

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Wet Pullup");
        meta.setCustomModelData(626010);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:240 — CMD 626011

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Wet Thick Diaper");
        meta.setCustomModelData(626011);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:254 — CMD 626019

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Wet Undies");
        meta.setCustomModelData(626019);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:268 — CMD 626020

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Dirty Undies");
        meta.setCustomModelData(626020);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/underwear.java:282 — CMD 626021

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Ruined Undies");
        meta.setCustomModelData(626021);
        List<String> lore = Arrays.asList(
                    "Owner: " + ChatColor.AQUA + owner.getDisplayName(),
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/pants.java:34 — CMD 626015

**Add equippable:** `pants`

```java
// after meta.setCustomModelData(626015);
EquippableComponent equip = meta.getEquippable();
equip.setSlot(EquipmentSlot.LEGS);
equip.setModel(NamespacedKey.minecraft("pants"));
meta.setEquippable(equip);
```

Context:
```java
        meta.setDisplayName("Pants");
        meta.setColor(getColorFromWool(woolMaterial));
        meta.setCustomModelData(626015);
        item.setItemMeta(meta);
        meta.setUnbreakable(true);
```

## /home/dudepher/SyncThings/Documents/Projects/AccidentProne-claude/Accident-Prone-1.21.4/src/main/java/com/storynook/items/Stuffies.java:17 — CMD 628000

**Skip** — no equipment_id for this CMD (non-armor).

Context:
```java
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Scruffy");
        meta.setCustomModelData(628000);
        List<String> lore = Arrays.asList("Created By SkullDoge");
        meta.setLore(lore);
```
