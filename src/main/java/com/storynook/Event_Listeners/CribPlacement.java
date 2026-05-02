package com.storynook.Event_Listeners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.storynook.Plugin;
import com.storynook.items.cribs;
import com.storynook.items.CustomItemCoolDown;
import com.storynook.items.CustomItemCheck;

public class CribPlacement implements Listener{
    @SuppressWarnings("unused")
    private JavaPlugin plugin;
    public CribPlacement(Plugin plugin) {
        this.plugin = plugin;
    }
    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            ItemStack itemInHand = event.getItem();

             if (itemInHand != null && itemInHand.getType() == Material.SLIME_BALL && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasCustomModelData()) {
                Player player = event.getPlayer();

                //Placement Cooldown
                CustomItemCoolDown cooldown = new CustomItemCoolDown();
                if(cooldown.cooldown.contains(player.getUniqueId())){
                    return;
                }
                cooldown.Cooldown(player, 1);
                
                ItemMeta meta = itemInHand.getItemMeta();
                if (CustomItemCheck.isCrib(meta)) {
                    Location blockLocation = clickedBlock.getLocation();
                    BlockFace face = event.getBlockFace();
                    if (face == BlockFace.UP){
                        Location frameLocation = clickedBlock.getRelative(face).getLocation().add(0.5, 0, 0.5);
                        
                        BlockFace playerDirection = player.getFacing();
                        
                        // Create and place item frame
                        ArmorStand armorStand = (ArmorStand) blockLocation.getWorld().spawnEntity(frameLocation, EntityType.ARMOR_STAND);
                        
                        float yaw;
                        switch(playerDirection) {
                            case NORTH:
                                yaw = 0;
                                break;
                            case EAST:
                                yaw = 90;
                                break;
                            case SOUTH:
                                yaw = 180;
                                break;
                            case WEST:
                                yaw = 270;
                                break;
                            default:
                                // Handle any other cases if necessary
                                yaw = 0;
                                break;
                        }
                        
                        armorStand.setRotation(yaw, 0);

                        armorStand.setVisible(false);
                        armorStand.setCanPickupItems(false);
                        armorStand.setGravity(false);
                        armorStand.setCustomName("Crib");
                        
                        itemInHand.setAmount(itemInHand.getAmount() - 1);

                        ItemStack crib = new ItemStack(Material.SLIME_BALL);
                        ItemMeta cribmeta = crib.getItemMeta();
                        cribmeta.setCustomModelData(meta.getCustomModelData());
                        cribmeta.setDisplayName("");
                        crib.setItemMeta(cribmeta);
                        armorStand.setHelmet(crib);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        if (event.getRecipe() == null || event.getInventory() == null) return;
        
        ItemStack result = event.getRecipe().getResult();
        ItemMeta resultmeta = result.getItemMeta();

        if (resultmeta.hasCustomModelData() && CustomItemCheck.isCrib(resultmeta)) {
            Material woodType = null;
            boolean mismatched = false;

            List<Material> validWoodMaterials = new ArrayList<>(Arrays.asList(
                Material.BIRCH_SLAB, Material.SPRUCE_SLAB, Material.OAK_SLAB,
                Material.ACACIA_SLAB, Material.JUNGLE_SLAB, Material.QUARTZ_SLAB,
                Material.WARPED_SLAB, Material.DARK_OAK_SLAB, Material.MANGROVE_SLAB
            ));

            try {
                // Check if CHERRY_SLAB exists in the current version
                Class<Material> materialClass = Material.class;
                if (materialClass.getField("CHERRY_SLAB").get(null) != null) {
                    validWoodMaterials.add(Material.valueOf("CHERRY_SLAB"));
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // CHERRY_SLAB doesn't exist in this version
            }
            for (ItemStack item : matrix) {

                if (woodType == null) {
                    if (validWoodMaterials.contains(item.getType())){woodType = item.getType();}
                } else {
                    if (!woodType.equals(item.getType()) && item.getType() != Material.STICK) {
                        mismatched = true;
                        inventory.setResult(null);
                        break;
                    }
                }
            }
            if (!mismatched && woodType != null) {
                inventory.setResult(cribs.createCrib(woodType));
            }
            else{inventory.setResult(null);}
        }
    }
}
