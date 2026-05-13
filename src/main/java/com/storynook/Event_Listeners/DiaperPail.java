package com.storynook.Event_Listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;

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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.storynook.items.underwear;
import com.storynook.items.CustomItemCheck;
import com.storynook.items.CustomItemCoolDown;

public class DiaperPail implements Listener {
    public static Map<UUID, Inventory> pailInventories = new HashMap<>();
    private static Map<UUID, UUID> AccessedInventory = new HashMap<>();
    // Snapshot of pail contents at open-time, keyed by player UUID. Used to count
    // dirty items added during the session for the integrations bus.
    private static Map<UUID, Integer> usedCountAtOpen = new HashMap<>();
    private final com.storynook.Plugin plugin;
    public DiaperPail() { this.plugin = null; }
    public DiaperPail(com.storynook.Plugin plugin) { this.plugin = plugin; }

    //Opening of the Diaper Pail, creates inventory file on open if it doesn't exist yet
    @EventHandler
    public void OpenDiaperPail(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.BARRIER) {
            CustomItemCoolDown cooldown = new CustomItemCoolDown();
            if(cooldown.cooldown.contains(event.getPlayer().getUniqueId())){
                return;
            }

            Location clickLocation = event.getClickedBlock().getLocation();
            clickLocation.setX(clickLocation.getX() + 0.5);
            clickLocation.setY(clickLocation.getY() + 0.5);
            clickLocation.setZ(clickLocation.getZ() + 0.5);
            Collection<Entity> nearbyEntities = event.getClickedBlock().getWorld().getNearbyEntities(event.getClickedBlock().getLocation(), 0.5, 0.5, 0.5);
            
            for (Entity entity : nearbyEntities) {
                if (entity.getType() == EntityType.ARMOR_STAND) {
                    ArmorStand armorStand = (ArmorStand) entity;
                    String name = armorStand.getCustomName();
                    
                    if (name != null && name.startsWith("Pail_")) {
                        try {
                            UUID pailId = UUID.fromString(name.substring(6));
                            Inventory inventory = pailInventories.get(pailId);
                            
                            // Create empty inventory if it doesn't exist
                            if (inventory == null) {
                                File inventoryFile = new File(Bukkit.getServer().getPluginManager().getPlugin("Accident-Prone").getDataFolder(), "DiaperPails/" + pailId + ".yml");
                                if (!inventoryFile.exists()) {
                                    inventory = Bukkit.createInventory(null, 27, "Diaper Pail");
                                    saveInventory(inventory, inventoryFile);
                                    pailInventories.put(pailId, inventory);
                                } else {
                                    // Load existing inventory from file
                                    inventory = loadInventory(inventoryFile);
                                    pailInventories.put(pailId, inventory);
                                }
                            }
                            // Snapshot used-item count at open for the integrations bus.
                            int usedAtOpen = 0;
                            for (ItemStack itm : inventory.getContents()) {
                                if (itm != null && CustomItemCheck.isUsed(itm)) usedAtOpen++;
                            }
                            usedCountAtOpen.put(event.getPlayer().getUniqueId(), usedAtOpen);
                            event.getPlayer().openInventory(inventory);
                            AccessedInventory.put(event.getPlayer().getUniqueId(), pailId);
                            break;
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                     }
                }
            }
        }
    }

