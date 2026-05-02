package com.storynook.menus;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.scoreboard.ScoreboardManager;

import com.storynook.Plugin;
import com.storynook.ScoreBoard;
import com.storynook.Event_Listeners.PantsCrafting;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.SavePlayerStats;

import net.md_5.bungee.api.ChatColor;


public class SettingsMenu implements Listener {
    private Plugin plugin;
    
        public SettingsMenu(Plugin plugin) {
            this.plugin = plugin;
        }
        public static void OpenSettings(Player player, Plugin plugin) {
            UUID playerUUID = player.getUniqueId();
            PlayerStats stats = plugin.getPlayerStats(playerUUID);
            Map<String, Object> config = plugin.getGlobalConfig();
            boolean HardcoreEnabled = false;
            boolean HardCoreLock = false;
            // Calculate how many items we need based on enabled settings
            int totalItems = 3;

            // Count enabled main settings
            
            // Count enabled URLs
            int urlCount = 0;
            if (config.get("Discord_Link") != null && config.get("Discord_Link") != null && !((String) config.get("Discord_Link")).isEmpty()) urlCount++;
            if (config.get("Patreon_Link") != null && config.get("Patreon_Link") != null && !((String) config.get("Patreon_Link")).isEmpty()) urlCount++;
            if (config.get("Subscribestar_Link") != null && config.get("Subscribestar_Link") != null && !((String) config.get("Subscribestar_Link")).isEmpty()) urlCount++;

            // Count boolean values that are true
            if (config.get("Messing") != null && (Boolean) config.get("Messing")) totalItems++;
            if (config.get("Caregivers") != null && (Boolean) config.get("Caregivers")) totalItems++;
            if (config.get("Show_Undies") != null && (Boolean) config.get("Show_Undies")) totalItems++;
            if (config.get("Incontinence") != null && (Boolean) config.get("Incontinence")) totalItems++;
            if (config.get("Hardcore") != null && (Boolean) config.get("Hardcore")) {totalItems++; HardcoreEnabled = (Boolean) config.get("Hardcore"); HardCoreLock = (Boolean) config.get("Lock_Optin_with_Hardcore");}
            if (config.get("MinFilltoggle") != null && (Boolean) config.get("MinFilltoggle")) totalItems++;
            if (config.get("Accidents") != null && (Boolean) config.get("Accidents")) totalItems += 2;
            if (config.get("Hypno") != null && (Boolean) config.get("Hypno")) totalItems++;


            totalItems += urlCount;

            // Calculate inventory rows needed (9 items per row)
            int rows = (int) Math.ceil((double) totalItems / 9.0);
            if (rows < 1) rows = 1; // Ensure at least 1 row
            if (rows > 6) rows = 6; // Limit to 6 rows max

            Inventory menu = Bukkit.createInventory(player, 9 * rows, "Settings");
    
            ItemStack Optin = new ItemStack(Material.SLIME_BALL); // Custom button
            ItemMeta OptinMeta = Optin.getItemMeta();
            if (OptinMeta != null) {
                List<String> lore = Arrays.asList(
                    "Diaper Plugin: " + (stats.getOptin() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"), 
                    "Enables the plugin to track stats.", 
                    "Things like, bladder, underwear type, incontinence, etc.",
                    ((HardcoreEnabled && HardCoreLock) ? ChatColor.RED + "Unable to change Optin Settings while in Hardcore" : null)
                );
                OptinMeta.setLore(lore);
                OptinMeta.setDisplayName("Opt into plugin");
                OptinMeta.setCustomModelData(626009);
                EquippableComponent optinEquip = OptinMeta.getEquippable();
                optinEquip.setSlot(EquipmentSlot.LEGS);
                optinEquip.setModel(NamespacedKey.minecraft("diaper"));
                OptinMeta.setEquippable(optinEquip);
                Optin.setItemMeta(OptinMeta);
            }
            ItemStack Messing = new ItemStack(Material.SLIME_BALL);
            ItemMeta MessingMeta = Messing.getItemMeta();
            if (MessingMeta != null) {
                List<String> lore = Arrays.asList(
                    "Messing accidents can happen: " + (stats.getMessing() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                    "Enables the bowels to start filling."
                );
                MessingMeta.setLore(lore);
                MessingMeta.setDisplayName("Messing");
                MessingMeta.setCustomModelData(626004);
                Messing.setItemMeta(MessingMeta);
            }
            ItemStack ScoreBoard = new ItemStack(Material.PAINTING); // Custom button
            ItemMeta ScoreobardMeta = ScoreBoard.getItemMeta();
            if (ScoreobardMeta != null) {
                String UISetting;
                if (stats.getUI() == 0) {UISetting = "Hidden";}
                else if (stats.getUI() == 1) {UISetting = "Scoreboard";}
                else if(stats.getUI() == 2){UISetting = "HotBar";}
                else{UISetting = "Error";}
                List<String> lore = Arrays.asList(
                    "Current HUD Selected " + ChatColor.YELLOW + UISetting,
                    "Update your options in here.",
                    "Scoreboard style is defualt."
                );
                ScoreobardMeta.setLore(lore);
                ScoreobardMeta.setDisplayName("HUD for your Stats");
                ScoreBoard.setItemMeta(ScoreobardMeta);   
            }
    
            ItemStack HardCore = new ItemStack(Material.FIRE_CHARGE); // Custom button
            ItemMeta HardCoreMeta = HardCore.getItemMeta();
            if (HardCoreMeta != null) {
                List<String> lore = Arrays.asList(
                    "HardCore: " + (stats.getHardcore() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                    "Removes your ablility to change yourself.",
                    "Only Caregivers can change you.",
                    ChatColor.RED + "Once turned on there is a 20 hour timer to turn it off"
                );
                HardCoreMeta.setLore(lore);
                HardCoreMeta.setDisplayName("HardCore");
                HardCore.setItemMeta(HardCoreMeta);   
            }
    
            ItemStack Bedwetting = new ItemStack(Material.RED_BED); // Custom button
            ItemMeta BedwettingMeta = Bedwetting.getItemMeta();
            if (BedwettingMeta != null) {
                String BedwettingSetting;
                if (stats.getBedwetting() == 2) {BedwettingSetting = ChatColor.GREEN + "On";}
                else if (stats.getBedwetting() == 1) {BedwettingSetting = ChatColor.YELLOW + "Limited";}
                else if(stats.getBedwetting() == 0){BedwettingSetting = ChatColor.RED + "Off";}
                else{BedwettingSetting = "Error";}
                List<String> lore = Arrays.asList(
                    "Bedwetting: " + BedwettingSetting,
                    "Limited will be random based on your bladder control.",
                    stats.getHardcore() ? ChatColor.RED + "SETTING LOCKED" : ""
                );
                BedwettingMeta.setLore(lore);
                BedwettingMeta.setDisplayName("Bedwetting");
                Bedwetting.setItemMeta(BedwettingMeta);   
            }
            ItemStack CareGiver = new ItemStack(Material.PLAYER_HEAD); // Custom button
            ItemMeta CareGivermeta = CareGiver.getItemMeta();
            if (CareGivermeta != null) {
                List<String> lore = Arrays.asList(
                    "Edit your caregiver settings",
                    ChatColor.GREEN + "Add, " + ChatColor.RED + "Remove, " + ChatColor.GOLD + "List"
                );
                CareGivermeta.setLore(lore);
                CareGivermeta.setDisplayName("Caregiver Settings");
                CareGiver.setItemMeta(CareGivermeta);   
            }
            ItemStack MinFill = new ItemStack(Material.WATER_BUCKET); // Custom button
            ItemMeta MinFillMeta = MinFill.getItemMeta();
            if (CareGivermeta != null) {
                List<String> lore = Arrays.asList(
                    "Minimum amount needed for a warning.",
                    "Current limit: " + ChatColor.GOLD + stats.getMinFill(),
                    "Each Icon is 10, range 0-100",
                    "Use /minfill help for more info"
                );
                MinFillMeta.setLore(lore);
                MinFillMeta.setDisplayName("Warning threshold");
                MinFill.setItemMeta(MinFillMeta);   
            }
            ItemStack Incon = new ItemStack(Material.OAK_SIGN); // Custom button
            ItemMeta InconlMeta = Incon.getItemMeta();
            if (InconlMeta != null) {
                List<String> lore = Arrays.asList(
                    "Current Bladder incon: " + Math.round(stats.getBladderIncontinence()),
                    stats.getMessing() ? "Current Bowel Incon: " + Math.round(stats.getBowelIncontinence()) : "Locked? " + (stats.getBladderLockIncon() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                    stats.getHardcore() ? null : "Bladder locked? " + (stats.getBladderLockIncon() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"),
                    stats.getHardcore() ? null : stats.getMessing() ? "Bowel locked? " + (stats.getBowelLockIncon() ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No") : null
                );
                InconlMeta.setLore(lore);
                InconlMeta.setDisplayName("Incontinence");
                Incon.setItemMeta(InconlMeta);   
            }
            ItemStack showundies = new ItemStack(Material.SLIME_BALL); // Custom button
            ItemMeta showundiesMeta = showundies.getItemMeta();
            if (showundiesMeta != null) {
                List<String> lore = Arrays.asList(
                    "Underwear Visible on Player " + (stats.getvisableUnderwear() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"),
                    ChatColor.RED + "NOTE:",
                    "Optifine Required",
                    "To put on leggings, hold them in hand and right click, or disable."
                );
                showundiesMeta.setLore(lore);
                showundiesMeta.setDisplayName("Show Undies");
                showundiesMeta.setCustomModelData(626002);
                EquippableComponent showundiesEquip = showundiesMeta.getEquippable();
                showundiesEquip.setSlot(EquipmentSlot.LEGS);
                showundiesEquip.setModel(NamespacedKey.minecraft("undies"));
                showundiesMeta.setEquippable(showundiesEquip);
                showundies.setItemMeta(showundiesMeta);
            }
            ItemStack ParticleEffects = new ItemStack(Material.SLIME_BALL); // Custom button
            ItemMeta ParticleEffectsMeta = ParticleEffects.getItemMeta();
            if (ParticleEffectsMeta != null) {
                String particleString;
                String description;
                if (stats.getParticleEffects() == 0) {
                    particleString = "Particles visable: " + ChatColor.RED + "None";
                    description = "No particle effects showing off your accidents will be visable";
                }
                else if (stats.getParticleEffects() == 1) {
                    particleString = "Particles visable: " + ChatColor.YELLOW + "Leaking";
                    description = "When your diaper starts leaking, droplets will be falling from you";
                }
                else if (stats.getParticleEffects() == 2) {
                    particleString = "Particles visable: " + ChatColor.GREEN + "Stink Lines";
                    description = "Stink lines will be noticable from behind you when your underwear is full";
                }
                else if (stats.getParticleEffects() == 3) {
                    particleString = "Particles visable: " + ChatColor.GOLD + "Leaking & Stink Lines";
                    description = "Both Stink Lines, and leaking droplets will be visable";
                }
                else {
                    particleString = "There was an error";
                    description = "Please notify the admins";
                }
                List<String> lore = Arrays.asList(
                    particleString,
                    description
                );
                ParticleEffectsMeta.setLore(lore);
                ParticleEffectsMeta.setDisplayName("Particle Effects");
                ParticleEffectsMeta.setCustomModelData(627000);
                ParticleEffects.setItemMeta(ParticleEffectsMeta);   
            }

            ItemStack HypnoToggle = null;
            if (config.get("Hypno") != null && (Boolean) config.get("Hypno")) {
                HypnoToggle = new ItemStack(Material.CLOCK);
                ItemMeta hypnoMeta = HypnoToggle.getItemMeta();
                if (hypnoMeta != null) {
                    if (stats.hasActiveHypnoTriggers()) {
                        LocalDateTime maxExpiry = stats.getMaxHypnoExpiry();
                        long hoursLeft = maxExpiry == null ? 0 : LocalDateTime.now().until(maxExpiry, ChronoUnit.HOURS);
                        if (hoursLeft < 0) hoursLeft = 0;
                        long daysLeft = hoursLeft / 24;
                        long remainHours = hoursLeft % 24;
                        hypnoMeta.setDisplayName(ChatColor.RED + "Hypnosis: Active - Cannot Disable");
                        hypnoMeta.setLore(Arrays.asList(
                            ChatColor.GRAY + "You are under active hypnosis.",
                            ChatColor.RED + "Clears in: " + daysLeft + "d " + remainHours + "h"
                        ));
                    } else {
                        int perm = stats.getHypnoPermission();
                        if (perm == 0) {
                            hypnoMeta.setDisplayName(ChatColor.GRAY + "Hypnosis: Disabled");
                            hypnoMeta.setLore(Arrays.asList(ChatColor.GRAY + "Nobody can use a hypnosis clock on you."));
                        } else if (perm == 1) {
                            hypnoMeta.setDisplayName(ChatColor.YELLOW + "Hypnosis: Caregivers Only");
                            hypnoMeta.setLore(Arrays.asList(ChatColor.GRAY + "Only your caregivers can hypnotize you."));
                        } else {
                            hypnoMeta.setDisplayName(ChatColor.GREEN + "Hypnosis: Anyone");
                            hypnoMeta.setLore(Arrays.asList(ChatColor.GRAY + "Any player with a hypnosis clock can hypnotize you."));
                        }
                    }
                    HypnoToggle.setItemMeta(hypnoMeta);
                }
            }

            ItemStack AudioSettings = new ItemStack(Material.SLIME_BALL); // Custom button
            ItemMeta AudioSettingsmeta = AudioSettings.getItemMeta();
            AudioSettingsmeta.setCustomModelData(625000);
            if (AudioSettingsmeta != null) {
                List<String> lore = Arrays.asList(
                    "Preivew the sound effects,",
                    "as well as set your preferences"
                );
                AudioSettingsmeta.setLore(lore);
                AudioSettingsmeta.setDisplayName("Audio Settings");
                AudioSettings.setItemMeta(AudioSettingsmeta);   
            }

            // Create URL items
            List<ItemStack> urlItems = new ArrayList<>();
            if (config.get("Discord_Link") != null && !((String) config.get("Discord_Link")).isEmpty()) {
                ItemStack discord = new ItemStack(Material.SLIME_BALL);
                ItemMeta discordMeta = discord.getItemMeta();
                if (discordMeta != null) {
                    discordMeta.setDisplayName("Discord Link");
                    discordMeta.setLore(Arrays.asList("Join our Discord community"));
                    discordMeta.setCustomModelData(629000);
                    discord.setItemMeta(discordMeta);
                    urlItems.add(discord);
                }
            }
            
            if (config.get("Patreon_Link") != null && !((String) config.get("Patreon_Link")).isEmpty()) {
                ItemStack patreon = new ItemStack(Material.SLIME_BALL);
                ItemMeta patreonMeta = patreon.getItemMeta();
                if (patreonMeta != null) {
                    patreonMeta.setDisplayName("Patreon Link");
                    patreonMeta.setLore(Arrays.asList("Support us on Patreon"));
                    patreonMeta.setCustomModelData(629001);
                    patreon.setItemMeta(patreonMeta);
                    urlItems.add(patreon);
                }
            }
            
            if (config.get("Subscribestar_Link") != null && !((String) config.get("Subscribestar_Link")).isEmpty()) {
                ItemStack subscribestar = new ItemStack(Material.SLIME_BALL);
                ItemMeta subscribestarMeta = subscribestar.getItemMeta();
                if (subscribestarMeta != null) {
                    subscribestarMeta.setDisplayName("Subscribestar Link");
                    subscribestarMeta.setLore(Arrays.asList("Support us on Subscribestar"));
                    subscribestarMeta.setCustomModelData(629002);
                    subscribestar.setItemMeta(subscribestarMeta);
                    urlItems.add(subscribestar);
                }
            }

            int slot = 0;
            menu.setItem(slot++, Optin);
            if (config.get("Messing") != null && (Boolean) config.get("Messing")) menu.setItem(slot++, Messing);
            menu.setItem(slot++, ScoreBoard);
            if (config.get("Hardcore") != null && (Boolean) config.get("Hardcore")) menu.setItem(slot++, HardCore);
            if (config.get("Accidents") != null && (Boolean) config.get("Accidents")) menu.setItem(slot++, Bedwetting);
            if (config.get("Caregivers") != null && (Boolean) config.get("Caregivers")) menu.setItem(slot++, CareGiver);
            if (config.get("MinFilltoggle") != null && (Boolean) config.get("MinFilltoggle")) menu.setItem(slot++, MinFill);
            if (config.get("Incontinence") != null && (Boolean) config.get("Incontinence")) menu.setItem(slot++, Incon);
            if (config.get("Show_Undies") != null && (Boolean) config.get("Show_Undies")) menu.setItem(slot++, showundies);
            if (HypnoToggle != null) menu.setItem(slot++, HypnoToggle);
            menu.setItem(slot++, AudioSettings);
            
            slot = (9*rows) - (urlCount);

            for (ItemStack urlItem : urlItems) {
                menu.setItem(slot++, urlItem);
            }
            
            player.openInventory(menu);
        }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Map<String, Object> config = plugin.getGlobalConfig();
        boolean HardcoreEnabled = false;
        boolean HardCoreLock = false;
        if (config.get("Hardcore") != null) {
            HardcoreEnabled = (Boolean) config.get("Hardcore"); 
        }
        if (config.get("Lock_Optin_with_Hardcore") != null) {
            HardCoreLock = (Boolean) config.get("Lock_Optin_with_Hardcore");
        }
        if (!event.getView().getTitle().equals("Settings")) {
            return;
        }

        event.setCancelled(true); // Prevent default behavior

        if (event.getCurrentItem() == null || event.getCurrentItem().getItemMeta() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        PlayerStats stats = plugin.getPlayerStats(playerUUID);

        if (stats == null) {
            return;
        }

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (event.getView().getTitle().equals("Settings")) {
            if (meta.hasCustomModelData() && meta.getCustomModelData() == 626009) {
                if (HardcoreEnabled && HardCoreLock && stats.getHardcore()) {
                    player.sendMessage("Unable to Opt out while in Hardcore.");
                }
                else {
                    stats.setOptin(!stats.getOptin());
                    player.sendMessage(stats.getOptin() ? "You have opted into the plugin." : "You have opted out of the plugin.");
                    updateScoreboard(player, stats);
                    OpenSettings(player, plugin);
                }
            } else if (meta.hasCustomModelData() && meta.getCustomModelData() == 626004) {
                stats.setMessing(!stats.getMessing());
                player.sendMessage(stats.getMessing() ? "Messing has been enabled." : "Messing has been disabled.");
                OpenSettings(player, plugin);
            } else if (event.getCurrentItem().getType() == Material.PAINTING) {
                HUDMenu.HUDMenuUI(player, plugin);
            } else if (event.getCurrentItem().getType() == Material.FIRE_CHARGE) {
                toggleHardcore(player, plugin);
            }
            else if (event.getCurrentItem().getType() == Material.RED_BED) {
                if(stats.getOptin()){
                    if (!stats.getHardcore()) {
                        int newBedwetting = stats.getBedwetting();
                        newBedwetting++;
                        if (newBedwetting > 2) {
                            newBedwetting = 0;
                        }
                        stats.setBedwetting(newBedwetting);
                        if (stats.getBedwetting() == 0){player.sendMessage("Bedwetting: " + ChatColor.RED + "Disabled");}
                        if (stats.getBedwetting() == 1){player.sendMessage("Bedwetting: " + ChatColor.YELLOW + "Limited");}
                        if (stats.getBedwetting() == 2){player.sendMessage("Bedwetting: " + ChatColor.GREEN + "always");}
                        SavePlayerStats.savePlayerStats(player); // Save the updated stats
                    }
                    else{
                        player.sendMessage("You are in " + ChatColor.RED + "Hardcore Mode" + ChatColor.WHITE + " setting is locked.");
                    }
                } 
                OpenSettings(player, plugin);
            }
            else if (event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                Caregivermenu.OpenCareGiverSettings(player, plugin, 0);
            }
            else if (event.getCurrentItem().getType() == Material.WATER_BUCKET) {
                if(stats.getOptin()){
                    plugin.setAwaitingInput(player.getUniqueId(), "minFill");
                    player.closeInventory(); // Optionally close the inventory
                    player.sendMessage(ChatColor.YELLOW + "Please enter a new minimum fill value:");
                } else {player.sendMessage("You must be opted in to change this setting");}
            }
            else if (event.getCurrentItem().getType() == Material.OAK_SIGN) {
                if(stats.getOptin()){
                    IncontinenceMenu.IncontinenceSettings(player, plugin);
                }else {player.sendMessage("You must be opted in to change this setting");}
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 627000 && event.getCurrentItem().getType() == Material.SLIME_BALL) {
                if(stats.getOptin()){
                    int ParticleSelection = stats.getParticleEffects();
                    ParticleSelection++;
                    if (ParticleSelection > 3) {
                        ParticleSelection = 0;
                    }
                    stats.setParticleEffects(ParticleSelection);
                    if (stats.getParticleEffects() == 0){player.sendMessage("Accident Particle Effects: " + ChatColor.RED + "Off");}
                    if (stats.getParticleEffects() == 1){player.sendMessage("Leaking Particle Effects:  " + ChatColor.YELLOW + "On");}
                    if (stats.getParticleEffects() == 2){player.sendMessage("Stink Lines Particle Effects: " + ChatColor.GREEN + "On");}
                    if (stats.getParticleEffects() == 3){player.sendMessage("Leaking and Stink Line Parrticle Effects: " + ChatColor.GOLD + "On");}
                    plugin.manageParticleEffects(player);
                    SavePlayerStats.savePlayerStats(player);
                } else {player.sendMessage("You must be opted in to change this setting");}
                OpenSettings(player, plugin);
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 626002 && event.getCurrentItem().getType() == Material.SLIME_BALL) {
                stats.setvisableUnderwear(!stats.getvisableUnderwear());
                player.sendMessage(stats.getvisableUnderwear() ? "Undies are now visible using Optifine." : "Undies are now hidden.");
                OpenSettings(player, plugin);
                PantsCrafting.equipDiaperArmor(player, false, false);
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 625000 && event.getCurrentItem().getType() == Material.SLIME_BALL) {
                SoundEffectsMenu.SoundEffects(player, plugin,0);
            }
            else if (event.getCurrentItem().getType() == Material.CLOCK) {
                if (config.get("Hypno") != null && (Boolean) config.get("Hypno")) {
                    if (!stats.hasActiveHypnoTriggers()) {
                        int newPerm = (stats.getHypnoPermission() + 1) % 3;
                        stats.setHypnoPermission(newPerm);
                        if (newPerm == 0) {
                            player.sendMessage("Hypnosis: " + ChatColor.GRAY + "Disabled");
                        } else if (newPerm == 1) {
                            player.sendMessage("Hypnosis: " + ChatColor.YELLOW + "Caregivers Only");
                        } else {
                            player.sendMessage("Hypnosis: " + ChatColor.GREEN + "Anyone");
                        }
                        SavePlayerStats.savePlayerStats(player);
                        OpenSettings(player, plugin);
                    }
                }
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 629000 && event.getCurrentItem().getType() == Material.SLIME_BALL)
            {
                String url = (String) config.get("Discord_Link");
                player.closeInventory(); 
                player.sendMessage("Check out our Discord here: " + ChatColor.AQUA + url);
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 629001 && event.getCurrentItem().getType() == Material.SLIME_BALL)
            {
                String url = (String) config.get("Patreon_Link");
                player.closeInventory(); 
                player.sendMessage("Check out our Patreon here: " + ChatColor.AQUA + url);
            }
            else if (meta.hasCustomModelData() && meta.getCustomModelData() == 629002 && event.getCurrentItem().getType() == Material.SLIME_BALL)
            {
                String url = (String) config.get("Subscribestar_Link");
                player.closeInventory(); 
                player.sendMessage("Check out our Subscribestar here: " + ChatColor.AQUA + url);
            }
        }
    }
    private void updateScoreboard(Player player, PlayerStats stats) {
        // Ensure ScoreboardManager is not null
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            plugin.getLogger().warning("ScoreboardManager is null; Unable to set player scoreboard!");
            return;
        }

        // Update the scoreboard or clear it
        if (stats.getOptin() && stats.getUI() == 1) {
            ScoreBoard.createSidebar(player);
        } else if(stats.getOptin() && !(stats.getUI() == 1)){
            player.setScoreboard(scoreboardManager.getNewScoreboard()); // Clear the scoreboard
        }
    }

    public static String formatRemainingTime(PlayerStats stats) {
        long currentTime = System.currentTimeMillis() / 1000L;
        long timeElapsedSeconds = currentTime - stats.getHardcoreEnabledTime();
        long remainingSeconds = (20 * 3600) - timeElapsedSeconds;
        if (remainingSeconds <= 0) {
            return ChatColor.GREEN + "No time remaining";
        }
        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        return String.format("%d hours and %d minutes", hours, minutes);
    }
        
    public static void toggleHardcore(Player player, Plugin plugin) {
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        
        boolean newHardcoreState = !stats.getHardcore();
        
        if (newHardcoreState) {   // Enabling Hardcore
            stats.setHardcoreEnabledTime(System.currentTimeMillis() / 1000L);   // Store current time in seconds
            stats.setHardcore(true);
            stats.setshowfill(false);
            player.sendMessage(ChatColor.RED + "You have enabled Hardcore mode! You must wait at least 20 hours before disabling it.");
        } else {   // Disabling Hardcore
            if (stats.getHardcore()) {
                long currentTime = System.currentTimeMillis() / 1000L;
                long timeElapsedSeconds = currentTime - stats.getHardcoreEnabledTime();
                
                if (timeElapsedSeconds < 20 * 3600) {  // If less than 20 hours have passed
                    String remainingTime = formatRemainingTime(stats);
            
                    player.sendMessage(ChatColor.RED + "You can't disable Hardcore yet! You must wait another " + remainingTime);
                    return;  // Prevent disabling
                }
            }
            stats.setHardcore(false);
            player.sendMessage(ChatColor.GREEN + "You have disabled Hardcore mode.");
        }

        SavePlayerStats.savePlayerStats(player);   // Save the updated stats
        OpenSettings(player, plugin);
    }
}
