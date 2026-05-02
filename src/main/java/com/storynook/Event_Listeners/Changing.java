package com.storynook.Event_Listeners;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.swing.text.html.parser.Entity;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import com.storynook.PlaySounds;
import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCheck;
import com.storynook.items.underwear;
import com.storynook.items.CustomItemCoolDown;
import com.storynook.nanny.Capability;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyManager;
import com.storynook.nanny.NannyPolicy;

public class Changing implements Listener{
    static HashMap<UUID, Boolean> Justchanged = new HashMap<>();
    static HashMap<UUID, Double> distanceinBlocks = new HashMap<>();
    
    private static Plugin plugin;
    public Changing(Plugin plugin) {
        this.plugin = plugin;
    }  

    // @EventHandler
    // public void onPlayerInteractWithEntity(PlayerInteractEntityEvent event) {
    //     if (event.getRightClicked() instanceof Player) {

    //         Player actor = event.getPlayer();
    //         Player target = (Player) event.getRightClicked();
        
    //         if (target instanceof Player){
    //             int rightclicktimes = plugin.rightclickCount.getOrDefault(actor.getUniqueId(), 0);
    //             PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
    //             ItemStack item = actor.getInventory().getItemInMainHand();
    //             if (targetStats != null && targetStats.getOptin()) {
    //                 if (targetStats != null && targetStats.isCaregiver(actor.getUniqueId(), true)) {
    //                     if (item != null && item.getType() != Material.AIR) {
    //                         ItemMeta meta = item.getItemMeta();
    //                         if (meta != null && meta.hasCustomModelData()) {
    //                             int customModelData = item.getItemMeta().getCustomModelData();
    //                             if (item.getType() == Material.CLOCK) {
                                    
