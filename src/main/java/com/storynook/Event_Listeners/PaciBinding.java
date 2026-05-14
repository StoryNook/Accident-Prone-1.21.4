package com.storynook.Event_Listeners;

import java.util.Collections;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.PlayerStats;
import com.storynook.items.CustomItemCheck;

public class PaciBinding implements Listener {

    private final Plugin plugin;
    private final NamespacedKey nameKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey curseKey;
    private final NamespacedKey boundPaciMarker;

    public PaciBinding(Plugin plugin) {
        this.plugin = plugin;
        this.nameKey = new NamespacedKey(plugin, "crafted_by");
        this.uuidKey = new NamespacedKey(plugin, "crafted_by_uuid");
        this.curseKey = new NamespacedKey(plugin, "cursed");
        this.boundPaciMarker = new NamespacedKey(plugin, "bound_paci");
    }

    private boolean bindingFlagOn() {
        Object v = plugin.getGlobalConfig().get("Binding_Diapers");
        return v instanceof Boolean && (Boolean) v;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!bindingFlagOn()) return;
        if (event.getRecipe() == null || event.getInventory() == null) return;

        ItemStack result = event.getRecipe().getResult();
        if (result == null) return;
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;
        if (!resultMeta.getPersistentDataContainer().has(boundPaciMarker, PersistentDataType.BYTE)) return;

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        int paciCount = 0;
        int bookCount = 0;
        ItemStack paciItem = null;

        for (ItemStack item : matrix) {
            if (item == null) continue;
            if (CustomItemCheck.isPaci(item)) {
                if (paciItem == null) paciItem = item;
                paciCount += item.getAmount();
                if (paciCount > 1) { inv.setResult(null); return; }
            } else if (item.getType() == Material.ENCHANTED_BOOK) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof EnchantmentStorageMeta) {
                    Map<Enchantment, Integer> stored =
                        ((EnchantmentStorageMeta) meta).getStoredEnchants();
                    if (stored.containsKey(Enchantment.BINDING_CURSE)) {
                        bookCount += item.getAmount();
                        if (bookCount > 1) { inv.setResult(null); return; }
                    } else {
                        inv.setResult(null); return;
                    }
                } else {
                    inv.setResult(null); return;
                }
            } else {
                inv.setResult(null); return;
            }
        }

        if (paciCount == 1 && bookCount == 1 && paciItem != null && !inv.getViewers().isEmpty()) {
            HumanEntity viewer = inv.getViewers().get(0);
            if (!(viewer instanceof Player)) return;
            ItemStack cursed = paciItem.clone();
            applyCurseMarkers(cursed, (Player) viewer);
            inv.setResult(cursed);
        } else {
            inv.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!bindingFlagOn()) return;
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null) return;
        if (!CustomItemCheck.isPaci(left)) return;
        if (right.getType() != Material.ENCHANTED_BOOK) return;
        ItemMeta rightMeta = right.getItemMeta();
        if (!(rightMeta instanceof EnchantmentStorageMeta)) return;
        Map<Enchantment, Integer> stored = ((EnchantmentStorageMeta) rightMeta).getStoredEnchants();
        if (!stored.containsKey(Enchantment.BINDING_CURSE)) return;
        if (inv.getViewers().isEmpty()) return;
        HumanEntity viewer = inv.getViewers().get(0);
        if (!(viewer instanceof Player)) return;
        ItemStack cursed = left.clone();
        applyCurseMarkers(cursed, (Player) viewer);
        event.setResult(cursed);
    }

    private void applyCurseMarkers(ItemStack result, Player crafter) {
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, crafter.getName());
        meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, crafter.getUniqueId().toString());
        meta.getPersistentDataContainer().set(curseKey, PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment"));
        result.setItemMeta(meta);
    }

    private void rewriteLoreFor(Player viewer, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(curseKey, PersistentDataType.BYTE)) return;
        if (!meta.getPersistentDataContainer().has(uuidKey, PersistentDataType.STRING)) return;
        if (item.getType() != Material.LEATHER_HELMET) return;
        if (!CustomItemCheck.isPaci(item)) return;

        String crafterUuid = meta.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
        boolean isCrafter = crafterUuid != null && crafterUuid.equals(viewer.getUniqueId().toString());

        PlayerStats stats = plugin.getPlayerStats(viewer.getUniqueId());
        boolean hardcore = stats != null && stats.getHardcore();

        if (isCrafter && !hardcore) {
            meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Cursed: Binding Enchantment (Crafted by you)"));
        } else {
            meta.setLore(null);
        }
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player viewer = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            rewriteLoreFor(viewer, item);
            inv.setItem(i, item);
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player viewer = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            rewriteLoreFor(viewer, item);
            inv.setItem(i, item);
        }
    }

    @EventHandler
    public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player viewer = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();
        rewriteLoreFor(viewer, item);
        event.getItem().setItemStack(item);
    }
}