    private static void saveInventory(Inventory inventory, File file) {
        YamlConfiguration config = new YamlConfiguration();
        
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            
            if (item != null && !item.getType().isAir()) {
                String path = "items." + i;
                
                // Save basic item info
                config.set(path + ".type", item.getType().name());
                config.set(path + ".amount", item.getAmount());
                
                // Save item meta data
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    //Save Name
                    if (meta.hasDisplayName()) {
                        config.set(path + ".name", meta.getDisplayName());
                    }
                    // Serialize lore (convert to list of strings)
                    if (meta.hasLore()) {
                        config.set(path + ".lore", meta.getLore());
                    }
                    
                    // Save custom model data
                    if (meta.hasCustomModelData()) {
                        config.set(path + ".custom_model_data", meta.getCustomModelData());
                    }
                }
            }
        }

        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Finds the nearest diaper pail (an ArmorStand whose custom name starts
     * with {@code "Pail_<UUID>"}) within {@code radius} blocks of
     * {@code origin}, and adds {@code soiled} to its inventory. Auto-saves
     * the pail's YAML. Returns true if the item was fully deposited, false
     * if no pail was in range or the pail was full.
     *
     * Used by NannyCareEngine after a change action to dispose of the
     * soiled diaper.
     */
    public static boolean deposit(Location origin, double radius, ItemStack soiled) {
        if (origin == null || origin.getWorld() == null || soiled == null) return false;

        ArmorStand nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (entity.getType() != EntityType.ARMOR_STAND) continue;
            ArmorStand stand = (ArmorStand) entity;
            String name = stand.getCustomName();
            if (name == null || !name.startsWith("Pail_")) continue;
            double d = stand.getLocation().distanceSquared(origin);
            if (d < nearestDistSq) { nearestDistSq = d; nearest = stand; }
        }
        if (nearest == null) return false;

        UUID pailId;
        try {
            pailId = UUID.fromString(nearest.getCustomName().substring(5));
        } catch (IllegalArgumentException e) {
            return false;
        }

        File pluginFolder = Bukkit.getServer().getPluginManager()
                .getPlugin("Accident-Prone").getDataFolder();
        File invFile = new File(pluginFolder, "DiaperPails/" + pailId + ".yml");
        Inventory inv = pailInventories.get(pailId);
        if (inv == null) {
            if (invFile.exists()) inv = loadInventory(invFile);
            else inv = Bukkit.createInventory(null, 27, "Diaper Pail");
            pailInventories.put(pailId, inv);
        }
        HashMap<Integer, ItemStack> leftover = inv.addItem(soiled);
        saveInventory(inv, invFile);
        return leftover.isEmpty();
    }

    public static Inventory loadInventory(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Inventory inventory = Bukkit.createInventory(null, 27, "Diaper Pail");

        for (int i = 0; i < inventory.getSize(); i++) {
            String path = "items." + i;
            
            if (config.contains(path)) {
                // Create a new item stack
                ItemStack item = new ItemStack(
                    Material.valueOf(config.getString(path + ".type")),
                    config.getInt(path + ".amount")
                );

                ItemMeta meta = item.getItemMeta();

                // Load name
                if (config.isString(path + ".name")) {
                    meta.setDisplayName(config.getString(path + ".name"));
                }
                
                // Load lore
                if (config.isList(path + ".lore")) {
                    meta.setLore((List<String>) config.getList(path + ".lore"));
                }
                
                // Load custom model data
                if (config.contains(path + ".custom_model_data")) {
                    meta.setCustomModelData(config.getInt(path + ".custom_model_data"));
                }

                item.setItemMeta(meta);
                inventory.setItem(i, item);
            }
        }

        return inventory;
    }

    //When the Diaper Pail is closed, checks to see if it is full and replaces inventory with new diapers
    @EventHandler
    public void CloseDiaperPail(InventoryCloseEvent event){
        if (event.getView().getTitle().equals("Diaper Pail")) {
            Inventory inventory = event.getInventory();
            boolean isValid = true;
            
            // Store original items for revert
            ItemStack[] originalContents = new ItemStack[inventory.getSize()];
            System.arraycopy(inventory.getContents(), 0, originalContents, 0, originalContents.length);

            for (ItemStack item : inventory.getContents()) {
                if (item == null || !CustomItemCheck.isUsed(item)) {
                    isValid = false;
                    break;
                }
            }

            UUID playerUuid = event.getPlayer().getUniqueId();
            UUID pailId = AccessedInventory.get(playerUuid);

            // Count used items currently in the pail and compare to the open-time
            // snapshot -- difference is "dirty items the player just deposited".
            // Fire pail_fill once PER deposited item so quest counters tick naturally
            // and AdvancedJobs jobs accumulate progress per disposal.
            int usedNow = 0;
            int firstDirtyCmd = 0;
            for (ItemStack itm : inventory.getContents()) {
                if (itm != null && CustomItemCheck.isUsed(itm)) {
                    usedNow++;
                    if (firstDirtyCmd == 0 && itm.hasItemMeta() && itm.getItemMeta().hasCustomModelData()) {
                        firstDirtyCmd = itm.getItemMeta().getCustomModelData();
                    }
                }
            }
            int openSnapshot = usedCountAtOpen.getOrDefault(playerUuid, 0);
            int deposited = Math.max(0, usedNow - openSnapshot);
            usedCountAtOpen.remove(playerUuid);
            if (deposited > 0 && plugin != null && plugin.getIntegrationsBus() != null
                    && event.getPlayer() instanceof Player) {
                Player closer = (Player) event.getPlayer();
                java.util.Map<String,Object> ctx = new java.util.HashMap<>();
                ctx.put("dirtyConsumed", true);
                ctx.put("cmd", firstDirtyCmd);
                ctx.put("count", deposited);  // hooks multiply payout/progress by count
                plugin.getIntegrationsBus().fire(closer,
                        com.storynook.Integrations.events.ActionId.PAIL_FILL,
                        null, ctx);
            }

            if (isValid){
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, null);
                }
                // Add 9 new diaper items
                for (int i = 0; i < 9; i++) {
                    ItemStack diaper = underwear.Diaper();
                    if (diaper != null) {
                        inventory.setItem(i, diaper);
                    }
                }
            } else {
                // Revert to original contents
                System.arraycopy(originalContents, 0, inventory.getContents(), 0, originalContents.length);
                if (pailId != null) {
                    File inventoryFile = new File(Bukkit.getServer().getPluginManager().getPlugin("Accident-Prone").getDataFolder(), "DiaperPails/" + pailId + ".yml");
                    saveInventory(inventory, inventoryFile);
                }
            }
            AccessedInventory.remove(playerUuid);
        }
    }
    
    @SuppressWarnings("deprecation")
    @EventHandler
    public void PlaceDiaperPail(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            ItemStack itemInHand = event.getItem();

             if (itemInHand != null && itemInHand.getType() == Material.SLIME_BALL && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasCustomModelData()) {
                Player player = event.getPlayer();
                
                // Check if the block clicked is a barrier block and cancel action if it is
                if (clickedBlock.getType() == Material.BARRIER) {
                    event.setCancelled(true);
                    return;
                }
                CustomItemCoolDown cooldown = new CustomItemCoolDown();
                cooldown.Cooldown(player, 1);
                
                ItemMeta meta = itemInHand.getItemMeta();
                if (meta.getCustomModelData() == 628000) {
                    Location blockLocation = clickedBlock.getLocation();
                    BlockFace face = event.getBlockFace();
                    if(face == BlockFace.UP){
                        // Move the barrier location up by one y
                        Location frameLocation = new Location(blockLocation.getWorld(), blockLocation.getX(), blockLocation.getY() + 1, blockLocation.getZ());
                        Location armorStandLocation = clickedBlock.getRelative(face).getLocation().add(0.5, 0, 0.5);

                        BlockFace playerDirection = player.getFacing();
                        
                        // Create and place item frame
                        ArmorStand armorStand = (ArmorStand) blockLocation.getWorld().spawnEntity(armorStandLocation, EntityType.ARMOR_STAND);
                        armorStand.setVisible(false);
                        
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
                        armorStand.setCanPickupItems(false);
                        armorStand.setGravity(false);
                        
                        // Place a barrier block at the same location as the armor stand
                        frameLocation.getBlock().setType(Material.BARRIER);

                        itemInHand.setAmount(itemInHand.getAmount() - 1);

                        ItemStack diaperpail = new ItemStack(Material.SLIME_BALL);
                        ItemMeta diaperpailmeta = diaperpail.getItemMeta();
                        diaperpailmeta.setCustomModelData(meta.getCustomModelData());
                        diaperpailmeta.setDisplayName("");
                        diaperpail.setItemMeta(diaperpailmeta);
                        armorStand.setHelmet(diaperpail);

                        UUID pailId = UUID.randomUUID(); // Generate unique ID for this pail
                        armorStand.setCustomName("Pail_" + pailId.toString()); // Store ID in custom name
                    }
                }
            }
        }
    }
}
