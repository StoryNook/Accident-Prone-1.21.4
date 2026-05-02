package com.storynook.Event_Listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCheck;
import com.storynook.items.CustomItemCoolDown;

public class PlayerInteract implements Listener {
    private final Plugin plugin;
    public PlayerInteract(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player actor = event.getPlayer();
        PlayerStats Stats = plugin.getPlayerStats(actor.getUniqueId());
        ItemStack itemInHand = actor.getInventory().getItemInMainHand();
        if (itemInHand != null && itemInHand.getType() != Material.AIR) {
            ItemMeta meta = itemInHand.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                int rightclicktimes = plugin.rightclickCount.getOrDefault(actor.getUniqueId(), 0);
                int customModelData = meta.getCustomModelData();
                if (customModelData == 626007) {
                    event.setCancelled(true); // Cancel the interaction
                    if (Stats.getOptin() && Stats.getLayers() < 4) {
                        //Cooldown
                        CustomItemCoolDown cooldown = new CustomItemCoolDown();
                        if(cooldown.cooldown.contains(actor.getUniqueId())){
                            return;
                        }
                        cooldown.Cooldown(actor, 5);

                        int maxLayers = 0;
                        switch(Stats.getUnderwearType()) {
                            case 0: maxLayers = 1; break;
                            case 1: maxLayers = 2; break;
                            case 2: maxLayers = 3; break;
                            case 3: maxLayers = 4; break;
                            default: return;
                        }
                        if (Stats.getLayers() >= maxLayers) {
                            actor.sendMessage(ChatColor.RED + "You cannot add more layers with your current underwear.");
                            return;
                        }
                        if (Stats.getDiaperWetness() >= 100) {
                            actor.sendMessage(ChatColor.RED + "It's a little too late for that, don't you think?");
                            return;
                        }
                        Stats.setLayers(Stats.getLayers() + 1);
                        actor.sendMessage(ChatColor.GREEN + "Added a layer! Current layers: " + Stats.getLayers());

                        if (itemInHand.getAmount() > 1) {
                            itemInHand.setAmount(itemInHand.getAmount() - 1);
                        } else {
                            actor.getInventory().setItemInMainHand(null);
                        }
                    }
                    return; // Exit the method to prevent further execution
                }
                else if(CustomItemCheck.VailidUnderwear(itemInHand)){
                    if (event.getAction().name().contains("RIGHT_CLICK")) {
                        if (itemInHand == null || !itemInHand.hasItemMeta() || !itemInHand.getItemMeta().hasCustomModelData()) {
                            plugin.rightclickCount.put(actor.getUniqueId(), 0);
                            plugin.firstimeran.put(actor.getUniqueId(), false);
                            return;
                        } else if (Stats.getHardcore()) {
                            actor.sendMessage("You are in HardCore mode. You should ask a caregiver for help.");
                            return;
                        }
                        else if (!Stats.getOptin()) {
                            return;
                        }
                        else {
                            rightclicktimes++;
                            if (rightclicktimes > 1) {
                                plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
                                return;
                            }
                            else if (rightclicktimes == 1){
                                plugin.firstimeran.put(actor.getUniqueId(), true);
                                plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
                                // playAudio(actor, customModelData, Stats.getUnderwearType());
                                Changing.handleRightClickHold(actor, null, false, customModelData, Stats.getUnderwearType());
                            }
                            else {
                                return;
                            }
                        }
                    }
                }
                
            }
            else if (itemInHand.getType() == Material.CLOCK) {
                Hypno.HypnoInteract(actor, null);
            }
        }
    }
}
