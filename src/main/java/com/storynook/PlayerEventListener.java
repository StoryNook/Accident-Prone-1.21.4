package com.storynook;

import com.storynook.Event_Listeners.Laxative;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.UpdateStats;
import com.storynook.PlayerStatsManagement.SavePlayerStats;
import com.storynook.items.CustomItemCheck;
import com.storynook.items.pants;
import com.storynook.menus.IncontinenceMenu;
import com.storynook.menus.NannyMenu;
import com.storynook.menus.SettingsMenu;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyManager;
import org.bukkit.Location;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;


public class PlayerEventListener implements Listener {
    private final Plugin plugin;
    HashMap<UUID, HashSet<NamespacedKey>> playerCraftedSpecialItems = new HashMap<>();
    
        
    public PlayerEventListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Map<String, Object> Globalconfig = plugin.getGlobalConfig();
        plugin.loadPlayerStats(event.getPlayer()); //Uses the plugin instance to load player stats
        //Discover all of the custom crafting recipes

        ArrayList<String> recipes = new ArrayList<String>();

        
        if (Globalconfig.get("Diapers") != null && (Boolean) Globalconfig.get("Diapers")) {
            recipes.add("Diaper");
            recipes.add("ThickDiaper");
            recipes.add("Pullup");
            recipes.add("Tape");
            recipes.add("DiaperStuffer");
            recipes.add("DiaperPail");
        }
        recipes.add("Toilet");
        if (Globalconfig.get("Accidents") != null && (Boolean) Globalconfig.get("Accidents")) {
            recipes.add("Underwear");
            recipes.add("CleanPants");
            recipes.add("Washer");
        }
        // String[] recipes = {
        //     "Diaper",
        //     "ThickDiaper", 
        //     "Pullup",
        //     "Underwear",
        //     "Tape",
        //     "CleanPants",
        //     "Toilet",
        //     "DiaperStuffer",
        //     "Washer",
        //     "LaxedItem",
        //     "Crib",
        //     "DiaperPail"
        // };
        if (Globalconfig.get("Hypno") != null && (Boolean) Globalconfig.get("Hypno")) {
            recipes.add("Hypnosis");
        }
        if (Globalconfig.get("Nursery_Items") != null && (Boolean) Globalconfig.get("Nursery_Items")) {
            recipes.add("highchair");
        }

        for (String recipe : recipes) {
            event.getPlayer().discoverRecipe(new NamespacedKey(plugin, recipe));
        }

