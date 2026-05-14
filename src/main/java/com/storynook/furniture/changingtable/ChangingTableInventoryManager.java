package com.storynook.furniture.changingtable;

import com.storynook.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-table 9-slot (1-row) inventory with YAML persistence at
 * &lt;dataFolder&gt;/ChangingTables/&lt;tableUuid&gt;.yml.
 *
 * <p>Close-time sweep ejects any items that violate
 * {@link ChangingTableInventoryFilter#isStorable(ItemStack)}; click/drag-time
 * enforcement happens in Task 17.
 *
 * <p>Mirrors the DiaperPail per-UUID-YAML pattern.
 */
public final class ChangingTableInventoryManager implements Listener {

    private static final int SLOTS = 9;
    private static final String INV_TITLE = "Changing Table";

    private final Plugin plugin;
    /** tableId -> Inventory (lazy-loaded on first open). */
    private final Map<UUID, Inventory> cache = new ConcurrentHashMap<>();
    /** viewerUuid -> tableId, so close-event knows which table to flush. */
    private final Map<UUID, UUID> viewerToTable = new ConcurrentHashMap<>();
    /** viewerUuid -> per-category counts at open time (for stocking-event delta in T24). */
    private final Map<UUID, int[]> openSnapshot = new ConcurrentHashMap<>();

    public ChangingTableInventoryManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ChangingTable table) {
        Inventory inv = cache.computeIfAbsent(table.id(), this::loadOrCreate);
        viewerToTable.put(player.getUniqueId(), table.id());
        openSnapshot.put(player.getUniqueId(), categoryCounts(inv));
        player.openInventory(inv);
    }

    public boolean isOurInventory(Inventory inv) {
        if (inv == null) return false;
        return cache.containsValue(inv);
    }

    public UUID tableIdFor(Inventory inv) {
        for (Map.Entry<UUID, Inventory> e : cache.entrySet()) {
            if (e.getValue() == inv) return e.getKey();
        }
        return null;
    }

    public int[] snapshotForViewer(UUID viewer) {
        return openSnapshot.get(viewer);
    }

    /** Called by the listener when a table is being torn down. Drops contents into
     *  the world and deletes the YAML file. */
    public void dropAndDelete(ChangingTable table) {
        Inventory inv = cache.remove(table.id());
        if (inv != null) {
            org.bukkit.World w = Bukkit.getWorld(table.worldName());
            if (w != null) {
                org.bukkit.Location dropLoc = w.getBlockAt(table.footX(), table.footY(), table.footZ())
                    .getLocation().add(0.5, 1.0, 0.5);
                for (ItemStack s : inv.getContents()) {
                    if (s != null && s.getType() != Material.AIR) {
                        w.dropItemNaturally(dropLoc, s);
                    }
                }
            }
        }
        File f = invFile(table.id());
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** Flush all cached inventories to disk, close any open views, and drop the
     *  cache. Called from {@code /diaperreload} so subsequent opens re-read
     *  fresh YAML state. */
    public void clearCache() {
        // Save anything dirty before evicting.
        for (Map.Entry<UUID, Inventory> e : cache.entrySet()) {
            saveInventory(e.getKey(), e.getValue());
        }
        // Close any open views so cached inventory references don't outlive the cache.
        for (Inventory inv : cache.values()) {
            // Copy viewers to a list — closing during iteration would CME.
            java.util.List<org.bukkit.entity.HumanEntity> viewers = new java.util.ArrayList<>(inv.getViewers());
            for (org.bukkit.entity.HumanEntity v : viewers) {
                v.closeInventory();
            }
        }
        cache.clear();
        viewerToTable.clear();
        openSnapshot.clear();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID viewer = event.getPlayer().getUniqueId();
        UUID tableId = viewerToTable.remove(viewer);
        openSnapshot.remove(viewer);
        if (tableId == null) return;
        Inventory cached = cache.get(tableId);
        if (cached != event.getInventory()) return;

        // Close-time sweep: eject anything unstorable (defensive).
        for (int i = 0; i < cached.getSize(); i++) {
            ItemStack s = cached.getItem(i);
            if (!ChangingTableInventoryFilter.isStorable(s)) {
                cached.setItem(i, null);
                if (event.getPlayer() instanceof Player p) {
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(s);
                    for (ItemStack rem : leftover.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), rem);
                    }
                }
            }
        }
        saveInventory(tableId, cached);
    }

    private Inventory loadOrCreate(UUID tableId) {
        File f = invFile(tableId);
        Inventory inv = Bukkit.createInventory(null, SLOTS, INV_TITLE);
        if (!f.exists()) return inv;
        FileConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.contains("items")) {
            for (String key : yml.getConfigurationSection("items").getKeys(false)) {
                int slot;
                try { slot = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
                ItemStack stack = yml.getItemStack("items." + key);
                if (stack != null && slot >= 0 && slot < SLOTS) inv.setItem(slot, stack);
            }
        }
        return inv;
    }

    private void saveInventory(UUID tableId, Inventory inv) {
        File f = invFile(tableId);
        FileConfiguration yml = new YamlConfiguration();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && s.getType() != Material.AIR) {
                yml.set("items." + i, s);
            }
        }
        try {
            f.getParentFile().mkdirs();
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save changing-table inventory " + tableId + ": " + e.getMessage());
        }
    }

    private File invFile(UUID tableId) {
        return new File(plugin.getDataFolder(), "ChangingTables/" + tableId + ".yml");
    }

    /** Returns int[4] — count of storable items in each category (1, 2, 3 used; 0 never stored). */
    public static int[] categoryCounts(Inventory inv) {
        int[] out = new int[4];
        for (ItemStack s : inv.getContents()) {
            if (s == null) continue;
            int cat = categoryOf(s);
            if (cat >= 0 && cat < 4) out[cat] += s.getAmount();
        }
        return out;
    }

    private static int categoryOf(ItemStack s) {
        if (s == null || !s.hasItemMeta()) return -1;
        ItemMeta meta = s.getItemMeta();
        if (!meta.hasCustomModelData()) return -1;
        int cmd = meta.getCustomModelData();
        com.storynook.DesignRegistry.DesignDef def = com.storynook.DesignRegistry.findByCleanCmd(cmd);
        if (def != null) return def.category;
        if (cmd == 626001) return 3;
        if (cmd == 626003) return 1;
        if (cmd == 626009) return 2;
        return -1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!isOurInventory(event.getView().getTopInventory())) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        boolean clickInTop = event.getClickedInventory() == event.getView().getTopInventory();

        // Determine which item is moving INTO the top inventory.
        ItemStack candidate = null;
        if (clickInTop) {
            // Cursor drop into top, or hotbar/number swap moving item up.
            if (cursor != null && cursor.getType() != Material.AIR) candidate = cursor;
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                ItemStack hotbar = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                if (hotbar != null && hotbar.getType() != Material.AIR) candidate = hotbar;
            }
        } else {
            // Bottom-inventory click — shift-click moves an item up.
            if (event.isShiftClick() && current != null && current.getType() != Material.AIR) {
                candidate = current;
            }
        }
        if (candidate != null && !ChangingTableInventoryFilter.isStorable(candidate)) {
            event.setCancelled(true);   // silent rejection
        }

        // Stock-event fire (post-event tick — by now the move has actually happened).
        Inventory top = event.getView().getTopInventory();
        if (isOurInventory(top) && !event.isCancelled() && event.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> fireStockEventIfDeposit(p.getUniqueId(), top, p));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!isOurInventory(event.getView().getTopInventory())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                ItemStack s = event.getNewItems().get(slot);
                if (s != null && !ChangingTableInventoryFilter.isStorable(s)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Stock-event fire.
        Inventory top = event.getView().getTopInventory();
        if (isOurInventory(top) && !event.isCancelled() && event.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> fireStockEventIfDeposit(p.getUniqueId(), top, p));
        }
    }

    private void fireStockEventIfDeposit(UUID viewer, Inventory inv, Player player) {
        int[] before = openSnapshot.get(viewer);
        if (before == null) return;
        int[] after = categoryCounts(inv);
        int dCat1 = Math.max(0, after[1] - before[1]);
        int dCat2 = Math.max(0, after[2] - before[2]);
        int dCat3 = Math.max(0, after[3] - before[3]);
        int total = dCat1 + dCat2 + dCat3;
        if (total <= 0) return;
        // Update the snapshot so future clicks compute delta-from-now.
        openSnapshot.put(viewer, after);

        java.util.Map<String, Object> ctx = new java.util.HashMap<>();
        ctx.put("count", total);
        ctx.put("category_1", dCat1);
        ctx.put("category_2", dCat2);
        ctx.put("category_3", dCat3);
        plugin.getIntegrationsBus().fire(player,
            com.storynook.Integrations.events.ActionId.STOCK_CHANGING_TABLE, null, ctx);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (isOurInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }
}
