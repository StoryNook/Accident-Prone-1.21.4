package com.storynook.nanny;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.storynook.DesignRegistry;
import com.storynook.Plugin;

/**
 * Per-Nanny supply chain. Provides the smart-filter, personal-inventory,
 * chest-scanning, simulated-crafting, and follow-mode pre-grab APIs that
 * NannyCareEngine and NannyMenu use. Stateless other than the plugin
 * reference — all per-Nanny state lives on {@link NannyData}.
 */
public class NannyInventoryManager {

    private final Plugin plugin;

    /** CustomModelData values the Nanny treats as usable diaper-family items. */
    private static final Set<Integer> DIAPER_MODEL_DATA = new HashSet<>(Arrays.asList(
        626001, 626002, 626003, 626009,             // underwear / thick / pull-up family
        626004, 626005, 626010, 626011,             // pull-up + cousins
        626006, 626007, 626008, 626012, 626013      // diaper / stuffer / tape / parts
    ));

    /** Vanilla materials the Nanny will use to feed wards. */
    private static final Set<Material> FOOD_WHITELIST = new HashSet<>(Arrays.asList(
        Material.BREAD, Material.COOKIE, Material.MELON_SLICE,
        Material.APPLE, Material.GOLDEN_APPLE, Material.COOKED_BEEF,
        Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN,
        Material.BAKED_POTATO, Material.CARROT
    ));

    public NannyInventoryManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Filter and predicates
    // ---------------------------------------------------------------

