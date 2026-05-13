package com.storynook.furniture;

import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.storynook.Plugin;

public class CribListener implements Listener {

    private final Plugin plugin;
    private final CribRegistry registry;
    private final CribPdcKeys keys;

    public CribListener(Plugin plugin, CribRegistry registry, CribPdcKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        // Kill-switch
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getBlockFace() != BlockFace.UP) return;

        ItemStack inHand = event.getItem();
        if (inHand == null) return;
        if (inHand.getType() != Material.SLIME_BALL) return;
        ItemMeta meta = inHand.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;
        int cmd = meta.getCustomModelData();
        if (cmd < 627000 || cmd > 627009) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Block origin = clicked.getRelative(BlockFace.UP);

        float yaw = CribPlacementService.snapYaw(player.getLocation().getYaw());
        int variant = cmd - 627000;

        Block bedHead = origin;
        Block bedFoot = origin.getRelative(yawToFace(yaw));

        boolean baseSolid = clicked.getType().isSolid()
            && !clicked.isLiquid()
            && clicked.getType() != Material.AIR;
        boolean footprintAllAir =
            (origin.getType() == Material.AIR || origin.getType().isAir())
            && (bedFoot.getType() == Material.AIR || bedFoot.getType().isAir());
        Location playerLoc = player.getLocation();
        boolean playerInFootprint = sameCell(playerLoc, origin) || sameCell(playerLoc, bedFoot);