    //                                 Hypno.HypnoInteract(actor, target);
    //                                 return;
    //                             }
    //                             if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) {
    //                                 return;
    //                             }
    //                             if (!CustomItemCheck.VailidUnderwear(item)) {
    //                                 return;
    //                             }
    //                             rightclicktimes++;
    //                             if (rightclicktimes > 1) {
    //                                 // rightclicktimes = 1;
    //                                 plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                                 return;
    //                             }
    //                             else if (rightclicktimes == 1){
    //                                 plugin.firstimeran.put(actor.getUniqueId(), true);
    //                                 plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                                 // playAudio(actor, customModelData, targetStats.getUnderwearType());
    //                                 handleRightClickHold(actor, target, true, customModelData, targetStats.getUnderwearType());
    //                             }
    //                             else {
    //                                 return;
    //                             }
    //                         }
    //                     }
    //                     if (item != null && item.getType() == Material.AIR && actor.isSneaking()) {
    //                         rightclicktimes++;
    //                         if (rightclicktimes > 1) {
    //                             // rightclicktimes = 1;
    //                             plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                             return;
    //                         }
    //                         else if (rightclicktimes == 1){
    //                             plugin.firstimeran.put(actor.getUniqueId(), true);
    //                             plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                             plugin.CheckLittles(actor, targetStats, target);
    //                         }
    //                         else {
    //                             return;
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }
            
    // @EventHandler
    // public void onPlayerInteract(PlayerInteractEvent event) {
    //     Player actor = event.getPlayer();
    //     PlayerStats Stats = plugin.getPlayerStats(actor.getUniqueId());
    //     ItemStack itemInHand = actor.getInventory().getItemInMainHand();
    //     if (itemInHand != null && itemInHand.getType() != Material.AIR) {
    //         ItemMeta meta = itemInHand.getItemMeta();
    //         if (meta != null && meta.hasCustomModelData()) {
    //             int rightclicktimes = plugin.rightclickCount.getOrDefault(actor.getUniqueId(), 0);
    //             int customModelData = meta.getCustomModelData();
    //             if (customModelData == 626007) {
    //                 event.setCancelled(true); // Cancel the interaction
    //                 if (Stats.getOptin() && Stats.getLayers() < 4) {
    //                     //Cooldown
    //                     CustomItemCoolDown cooldown = new CustomItemCoolDown();
    //                     if(cooldown.cooldown.contains(actor.getUniqueId())){
    //                         return;
    //                     }
    //                     cooldown.Cooldown(actor, 5);

    //                     int maxLayers = 0;
    //                     switch(Stats.getUnderwearType()) {
    //                         case 0: maxLayers = 1; break;
    //                         case 1: maxLayers = 2; break;
    //                         case 2: maxLayers = 3; break;
    //                         case 3: maxLayers = 4; break;
    //                         default: return;
    //                     }
    //                     if (Stats.getLayers() >= maxLayers) {
    //                         actor.sendMessage(ChatColor.RED + "You cannot add more layers with your current underwear.");
    //                         return;
    //                     }
    //                     if (Stats.getDiaperWetness() >= 100) {
    //                         actor.sendMessage(ChatColor.RED + "It's a little too late for that, don't you think?");
    //                         return;
    //                     }
    //                     Stats.setLayers(Stats.getLayers() + 1);
    //                     actor.sendMessage(ChatColor.GREEN + "Added a layer! Current layers: " + Stats.getLayers());

    //                     if (itemInHand.getAmount() > 1) {
    //                         itemInHand.setAmount(itemInHand.getAmount() - 1);
    //                     } else {
    //                         actor.getInventory().setItemInMainHand(null);
    //                     }
    //                 }
    //                 return; // Exit the method to prevent further execution
    //             }
    //             else if(CustomItemCheck.VailidUnderwear(itemInHand)){
    //                 if (event.getAction().name().contains("RIGHT_CLICK")) {
    //                     if (itemInHand == null || !itemInHand.hasItemMeta() || !itemInHand.getItemMeta().hasCustomModelData()) {
    //                         plugin.rightclickCount.put(actor.getUniqueId(), 0);
    //                         plugin.firstimeran.put(actor.getUniqueId(), false);
    //                         return;
    //                     } else if (Stats.getHardcore()) {
    //                         actor.sendMessage("You are in HardCore mode. You should ask a caregiver for help.");
    //                         return;
    //                     }
    //                     else if (!Stats.getOptin()) {
    //                         return;
    //                     }
    //                     else {
    //                         rightclicktimes++;
    //                         if (rightclicktimes > 1) {
    //                             plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                             return;
    //                         }
    //                         else if (rightclicktimes == 1){
    //                             plugin.firstimeran.put(actor.getUniqueId(), true);
    //                             plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
    //                             // playAudio(actor, customModelData, Stats.getUnderwearType());
    //                             handleRightClickHold(actor, null, false, customModelData, Stats.getUnderwearType());
    //                         }
    //                         else {
    //                             return;
    //                         }
    //                     }
    //                 }
    //             }
                
    //         }
    //         else if (itemInHand.getType() == Material.CLOCK) {
    //             Hypno.HypnoInteract(actor, null);
    //         }
    //     }
    // }
            
    public static void handleRightClickHold(Player actor, Player target, boolean isCaregiverInteraction, int totype, int fromtype) {
        // Phase 5a: caregiver-blocking gate. If the target's Nanny has
        // BLOCK_CAREGIVERS permission and the actor is not the Nanny's owner,
        // cancel the interaction.
        try {
            if (target != null && actor != null && actor != target) {
                NannyManager mgr = plugin.getNannyManager();
                if (mgr != null) {
                    java.util.UUID actorUUID = actor.getUniqueId();
                    for (NannyData data : mgr.getAllNannies().values()) {
                        boolean isWard = data.getOwnerUUID().equals(target.getUniqueId())
                                || data.getWards().contains(target.getUniqueId());
                        if (!isWard) continue;
                        if (!NannyPolicy.allows(data, Capability.BLOCK_CAREGIVERS)) continue;
                        if (!data.getOwnerUUID().equals(actorUUID)) {
                            actor.sendMessage(org.bukkit.ChatColor.RED + "["
                                    + data.getName() + "] Only " + data.getName()
                                    + " or her owner may change this ward.");
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // never break the change pipeline on a policy lookup failure
        }
        playAudio(actor, totype, fromtype);
        if (plugin.rightclickCount.get(actor.getUniqueId()) > 0 && plugin.firstimeran.get(actor.getUniqueId())) {
            plugin.firstimeran.put(actor.getUniqueId(), false);
            BossBar bossBar = Bukkit.createBossBar(ChatColor.GREEN + "Changing", BarColor.BLUE, BarStyle.SOLID);
            bossBar.addPlayer(actor);
            bossBar.setProgress(0.0); // Start with progress 0 (empty)

            int timeLeft; // Time in seconds

            if (isCaregiverInteraction && target != null) {
                PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
                timeLeft = stats.getDiaperFullness() > 50 ? 5 : 3;
            }
            else{
                PlayerStats stats = plugin.getPlayerStats(actor.getUniqueId());
                timeLeft = (int) Math.min(10, Math.max(5, stats.getDiaperFullness() * 0.1));
            }

            BukkitRunnable task = new BukkitRunnable() {
                private int ticksLeft = 20 * timeLeft; // Convert seconds to ticks
                @Override
                public void run() {
                    if (isCaregiverInteraction && target != null) {
                        Player caregiver = (Player) actor;
                        Location senderLocation = caregiver.getLocation();
                        Location targetLocation = target.getLocation();
                
                        // Check the distance between the sender and the target player
                        double distance = senderLocation.distance(targetLocation);
                        distanceinBlocks.put(actor.getUniqueId(), distance);
                    }
                    if (ticksLeft <= 0) {
                        bossBar.removePlayer(actor);
                        handleInteraction(actor, target, isCaregiverInteraction);
                        stopAudio(actor, totype, fromtype);
                        plugin.rightclickCount.put(actor.getUniqueId(), 0);
                        this.cancel();
                    } else {
                        ItemStack item = actor.getInventory().getItemInMainHand();
                        if (item == null || !item.hasItemMeta() || !CustomItemCheck.VailidUnderwear(item) || (isCaregiverInteraction && distanceinBlocks.get(actor.getUniqueId()) > 3)) {
                            plugin.rightclickCount.put(actor.getUniqueId(), 0);
                            bossBar.removePlayer(actor);
                            stopAudio(actor,totype, fromtype);
                            this.cancel();
                        }
                        ticksLeft--;
                        double progress = (double) ticksLeft / (20 * timeLeft);
                        bossBar.setProgress(progress);
                    }
                }
            };

            task.runTaskTimer(plugin, 0L, 1L); // Run every tick
        }
    }

    public void commandChange(Player target, int totype, int fromtype) {
        playAudio(target, totype, fromtype);
        int timeLeft; // Time in seconds
        PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
        timeLeft = stats.getDiaperFullness() > 50 ? 5 : 3;

        BukkitRunnable task = new BukkitRunnable() {
            private int ticksLeft = 20 * timeLeft; // Convert seconds to ticks
            @Override
            public void run() {
                
                if (ticksLeft <= 0) {
                    stopAudio(target, totype, fromtype);
                    target.sendMessage(ChatColor.GREEN + "You got changed and cleaned!");
                    distributeUsedItems(target, target, stats);
                    ItemStack diaper = new ItemStack(Material.SLIME_BALL, 1);
                    ItemMeta meta = diaper.getItemMeta();
                    meta.setDisplayName("Thick Diaper");
                    meta.setCustomModelData(626003);
                    diaper.setItemMeta(meta);
                    resetAndUpdateStats(stats, diaper, target, target);
                    this.cancel();
                } else {
                    ticksLeft--;
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    private static void playAudio(Player player, int totype, int fromtype) {
        if ((totype == 626002 && fromtype != 0) || fromtype != 0) {
            PlaySounds.playsounds(player, "changing", 5, 1.0, 0.2, true);
        }
    }

    private static void stopAudio(Player player, int totype, int fromtype) {
        if ((totype == 626002 && fromtype != 0) || fromtype !=0) {
            PlaySounds.stopSounds(player);
        }
    }
    private static void handleInteraction(Player actor, Player target, boolean isCaregiverInteraction) {
        if (!isCaregiverInteraction) {
            target = actor;
        }
        PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
        ItemStack item = actor.getInventory().getItemInMainHand();

        if (CustomItemCheck.VailidUnderwear(item)) {
            // Remove or decrement the item the actor is holding
            decrementItem(actor, item);
            // Logic to provide items based on wetness and fullness
            distributeUsedItems(actor, target, stats);

            // Reset and update target-side stats (delegates to applyChange)
            applyChange(target, item);

            if (actor == target) {
                actor.sendMessage(ChatColor.GREEN + "You got changed and cleaned!");
            }
            else if(actor != target){
                actor.sendMessage(ChatColor.GREEN + "You changed: " + target.getName());
                target.sendMessage(ChatColor.GREEN + "You were changed by: " + actor.getName() + " Be sure to thank them!");
            }
        } else{
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) {
                return;
            }
        }
    }

    /**
     * Performs the diaper-change stat mutation on {@code target} without any
     * actor-side UI (no boss bar, no held-item validation, no distance check,
     * no soiled-item distribution to an actor's inventory). Used by the
     * existing handleInteraction flow (after the boss-bar timer succeeds) and
     * by NannyCareEngine when the Nanny acts autonomously.
     *
     * Reads the new underwearType from cleanDiaper's CustomModelData and
     * resets wetness, fullness, timeWorn, layers via {@link #resetAndUpdateStats}.
     * No-op if any argument is null, target stats are unavailable, or the
     * clean diaper has no CustomModelData.
     */
    public static void applyChange(Player target, ItemStack cleanDiaper) {
        if (target == null || cleanDiaper == null) return;
        PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
        if (stats == null) return;
        ItemMeta meta = cleanDiaper.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;
        resetAndUpdateStats(stats, cleanDiaper, target, target);
    }
            
    private static void distributeUsedItems(Player actor, Player target, PlayerStats stats) {
        // Distribute items according to the target's diaper status

        if(stats.getDiaperFullness() > 0 && stats.getUnderwearType() > 0){ItemStack stinkydiaper = underwear.createStinkyDiaper(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(stinkydiaper);}
        else if(stats.getDiaperWetness() > 0 && stats.getUnderwearType() == 1){ItemStack wetpullup = underwear.createWetPullup(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints()); actor.getInventory().addItem(wetpullup);}
        else if(stats.getDiaperWetness() > 0 && stats.getUnderwearType() == 2){ItemStack wetdiaper = underwear.createWetDiaper(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(wetdiaper);}
        else if(stats.getDiaperWetness() > 0 && stats.getUnderwearType() == 3){ItemStack wetthickdiaper = underwear.createWetThickDiaper(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(wetthickdiaper);}
        else if (stats.getDiaperWetness() >= 100 && stats.getDiaperFullness() >= 100){
            ItemStack wetANDdirtyunderwear = underwear.createWetANDDirtyUndies(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(wetANDdirtyunderwear);
        }
        else if(stats.getDiaperWetness() >= 100 && stats.getUnderwearType() == 0){ItemStack wetunderwear = underwear.createWetUndies(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(wetunderwear);}
        else if(stats.getDiaperFullness() >= 100 && stats.getUnderwearType() == 0){ItemStack dirtyunderwear = underwear.createDirtyUndies(target, (int)stats.getDiaperWetness(),(int)stats.getDiaperFullness(),(int) stats.getRashPoints());actor.getInventory().addItem(dirtyunderwear);}
        else if (stats.getDiaperFullness() == 0 && stats.getDiaperWetness() == 0 || stats.getUnderwearType() == 0 && stats.getDiaperWetness() < 100) {
            switch (stats.getUnderwearType()) {
                case 0:
                    actor.getInventory().addItem(underwear.Underwear());
                    break;
                case 1:
                    actor.getInventory().addItem(underwear.Pullup());
                    break;
                case 2:
                    actor.getInventory().addItem(underwear.Diaper());
                    break;
                case 3:
                    actor.getInventory().addItem(underwear.ThickDiaper());
                    break;
                default:
                    break;
            }
        }
    }
            
    private static void decrementItem(Player actor, ItemStack item) {
        // Decrement or remove the item from the actor's inventory

        int currentAmount = item.getAmount();
        if (currentAmount > 1) {
            item.setAmount(currentAmount - 1);
        } else {
            actor.getInventory().setItemInMainHand(null);
        }
    }

    private static void resetAndUpdateStats(PlayerStats stats, ItemStack Underwear, Player target, Player actor) {
        //See if the player is already bound and therefore needs to be freed
        if (stats.getDiaperBondedTo() != null) {
            UUID bondedToUUID = stats.getDiaperBondedTo();

            // Check if the player who was bonded to is online
            Player bondedPlayer = plugin.getServer().getPlayer(bondedToUUID);
            
            if (bondedPlayer != null) {
                // Player is online, remove from their stats directly
                PlayerStats bondedStats = plugin.getPlayerStats(bondedToUUID);
                if (bondedStats != null) {
                    bondedStats.removeMyDiaperBindee(target.getUniqueId());
                }
            } else {
                // Player is offline, need to load their stats file and remove the binding
                File playerFile = plugin.getPlayerFile(bondedToUUID);
                if (playerFile.exists()) {
                    try {
                        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                        if (config.contains("MyDiaperBindees")) {
                            List<String> boundTo = config.getStringList("MyDiaperBindees");
                            boundTo.remove(target.getUniqueId().toString());
                            config.set("MyDiaperBindees", boundTo);
                            config.save(playerFile);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("Failed to update offline player stats for " + bondedToUUID);
                        e.printStackTrace();
                    }
                }
            }
            
            stats.setDiaperBondedTo(null);
        }
        int customModelData = Underwear.getItemMeta().getCustomModelData();
        // Read the Binding_Diapers configuration value
        boolean bindingDiapers = false;
        UUID itemCraftedUUID = null;
        if (plugin.getGlobalConfig() != null) {
            Object bindingValue = plugin.getGlobalConfig().get("Binding_Diapers");
            if (bindingValue instanceof Boolean) {
                bindingDiapers = (Boolean) bindingValue;
            }
        }
        if (bindingDiapers) {
            ItemMeta meta = Underwear.getItemMeta();
            if (meta != null) {
                NamespacedKey curseKey = new NamespacedKey(plugin, "cursed");
                NamespacedKey uuidKey = new NamespacedKey(plugin, "crafted_by_uuid");
                
                // Check if item is cursed
                boolean isCursed = meta.getPersistentDataContainer().has(curseKey, PersistentDataType.BYTE);
                
                // Check if item was crafted by the current player
                boolean craftedByPlayer = false;
                if (meta.getPersistentDataContainer().has(uuidKey, PersistentDataType.STRING)) {
                    String itemUUID = meta.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
                    if (itemUUID != null) {
                        try {
                            itemCraftedUUID = UUID.fromString(itemUUID);
                            craftedByPlayer = itemCraftedUUID.equals(target.getUniqueId());
                        } catch (IllegalArgumentException e) {
                            // Invalid UUID format
                        }
                    }
                }
                if (isCursed && !craftedByPlayer) {
                    stats.setDiaperBondedTo(itemCraftedUUID);
                    PlayerStats CasterStats = plugin.getPlayerStats(itemCraftedUUID);
                    CasterStats.addMyDiaperBindee(target.getUniqueId());
                }
            }
        }
        // Reset stats and set new underwear type
        stats.setDiaperFullness(0);
        stats.setDiaperWetness(0);
        stats.setRashPoints(0);
        stats.setLayers(0);
        com.storynook.DesignRegistry.DesignDef def = com.storynook.DesignRegistry.findByCleanCmd(customModelData);
        if (def != null) {
            stats.applyUnderwear(def.category, def.designId);
        }
        Justchanged.put(target.getUniqueId(), true);
        PantsCrafting.equipDiaperArmor(target, true, false);
        plugin.manageParticleEffects(target);
    }
}
