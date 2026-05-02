package com.storynook.nanny;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;

/**
 * D4: Event-driven Nanny dialogue triggers.
 *
 * <p>Routes Bukkit events to {@link NannyChatEngine#speakIfNearby}. Also
 * tracks "ward inventory open" state — read by
 * {@link NannyCareEngine}'s mob-warning scan to gate warnings on whether
 * the player is actually distracted.
 */
public class NannyReactiveListener implements Listener {

    private final Plugin plugin;

    /** Wards currently looking at an inventory screen. Read by the heartbeat for mob_warning. */
    private final Set<UUID> wardsWithInventoryOpen = new HashSet<>();

    public NannyReactiveListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isInventoryOpen(UUID wardUUID) {
        return wardsWithInventoryOpen.contains(wardUUID);
    }

    private NannyChatEngine chat() {
        NannyManager mgr = plugin.getNannyManager();
        return (mgr != null) ? mgr.getChatEngine() : null;
    }

    // ----- ward_attacked -----

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Entity damager = event.getDamager();
        // Resolve projectile shooters and TNT origin to their living source
        if (damager instanceof org.bukkit.entity.Projectile) {
            org.bukkit.projectiles.ProjectileSource src =
                    ((org.bukkit.entity.Projectile) damager).getShooter();
            if (src instanceof Entity) damager = (Entity) src;
        }
        if (!isHostile(damager)) return;

        NannyChatEngine ce = chat();
        if (ce != null) {
            ce.speakIfNearby(victim, "ward_attacked",
                    "reactive:attacked", 30_000L,
                    NannyChatEngine.PRI_ACCIDENT);
        }
    }

    private boolean isHostile(Entity e) {
        return e instanceof Monster
                || e instanceof Slime
                || e instanceof Phantom
                || e instanceof Hoglin;
    }

    // ----- ate_bad_food -----

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        Material m = item.getType();
        boolean bad = (m == Material.POISONOUS_POTATO
                || m == Material.ROTTEN_FLESH
                || m == Material.PUFFERFISH
                || m == Material.SPIDER_EYE
                || m == Material.CHORUS_FRUIT);
        if (!bad) return;

        NannyChatEngine ce = chat();
        if (ce != null) {
            ce.speakIfNearby(event.getPlayer(), "ate_bad_food",
                    "reactive:bad_food", 60_000L,
                    NannyChatEngine.PRI_CARE);
        }
    }

    // ----- respawn_after_death -----

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            NannyChatEngine ce = chat();
            if (ce != null) {
                ce.speakIfNearby(p, "respawn_after_death",
                        "lifecycle:respawn", 30_000L,
                        NannyChatEngine.PRI_LIFECYCLE);
            }
        }, 20L);
    }

    // ----- nether_entered / end_entered -----

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld() == null) return;
        World.Environment env = p.getWorld().getEnvironment();
        String category;
        if (env == World.Environment.NETHER) category = "nether_entered";
        else if (env == World.Environment.THE_END) category = "end_entered";
        else return;

        NannyChatEngine ce = chat();
        if (ce == null) return;
        // Delay one tick — the Nanny's onPlayerChangedWorld may have just teleported her
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                ce.speakIfNearby(p, category,
                        "lifecycle:" + category, 5L * 60L * 1000L,
                        NannyChatEngine.PRI_LIFECYCLE);
            }
        }, 20L);
    }

    // ----- rain_started -----

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!event.toWeatherState()) return; // only fire on rain start, not end
        World world = event.getWorld();
        if (world == null) return;
        NannyChatEngine ce = chat();
        if (ce == null) return;

        for (Player p : world.getPlayers()) {
            // Only fire for players who are actually outdoors
            if (p.getLocation().getBlock().getLightFromSky() <= 8) continue;
            ce.speakIfNearby(p, "rain_started",
                    "reactive:rain", 10L * 60L * 1000L,
                    NannyChatEngine.PRI_LIFECYCLE);
        }
    }

    // ----- ward inventory tracker (for mob_warning gate) -----

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            wardsWithInventoryOpen.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            wardsWithInventoryOpen.remove(event.getPlayer().getUniqueId());
        }
    }
}
