package com.storynook.Event_Listeners;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.storynook.Plugin;
import com.storynook.AccidentsANDWanrings.HandleAccident;
import com.storynook.PlayerStatsManagement.HypnoTrigger;
import com.storynook.PlayerStatsManagement.PlayerStats;

public class Hypno implements Listener {
    static HashMap<UUID, Double> distanceinBlocks = new HashMap<>();
    private static Plugin plugin;

    public Hypno(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        if (event.getRecipe() == null || event.getInventory() == null) return;

        ItemStack result = event.getRecipe().getResult();
        ItemMeta resultMeta = result.getItemMeta();

        if (resultMeta.getPersistentDataContainer().has(
            new NamespacedKey(plugin, "hypnosis"), PersistentDataType.BYTE)) {

            int clockcount = 0;
            int bindingCount = 0;
            ItemStack clockItem = null;

            for (ItemStack item : matrix) {
                if (item == null) continue;

                if (item.getType() == Material.CLOCK) {
                    if (clockItem == null) {
                        clockItem = item;
                    } else {
                        inventory.setResult(null);
                        return;
                    }
                    clockcount += item.getAmount();
                    if (clockcount > 1) {
                        inventory.setResult(null);
                        return;
                    }
                } else if (item.getType() == Material.ENCHANTED_BOOK) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof EnchantmentStorageMeta) {
                        EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) meta;
                        Map<Enchantment, Integer> storedEnchants = storageMeta.getStoredEnchants();
                        if (storedEnchants.containsKey(Enchantment.BINDING_CURSE)) {
                            bindingCount += item.getAmount();
                            if (bindingCount > 1) {
                                inventory.setResult(null);
                                return;
                            }
                        }
                    }
                } else {
                    inventory.setResult(null);
                    return;
                }
            }

            if (clockcount == 1 && bindingCount == 1 && clockItem != null) {
                result = new ItemStack(clockItem);
                ItemMeta meta = result.getItemMeta();

                if (meta != null) {
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "crafted_by"), PersistentDataType.STRING,
                        ((Player) inventory.getViewers().get(0)).getName());
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "crafted_by_uuid"), PersistentDataType.STRING,
                        ((Player) inventory.getViewers().get(0)).getUniqueId().toString());
                    meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "hypnosis"), PersistentDataType.BYTE, (byte) 1);
                    meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Hypnosis Clock"));
                    result.setItemMeta(meta);
                }

                inventory.setResult(result);
            } else {
                inventory.setResult(null);
            }
        } else {
            for (ItemStack item : matrix) {
                if (item == null) continue;
                if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "cursed"), PersistentDataType.BYTE)) {
                    inventory.setResult(null);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            if (updateLoreForPlayer(item, player)) inventory.setItem(i, item);
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            if (updateLoreForPlayer(item, player)) inventory.setItem(i, item);
        }
    }

    @EventHandler
    public void onItemPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();
        if (item == null || !item.hasItemMeta()) return;
        if (updateLoreForPlayer(item, player)) event.getItem().setItemStack(item);
    }

    private static boolean updateLoreForPlayer(ItemStack item, Player player) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "hypnosis"), PersistentDataType.BYTE)) {
            return false;
        }

        String crafterUUID = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "crafted_by_uuid"), PersistentDataType.STRING);
        PlayerStats playerStats = plugin.getPlayerStats(player.getUniqueId());

        if (crafterUUID != null && crafterUUID.equals(player.getUniqueId().toString())
            && (playerStats == null || !playerStats.getHardcore())) {
            String word = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "HypnoTriggerWord"), PersistentDataType.STRING);
            String type = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "HypnoType"), PersistentDataType.STRING);
            if (word != null && type != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.RED + "Trigger: " + word);
                lore.add(ChatColor.GOLD + "Type: " + type);
                meta.setLore(lore);
            } else {
                meta.setLore(Collections.singletonList(ChatColor.DARK_RED + "Hypnosis Clock"));
            }
        } else {
            meta.setLore(null);
        }
        item.setItemMeta(meta);
        return true;
    }

    public static void HypnoInteract(Player actor, Player target) {
        int rightclicktimes = plugin.rightclickCount.getOrDefault(actor.getUniqueId(), 0);
        rightclicktimes++;
        if (rightclicktimes > 1) {
            plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
            return;
        } else if (rightclicktimes == 1) {
            plugin.firstimeran.put(actor.getUniqueId(), true);
            plugin.rightclickCount.put(actor.getUniqueId(), rightclicktimes);
            handleRightClickHold(actor, target);
        }
    }

    public static void handleRightClickHold(Player actor, Player target) {
        if (plugin.rightclickCount.get(actor.getUniqueId()) > 0 && plugin.firstimeran.get(actor.getUniqueId())) {
            plugin.firstimeran.put(actor.getUniqueId(), false);
            BossBar bossBar = Bukkit.createBossBar(ChatColor.GOLD + "Hypnosis", BarColor.GREEN, BarStyle.SOLID);
            bossBar.addPlayer(actor);
            bossBar.setProgress(0.0);

            int timeLeft = 5;

            BukkitRunnable task = new BukkitRunnable() {
                private int ticksLeft = 20 * timeLeft;
                @Override
                public void run() {
                    if (target != null) {
                        Location senderLocation = actor.getLocation();
                        Location targetLocation = target.getLocation();
                        double distance = senderLocation.distance(targetLocation);
                        distanceinBlocks.put(actor.getUniqueId(), distance);
                    }
                    if (ticksLeft <= 0) {
                        bossBar.removePlayer(actor);
                        handleInteraction(actor, target);
                        plugin.rightclickCount.put(actor.getUniqueId(), 0);
                        this.cancel();
                    } else {
                        ItemStack item = actor.getInventory().getItemInMainHand();
                        if (item == null || !item.hasItemMeta() || item.getType() != Material.CLOCK
                            || (target != null && actor != target && distanceinBlocks.get(actor.getUniqueId()) > 3)) {
                            plugin.rightclickCount.put(actor.getUniqueId(), 0);
                            bossBar.removePlayer(actor);
                            this.cancel();
                        }
                        ticksLeft--;
                        bossBar.setProgress((double) ticksLeft / (20 * timeLeft));
                    }
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
        }
    }

    /**
     * Phase 5b: applies hypnosis stat side-effects on {@code target} given a
     * pre-resolved hypnosis clock {@code ItemStack}. Bypasses the actor-side
     * UI (mainhand read, boss bar) so a Citizens2 NPC can act as the caster
     * without NPE.
     *
     * <p>Replicates the trigger-add half of {@link #handleInteraction}: reads
     * {@code HypnoTriggerWord} + {@code HypnoType} from the clock's PDC,
     * checks the target's {@code hypnoPermission} + {@code hypnoTriggers}
     * cap, and adds a {@code HypnoTrigger} with expiry =
     * {@code now + Hypno_Duration_Days}.
     *
     * @return true if a trigger was added; false if gated out
     */
    public static boolean applyHypnosis(Player target, ItemStack clock) {
        if (target == null || clock == null) return false;
        if (clock.getType() != Material.CLOCK || !clock.hasItemMeta()) return false;
        ItemMeta meta = clock.getItemMeta();
        if (!meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "hypnosis"),
                PersistentDataType.BYTE)) return false;

        PlayerStats stats = plugin.getPlayerStats(target.getUniqueId());
        if (stats == null) return false;

        if (stats.getHypnoPermission() <= 0) return false;

        String word = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "HypnoTriggerWord"),
                PersistentDataType.STRING);
        String type = meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, "HypnoType"),
                PersistentDataType.STRING);
        if (word == null || word.isEmpty() || type == null || type.isEmpty()) return false;

        Object capObj = plugin.getGlobalConfig().get("Hypno_Max_Triggers");
        int cap = (capObj instanceof Number) ? ((Number) capObj).intValue() : 0;
        stats.cleanExpiredTriggers();
        if (cap > 0 && stats.getHypnoTriggers().size() >= cap) {
            return false;
        }

        Object daysObj = plugin.getGlobalConfig().get("Hypno_Duration_Days");
        long days = (daysObj instanceof Number) ? ((Number) daysObj).longValue() : 3L;
        LocalDateTime expiry = LocalDateTime.now().plusDays(days);

        stats.addHypnoTrigger(new HypnoTrigger(word, type, expiry, null));
        com.storynook.PlayerStatsManagement.SavePlayerStats.savePlayerStats(target);
        return true;
    }

    private static void handleInteraction(Player actor, Player target) {
        if (target == null) target = actor;

        Object hypnoEnabled = plugin.getGlobalConfig().get("Hypno");
        if (!(hypnoEnabled instanceof Boolean) || !(Boolean) hypnoEnabled) return;

        ItemStack item = actor.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (item.getType() != Material.CLOCK
            || !meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "hypnosis"), PersistentDataType.BYTE)) {
            return;
        }

        PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
        if (targetStats == null) return;

        if (targetStats.getHypnoPermission() == 0) {
            actor.sendMessage(ChatColor.RED + "This player has not opted in to hypnosis.");
            return;
        }
        if (targetStats.getHypnoPermission() == 1 && !targetStats.isCaregiver(actor.getUniqueId(), true)) {
            actor.sendMessage(ChatColor.RED + "You are not authorized to hypnotize this player.");
            return;
        }

        Object maxObj = plugin.getGlobalConfig().get("Hypno_Max_Triggers");
        int max = (maxObj instanceof Integer) ? (Integer) maxObj : 0;
        targetStats.cleanExpiredTriggers();
        if (max > 0 && targetStats.getHypnoTriggers().size() >= max) {
            actor.sendMessage(ChatColor.RED + "This player has reached the maximum number of active hypnosis triggers.");
            return;
        }

        String triggerWord = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "HypnoTriggerWord"), PersistentDataType.STRING);
        String triggerType = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "HypnoType"), PersistentDataType.STRING);

        if (triggerWord == null) triggerWord = plugin.getRandomHypnoWord();
        if (triggerType == null) triggerType = "wetting";

        Object durationObj = plugin.getGlobalConfig().get("Hypno_Duration_Days");
        long durationDays = (durationObj instanceof Long) ? (Long) durationObj : 3L;

        targetStats.addHypnoTrigger(new HypnoTrigger(
            triggerWord, triggerType,
            LocalDateTime.now().plusDays(durationDays),
            actor.getUniqueId().toString()));

        decrementItem(actor, item);
    }

    private static void decrementItem(Player actor, ItemStack item) {
        int currentAmount = item.getAmount();
        if (currentAmount > 1) {
            item.setAmount(currentAmount - 1);
        } else {
            actor.getInventory().setItemInMainHand(null);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (plugin.VentureChat) return;
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> fireTriggers(message));
    }

    public static void fireTriggers(String message) {
        Object durationObj = plugin.getGlobalConfig().get("Hypno_Duration_Days");
        long durationDays = (durationObj instanceof Long) ? (Long) durationObj : 3L;

        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerStats targetStats = plugin.getPlayerStats(target.getUniqueId());
            if (targetStats == null) continue;
            if (targetStats.getHypnoPermission() == 0) continue;

            targetStats.cleanExpiredTriggers();

            boolean wettingFired = false;
            boolean messingFired = false;

            for (HypnoTrigger trigger : new ArrayList<>(targetStats.getHypnoTriggers())) {
                if (message.toLowerCase().contains(trigger.getWord().toLowerCase())) {
                    trigger.setExpiry(LocalDateTime.now().plusDays(durationDays));

                    String messageType = (Math.random() < 0.2) ? "SILENT"
                        : "wetting".equals(trigger.getType()) ? "Hypno_Wetting" : "Hypno_Messing";

                    if ("wetting".equals(trigger.getType()) && !wettingFired) {
                        HandleAccident.handleAccident(true, target, true, messageType);
                        wettingFired = true;
                    } else if ("messing".equals(trigger.getType()) && !messingFired) {
                        HandleAccident.handleAccident(false, target, true, messageType);
                        messingFired = true;
                    }
                }
            }
        }
    }
}
