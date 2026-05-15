package com.storynook.Event_Listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.storynook.PlaySounds;
import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.PlayerStatsManagement.UpdateStats;
import com.storynook.items.ItemManager;
import com.storynook.items.CustomItemCheck;

public class Toilet implements Listener{
    private final Plugin plugin;
    public Toilet(Plugin plugin) {
        this.plugin = plugin;
    }
    //Places custom item toilet. MONITOR + ignoreCancelled so we never plant the
    //trapdoor when another plugin (e.g. WorldGuard) rolled back the cauldron.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceToilet(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 626006) {

            // Get the block where the player placed the toilet (already a cauldron — the item itself is one)
            Block block = event.getBlockPlaced();
            Location loc = block.getLocation();

            // Place a trapdoor on top
            Block trapdoorBlock = loc.clone().add(0, 1, 0).getBlock();
            trapdoorBlock.setType(Material.IRON_TRAPDOOR);

            // Set the trapdoor to a specific orientation (optional)
            TrapDoor trapdoor = (TrapDoor) trapdoorBlock.getBlockData();
            trapdoor.setHalf(Bisected.Half.BOTTOM); // Make the trapdoor flush with the cauldron
            BlockFace playerDirection = event.getPlayer().getFacing();
            switch (playerDirection) {
                case NORTH:
                    trapdoor.setFacing(BlockFace.SOUTH);
                    break;
                case EAST:
                    trapdoor.setFacing(BlockFace.WEST);
                    break;
                case SOUTH:
                    trapdoor.setFacing(BlockFace.NORTH);
                    break;
                case WEST:
                    trapdoor.setFacing(BlockFace.EAST);
                    break;
                default:
                    // Handle other cases if needed
                    break;
            }
            trapdoor.setOpen(true);
            trapdoorBlock.setBlockData(trapdoor);
        }
    }

    
    //Toilet interaction
    @EventHandler
    public void onToiletInteract(PlayerInteractEvent event) {
        // PlayerInteractEvent fires for main hand and off-hand both. Without
        // this filter, our trapdoor toggle would flip twice and net to nothing.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null) {
                // Check if the clicked block is a cauldron or the trapdoor above it
                if (block.getType() == Material.CAULDRON) {
                    processCauldronInteraction(block, event.getPlayer());
                } else if (block.getType() == Material.IRON_TRAPDOOR) {
                    Block belowBlock = block.getLocation().subtract(0, 1, 0).getBlock();
                    if (belowBlock.getType() == Material.CAULDRON) {
                        // Only toggle when the player's hand is empty — matches the
                        // sit rule and prevents accidental flips while building.
                        if (event.getPlayer().getInventory().getItemInMainHand().getType() == Material.AIR) {
                            event.setCancelled(true);
                            toggleTrapdoor(block);
                        }
                    }
                }
            }
        }
    }
    //Confrim Toilet
    private void processCauldronInteraction(Block cauldronBlock, Player player) {
        Block trapdoorBlock = cauldronBlock.getLocation().add(0, 1, 0).getBlock();
        if (trapdoorBlock.getType() == Material.IRON_TRAPDOOR) {
            // Lid closed = no seat. Silent — matches the leggings-blocks-sit behavior.
            TrapDoor trapdoor = (TrapDoor) trapdoorBlock.getBlockData();
            if (!trapdoor.isOpen()) {
                return;
            }
            // Existing code to make the player interact with the cauldron toilet
            interactWithCauldron(player, cauldronBlock, trapdoorBlock);
        }
    }

    // State lives on the block; no persistence needed.
    private void toggleTrapdoor(Block trapdoorBlock) {
        TrapDoor trapdoor = (TrapDoor) trapdoorBlock.getBlockData();
        trapdoor.setOpen(!trapdoor.isOpen());
        trapdoorBlock.setBlockData(trapdoor);
    }

    //Using Toilet Action
    private void interactWithCauldron(Player player, Block cauldronBlock, Block trapdoorBlock) {
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        // Check player's leggings
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings != null && leggings.getType() == Material.LEATHER_LEGGINGS) {
            if (!CustomItemCheck.isDiaper(leggings)) {
                return;
            }
        }
        else if (leggings != null && leggings.getType() != Material.LEATHER_LEGGINGS){
            return;
        }
        
        // Set the player's position to be sitting on the cauldron
        Location cauldronLoc = cauldronBlock.getLocation();
        Location armorStandLocation = cauldronLoc.add(0.5, 1.0, 0.5);

        ArmorStand armorStand = player.getWorld().spawn(armorStandLocation, ArmorStand.class, asm -> {
            asm.setVisible(false); // Hide the armor stand
            asm.setMarker(true); // No bounding box/collision
            asm.setGravity(false); // No gravity so it stays in place
            asm.setCustomName("ToiletArmor");
        });

        // Make the player sit on the armor stand
        armorStand.addPassenger(player);
        plugin.markOnToilet(player.getUniqueId());

        // Decide what (if anything) to relieve based on warning history + incontinence.
        PlayerStats stats = plugin.getPlayerStats(player.getUniqueId());
        boolean falseAlarm = false;

        if (stats.getBladder() > 10) {
            int preBladder = (int) stats.getBladder();
            if (canRelieveOnToilet(player, stats, true)) {
                relieveOnToilet(stats, true);
                if (plugin.getIntegrationsBus() != null) {
                    java.util.Map<String,Object> ctx = new java.util.HashMap<>();
                    ctx.put("bladder", preBladder);
                    ctx.put("bowels", (int) stats.getBowels());
                    ctx.put("stat", "bladder");
                    plugin.getIntegrationsBus().fire(player,
                            com.storynook.Integrations.events.ActionId.TOILET_RELIEF, null, ctx);
                }
            } else {
                falseAlarm = true;
            }
        }
        if (stats.getMessing() && stats.getBowels() > 10) {
            int preBowels = (int) stats.getBowels();
            if (canRelieveOnToilet(player, stats, false)) {
                relieveOnToilet(stats, false);
                if (plugin.getIntegrationsBus() != null) {
                    java.util.Map<String,Object> ctx = new java.util.HashMap<>();
                    ctx.put("bladder", (int) stats.getBladder());
                    ctx.put("bowels", preBowels);
                    ctx.put("stat", "bowels");
                    plugin.getIntegrationsBus().fire(player,
                            com.storynook.Integrations.events.ActionId.TOILET_RELIEF, null, ctx);
                }
            } else {
                falseAlarm = true;
            }
        }

        if (falseAlarm) {
            String msg = plugin.getMessagesConfig() != null
                ? plugin.getMessagesConfig().getString("Toilet_False_Alarm")
                : null;
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(msg);
            }
        } else if (stats.getBowelIncontinence() > 7 || stats.getBladderIncontinence() > 7) {
            player.sendMessage("Good job making it to the potty!");
        }
        else if (stats.getBowelIncontinence() >= 4 || stats.getBladderIncontinence() >= 4) {
            player.sendMessage("Potty training is going well!");
        }
        UpdateStats.playerSecondsLeftMap.put(player.getUniqueId(), 0);
        UpdateStats.playerWarningsMap.put(player.getUniqueId(), false);
        stats.setUrgeToGo(0);

        BukkitTask[] taskId = new BukkitTask[1];

        taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                // If the player is no longer a passenger of the armor stand
                if (!armorStand.getPassengers().contains(player)) {
                    // Play the sound
                    PlaySounds.playsounds(player, "flush", 10, 0.5, 0.05, true);
                    // Remove the armor stand
                    armorStand.remove();
                    plugin.clearOnToilet(player.getUniqueId());

                    // Cancel this task
                    taskId[0].cancel();
                }
            }
        }, 0L, 5L);
    }

    // Empties one stat as if the player used the toilet: zero the stat, decrement
    // incontinence (unless locked). No diaper transfer, no incontinence gain.
    // Callable from the sit handler and from /pee /poop while on toilet.
    public static void relieveOnToilet(PlayerStats stats, boolean isBladder) {
        if (stats == null) return;
        double delta = toiletInconDelta(stats.getUnderwearType());
        if (isBladder) {
            stats.setBladder(0);
            if (!stats.getBladderLockIncon()) stats.decreaseBladderIncontinence(delta);
        } else {
            stats.setBowels(0);
            if (!stats.getBowelLockIncon()) stats.decreaseBowelIncontinence(delta);
        }
    }

    // Incontinence decrease per underwear type on successful toilet use.
    // 0=underwear, 1=pull-ups: small reward — making it is expected.
    // 2=diaper, 3=thick diaper: big reward — toilet use is a meaningful choice.
    private static double toiletInconDelta(int underwearType) {
        switch (underwearType) {
            case 0: return 0.1;   // underwear: small reward
            case 1: return 0.1;   // pull-ups: small reward
            case 2: return 0.5;   // diaper: meaningful reward
            case 3: return 0.8;   // thick diaper: big reward
            default: return 0.2;
        }
    }

    // Decides whether sitting on the toilet should auto-relieve a given stat.
    // Eligible iff (A) a warning for this stat fired within the recent window
    // (window scales linearly: max at incon 1, min at incon 10), or (B) the
    // player is incontinent enough that the proactive override permits it
    // (threshold scales linearly from 100 at incon 1 to MinFill at incon 10).
    // When Accidents is off, the legacy "always relieve" behavior is preserved.
    private boolean canRelieveOnToilet(Player player, PlayerStats stats, boolean isBladder) {
        if (plugin.getDiaperPunishment() != null && plugin.getDiaperPunishment().isBlocked(player)) {
            plugin.getDiaperPunishment().recordViolation(player);
            player.sendMessage(org.bukkit.ChatColor.RED + "Your Nanny says you're not allowed.");
            return false;
        }
        if (!Boolean.TRUE.equals(plugin.getGlobalConfig().get("Accidents"))) {
            return true;
        }
        double incon = isBladder ? stats.getBladderIncontinence() : stats.getBowelIncontinence();
        double fill  = isBladder ? stats.getBladder()             : stats.getBowels();
        long lastWarn = isBladder
            ? plugin.getLastBladderWarningMillis(player.getUniqueId())
            : plugin.getLastBowelWarningMillis(player.getUniqueId());

        double clampedIncon = Math.max(1.0, Math.min(10.0, incon));

        // (A) recent warning, window shrinks with incon
        double maxSec = ((Number) plugin.getGlobalConfig().getOrDefault("Toilet_Warning_Window_Max_Seconds", 60.0)).doubleValue();
        double minSec = ((Number) plugin.getGlobalConfig().getOrDefault("Toilet_Warning_Window_Min_Seconds", 5.0)).doubleValue();
        double windowSec = maxSec - (clampedIncon - 1.0) / 9.0 * (maxSec - minSec);
        if (lastWarn > 0 && System.currentTimeMillis() - lastWarn <= (long)(windowSec * 1000.0)) return true;

        // (B) proactive: threshold drops from 100 (incon 1) to MinFill (incon 10)
        int minFill = ((Number) plugin.getGlobalConfig().getOrDefault("MinFill", 30)).intValue();
        double proactiveThreshold = 100.0 - (clampedIncon - 1.0) / 9.0 * (100.0 - minFill);
        return fill >= proactiveThreshold;
    }

    // Safety hygiene: a player who disconnects while seated would otherwise
    // leave a stale entry in the on-toilet set. Without this, /pee on rejoin
    // would route through the toilet path even though they aren't seated.
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.clearOnToilet(event.getPlayer().getUniqueId());
    }
    //Returns toilet on break
    @EventHandler
    public void onToiletBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block != null && block.getType() == Material.CAULDRON) {
            Block trapdoorBlock = block.getLocation().add(0, 1, 0).getBlock();
            if (trapdoorBlock.getType() == Material.IRON_TRAPDOOR) {
                Location loc = block.getLocation();

                // Remove cauldron and trapdoor
                block.setType(Material.AIR);
                loc.add(0, 1, 0).getBlock().setType(Material.AIR);

                // Drop the custom item
                block.getWorld().dropItemNaturally(loc, new ItemStack(ItemManager.Toilet())); // Assuming you have a way to create the custom item
            }
        }
        if (block != null && block.getType() == Material.IRON_TRAPDOOR) {
            Block cauldronBlock = block.getLocation().add(0, -1, 0).getBlock();
            if (cauldronBlock.getType() == Material.CAULDRON) {
                Location loc = block.getLocation();

                // Remove cauldron and trapdoor
                block.setType(Material.AIR);
                loc.add(0, -1, 0).getBlock().setType(Material.AIR);

                // Drop the custom item
                block.getWorld().dropItemNaturally(loc, new ItemStack(ItemManager.Toilet())); // Assuming you have a way to create the custom item
            }
        }
    }
}