        CribPlacementService.Result result = CribPlacementService.validate(baseSolid, footprintAllAir, playerInFootprint);
        if (result != CribPlacementService.Result.ACCEPT) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(CribPlacementService.message(result)));
            event.setCancelled(true);
            return;
        }

        UUID cribId = UUID.randomUUID();

        Location displayLoc = new Location(origin.getWorld(),
            origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, yaw, 0f);
        ItemDisplay display = origin.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
            ItemStack visual = inHand.clone();
            visual.setAmount(1);
            d.setItemStack(visual);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // Crib model native dimensions (from crib_oak.json elements):
            //   X: -0.5 to 17.5 model units = 1.125 blocks (longer = bed length axis)
            //   Y: 0 to 11    model units = 0.6875 blocks
            //   Z: -0.25 to 9 model units = 0.578 blocks  (shorter = bed width axis)
            // Geometric centre: (8.5, 5.5, 4.375) model units = (0.531, 0.344, 0.273) blocks
            //
            // ItemDisplay with NONE transform renders the model's GEOMETRIC CENTRE
            // at the entity location (vanilla item-entity convention).
            //
            // Scale 2.3x -> exterior ~2.59 x 1.58 x 1.33 blocks; interior ~2.16 x 1.01 blocks
            // (fits a 2x1 vanilla bed). Uniform scale keeps proportions; non-uniform
            // would distort the model.
            //
            // Y translation +0.791 = (5.5/16) * 2.3 -- lifts the geometric centre up by
            // half the scaled Y height so the model bottom sits on the floor of the
            // air cell (= top of clicked block). Decrease to embed lower; increase to
            // float higher.
            //
            // X/Z translation 0 -- model is already centered on the entity (which is at
            // the air cell centre origin.X+0.5, origin.Z+0.5). Don't add X/Z translation
            // unless you want to deliberately offset the crib.
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0.791f, 0f),
                new AxisAngle4f((float) Math.toRadians(yaw), 0, 1, 0),
                new Vector3f(2.3f, 2.3f, 2.3f),
                new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setInvulnerable(true);
            d.setPersistent(true);
        });

        Interaction interaction = origin.getWorld().spawn(displayLoc, Interaction.class, i -> {
            i.setInteractionWidth(1.4f);
            i.setInteractionHeight(0.7f);
            i.setResponsive(true);
            i.setInvulnerable(true);
            i.setPersistent(true);
        });

        Crib crib = new Crib(
            cribId,
            origin.getWorld().getName(),
            origin.getX(), origin.getY(), origin.getZ(),
            yaw, variant,
            clicked.getX(), clicked.getY(), clicked.getZ(),
            bedHead.getX(), bedHead.getY(), bedHead.getZ(),
            display.getUniqueId(),
            interaction.getUniqueId(),
            player.getUniqueId()
        );
        crib.writeToPdc(interaction, keys);
        registry.register(crib);

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeftClickBlock(PlayerInteractEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Crib crib = registry.findByFloorBlock(
            clicked.getWorld().getName(),
            clicked.getX(), clicked.getY(), clicked.getZ()
        );
        if (crib == null) return;

        Player player = event.getPlayer();

        if (crib.hasBed()) {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent("Remove the bed first."));
            event.setCancelled(true);
            return;
        }

        // Despawn paired entities
        org.bukkit.entity.Entity displayEntity = Bukkit.getEntity(crib.displayUuid());
        if (displayEntity != null) displayEntity.remove();
        org.bukkit.entity.Entity interactionEntity = Bukkit.getEntity(crib.interactionUuid());
        if (interactionEntity != null) interactionEntity.remove();

        registry.unregister(crib.id());

        // Defensive: if a ward was contained, free them
        UUID containedWard = registry.containedWards().stream()
            .filter(uuid -> crib.id().equals(registry.cribIdForWard(uuid)))
            .findFirst().orElse(null);
        if (containedWard != null) {
            registry.releaseWard(containedWard);
            Player ward = Bukkit.getPlayer(containedWard);
            if (ward != null) {
                var stats = plugin.getPlayerStats(ward.getUniqueId());
                if (stats != null) stats.setContainedInCribId(null);
            }
        }

        // Drop the crib item
        ItemStack drop = makeCribItem(crib.woodVariant());
        Location dropLoc = new Location(clicked.getWorld(),
            crib.originX() + 0.5, crib.originY(), crib.originZ() + 0.5);
        clicked.getWorld().dropItemNaturally(dropLoc, drop);

        event.setCancelled(true);
    }

    private ItemStack makeCribItem(int variant) {
        ItemStack stack = new ItemStack(Material.SLIME_BALL, 1);
        ItemMeta m = stack.getItemMeta();
        m.setCustomModelData(627000 + variant);
        m.setDisplayName("Crib");
        stack.setItemMeta(m);
        return stack;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;

        Block broken = event.getBlock();
        String worldName = broken.getWorld().getName();

        // Floor-block protection: cancel break if it's a tracked crib's floor
        Crib floorCrib = registry.findByFloorBlock(worldName, broken.getX(), broken.getY(), broken.getZ());
        if (floorCrib != null) {
            event.setCancelled(true);
            event.getPlayer().spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent("Remove the crib first."));
            return;
        }

        // Bed protection: only if broken block is a Bed
        if (!(broken.getBlockData() instanceof org.bukkit.block.data.type.Bed)) return;

        Crib bedCrib = findCribByBedCell(worldName, broken.getX(), broken.getY(), broken.getZ());
        if (bedCrib == null) return;

        UUID containedWard = findContainedWardOf(bedCrib.id());
        if (containedWard != null && containedWard.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent("You can't break your own crib's bed."));
            return;
        }

        // Allowed; if a ward was contained, free them
        if (containedWard != null) {
            freeWard(containedWard);
        }
    }

    private Crib findCribByBedCell(String worldName, int x, int y, int z) {
        for (Crib c : new java.util.ArrayList<>(allRegisteredCribs())) {
            if (!c.worldName().equals(worldName)) continue;
            if ((c.bedHeadX() == x && c.bedHeadY() == y && c.bedHeadZ() == z)
             || (c.bedFootX() == x && c.bedFootY() == y && c.bedFootZ() == z)) {
                return c;
            }
        }
        return null;
    }

    private java.util.Collection<Crib> allRegisteredCribs() {
        java.util.List<Crib> all = new java.util.ArrayList<>();
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk ch : w.getLoadedChunks()) {
                all.addAll(registry.findByChunk(w.getName(), ch.getX(), ch.getZ()));
            }
        }
        return all;
    }

    private UUID findContainedWardOf(UUID cribId) {
        for (UUID wardUuid : registry.containedWards()) {
            if (cribId.equals(registry.cribIdForWard(wardUuid))) return wardUuid;
        }
        return null;
    }

    private void freeWard(UUID wardUuid) {
        registry.releaseWard(wardUuid);
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward != null) {
            var stats = plugin.getPlayerStats(ward.getUniqueId());
            if (stats != null) stats.setContainedInCribId(null);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        handleExplosionBlocks(event.blockList(), event.getEntity().getWorld().getName());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        handleExplosionBlocks(event.blockList(), event.getBlock().getWorld().getName());
    }

    private void handleExplosionBlocks(java.util.List<Block> blocks, String worldName) {
        java.util.Set<UUID> cribsToRemove = new java.util.HashSet<>();
        for (Block b : blocks) {
            Crib byFloor = registry.findByFloorBlock(worldName, b.getX(), b.getY(), b.getZ());
            if (byFloor != null) cribsToRemove.add(byFloor.id());
            Crib byBed = findCribByBedCell(worldName, b.getX(), b.getY(), b.getZ());
            if (byBed != null) cribsToRemove.add(byBed.id());
        }
        for (UUID cribId : cribsToRemove) {
            Crib c = registry.findById(cribId);
            if (c == null) continue;
            UUID ward = findContainedWardOf(cribId);
            if (ward != null) freeWard(ward);
            org.bukkit.entity.Entity disp = Bukkit.getEntity(c.displayUuid());
            if (disp != null) disp.remove();
            org.bukkit.entity.Entity inter = Bukkit.getEntity(c.interactionUuid());
            if (inter != null) inter.remove();
            registry.unregister(cribId);
        }
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Interaction interaction)) continue;
            if (!interaction.getScoreboardTags().contains(CribPdcKeys.SCOREBOARD_TAG)) continue;
            Crib crib = Crib.fromPdc(interaction, keys);
            if (crib == null) {
                plugin.getLogger().warning("Removing orphan crib Interaction at " + interaction.getLocation());
                interaction.remove();
                continue;
            }
            if (Bukkit.getEntity(crib.displayUuid()) == null) {
                plugin.getLogger().warning("Crib " + crib.id() + " has missing display; removing Interaction.");
                interaction.remove();
                continue;
            }
            registry.register(crib);
        }
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        registry.unregisterChunk(
            event.getWorld().getName(),
            event.getChunk().getX(),
            event.getChunk().getZ()
        );
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Object killSwitch = plugin.getGlobalConfig().get("Crib_New_System");
        if (killSwitch instanceof Boolean && !((Boolean) killSwitch)) return;
        Player player = event.getPlayer();
        var stats = plugin.getPlayerStats(player.getUniqueId());
        if (stats == null) return;
        UUID cribId = stats.getContainedInCribId();
        if (cribId == null) return;

        // Defer one tick so the player is fully spawned + chunks loaded.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Crib crib = registry.findById(cribId);
            if (crib == null) {
                stats.setContainedInCribId(null);
                return;
            }
            if (!crib.hasBed()) {
                stats.setContainedInCribId(null);
                return;
            }
            registry.containWard(player.getUniqueId(), cribId);
            Location target = crib.bedHeadLocation();
            if (target != null) player.teleport(target);
        }, 1L);
    }

    private static boolean sameCell(Location loc, Block block) {
        return loc.getBlockX() == block.getX()
            && loc.getBlockY() == block.getY()
            && loc.getBlockZ() == block.getZ();
    }

    private static BlockFace yawToFace(float yaw) {
        if (yaw == 0.0f)   return BlockFace.SOUTH;
        if (yaw == 90.0f)  return BlockFace.WEST;
        if (yaw == 180.0f) return BlockFace.NORTH;
        if (yaw == 270.0f) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }
}
