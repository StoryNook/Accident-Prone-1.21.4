package com.storynook.AccidentsANDWanrings;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import com.storynook.PlaySounds;
import com.storynook.Plugin;
import com.storynook.Event_Listeners.PantsCrafting;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyEventLog;
import com.storynook.nanny.NannyManager;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Random;

public class HandleAccident {
    private static Plugin plugin;
        public HandleAccident(Plugin plugin) {
            this.plugin = plugin;
        }
        public static void handleAccident(boolean isBladder, Player player, Boolean PlaySound, String MessageType) {
            
        PlayerStats triggerStats = plugin.getPlayerStats(player.getUniqueId());
        triggerStats.setUrgeToGo(0);
        PlayerStats stats = null;
        Player targetPlayer = null;
        
        // Read the Binding_Diapers configuration value
        boolean bindingDiapers = false;
        if (plugin.getGlobalConfig() != null) {
            Object bindingValue = plugin.getGlobalConfig().get("Binding_Diapers");
            if (bindingValue instanceof Boolean) {
                bindingDiapers = (Boolean) bindingValue;
            }
        }
        if(bindingDiapers){
            List<UUID> boundTo = triggerStats.getMyDiaperBindees();
            if (boundTo != null && !boundTo.isEmpty()) {
                List<Player> onlinePlayers = new ArrayList<>();

                for (UUID boundUUID : boundTo) {
                    Player boundPlayer = plugin.getServer().getPlayer(boundUUID);
                    if (boundPlayer != null && boundPlayer.isOnline()) {
                        onlinePlayers.add(boundPlayer);
                    }
                }
                // If there are online players, randomly select one
                if (!onlinePlayers.isEmpty()) {
                    Random random = new Random();
                    targetPlayer = onlinePlayers.get(random.nextInt(onlinePlayers.size()));
                    stats = plugin.getPlayerStats(targetPlayer.getUniqueId());
                } else {
                    // If no online players, use the triggerStats player
                    stats = triggerStats;
                    targetPlayer = player;
                }
            }
            else {
                // Not bound to anyone
                stats = triggerStats;
                targetPlayer = player;
            }
        }
        else{stats = triggerStats; targetPlayer = player;}
        
        if (isBladder) {
            double wetbyamount;
            switch (stats.getLayers()) {
                case 0: wetbyamount = triggerStats.getBladder(); break;
                case 1: wetbyamount = triggerStats.getBladder()/1.5; break;
                case 2: wetbyamount = triggerStats.getBladder()/2; break;
                case 3: wetbyamount = triggerStats.getBladder()/3; break;
                case 4: wetbyamount = triggerStats.getBladder()/4; break;
                default: wetbyamount = triggerStats.getBladder(); break;
            }

            switch (stats.getUnderwearType()) {
                case 0:
                    if (stats.getLayers() > 0) {if(stats.getDiaperWetness() >= 100) {stats.increaseDiaperWetness(100);} else {stats.increaseDiaperWetness(50);}}
                    else {stats.increaseDiaperWetness(100);}
                    break;
                case 1: stats.increaseDiaperWetness(wetbyamount); break;
                case 2: stats.increaseDiaperWetness(wetbyamount/2); break;
                case 3: stats.increaseDiaperWetness(wetbyamount/4); break;
                default: break;
            }
            triggerStats.setBladder(0);
            if (!triggerStats.getBladderLockIncon()) {
                triggerStats.increaseBladderIncontinence(accidentInconDelta(triggerStats.getUnderwearType()));
            }
        } else {
            if (stats.getUnderwearType() == 0) {stats.increaseDiaperFullness(100);}
            else if (stats.getUnderwearType() == 1) {stats.increaseDiaperFullness(triggerStats.getBowels());}
            else if (stats.getUnderwearType() == 2) {stats.increaseDiaperFullness(triggerStats.getBowels()/2);}
            else if (stats.getUnderwearType() == 3) {stats.increaseDiaperFullness(triggerStats.getBowels()/4);}
            triggerStats.setBowels(0);
            if(!triggerStats.getBowelLockIncon()){
                triggerStats.increaseBowelIncontinence(accidentInconDelta(triggerStats.getUnderwearType()));
            }
        }
        if (PlaySound) {
            PlaySounds.playsounds(targetPlayer,(isBladder ? "pee" : "mess"), 5,1.0,0.2, false);
        }
        if (targetPlayer != player) {
            targetPlayer.sendMessage(plugin.getRandomMessage("Binding_Accident", "?caster?", player.getName()));
            player.sendMessage(plugin.getRandomMessage("Caster_Accident", "?target?", targetPlayer.getName()));
            MessageType = "null";
        }
        switch (MessageType) {
            case "Normal":
                if (isBladder ? stats.getBladderIncontinence() == 10 && !stats.getBladderLockIncon() : stats.getBowelIncontinence() == 10 && !stats.getBowelLockIncon()) {
                    targetPlayer.sendMessage(plugin.getRandomMessage("Full_Incon"));
                }
                else if (isBladder ? stats.getBladderIncontinence() > 7 : stats.getBowelIncontinence() > 7) {
                    targetPlayer.sendMessage(plugin.getRandomMessage("High_Incon"));
                }
                else if (isBladder ? stats.getBladderIncontinence() > 3 : stats.getBowelIncontinence() > 3) {
                    targetPlayer.sendMessage(plugin.getRandomMessage("Medium_Incon"));
                }else {
                    targetPlayer.sendMessage(plugin.getRandomMessage("Genaric"));
                }
                break;
            case "Couldn't Hold":
                targetPlayer.sendMessage(plugin.getRandomMessage("Could_Not_Hold"));
                break;
            case "Body Betrayed":
                targetPlayer.sendMessage(plugin.getRandomMessage("Body_Betrayed"));
                break;
            case "Fear Wetting":
                player.sendMessage(plugin.getRandomMessage("Fear_Wetting"));
                break;
            case "Fear Messing":
                player.sendMessage(plugin.getRandomMessage("Fear_Messing"));
                break;
            case "Bedwetting":
                player.sendMessage(plugin.getRandomMessage("Bedwetting"));
                break;
            case "Lightening":
                player.sendMessage(plugin.getRandomMessage("Ligtening_Accidnet"));
                break;
            case "Tickling":
                player.sendMessage(plugin.getRandomMessage("Tickling"));
                break;
            case "Peeing_Self":
                player.sendMessage(plugin.getRandomMessage("Peeing_self"));
                break;
            case "Pooping_Self":
                player.sendMessage(plugin.getRandomMessage("Pooping_Self"));
                break;
            case "Hypno_Wetting":
                targetPlayer.sendMessage(plugin.getRandomMessage("Hypno_Wetting"));
                break;
            case "Hypno_Messing":
                targetPlayer.sendMessage(plugin.getRandomMessage("Hypno_Messing"));
                break;
            case "SILENT":
                break;
            case "null":
                break;
            default:
                break;
        } 
            
        
        
        if (stats.getDiaperWetness() >= 100 && stats.getDiaperFullness() >= 100) {
            changeLeggingsModel(targetPlayer, 626018);
        }
        else if (stats.getDiaperWetness() >= 100 && stats.getDiaperFullness() < 100) {
            changeLeggingsModel(targetPlayer, 626016);
        }
        else if(stats.getDiaperFullness() >= 100 && stats.getDiaperWetness() < 100){
            changeLeggingsModel(targetPlayer, 626017);
        }
        if (stats.getvisableUnderwear()) {
            PantsCrafting.equipDiaperArmor(targetPlayer, false, true);
        }
        // plugin.manageParticleEffects(player);
        // Phase 4: log WARD_HAD_ACCIDENT for any active Nanny watching this player
        try {
            NannyManager mgr = plugin.getNannyManager();
            if (mgr != null) {
                java.util.UUID playerUUID = player.getUniqueId();
                for (NannyData data : mgr.getAllNannies().values()) {
                    boolean isWard = data.getOwnerUUID().equals(playerUUID)
                            || data.getWards().contains(playerUUID);
                    if (!isWard) continue;
                    NannyEventLog log = mgr.getEventLog(data.getNannyUUID());
                    if (log != null) {
                        log.log(NannyEventLog.NannyEventType.WARD_HAD_ACCIDENT,
                                playerUUID,
                                (isBladder ? "wetting" : "messing") + " type=" + MessageType);
                    }
                }
                // D2: mood-keyed reaction broadcast (bedwetting branch is its own category).
                // Throttled per-(nanny, key, ward) inside speakIfNearby; this handles repeat
                // accidents in close succession without a separate cooldown here.
                com.storynook.nanny.NannyChatEngine ce = mgr.getChatEngine();
                if (ce != null) {
                    boolean isBedwetting = "Bedwetting".equals(MessageType);
                    String category = isBedwetting ? "bedwetting_reaction" : "accident_reaction";
                    String throttle = isBedwetting ? "accident:bedwetting" : "accident:awake";
                    ce.speakIfNearby(player, category, throttle, 60_000L,
                            com.storynook.nanny.NannyChatEngine.PRI_ACCIDENT);
                }
            }
        } catch (Throwable t) {
            // never let event logging break the accident pipeline
        }
    }