    public static boolean isUsableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (DIAPER_MODEL_DATA.contains(cmd)) return true;
            // Any registered design (clean CMD) is also a usable diaper item.
            if (DesignRegistry.isAnyCleanCmd(cmd)) return true;
        }
        if (FOOD_WHITELIST.contains(item.getType())) return true;
        if (item.getType() == Material.LEATHER_LEGGINGS) return true;
        return false;
    }

    public static boolean isCleanDiaper(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        int cmd = meta.getCustomModelData();
        // Soiled-pants states are 626015..626018 — those are NOT clean diapers
        return DIAPER_MODEL_DATA.contains(cmd) && (cmd < 626015 || cmd > 626018);
    }

    public static boolean isAnyFood(ItemStack item) {
        return item != null && FOOD_WHITELIST.contains(item.getType());
    }

    public static boolean isMelonSlice(ItemStack item) {
        return item != null && item.getType() == Material.MELON_SLICE;
    }

    /**
     * A drink the Nanny can give the ward to restore hydration.
     * Currently: water-type potion bottles (Material.POTION + PotionType.WATER).
     * Extend this when sippy cups / baby bottles are added — match by their
     * CustomModelData and route them through doHydrate the same way.
     */
    public static boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.PotionMeta)) return false;
        org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) meta;
        return pm.getBasePotionType() == org.bukkit.potion.PotionType.WATER;
    }

    /**
     * The empty glass bottle left over after the Nanny gives the ward a drink.
     * Filled at a water cauldron / source block. Toilet cauldrons are excluded
     * by the refill scan — see NannyCareEngine.findWaterRefillSource.
     */
    public static boolean isEmptyGlassBottle(ItemStack item) {
        return item != null && item.getType() == Material.GLASS_BOTTLE;
    }

    public static boolean isLeggings(ItemStack item) {
        return item != null && item.getType() == Material.LEATHER_LEGGINGS;
    }

    // ---------------------------------------------------------------
    // Personal inventory + chest scanning
    // ---------------------------------------------------------------

    public int findInPersonalInventory(NannyData data, Predicate<ItemStack> match) {
        ItemStack[] inv = data.getPersonalInventory();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && match.test(inv[i])) return i;
        }
        return -1;
    }

    /** Lists the chest blocks the Nanny is allowed to scan for the given mode. */
    private List<Block> chestCandidates(NannyData data, World world) {
        List<Block> out = new ArrayList<>();
        if (data.getChestMode() == NannyData.ChestMode.SELECTED) {
            for (String locStr : data.getSelectedChests()) {
                Block b = parseChestLocation(world, locStr);
                if (b != null) out.add(b);
            }
            return out;
        }
        if (data.getChestMode() == NannyData.ChestMode.ALL) {
            int r = data.getHomeRadius();
            int hx = (int) data.getHomeX();
            int hy = (int) data.getHomeY();
            int hz = (int) data.getHomeZ();
            // 4-block stride keeps the scan cheap; double-chests are 1 block
            // apart so this still hits at least one half of any pair.
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    for (int dy = -4; dy <= 4; dy++) {
                        out.add(world.getBlockAt(hx + dx, hy + dy, hz + dz));
                    }
                }
            }
        }
        return out;
    }

    private Block parseChestLocation(World world, String locStr) {
        try {
            String[] parts = locStr.split(",");
            return world.getBlockAt(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Locates and removes one matching item, checking the personal inventory
     * first then any configured chests. Returns the removed ItemStack with
     * {@code amount=1}, or {@code null} if none found.
     */
    public ItemStack takeOne(NannyData data, Predicate<ItemStack> match) {
        int slot = findInPersonalInventory(data, match);
        if (slot >= 0) {
            ItemStack inSlot = data.getPersonalInventory()[slot];
            ItemStack one = inSlot.clone();
            one.setAmount(1);
            if (inSlot.getAmount() > 1) inSlot.setAmount(inSlot.getAmount() - 1);
            else data.getPersonalInventory()[slot] = null;
            return one;
        }
        if (data.getChestMode() == NannyData.ChestMode.INVENTORY_ONLY) return null;
        World world = Bukkit.getWorld(data.getHomeWorld());
        if (world == null) return null;

        for (Block block : chestCandidates(data, world)) {
            if (block == null || block.getType() != Material.CHEST) continue;
            BlockState state = block.getState();
            if (!(state instanceof Chest)) continue;
            Inventory inv = ((Chest) state).getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || !isUsableItem(stack) || !match.test(stack)) continue;
                ItemStack one = stack.clone();
                one.setAmount(1);
                if (stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
                else inv.setItem(i, null);
                return one;
            }
        }
        return null;
    }

    public void addToPersonalInventory(NannyData data, ItemStack item) {
        if (item == null) return;
        ItemStack[] inv = data.getPersonalInventory();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] == null) { inv[i] = item; return; }
            if (inv[i].isSimilar(item) && inv[i].getAmount() < inv[i].getMaxStackSize()) {
                int room = inv[i].getMaxStackSize() - inv[i].getAmount();
                int add = Math.min(room, item.getAmount());
                inv[i].setAmount(inv[i].getAmount() + add);
                item.setAmount(item.getAmount() - add);
                if (item.getAmount() <= 0) return;
            }
        }
    }

    // ---------------------------------------------------------------
    // Counting + consumption helpers (used by crafting)
    // ---------------------------------------------------------------

    public int countAvailable(NannyData data, Predicate<ItemStack> match) {
        int total = 0;
        for (ItemStack stack : data.getPersonalInventory()) {
            if (stack != null && match.test(stack)) total += stack.getAmount();
        }
        if (data.getChestMode() == NannyData.ChestMode.INVENTORY_ONLY) return total;
        World world = Bukkit.getWorld(data.getHomeWorld());
        if (world == null) return total;
        for (Block block : chestCandidates(data, world)) {
            if (block == null || block.getType() != Material.CHEST) continue;
            BlockState state = block.getState();
            if (!(state instanceof Chest)) continue;
            Inventory inv = ((Chest) state).getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && match.test(s)) total += s.getAmount();
            }
        }
        return total;
    }

    private void consumeFromAnywhere(NannyData data, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] inv = data.getPersonalInventory();
        for (int i = 0; i < inv.length && remaining > 0; i++) {
            if (inv[i] != null && inv[i].getType() == mat) {
                int take = Math.min(remaining, inv[i].getAmount());
                inv[i].setAmount(inv[i].getAmount() - take);
                if (inv[i].getAmount() <= 0) inv[i] = null;
                remaining -= take;
            }
        }
        if (remaining <= 0) return;
        if (data.getChestMode() == NannyData.ChestMode.INVENTORY_ONLY) return;
        World world = Bukkit.getWorld(data.getHomeWorld());
        if (world == null) return;
        for (Block block : chestCandidates(data, world)) {
            if (remaining <= 0) break;
            if (block == null || block.getType() != Material.CHEST) continue;
            BlockState state = block.getState();
            if (!(state instanceof Chest)) continue;
            Inventory chest = ((Chest) state).getInventory();
            for (int i = 0; i < chest.getSize() && remaining > 0; i++) {
                ItemStack s = chest.getItem(i);
                if (s == null || s.getType() != mat) continue;
                int take = Math.min(remaining, s.getAmount());
                s.setAmount(s.getAmount() - take);
                if (s.getAmount() <= 0) chest.setItem(i, null);
                remaining -= take;
            }
        }
    }

    private boolean craftingTableInRange(NannyData data) {
        World world = Bukkit.getWorld(data.getHomeWorld());
        if (world == null) return false;
        int r = data.getHomeRadius();
        int hx = (int) data.getHomeX();
        int hy = (int) data.getHomeY();
        int hz = (int) data.getHomeZ();
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                for (int dy = -4; dy <= 4; dy++) {
                    if (world.getBlockAt(hx + dx, hy + dy, hz + dz).getType() == Material.CRAFTING_TABLE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Simulated crafting
    // ---------------------------------------------------------------

    /**
     * Crafts bread (3 wheat) or cookies (2 wheat + 1 cocoa) if a crafting
     * table is in range and ingredients are available. Returns the crafted
     * ItemStack, or {@code null} if disallowed by mode / no table / no
     * ingredients.
     */
    public ItemStack tryCraftFood(NannyData data) {
        NannyData.CraftingMode mode = data.getCraftingMode();
        if (mode == NannyData.CraftingMode.NONE) return null;
        if (!craftingTableInRange(data)) return null;

        int wheat = countAvailable(data, s -> s.getType() == Material.WHEAT);
        if (wheat >= 3) {
            consumeFromAnywhere(data, Material.WHEAT, 3);
            return new ItemStack(Material.BREAD, 1);
        }
        int cocoa = countAvailable(data, s -> s.getType() == Material.COCOA_BEANS);
        if (wheat >= 2 && cocoa >= 1) {
            consumeFromAnywhere(data, Material.WHEAT, 2);
            consumeFromAnywhere(data, Material.COCOA_BEANS, 1);
            return new ItemStack(Material.COOKIE, 8);
        }
        return null;
    }

    /**
     * For craftingMode in {ALL, EVIL}. Crafts a basic diaper from a stuffer
     * (CMD 626007) + tape (CMD 626008) + a leather-leggings base. Returns
     * the crafted ItemStack, or {@code null} if disallowed / no table / no
     * ingredients. EVIL-specific recipes (Curse of Binding, laxative food)
     * are deferred to Phase 5.
     */
    public ItemStack tryCraftDiaper(NannyData data) {
        if (data.getCraftingMode() != NannyData.CraftingMode.ALL
                && data.getCraftingMode() != NannyData.CraftingMode.EVIL) return null;
        if (!craftingTableInRange(data)) return null;

        Predicate<ItemStack> isStuffer = stack -> {
            ItemMeta m = stack.getItemMeta();
            return m != null && m.hasCustomModelData() && m.getCustomModelData() == 626007;
        };
        Predicate<ItemStack> isTape = stack -> {
            ItemMeta m = stack.getItemMeta();
            return m != null && m.hasCustomModelData() && m.getCustomModelData() == 626008;
        };
        Predicate<ItemStack> isBaseLeggings = stack -> stack.getType() == Material.LEATHER_LEGGINGS;

        if (countAvailable(data, isStuffer) < 1) return null;
        if (countAvailable(data, isTape) < 1) return null;
        if (countAvailable(data, isBaseLeggings) < 1) return null;

        takeOne(data, isStuffer);
        takeOne(data, isTape);
        takeOne(data, isBaseLeggings);

        ItemStack diaper = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        ItemMeta meta = diaper.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(626006);
            meta.setDisplayName(net.md_5.bungee.api.ChatColor.WHITE + "Diaper");
            if (data.getCraftingMode() == NannyData.CraftingMode.EVIL) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1, true);
                meta.setLore(java.util.Arrays.asList(
                        net.md_5.bungee.api.ChatColor.RED + "Curse of Binding"));
            }
            diaper.setItemMeta(meta);
        }
        return diaper;
    }

    /**
     * Phase 5b: crafts a laxative-tagged food item for force-feed actions.
     * Only valid when craftingMode == EVIL (gated by NannyPolicy elsewhere).
     * Recipe: 1 bread + 1 cocoa beans + 1 sugar.
     *
     * @return the laxative ItemStack, or null when ingredients/table missing
     */
    public ItemStack tryCraftLaxative(NannyData data) {
        if (data.getCraftingMode() != NannyData.CraftingMode.EVIL) return null;
        if (!craftingTableInRange(data)) return null;

        if (countAvailable(data, s -> s.getType() == Material.BREAD) < 1) return null;
        if (countAvailable(data, s -> s.getType() == Material.COCOA_BEANS) < 1) return null;
        if (countAvailable(data, s -> s.getType() == Material.SUGAR) < 1) return null;

        consumeFromAnywhere(data, Material.BREAD, 1);
        consumeFromAnywhere(data, Material.COCOA_BEANS, 1);
        consumeFromAnywhere(data, Material.SUGAR, 1);

        ItemStack lax = new ItemStack(Material.BREAD, 1);
        ItemMeta meta = lax.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(net.md_5.bungee.api.ChatColor.DARK_PURPLE + "Laxative");
            meta.setLore(java.util.Arrays.asList(
                    net.md_5.bungee.api.ChatColor.GRAY + "Mysterious side effects."));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "laxative_effect"),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            lax.setItemMeta(meta);
        }
        return lax;
    }

    // ---------------------------------------------------------------
    // Follow-mode pre-grab
    // ---------------------------------------------------------------

    /**
     * Tops up the Nanny's personal inventory to {@code >= 3} clean diapers
     * and {@code >= 3} food before she leaves home in follow mode. Pulls
     * from chests / crafts as needed. Returns true if both minimums are
     * met after the top-up.
     */
    public boolean prepareFollowSupplies(NannyData data) {
        int diaperShortfall = 3 - countAvailable(data, NannyInventoryManager::isCleanDiaper);
        while (diaperShortfall > 0) {
            ItemStack got = takeOne(data, NannyInventoryManager::isCleanDiaper);
            if (got == null) {
                ItemStack crafted = tryCraftDiaper(data);
                if (crafted == null) break;
                got = crafted;
            }
            addToPersonalInventory(data, got);
            diaperShortfall--;
        }
        int foodShortfall = 3 - countAvailable(data, NannyInventoryManager::isAnyFood);
        while (foodShortfall > 0) {
            ItemStack got = takeOne(data, NannyInventoryManager::isAnyFood);
            if (got == null) {
                ItemStack crafted = tryCraftFood(data);
                if (crafted == null) break;
                got = crafted;
            }
            addToPersonalInventory(data, got);
            foodShortfall--;
        }
        return countAvailable(data, NannyInventoryManager::isCleanDiaper) >= 3
            && countAvailable(data, NannyInventoryManager::isAnyFood) >= 3;
    }
}