        // plugin.manageParticleEffects(event.getPlayer());
    }
            
    //Updates stats and world events when the player leaves
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getVehicle() instanceof ArmorStand) {
            player.leaveVehicle();
            ArmorStand armorStand = (ArmorStand) player.getVehicle();
            armorStand.remove();
        }
        SavePlayerStats.savePlayerStats(event.getPlayer()); // Uses the plugin instance to save player stats
        if (plugin.getIntegrationsBus() != null) {
            plugin.getIntegrationsBus().clearCooldownsForPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        // Determine if the player is sprinting or walking normally
        if (player.isSprinting()) {
            UpdateStats.activityMultiplier.put(playerUUID, 1.5); // Increase for sprinting
        } else {
            UpdateStats.activityMultiplier.put(playerUUID, 1.0); // Normal walking or standing still
        }

        // Check for jumping specifically
        if (player.isOnGround() && player.getLocation().getY() > event.getFrom().getY()) {
            // The player location is ascending from the last event call and they were on the ground
            Double currentMultiplier = UpdateStats.activityMultiplier.getOrDefault(playerUUID, 1.0);
            UpdateStats.activityMultiplier.put(playerUUID, currentMultiplier + 0.5);
        }
    }
        
            
    //Handles the Player's consume event so they stay hydrated
    @EventHandler
    public void onPlayerDrink(PlayerItemConsumeEvent event) {
        PlayerStats stats = plugin.getPlayerStats(event.getPlayer().getUniqueId());
        if (stats != null) {
            ItemStack consumedItem = event.getItem();
            if (consumedItem.getType() == Material.MILK_BUCKET) {
                UpdateStats.HydrationSpike.put(event.getPlayer().getUniqueId(), 10);
                stats.increaseHydration(90);
                // That's a lot of milk: 1/100 chance it acts like a laxative.
                if (Math.random() < 0.01) {
                    Laxative.applyLaxDose(plugin, event.getPlayer(), 25);
                }
            } else if (isHydrating(consumedItem)) {
                // Increase hydration when the player drinks
                UpdateStats.HydrationSpike.put(event.getPlayer().getUniqueId(), 10);
                stats.increaseHydration(30);

                com.storynook.Integrations.IntegrationsBus bus = plugin.getIntegrationsBus();
                if (bus != null) {
                    java.util.Map<String,Object> ctx = new java.util.HashMap<>();
                    ctx.put("hydration", (int) stats.getHydration());
                    bus.fire(event.getPlayer(),
                            com.storynook.Integrations.events.ActionId.HYDRATE_THRESHOLD,
                            null, ctx);
                }
            }
        }
    }
    private boolean isHydrating(ItemStack item){
        if (item.getType() == Material.POTION || item.getType() == Material.MELON_SLICE) {
            return true;
        }
        return false;
    }
            

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        if (event.getRecipe() == null || event.getInventory() == null) return;

            ItemStack result = event.getRecipe().getResult();
            if (result.getType() != Material.LEATHER_LEGGINGS) return;

            Material woolColor = null;

            // Check wool colors in the grid
            for (ItemStack item : matrix) {
                if (item != null && item.getType() == Material.LEATHER) {
                    return;
                }
                else if (item != null && item.getType().toString().endsWith("_WOOL")) {
                    if (woolColor == null) {
                        woolColor = item.getType(); // Set initial color
                    } else if (!woolColor.equals(item.getType())) {
                        event.getInventory().setResult(new ItemStack(Material.AIR)); // Mismatched colors cancel craft
                        return;
                    }
                }
            }

            if (woolColor != null) {

                Color color = pants.getColorFromWool(woolColor);
                if (color == null) {
                    // Cancel crafting if color mapping fails
                    event.getInventory().setResult(new ItemStack(Material.AIR));
                    return;
                }
                event.getInventory().setResult(pants.createPants(woolColor));
            }
            else {
                event.getInventory().setResult(new ItemStack(Material.AIR)); // Ensure no item is crafted if no wool is found
            }

        for (ItemStack item : matrix) {
            if (item == null) continue;

            if (CustomItemCheck.isCustomItem(item)) {
                inventory.setResult(null); // Only block crafting if it's not intended
            }
        }
    }

    // @EventHandler
    // public void onPotionEffect(EntityPotionEffectEvent event) {
    //     if (event.getEntity() instanceof Player) {
    //         Player player = (Player) event.getEntity();
    //         PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());

    //         if (stats != null && event.getNewEffect() != null) {
    //             PotionEffectType effectType = event.getNewEffect().getType();
                
    //             if (effectType.equals(PotionEffectType.SPEED)) {
    //                 int amplifier = event.getNewEffect().getAmplifier();
    //                 stats.setBladderFillRate(1 + (int) (0.2 * (amplifier + 1))); // Increase Bladder and bowel fill rate because of speed potion
    //                 stats.setBowelFillRate(1 + (int) (0.2 * (amplifier + 1)));
    //             }
    //         }
    //     }
    // }

    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (plugin.isAwaitingInput(playerUUID)) {
            event.setCancelled(true); // Prevents the chat from showing to other players
            String inputType = plugin.getAwaitingInputType(playerUUID);
            String message = event.getMessage().trim();
            try {
                if ("minFill".equals(inputType)) {
                    int newMinFill = Integer.parseInt(message);
                    PlayerStats stats = plugin.getPlayerStats(playerUUID);
                    stats.setMinFill(newMinFill);
                    player.sendMessage(ChatColor.GREEN + "Minimum fill set to: " + newMinFill);
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () -> SettingsMenu.OpenSettings(player, plugin));
                }
                else if ("bladderincon".equals(inputType)){
                    int newBladderincon = Integer.parseInt(message);
                    PlayerStats stats = plugin.getPlayerStats(playerUUID);
                    stats.setBladderIncontinence(newBladderincon);
                    player.sendMessage(ChatColor.GREEN + "Bladder Incontinence set to: " + newBladderincon);
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () -> IncontinenceMenu.IncontinenceSettings(player, plugin));
                }else if ("bowelincon".equals(inputType)){
                    int newBowelincon = Integer.parseInt(message);
                    PlayerStats stats = plugin.getPlayerStats(playerUUID);
                    stats.setBowelIncontinence(newBowelincon);
                    player.sendMessage(ChatColor.GREEN + "Bowel Incontinence set to: " + newBowelincon);
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () ->  IncontinenceMenu.IncontinenceSettings(player, plugin));
                } else if ("nannyName".equals(inputType)) {
                    NannyManager mgr = plugin.getNannyManager();
                    plugin.clearAwaitingInput(playerUUID);
                    if (mgr == null) {
                        player.sendMessage(ChatColor.RED + "Nanny system not initialised.");
                    } else {
                        Location pending = mgr.getPendingNannyCreations().remove(playerUUID);
                        String trimmedName = message.length() > 32 ? message.substring(0, 32) : message;
                        if (pending != null) {
                            // New Nanny creation flow from a Nanny Egg
                            Bukkit.getScheduler().runTask(plugin, () -> mgr.createNanny(player, trimmedName, pending));
                        } else {
                            // Renaming an existing Nanny via the menu
                            NannyData data = mgr.getNannyForOwner(playerUUID);
                            if (data == null) {
                                player.sendMessage(ChatColor.RED + "You don't own a Nanny.");
                            } else {
                                mgr.updateNannyName(data.getNannyUUID(), trimmedName);
                                player.sendMessage(ChatColor.GREEN + "Nanny name set to: " + trimmedName);
                                Bukkit.getScheduler().runTask(plugin, () -> NannyMenu.openGeneral(player, plugin));
                            }
                        }
                    }
                } else if ("nannyChangeThreshold".equals(inputType)) {
                    int v = Integer.parseInt(message);
                    NannyManager mgr = plugin.getNannyManager();
                    NannyData ndata = mgr == null ? null : mgr.getNannyForOwner(playerUUID);
                    if (ndata != null) {
                        ndata.setChangeThreshold(Math.max(0, Math.min(100, v)));
                        ndata.save(plugin.getDataFolder());
                        player.sendMessage(ChatColor.GREEN + "Change threshold set to: " + ndata.getChangeThreshold());
                    }
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () -> NannyMenu.openSupplies(player, plugin));
                } else if ("nannyFeedThreshold".equals(inputType)) {
                    int v = Integer.parseInt(message);
                    NannyManager mgr = plugin.getNannyManager();
                    NannyData ndata = mgr == null ? null : mgr.getNannyForOwner(playerUUID);
                    if (ndata != null) {
                        ndata.setFeedThreshold(Math.max(0, Math.min(20, v)));
                        ndata.save(plugin.getDataFolder());
                        player.sendMessage(ChatColor.GREEN + "Feed threshold set to: " + ndata.getFeedThreshold());
                    }
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () -> NannyMenu.openSupplies(player, plugin));
                } else if ("nannyHydrationThreshold".equals(inputType)) {
                    int v = Integer.parseInt(message);
                    NannyManager mgr = plugin.getNannyManager();
                    NannyData ndata = mgr == null ? null : mgr.getNannyForOwner(playerUUID);
                    if (ndata != null) {
                        ndata.setHydrationThreshold(Math.max(0, Math.min(100, v)));
                        ndata.save(plugin.getDataFolder());
                        player.sendMessage(ChatColor.GREEN + "Hydration threshold set to: " + ndata.getHydrationThreshold());
                    }
                    plugin.clearAwaitingInput(playerUUID);
                    Bukkit.getScheduler().runTask(plugin, () -> NannyMenu.openSupplies(player, plugin));
                } else if ("nannySkinUrl".equals(inputType)) {
                    NannyManager mgr = plugin.getNannyManager();
                    plugin.clearAwaitingInput(playerUUID);
                    if (mgr == null) {
                        player.sendMessage(ChatColor.RED + "Nanny system not initialised.");
                    } else {
                        NannyData data = mgr.getNannyForOwner(playerUUID);
                        if (data == null) {
                            player.sendMessage(ChatColor.RED + "You don't own a Nanny.");
                        } else {
                            String skin = "default".equalsIgnoreCase(message) ? "" : message;
                            mgr.updateNannySkin(data.getNannyUUID(), skin);
                            player.sendMessage(ChatColor.GREEN + "Nanny skin set to: " + (skin.isEmpty() ? "(default)" : skin));
                            Bukkit.getScheduler().runTask(plugin, () -> NannyMenu.openGeneral(player, plugin));
                        }
                    }
                }
            } catch (NumberFormatException e) {
                plugin.clearAwaitingInput(playerUUID);
                player.sendMessage(ChatColor.RED + "Invalid number. Please enter a valid number.");
                if ("minFill".equals(inputType)) {
                    Bukkit.getScheduler().runTask(plugin, () -> SettingsMenu.OpenSettings(player, plugin));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> IncontinenceMenu.IncontinenceSettings(player, plugin));
                }
            } finally {
                // Ensure the input state is always cleared
                plugin.clearAwaitingInput(playerUUID);
            }
        }
    }
}