    // Incontinence increase per underwear type on accident.
    // 0=underwear, 1=pull-ups: big increase — accidents are a big deal.
    // 2=diaper, 3=thick diaper: small increase — protection absorbs the consequence.
    private static double accidentInconDelta(int underwearType) {
        switch (underwearType) {
            case 0: return 1.0;   // underwear
            case 1: return 1.0;   // pull-ups
            case 2: return 0.2;   // diaper
            case 3: return 0.1;   // thick diaper
            default: return 0.5;
        }
    }

    private static void changeLeggingsModel(Player player, int modelData) {
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && leggings.getType() == Material.LEATHER_LEGGINGS) {
            LeatherArmorMeta meta = (LeatherArmorMeta) leggings.getItemMeta();
            if (meta.getCustomModelData() == 626015 || meta.getCustomModelData() == 626016 || meta.getCustomModelData() == 626017) {
                String equipId = pantsEquipmentIdForCmd(modelData);
                setPantsState(meta, modelData, equipId);
                leggings.setItemMeta(meta);
                return;
            }
        }
    }

    /**
     * Apply both the CMD and the matching equippable component to a pants meta.
     * 1.21.4 leather_leggings rendering uses the equippable model ref, so both
     * must move together when the wet/messy state changes.
     */
    private static void setPantsState(ItemMeta meta, int cmd, String equipmentId) {
        meta.setCustomModelData(cmd);
        if (equipmentId != null) {
            EquippableComponent equip = meta.getEquippable();
            equip.setSlot(EquipmentSlot.LEGS);
            equip.setModel(NamespacedKey.minecraft(equipmentId));
            meta.setEquippable(equip);
        }
    }

    private static String pantsEquipmentIdForCmd(int cmd) {
        switch (cmd) {
            case 626015: return "pants";
            case 626016: return "pants_wet";
            case 626017: return "pants_mess";
            case 626018: return "pants_wetmess";
            default: return null;
        }
    }
}
