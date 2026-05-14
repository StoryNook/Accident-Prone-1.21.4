package com.storynook.furniture.highchair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import com.storynook.Integrations.events.ActionId;
import com.storynook.Plugin;

public class HighchairListener implements Listener {

    private final Plugin plugin;
    private final HighchairRegistry registry;
    private final HighchairPdcKeys keys;

    /** ward UUID → seat ArmorStand UUID (session-only). */
    private final Map<UUID, UUID> wardToSeat = new ConcurrentHashMap<>();
    /** ward UUID → millis of last action-bar bounce, for rate-limiting LOCKED sneak rejections. */
    private final Map<UUID, Long> lockedSneakWarnAt = new ConcurrentHashMap<>();

    /** caregiver UUID + highchair UUID → active placement task. */
    private final Map<String, BukkitTask> activeTimers = new HashMap<>();

    private static String timerKey(UUID caregiverUuid, UUID highchairId) {
        return caregiverUuid + ":" + highchairId;
    }

    public HighchairListener(Plugin plugin, HighchairRegistry registry, HighchairPdcKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
    }

    private boolean nurseryEnabled() {
        Object v = plugin.getGlobalConfig().get("Nursery_Items");
        return !(v instanceof Boolean) || (Boolean) v;
    }

    /** True iff the item is a highchair item (SLIME_BALL with CMD in 630000..630099). */
    static boolean isHighchairItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.SLIME_BALL) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        int cmd = meta.getCustomModelData();
        return cmd >= HighchairRegistry.CMD_BASE && cmd <= HighchairRegistry.CMD_MAX;
    }

    /**
     * Spawns the invisible-marker seat ArmorStand at the highchair's seat
     * anchor, mounts the ward as a passenger, and records bookkeeping. The
     * caller is responsible for {@code registry.recordSeating(...)}.
     */
    void spawnSeatAndMount(Highchair h, Player ward) {
        if (h.world() == null) return;
        // The barrier sits at originY..originY+1; we want the ward's butt on top
        // of the barrier (= top of the seat). A marker ArmorStand has zero
        // mount-point offset, so placing it at originY+1 puts the player at the
        // barrier's top surface.
        Location seatLoc = new Location(h.world(),
            h.originX() + 0.5, h.originY() + 1.0, h.originZ() + 0.5);
        ArmorStand seat = h.world().spawn(seatLoc, ArmorStand.class, asm -> {
            asm.setVisible(false);
            asm.setMarker(true);
            asm.setGravity(false);
            asm.setInvulnerable(true);
            asm.setRotation(h.yaw(), 0);
            asm.setCustomName("HighchairSeat");
            asm.setPersistent(false);
        });
        seat.addPassenger(ward);
        wardToSeat.put(ward.getUniqueId(), seat.getUniqueId());
    }

    /** Dismounts the ward, despawns the seat ArmorStand, clears bookkeeping. */
    public void releaseWard(UUID wardUuid) {
        UUID seatUuid = wardToSeat.remove(wardUuid);
        if (seatUuid != null) {
            org.bukkit.entity.Entity seat = Bukkit.getEntity(seatUuid);
            if (seat != null) {
                seat.eject();
                seat.remove();
            }
        }
        registry.clearSeating(wardUuid);
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        if (!nurseryEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getBlockFace() != BlockFace.UP) return;

        ItemStack inHand = event.getItem();
        if (!isHighchairItem(inHand)) return;
        int cmd = inHand.getItemMeta().getCustomModelData();
        int colorIndex = cmd - HighchairRegistry.CMD_BASE;
        if (HighchairRegistry.displayKeyFor(colorIndex) == null) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Block origin = clicked.getRelative(BlockFace.UP);

        boolean baseSolid = clicked.getType().isSolid()
            && !clicked.isLiquid()
            && clicked.getType() != Material.AIR;
        boolean originAir = origin.getType() == Material.AIR || origin.getType().isAir();
        Location pl = player.getLocation();
        boolean playerInOrigin = pl.getBlockX() == origin.getX()
            && pl.getBlockY() == origin.getY()
            && pl.getBlockZ() == origin.getZ();

        HighchairPlacementService.Result result =
            HighchairPlacementService.validate(baseSolid, originAir, playerInOrigin);
        if (result != HighchairPlacementService.Result.ACCEPT) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(HighchairPlacementService.message(result)));
            event.setCancelled(true);
            return;
        }

        float yaw = HighchairPlacementService.snapYaw(player.getLocation().getYaw());
        UUID id = UUID.randomUUID();

        // Visual
        Location displayLoc = new Location(origin.getWorld(),
            origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, yaw, 0f);

        ItemStack visual = inHand.clone();
        visual.setAmount(1);
        ItemDisplay display = origin.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // Highchair model native dimensions (from light_blue/pink_highchair.json):
            //   X: -6.5 to 23  = 1.844 blocks
            //   Y: -1   to 32  = 2.063 blocks  (≈2 cells: seat at ~y=1, back top at ~y=2)
            //   Z: -2   to 22  = 1.500 blocks
            //
            // Scale 1.0 keeps the chair ~2 blocks tall, matching the spec intent
            // (seat fills the barrier cell; back rises into the cell above).
            //
            // Y translation 0.469 was tuned empirically — at 0.969 the chair floated
            // ~0.5 blocks above the ground, suggesting NONE transform positions the
            // model differently than the geometric-centre-at-entity assumption.
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0.469f, 0f),
                new AxisAngle4f((float) Math.toRadians(yaw), 0, 1, 0),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setInvulnerable(true);
            d.setPersistent(true);
        });

        Interaction interaction = origin.getWorld().spawn(displayLoc, Interaction.class, i -> {
            i.setInteractionWidth(1.0f);
            i.setInteractionHeight(2.0f);
            i.setResponsive(true);
            i.setInvulnerable(true);
            i.setPersistent(true);
        });

        // Barrier collision
        origin.setType(Material.BARRIER);

        Highchair h = new Highchair(
            id,
            origin.getWorld().getName(),
            origin.getX(), origin.getY(), origin.getZ(),
            yaw, colorIndex,
            display.getUniqueId(),
            interaction.getUniqueId(),
            player.getUniqueId()
        );
        h.writeToPdc(interaction, keys);
        registry.register(h);

        if (player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeftClickBarrier(PlayerInteractEvent event) {
        if (!nurseryEnabled()) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BARRIER) return;

        Highchair h = registry.findByOriginBlock(
            clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (h == null) return;

        teardown(h, /*dropItem*/ true);
        event.setCancelled(true);
    }

    /**
     * Tears down a highchair: frees any contained ward, despawns paired entities,
     * sets the barrier to AIR, optionally drops the item, unregisters.
     */
    private void teardown(Highchair h, boolean dropItem) {
        // Free any contained ward — despawn seat ArmorStand + clear bookkeeping.
        for (UUID ward : new HashSet<>(registry.containedWards())) {
            if (h.id().equals(registry.highchairIdForWard(ward))) {
                releaseWard(ward);
            }
        }

        org.bukkit.entity.Entity disp = Bukkit.getEntity(h.displayUuid());
        if (disp != null) disp.remove();
        org.bukkit.entity.Entity inter = Bukkit.getEntity(h.interactionUuid());
        if (inter != null) inter.remove();

        Location originLoc = h.world() == null ? null
            : new Location(h.world(), h.originX(), h.originY(), h.originZ());
        if (originLoc != null) {
            Block b = originLoc.getBlock();
            if (b.getType() == Material.BARRIER) b.setType(Material.AIR);
        }

        if (dropItem && h.world() != null) {
            ItemStack drop = HighchairItem.make(h.colorIndex());
            if (drop != null) {
                h.world().dropItemNaturally(
                    new Location(h.world(), h.originX() + 0.5, h.originY(), h.originZ() + 0.5),
                    drop);
            }
        }

        registry.unregister(h.id());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!nurseryEnabled()) return;
        Block b = event.getBlock();
        if (b.getType() != Material.BARRIER) return;
        Highchair h = registry.findByOriginBlock(
            b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (h == null) return;
        // Defensive — route through teardown so we don't orphan entities. Drop the item.
        teardown(h, /*dropItem*/ true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!nurseryEnabled()) return;
        handleExplosion(event.blockList(), event.getEntity().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!nurseryEnabled()) return;
        handleExplosion(event.blockList(), event.getBlock().getWorld().getName());
    }

    private void handleExplosion(List<Block> blocks, String worldName) {
        Set<UUID> toRemove = new HashSet<>();
        for (Block b : blocks) {
            Highchair h = registry.findByOriginBlock(worldName, b.getX(), b.getY(), b.getZ());
            if (h != null) toRemove.add(h.id());
        }
        for (UUID id : toRemove) {
            Highchair h = registry.findById(id);
            if (h != null) teardown(h, /*dropItem*/ false);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!nurseryEnabled()) return;
        for (org.bukkit.entity.Entity e : event.getChunk().getEntities()) {
            if (!(e instanceof Interaction interaction)) continue;
            if (!interaction.getScoreboardTags().contains(HighchairPdcKeys.SCOREBOARD_TAG)) continue;
            Highchair h = Highchair.fromPdc(interaction, keys);
            if (h == null) {
                plugin.getLogger().warning("Removing orphan highchair Interaction at " + interaction.getLocation());
                interaction.remove();
                continue;
            }
            if (Bukkit.getEntity(h.displayUuid()) == null) {
                plugin.getLogger().warning("Highchair " + h.id() + " has missing display; removing Interaction.");
                interaction.remove();
                continue;
            }
            registry.register(h);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!nurseryEnabled()) return;
        String worldName = event.getWorld().getName();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        // Release any wards seated in highchairs in this chunk.
        for (Highchair h : registry.findByChunk(worldName, cx, cz)) {
            for (UUID ward : new HashSet<>(registry.containedWards())) {
                if (h.id().equals(registry.highchairIdForWard(ward))) {
                    releaseWard(ward);
                }
            }
        }
        registry.unregisterChunk(worldName, cx, cz);
    }

    @EventHandler
    public void onInteractInteractionEntity(PlayerInteractAtEntityEvent event) {
        if (!nurseryEnabled()) return;
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains(HighchairPdcKeys.SCOREBOARD_TAG)) return;

        Player clicker = event.getPlayer();
        Highchair h = Highchair.fromPdc(interaction, keys);
        if (h == null) return;

        // Carry-place flow takes priority.
        com.storynook.furniture.carry.CarryManager carry = plugin.getCarryManager();
        if (carry != null && carry.isCarrying(clicker.getUniqueId())) {
            startCarryPlaceTimer(clicker, h);
            event.setCancelled(true);
            return;
        }

        // Already someone in this chair?
        for (UUID w : registry.containedWards()) {
            if (h.id().equals(registry.highchairIdForWard(w))) {
                clicker.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("Someone's already sitting here."));
                event.setCancelled(true);
                return;
            }
        }
        // Already seated elsewhere?
        if (registry.highchairIdForWard(clicker.getUniqueId()) != null) {
            clicker.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("You're already sitting somewhere."));
            event.setCancelled(true);
            return;
        }

        registry.recordSeating(clicker.getUniqueId(), h.id(), HighchairRegistry.LockMode.SELF, null);
        spawnSeatAndMount(h, clicker);
        event.setCancelled(true);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!nurseryEnabled()) return;
        if (!(event.getEntity() instanceof Player ward)) return;
        UUID wardUuid = ward.getUniqueId();
        UUID seatUuid = wardToSeat.get(wardUuid);
        if (seatUuid == null) return;
        if (!event.getDismounted().getUniqueId().equals(seatUuid)) return;

        HighchairRegistry.LockMode mode = registry.lockModeForWard(wardUuid);
        if (mode == HighchairRegistry.LockMode.SELF) {
            // Allow + clean up bookkeeping after the dismount completes.
            wardToSeat.remove(wardUuid);
            registry.clearSeating(wardUuid);
            org.bukkit.entity.Entity seat = Bukkit.getEntity(seatUuid);
            if (seat != null) {
                Bukkit.getScheduler().runTaskLater(plugin, seat::remove, 1L);
            }
            return;
        }
        if (mode == HighchairRegistry.LockMode.LOCKED) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = lockedSneakWarnAt.get(wardUuid);
            if (last == null || now - last > 2000L) {
                lockedSneakWarnAt.put(wardUuid, now);
                ward.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("Ask your caregiver to let you out."));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!nurseryEnabled()) return;
        UUID id = event.getPlayer().getUniqueId();
        if (registry.highchairIdForWard(id) != null) {
            releaseWard(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!nurseryEnabled()) return;
        UUID id = event.getEntity().getUniqueId();
        if (registry.highchairIdForWard(id) != null) {
            releaseWard(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!nurseryEnabled()) return;
        UUID id = event.getPlayer().getUniqueId();
        if (registry.highchairIdForWard(id) != null) {
            releaseWard(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Do NOT gate on nurseryEnabled — even if the flag flips off
        // mid-session, a contained ward should be released cleanly on quit.
        UUID id = event.getPlayer().getUniqueId();
        if (registry.highchairIdForWard(id) != null) {
            releaseWard(id);
        }
        lockedSneakWarnAt.remove(id);
    }

    private void startCarryPlaceTimer(Player caregiver, Highchair h) {
        com.storynook.furniture.carry.CarryManager carry = plugin.getCarryManager();
        if (carry == null) return;
        UUID wardUuid = carry.wardOf(caregiver.getUniqueId());
        if (wardUuid == null) return;
        Player ward = Bukkit.getPlayer(wardUuid);
        if (ward == null) return;

        // No double-start
        String key = timerKey(caregiver.getUniqueId(), h.id());
        if (activeTimers.containsKey(key)) return;

        // Already someone in this chair?
        for (UUID w : registry.containedWards()) {
            if (h.id().equals(registry.highchairIdForWard(w))) {
                caregiver.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("Someone's already sitting here."));
                return;
            }
        }

        Location originLoc = h.originCenter();
        if (originLoc == null) return;

        final int totalTicks = 60; // 3s * 20tps
        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                elapsed += 2; // we run every 2 ticks
                // Cancellation conditions
                Player cg = Bukkit.getPlayer(caregiver.getUniqueId());
                Player wd = Bukkit.getPlayer(wardUuid);
                if (cg == null || wd == null) {
                    cancelWithMessage(caregiver, "Placement cancelled.");
                    return;
                }
                if (!carry.isCarrying(cg.getUniqueId()) || !wardUuid.equals(carry.wardOf(cg.getUniqueId()))) {
                    cancelWithMessage(cg, "Placement cancelled.");
                    return;
                }
                if (registry.findById(h.id()) == null) {
                    cancelWithMessage(cg, "Placement cancelled.");
                    return;
                }
                Location cgLoc = cg.getLocation();
                if (cgLoc.getWorld() != originLoc.getWorld()
                        || cgLoc.distanceSquared(originLoc) > 4.0) { // >2 blocks horizontally-ish
                    cancelWithMessage(cg, "Placement cancelled.");
                    return;
                }

                // Progress action-bar
                if (elapsed % 4 == 0) {
                    int filled = elapsed * 7 / totalTicks;
                    StringBuilder bar = new StringBuilder("[");
                    for (int i = 0; i < 7; i++) bar.append(i < filled ? '#' : '-');
                    bar.append("] ");
                    bar.append(String.format("%.1fs", (totalTicks - elapsed) / 20.0));
                    String text = "Placing in highchair... " + bar;
                    cg.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
                    wd.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
                }

                if (elapsed >= totalTicks) {
                    cancel();
                    activeTimers.remove(timerKey(caregiver.getUniqueId(), h.id()));
                    completePlacement(cg, wd, h);
                }
            }

            private void cancelWithMessage(Player to, String msg) {
                cancel();
                activeTimers.remove(timerKey(caregiver.getUniqueId(), h.id()));
                if (to != null) {
                    to.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        activeTimers.put(key, task);
    }

    private void completePlacement(Player caregiver, Player ward, Highchair h) {
        // Drop carry. CarryManager.clearCarry takes the caregiver UUID.
        com.storynook.furniture.carry.CarryManager carry = plugin.getCarryManager();
        if (carry != null) carry.clearCarry(caregiver.getUniqueId());

        registry.recordSeating(ward.getUniqueId(), h.id(),
            HighchairRegistry.LockMode.LOCKED, caregiver.getUniqueId());
        spawnSeatAndMount(h, ward);

        // Integration fire
        if (plugin.getIntegrationsBus() != null) {
            plugin.getIntegrationsBus().fire(caregiver, ActionId.HIGHCHAIR_PLACE, ward, new HashMap<>());
        }
    }
}
