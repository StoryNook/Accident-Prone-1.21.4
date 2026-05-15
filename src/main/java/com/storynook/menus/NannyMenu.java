package com.storynook.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import com.storynook.Plugin;
import com.storynook.nanny.BehaviorScoreboard;
import com.storynook.nanny.Capability;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyManager;

import net.md_5.bungee.api.ChatColor;

public class NannyMenu implements Listener {

    private static final String TITLE_GENERAL  = "Nanny Settings";
    private static final String TITLE_WARDS    = "Nanny Wards";
    private static final String TITLE_BEHAVIOR = "Nanny Behavior";
    private static final String TITLE_SUPPLIES = "Nanny Supplies";
    private static final String TITLE_ADVANCED = "Nanny Advanced";

    private final Plugin plugin;

    // Per-player paging for wards tab
    private static final Map<UUID, Integer> wardsPage = new HashMap<>();
    // Maps player-head display name -> target UUID for wards tab clicks
    private static final Map<String, UUID> wardHeadMap = new HashMap<>();

    public NannyMenu(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Visual helpers ----------

    private static final String BULLET_ON  = ChatColor.GREEN + "▶ ";
    private static final String BULLET_OFF = ChatColor.DARK_GRAY + "• ";

    private static ItemStack glow(ItemStack item) {
        ItemMeta m = item.getItemMeta();
        if (m == null) return item;
        m.addEnchant(Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(m);
        return item;
    }

    private static NamespacedKey capKey(Plugin plugin) {
        return new NamespacedKey(plugin, "nanny_capability");
    }

    private static void tagCapability(ItemStack item, Capability cap, Plugin plugin) {
        ItemMeta m = item.getItemMeta();
        if (m == null) return;
        m.getPersistentDataContainer().set(capKey(plugin), PersistentDataType.STRING, cap.name());
        item.setItemMeta(m);
    }

    private static Capability readCapability(ItemStack item, Plugin plugin) {
        if (item == null) return null;
        ItemMeta m = item.getItemMeta();
        if (m == null) return null;
        String stored = m.getPersistentDataContainer().get(capKey(plugin), PersistentDataType.STRING);
        if (stored == null) return null;
        try { return Capability.valueOf(stored); }
        catch (IllegalArgumentException e) { return null; }
    }

    // Friendly title + bullet-list description per Capability (player-facing).
    private static final Map<Capability, String[]> CAP_INFO = new EnumMap<>(Capability.class);
    static {
        CAP_INFO.put(Capability.POTTY_REMINDERS, new String[] {
            "Potty Reminders",
            "Sends in-chat reminders when a ward's bladder",
            "or bowels get high.",
            "Heads-up only - never forces an action."
        });
        CAP_INFO.put(Capability.ARMOR_LOCK, new String[] {
            "Armor Lock",
            "Prevents the ward from removing their pants",
            "or diaper armor on their own.",
            "Nanny / caregiver must unlock to change them."
        });
        CAP_INFO.put(Capability.CRIB_PLACEMENT, new String[] {
            "Crib Placement",
            "When a ward gets tired the Nanny walks them",
            "to the nearest 'Crib' armor stand and seats them.",
            "Auto-disables once the ward sleeps it off."
        });
        CAP_INFO.put(Capability.BLOCK_CAREGIVERS, new String[] {
            "Block Other Caregivers",
            "Other players' caregiver actions are denied",
            "on this Nanny's wards.",
            "Useful when you want exclusive control."
        });
        CAP_INFO.put(Capability.FORCE_FEED_LAXATIVE, new String[] {
            "Force-Feed Laxative",
            "Nanny may give a ward a laxative when their",
            "bowels stay too low for too long.",
            "Bowels feature must be enabled."
        });
        CAP_INFO.put(Capability.BINDING_LEGGINGS, new String[] {
            "Binding Leggings",
            "Nanny can craft / equip cursed binding leggings",
            "that redirect accidents back to the wearer.",
            "Requires the Binding Diapers global flag."
        });
        CAP_INFO.put(Capability.LEASH_WARD, new String[] {
            "Leash Ward",
            "Nanny can attach a lead to a ward and walk",
            "them around the world.",
            "Ward cannot detach the lead themselves."
        });
        CAP_INFO.put(Capability.HYPNOSIS_USE, new String[] {
            "Hypnosis Use",
            "Nanny can use the hypnosis clock on a ward",
            "to apply trigger words.",
            "Requires the Hypnosis global flag."
        });
        CAP_INFO.put(Capability.ROOM_LOCKDOWN, new String[] {
            "Room Lockdown",
            "Nanny can mark a room and prevent the ward",
            "from leaving the area until released.",
            "Cancels on disconnect / Nanny death."
        });
        CAP_INFO.put(Capability.EVIL_CRAFTING, new String[] {
            "Evil Crafting",
            "Unlocks the cursed crafting recipes",
            "(binding diapers, etc).",
            "Only relevant if Evil supplies are enabled."
        });
        CAP_INFO.put(Capability.DIAPER_PUNISHMENT, new String[] {
            "Diaper Punishment",
            "Lock the ward into a binding diaper, block their",
            "toilet use for 1-30 Minecraft days. 3 violations",
            "escalate to cursed indestructible pants."
        });
        CAP_INFO.put(Capability.BASIC_CARE, new String[] {
            "Basic Care",
            "Always-on baseline behavior - feeding,",
            "hydrating, comforting.",
            "Cannot be disabled."
        });
    }

    private static String[] capInfo(Capability cap) {
        String[] info = CAP_INFO.get(cap);
        if (info != null) return info;
        return new String[] { cap.name(), ChatColor.GRAY + "(no description available)" };
    }

    // Mood tier descriptions surfaced on the wool buttons.
    private static String[] moodDescription(NannyData.MoodTier tier) {
        switch (tier) {
            case SWEET:  return new String[] {
                "Permissive - reminders only, no enforcement.",
                "Best for casual / story play."
            };
            case CARING: return new String[] {
                "Default. Light reminders, no force.",
                "Standard caregiver behavior."
            };
            case STRICT: return new String[] {
                "Enforces armor lock + crib placement.",
                "Tired wards get walked to a crib."
            };
            case WARDEN: return new String[] {
                "Full enforcement: blocks other caregivers,",
                "force-feeds laxatives, leashes, room lockdown."
            };
            case CUSTOM: return new String[] {
                "Pick exactly which capabilities are on",
                "in the Advanced tab."
            };
            default: return new String[] { "" };
        }
    }

    // ---------- Entry point ----------

    public static void open(Player player, Plugin plugin) {
        openGeneral(player, plugin);
    }

    private static NannyData getOwnedNanny(Player player, Plugin plugin) {
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return null;
        return mgr.getNannyForOwner(player.getUniqueId());
    }

    // ---------- General tab ----------

    public static void openGeneral(Player player, Plugin plugin) {
        Inventory menu = Bukkit.createInventory(player, 9 * 3, TITLE_GENERAL);
        NannyData data = getOwnedNanny(player, plugin);

        if (data == null) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nm = none.getItemMeta();
            if (nm != null) {
                nm.setDisplayName(ChatColor.RED + "No Nanny");
                nm.setLore(Arrays.asList(
                    ChatColor.GRAY + "You don't own a Nanny.",
                    ChatColor.GRAY + "Ask an admin for a Nanny Egg."
                ));
                none.setItemMeta(nm);
            }
            menu.setItem(13, none);
            placeTabs(menu, "general");
            player.openInventory(menu);
            return;
        }

        ItemStack name = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = name.getItemMeta();
        if (nameMeta != null) {
            nameMeta.setDisplayName(ChatColor.AQUA + "Nanny Name");
            nameMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + data.getName(),
                ChatColor.YELLOW + "Click to rename"
            ));
            name.setItemMeta(nameMeta);
        }

        ItemStack skin = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta skinMeta = skin.getItemMeta();
        if (skinMeta != null) {
            String shown = (data.getSkinUrl() == null || data.getSkinUrl().isEmpty())
                    ? "(default)"
                    : data.getSkinUrl();
            skinMeta.setDisplayName(ChatColor.AQUA + "Skin");
            skinMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Copies another player's skin by name.",
                ChatColor.GRAY + "Current: " + ChatColor.WHITE + shown,
                ChatColor.YELLOW + "Click to set"
            ));
            skin.setItemMeta(skinMeta);
        }

        boolean following = data.isFollowMode();
        ItemStack follow = new ItemStack(following ? Material.LEAD : Material.STRING);
        ItemMeta followMeta = follow.getItemMeta();
        if (followMeta != null) {
            followMeta.setDisplayName((following ? ChatColor.GREEN : ChatColor.GRAY) + "Follow Mode");
            followMeta.setLore(Arrays.asList(
                following ? ChatColor.GREEN + "✔ Following you" : ChatColor.GRAY + "✘ Staying put",
                ChatColor.GRAY + "When on, the Nanny walks after you",
                ChatColor.GRAY + "and teleports if you leave the area.",
                ChatColor.YELLOW + "Click to toggle"
            ));
            follow.setItemMeta(followMeta);
        }
        if (following) glow(follow);

        ItemStack home = new ItemStack(Material.RED_BED);
        ItemMeta homeMeta = home.getItemMeta();
        if (homeMeta != null) {
            homeMeta.setDisplayName(ChatColor.AQUA + "Set Home");
            homeMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Sets Nanny's home to your current location.",
                ChatColor.GRAY + "Radius: " + data.getHomeRadius() + " blocks"
            ));
            home.setItemMeta(homeMeta);
        }

        ItemStack summon = new ItemStack(Material.ENDER_PEARL);
        ItemMeta summonMeta = summon.getItemMeta();
        if (summonMeta != null) {
            summonMeta.setDisplayName(ChatColor.AQUA + "Summon");
            summonMeta.setLore(Arrays.asList(ChatColor.GRAY + "Teleport Nanny to you."));
            summon.setItemMeta(summonMeta);
        }

        menu.setItem(10, name);
        menu.setItem(12, skin);
        menu.setItem(14, follow);
        menu.setItem(16, home);
        menu.setItem(22, summon);

        // Behavior history info item (bottom-right of General tab)
        {
            ItemStack histItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta hm = histItem.getItemMeta();
            if (hm != null) {
                hm.setDisplayName(ChatColor.AQUA + "Behavior History");
                java.util.List<String> lore = new java.util.ArrayList<>();
                BehaviorScoreboard sb = plugin.getNannyManager() == null ? null
                        : plugin.getNannyManager().getBehaviorScoreboard();
                java.util.List<UUID> wardsAndOwner = new java.util.ArrayList<>(data.getWards());
                if (!wardsAndOwner.contains(data.getOwnerUUID())) wardsAndOwner.add(data.getOwnerUUID());
                if (sb == null || wardsAndOwner.isEmpty()) {
                    lore.add(ChatColor.GRAY + "(no wards yet)");
                } else {
                    for (UUID w : wardsAndOwner) {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(w);
                        String pname = op.getName() == null ? w.toString().substring(0, 8) : op.getName();
                        int score = sb.getScore(data, w);
                        int streak = sb.getStreak(data, w);
                        lore.add(ChatColor.WHITE + pname + ChatColor.GRAY + ": "
                                + (score < 0 ? ChatColor.RED : ChatColor.GREEN) + "score=" + score
                                + ChatColor.GRAY + " streak=" + streak);
                    }
                }
                hm.setLore(lore);
                histItem.setItemMeta(hm);
            }
            menu.setItem(26, histItem);
        }

        placeTabs(menu, "general");

        player.openInventory(menu);
    }

    // ---------- Wards tab ----------

    public static void openWards(Player player, Plugin plugin, int page) {
        Inventory menu = Bukkit.createInventory(player, 9 * 6, TITLE_WARDS);
        NannyData data = getOwnedNanny(player, plugin);

        UUID playerUUID = player.getUniqueId();
        wardsPage.put(playerUUID, page);
        placeTabs(menu, "wards");

        if (data == null) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nm = none.getItemMeta();
            if (nm != null) {
                nm.setDisplayName(ChatColor.RED + "No Nanny");
                none.setItemMeta(nm);
            }
            menu.setItem(22, none);
            player.openInventory(menu);
            return;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player);

        int perPage = 36; // slots 9..44
        int start = page * perPage;
        int end = Math.min(start + perPage, onlinePlayers.size());

        int slot = 9;
        for (int i = start; i < end; i++) {
            Player other = onlinePlayers.get(i);
            UUID otherUUID = other.getUniqueId();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta hm = (SkullMeta) head.getItemMeta();
            boolean isWard = data.getWards().contains(otherUUID);
            if (hm != null) {
                hm.setOwningPlayer(other);
                hm.setDisplayName((isWard ? ChatColor.GREEN : ChatColor.GRAY) + other.getName());
                hm.setLore(Arrays.asList(
                    isWard ? ChatColor.GREEN + "✔ Ward of this Nanny"
                           : ChatColor.GRAY + "✘ Not a ward",
                    ChatColor.YELLOW + "Click to toggle"
                ));
                head.setItemMeta(hm);
            }
            if (isWard) glow(head);
            menu.setItem(slot++, head);
            wardHeadMap.put(other.getName(), otherUUID);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta pm = prev.getItemMeta();
            if (pm != null) { pm.setDisplayName("Previous Page"); prev.setItemMeta(pm); }
            menu.setItem(45, prev);
        }
        if (end < onlinePlayers.size()) {
            ItemStack next = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta nm = next.getItemMeta();
            if (nm != null) { nm.setDisplayName("Next Page"); next.setItemMeta(nm); }
            menu.setItem(53, next);
        }

        player.openInventory(menu);
    }

    // ---------- Behavior tab ----------

    public static void openBehavior(Player player, Plugin plugin) {
        Inventory menu = Bukkit.createInventory(player, 9 * 4, TITLE_BEHAVIOR);
        NannyData data = getOwnedNanny(player, plugin);
        placeTabs(menu, "behavior");

        if (data == null) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nm = none.getItemMeta();
            if (nm != null) {
                nm.setDisplayName(ChatColor.RED + "No Nanny");
                none.setItemMeta(nm);
            }
            menu.setItem(13, none);
            player.openInventory(menu);
            return;
        }

        // Mood tier display: 5 slots, current selection highlighted
        NannyData.MoodTier current = data.getMoodTier();
        menu.setItem(10, moodItem(NannyData.MoodTier.SWEET,  Material.PINK_WOOL,    current));
        menu.setItem(11, moodItem(NannyData.MoodTier.CARING, Material.LIGHT_BLUE_WOOL, current));
        menu.setItem(12, moodItem(NannyData.MoodTier.STRICT, Material.ORANGE_WOOL,  current));
        menu.setItem(13, moodItem(NannyData.MoodTier.WARDEN, Material.RED_WOOL,     current));
        menu.setItem(14, moodItem(NannyData.MoodTier.CUSTOM, Material.PURPLE_WOOL,  current));

        // Chat enabled toggle
        boolean chatOn = data.isChatEnabled();
        ItemStack chat = new ItemStack(Material.PAPER);
        ItemMeta chatMeta = chat.getItemMeta();
        if (chatMeta != null) {
            chatMeta.setDisplayName((chatOn ? ChatColor.GREEN : ChatColor.GRAY) + "Chat");
            chatMeta.setLore(Arrays.asList(
                chatOn ? ChatColor.GREEN + "✔ Enabled" : ChatColor.GRAY + "✘ Disabled",
                ChatColor.GRAY + "When off, the Nanny will not speak",
                ChatColor.GRAY + "or respond to messages.",
                ChatColor.YELLOW + "Click to toggle"
            ));
            chat.setItemMeta(chatMeta);
        }
        if (chatOn) glow(chat);

        // Chat respond-to cycle (bullet list with current marked)
        NannyData.ChatRespondTo rt = data.getChatRespondTo();
        ItemStack respondTo = new ItemStack(Material.OAK_SIGN);
        ItemMeta rtMeta = respondTo.getItemMeta();
        if (rtMeta != null) {
            rtMeta.setDisplayName(ChatColor.AQUA + "Chat Scope");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Who the Nanny will reply to:");
            lore.add((rt == NannyData.ChatRespondTo.OWNER  ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "OWNER " + ChatColor.GRAY + "- only you");
            lore.add((rt == NannyData.ChatRespondTo.LISTED ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "LISTED " + ChatColor.GRAY + "- you + your wards");
            lore.add((rt == NannyData.ChatRespondTo.ANYONE ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "ANYONE " + ChatColor.GRAY + "- any nearby player");
            lore.add(ChatColor.YELLOW + "Click to cycle");
            rtMeta.setLore(lore);
            respondTo.setItemMeta(rtMeta);
        }

        menu.setItem(21, chat);
        menu.setItem(23, respondTo);

        boolean seekOn = data.isSeekEnabled();
        ItemStack seek = new ItemStack(Material.COMPASS);
        ItemMeta seekMeta = seek.getItemMeta();
        if (seekMeta != null) {
            seekMeta.setDisplayName((seekOn ? ChatColor.GREEN : ChatColor.GRAY) + "Hide and Seek");
            seekMeta.setLore(Arrays.asList(
                seekOn ? ChatColor.GREEN + "✔ Enabled" : ChatColor.GRAY + "✘ Disabled",
                ChatColor.GRAY + "When you log in, the Nanny",
                ChatColor.GRAY + "walks toward your last location.",
                ChatColor.YELLOW + "Click to toggle"
            ));
            seek.setItemMeta(seekMeta);
        }
        if (seekOn) glow(seek);
        menu.setItem(25, seek);

        com.storynook.nanny.MembershipProvider provider =
                plugin.getNannyManager() != null
                        ? plugin.getNannyManager().getMembershipProvider() : null;
        boolean aiUnlocked = provider != null
                && provider.isUnlocked(player.getUniqueId());

        NannyData.ChatTier currentTier = data.getChatTier();
        ItemStack tier = new ItemStack(aiUnlocked ? Material.WRITABLE_BOOK : Material.BOOK);
        ItemMeta tierMeta = tier.getItemMeta();
        if (tierMeta != null) {
            tierMeta.setDisplayName(ChatColor.AQUA + "Chat Tier");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "How the Nanny generates replies:");
            lore.add((currentTier == NannyData.ChatTier.BASIC ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "BASIC " + ChatColor.GRAY + "- canned messages");
            lore.add((currentTier == NannyData.ChatTier.AI ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "AI    " + ChatColor.GRAY + "- generated by an LLM");
            if (!aiUnlocked && currentTier == NannyData.ChatTier.AI) {
                lore.add(ChatColor.RED + "AI tier requires membership");
                lore.add(ChatColor.GRAY + "(falling back to BASIC responses)");
            } else if (!aiUnlocked) {
                lore.add(ChatColor.RED + "AI tier requires membership");
            } else {
                lore.add(ChatColor.YELLOW + "Click to cycle");
            }
            tierMeta.setLore(lore);
            tier.setItemMeta(tierMeta);
        }
        if (currentTier == NannyData.ChatTier.AI && aiUnlocked) glow(tier);
        menu.setItem(19, tier);

        // Armor Lock (slot 27)
        boolean armorLockAllowed = com.storynook.nanny.NannyPolicy.allows(data, Capability.ARMOR_LOCK);
        boolean anyLocked = false;
        for (Boolean b : data.getLockedArmor().values()) {
            if (Boolean.TRUE.equals(b)) { anyLocked = true; break; }
        }
        ItemStack armorLockItem = new ItemStack(Material.IRON_BARS);
        ItemMeta alm = armorLockItem.getItemMeta();
        if (alm != null) {
            alm.setDisplayName((anyLocked ? ChatColor.GREEN : ChatColor.GRAY) + "Armor Lock");
            List<String> lore = new ArrayList<>();
            lore.add(anyLocked ? ChatColor.GREEN + "✔ Active on at least one ward"
                               : ChatColor.GRAY + "✘ No wards locked");
            lore.add(ChatColor.GRAY + "Wards can't take off pants / diaper");
            lore.add(ChatColor.GRAY + "armor on their own.");
            if (!armorLockAllowed) {
                lore.add(ChatColor.RED + "Requires "
                        + com.storynook.nanny.NannyPolicy.minTier(Capability.ARMOR_LOCK).name()
                        + " mood tier");
            } else {
                lore.add(ChatColor.YELLOW + "Click to toggle on all current wards");
            }
            alm.setLore(lore);
            armorLockItem.setItemMeta(alm);
        }
        if (anyLocked) glow(armorLockItem);
        menu.setItem(27, armorLockItem);

        // Crib Placement (slot 29) — informational, governed by mood tier
        boolean cribAllowed = com.storynook.nanny.NannyPolicy.allows(data, Capability.CRIB_PLACEMENT);
        ItemStack cribItem = new ItemStack(Material.OAK_FENCE);
        ItemMeta cm = cribItem.getItemMeta();
        if (cm != null) {
            cm.setDisplayName((cribAllowed ? ChatColor.GREEN : ChatColor.GRAY) + "Crib Placement");
            List<String> lore = new ArrayList<>();
            lore.add(cribAllowed ? ChatColor.GREEN + "✔ Active"
                                 : ChatColor.GRAY + "✘ Inactive");
            lore.add(ChatColor.GRAY + "Tired wards are walked to the");
            lore.add(ChatColor.GRAY + "nearest 'Crib' armor stand.");
            if (!cribAllowed) {
                lore.add(ChatColor.RED + "Requires "
                        + com.storynook.nanny.NannyPolicy.minTier(Capability.CRIB_PLACEMENT).name()
                        + " mood tier");
            }
            cm.setLore(lore);
            cribItem.setItemMeta(cm);
        }
        if (cribAllowed) glow(cribItem);
        menu.setItem(29, cribItem);

        // Block Other Caregivers (slot 31)
        boolean blockOn = data.isBlockOtherCaregivers();
        NannyData.MoodTier tierMin = com.storynook.nanny.NannyPolicy.minTier(Capability.BLOCK_CAREGIVERS);
        boolean blockTierMet = data.getMoodTier().ordinal() >= tierMin.ordinal()
                || data.getMoodTier() == NannyData.MoodTier.CUSTOM;
        ItemStack blockItem = new ItemStack(Material.BARRIER);
        ItemMeta bm = blockItem.getItemMeta();
        if (bm != null) {
            bm.setDisplayName((blockOn && blockTierMet ? ChatColor.GREEN : ChatColor.GRAY)
                    + "Block Other Caregivers");
            List<String> lore = new ArrayList<>();
            lore.add(blockOn ? ChatColor.GREEN + "✔ Enabled"
                             : ChatColor.GRAY + "✘ Disabled");
            lore.add(ChatColor.GRAY + "Other players' caregiver actions");
            lore.add(ChatColor.GRAY + "are denied on this Nanny's wards.");
            if (!blockTierMet) {
                lore.add(ChatColor.RED + "Requires " + tierMin.name() + " mood tier to take effect");
            } else {
                lore.add(ChatColor.YELLOW + "Click to toggle");
            }
            bm.setLore(lore);
            blockItem.setItemMeta(bm);
        }
        if (blockOn && blockTierMet) glow(blockItem);
        menu.setItem(31, blockItem);

        player.openInventory(menu);
    }

    private static ItemStack moodItem(NannyData.MoodTier tier, Material mat, NannyData.MoodTier current) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean selected = tier == current;
            meta.setDisplayName((selected ? ChatColor.GREEN : ChatColor.GRAY) + tier.name());
            List<String> lore = new ArrayList<>();
            lore.add(selected ? ChatColor.GREEN + "▶ Selected" : ChatColor.GRAY + "Click to select");
            for (String line : moodDescription(tier)) {
                lore.add(ChatColor.GRAY + line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        if (tier == current) glow(item);
        return item;
    }

    // ---------- Tab header items ----------

    private static void placeTabs(Inventory menu, String activeTab) {
        ItemStack general  = tabItem(Material.BOOK,        "General",  activeTab.equals("general"));
        ItemStack wards    = tabItem(Material.PLAYER_HEAD, "Wards",    activeTab.equals("wards"));
        ItemStack beh      = tabItem(Material.CLOCK,       "Behavior", activeTab.equals("behavior"));
        ItemStack supplies = tabItem(Material.CHEST,       "Supplies", activeTab.equals("supplies"));
        ItemStack adv      = tabItem(Material.NETHER_STAR, "Advanced", activeTab.equals("advanced"));
        menu.setItem(0, general);
        menu.setItem(1, wards);
        menu.setItem(2, beh);
        menu.setItem(3, supplies);
        menu.setItem(4, adv);
    }

    // ---------- Supplies tab ----------

    public static void openSupplies(Player player, Plugin plugin) {
        Inventory menu = Bukkit.createInventory(player, 9 * 4, TITLE_SUPPLIES);
        NannyData data = getOwnedNanny(player, plugin);
        placeTabs(menu, "supplies");

        if (data == null) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nm = none.getItemMeta();
            if (nm != null) {
                nm.setDisplayName(ChatColor.RED + "No Nanny");
                none.setItemMeta(nm);
            }
            menu.setItem(13, none);
            player.openInventory(menu);
            return;
        }

        menu.setItem(9, suppliesChestModeItem(data));
        menu.setItem(11, suppliesCraftingModeItem(data));
        menu.setItem(13, suppliesThresholdItem("Change Threshold", data.getChangeThreshold(),
                ChatColor.GRAY + "Diaper % at which Nanny acts"));
        menu.setItem(15, suppliesThresholdItem("Feed Threshold", data.getFeedThreshold(),
                ChatColor.GRAY + "Hunger level (0-20) below which to feed"));
        menu.setItem(17, suppliesThresholdItem("Hydration Threshold", data.getHydrationThreshold(),
                ChatColor.GRAY + "Hydration (0-100) below which to hydrate"));

        ItemStack[] inv = data.getPersonalInventory();
        if (inv != null) {
            for (int i = 0; i < 18 && i < inv.length; i++) {
                menu.setItem(18 + i, inv[i]);
            }
        }
        player.openInventory(menu);
    }

    public static void openAdvanced(Player player, Plugin plugin) {
        Inventory menu = Bukkit.createInventory(player, 9 * 4, TITLE_ADVANCED);
        NannyData data = getOwnedNanny(player, plugin);
        placeTabs(menu, "advanced");

        if (data == null) {
            ItemStack none = new ItemStack(Material.BARRIER);
            ItemMeta nm = none.getItemMeta();
            if (nm != null) { nm.setDisplayName(ChatColor.RED + "No Nanny"); none.setItemMeta(nm); }
            menu.setItem(13, none);
            player.openInventory(menu);
            return;
        }

        if (data.getMoodTier() != NannyData.MoodTier.CUSTOM) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta im = info.getItemMeta();
            if (im != null) {
                im.setDisplayName(ChatColor.YELLOW + "CUSTOM tier required");
                im.setLore(Arrays.asList(
                    ChatColor.GRAY + "Switch mood tier to CUSTOM in the Behavior tab",
                    ChatColor.GRAY + "to enable per-capability toggles."));
                info.setItemMeta(im);
            }
            menu.setItem(13, info);
            player.openInventory(menu);
            return;
        }

        // Custom Tone cycler — slot 13 (centered, above the capability rows)
        ItemStack toneItem = new ItemStack(Material.BOOK);
        ItemMeta tm = toneItem.getItemMeta();
        if (tm != null) {
            tm.setDisplayName(ChatColor.AQUA + "Custom Tone");
            java.util.List<String> tlore = new java.util.ArrayList<>();
            tlore.add(ChatColor.GRAY + "Voice tier for this CUSTOM Nanny.");
            tlore.add(ChatColor.GRAY + "Current: " + ChatColor.WHITE + data.getCustomTone().name());
            tlore.add(ChatColor.YELLOW + "Click to cycle SWEET → CARING → STRICT → WARDEN");
            tm.setLore(tlore);
            toneItem.setItemMeta(tm);
        }
        menu.setItem(13, toneItem);

        Capability[] caps = {
            Capability.POTTY_REMINDERS,
            Capability.ARMOR_LOCK,
            Capability.CRIB_PLACEMENT,
            Capability.BLOCK_CAREGIVERS,
            Capability.FORCE_FEED_LAXATIVE,
            Capability.BINDING_LEGGINGS,
            Capability.LEASH_WARD,
            Capability.HYPNOSIS_USE,
            Capability.ROOM_LOCKDOWN,
            Capability.EVIL_CRAFTING,
            Capability.DIAPER_PUNISHMENT
        };

        int slot = 9;
        for (Capability cap : caps) {
            boolean on = Boolean.TRUE.equals(data.getCustomSettings().get(cap.name()));
            ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta m = item.getItemMeta();
            if (m != null) {
                String[] info = capInfo(cap);
                m.setDisplayName((on ? ChatColor.GREEN : ChatColor.GRAY) + info[0]);
                List<String> lore = new ArrayList<>();
                lore.add(on ? ChatColor.GREEN + "✔ Enabled" : ChatColor.GRAY + "✘ Disabled");
                for (int i = 1; i < info.length; i++) {
                    lore.add(ChatColor.GRAY + info[i]);
                }
                lore.add(ChatColor.YELLOW + "Click to toggle");
                m.setLore(lore);
                item.setItemMeta(m);
            }
            tagCapability(item, cap, plugin);
            if (on) glow(item);
            menu.setItem(slot, item);
            slot++;
            if (slot == 13) slot = 14;
            if (slot == 18) slot = 19;
        }
        player.openInventory(menu);
    }

    private static ItemStack suppliesChestModeItem(NannyData data) {
        NannyData.ChestMode mode = data.getChestMode();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Chest Mode");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Where the Nanny pulls supplies from:");
            lore.add((mode == NannyData.ChestMode.INVENTORY_ONLY ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "INVENTORY_ONLY " + ChatColor.GRAY + "- only the Nanny's own bag");
            lore.add((mode == NannyData.ChestMode.SELECTED ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "SELECTED " + ChatColor.GRAY + "- bag + chests you've tagged");
            lore.add((mode == NannyData.ChestMode.ALL ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "ALL " + ChatColor.GRAY + "- bag + every nearby chest");
            lore.add(ChatColor.YELLOW + "Click to cycle");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        glow(item);
        return item;
    }

    private static ItemStack suppliesCraftingModeItem(NannyData data) {
        NannyData.CraftingMode mode = data.getCraftingMode();
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Crafting Mode");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "What recipes the Nanny may craft:");
            lore.add((mode == NannyData.CraftingMode.NONE ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "NONE  " + ChatColor.GRAY + "- doesn't craft anything");
            lore.add((mode == NannyData.CraftingMode.BASIC ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "BASIC " + ChatColor.GRAY + "- core recipes only");
            lore.add((mode == NannyData.CraftingMode.ALL ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "ALL   " + ChatColor.GRAY + "- core + extended recipes");
            lore.add((mode == NannyData.CraftingMode.EVIL ? BULLET_ON : BULLET_OFF)
                    + ChatColor.WHITE + "EVIL  " + ChatColor.GRAY + "- includes cursed recipes");
            lore.add(ChatColor.YELLOW + "Click to cycle");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        glow(item);
        return item;
    }

    private static ItemStack suppliesThresholdItem(String name, int value, String desc) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            meta.setLore(Arrays.asList(
                "Current: " + ChatColor.YELLOW + value,
                desc,
                ChatColor.YELLOW + "Click to set"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack tabItem(Material mat, String label, boolean active) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((active ? ChatColor.GREEN : ChatColor.GRAY) + "Tab: " + label);
            if (active) {
                meta.setLore(Arrays.asList(ChatColor.GREEN + "▶ Currently viewing"));
            }
            item.setItemMeta(meta);
        }
        if (active) glow(item);
        return item;
    }

    // ---------- Click handler ----------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(TITLE_GENERAL) && !title.equals(TITLE_WARDS)
                && !title.equals(TITLE_BEHAVIOR) && !title.equals(TITLE_SUPPLIES)
                && !title.equals(TITLE_ADVANCED)) {
            return;
        }

        // Personal-inventory editor slots (18-35) and player's own inventory (36+) on the Supplies tab —
        // allow item movement. Slots 0-17 (tabs, mode buttons, threshold papers) stay click-locked.
        if (title.equals(TITLE_SUPPLIES)) {
            if (event.getRawSlot() >= 18) {
                return;
            }
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        Player player = (Player) event.getWhoClicked();
        ItemMeta meta = clicked.getItemMeta();
        String displayName = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";

        // Tab navigation (works from any tab)
        if (displayName.equals("Tab: General"))  { openGeneral(player, plugin); return; }
        if (displayName.equals("Tab: Wards"))    { openWards(player, plugin, 0); return; }
        if (displayName.equals("Tab: Behavior")) { openBehavior(player, plugin); return; }
        if (displayName.equals("Tab: Supplies")) { openSupplies(player, plugin); return; }
        if (displayName.equals("Tab: Advanced")) { openAdvanced(player, plugin); return; }

        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return;
        NannyData data = mgr.getNannyForOwner(player.getUniqueId());
        if (data == null) return;
        UUID nannyUUID = data.getNannyUUID();

        if (title.equals(TITLE_GENERAL)) {
            handleGeneralClick(event, player, data, nannyUUID, mgr, displayName);
        } else if (title.equals(TITLE_WARDS)) {
            handleWardsClick(event, player, data, nannyUUID, mgr, clicked, displayName);
        } else if (title.equals(TITLE_BEHAVIOR)) {
            handleBehaviorClick(event, player, data, nannyUUID, mgr, displayName);
        } else if (title.equals(TITLE_SUPPLIES)) {
            handleSuppliesClick(event, player, data, nannyUUID, mgr);
        } else if (title.equals(TITLE_ADVANCED)) {
            handleAdvancedClick(event, player, data, nannyUUID, mgr);
        }
    }

    private void handleAdvancedClick(InventoryClickEvent event, Player player, NannyData data,
                                     UUID nannyUUID, NannyManager mgr) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        Material type = clicked.getType();

        if (type == Material.BOOK) {
            // Custom Tone cycler
            NannyData.MoodTier[] order = {
                NannyData.MoodTier.SWEET,
                NannyData.MoodTier.CARING,
                NannyData.MoodTier.STRICT,
                NannyData.MoodTier.WARDEN
            };
            NannyData.MoodTier current = data.getCustomTone();
            int next = 0;
            for (int i = 0; i < order.length; i++) {
                if (order[i] == current) { next = (i + 1) % order.length; break; }
            }
            data.setCustomTone(order[next]);
            data.save(plugin.getDataFolder());
            player.sendMessage(ChatColor.AQUA + "Custom Tone: " + ChatColor.WHITE + order[next].name());
            openAdvanced(player, plugin);
            return;
        }

        if (type != Material.LIME_DYE && type != Material.GRAY_DYE) return;
        Capability cap = readCapability(clicked, plugin);
        if (cap == null) return;
        Boolean cur = data.getCustomSettings().get(cap.name());
        boolean newVal = !Boolean.TRUE.equals(cur);
        data.getCustomSettings().put(cap.name(), newVal);
        data.save(plugin.getDataFolder());
        String label = capInfo(cap)[0];
        player.sendMessage(label + ": " + (newVal ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
        openAdvanced(player, plugin);
    }

    private void handleSuppliesClick(InventoryClickEvent event, Player player, NannyData data,
                                     UUID nannyUUID, NannyManager mgr) {
        Material type = event.getCurrentItem() == null ? Material.AIR : event.getCurrentItem().getType();

        if (type == Material.CHEST) {
            NannyData.ChestMode[] order = NannyData.ChestMode.values();
            int next = (data.getChestMode().ordinal() + 1) % order.length;
            data.setChestMode(order[next]);
            data.save(plugin.getDataFolder());
            openSupplies(player, plugin);
        } else if (type == Material.CRAFTING_TABLE) {
            NannyData.CraftingMode[] order = NannyData.CraftingMode.values();
            int next = (data.getCraftingMode().ordinal() + 1) % order.length;
            data.setCraftingMode(order[next]);
            data.save(plugin.getDataFolder());
            openSupplies(player, plugin);
        } else if (type == Material.PAPER) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            String label = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
            String inputType = null;
            if (label.equals("Change Threshold")) inputType = "nannyChangeThreshold";
            else if (label.equals("Feed Threshold")) inputType = "nannyFeedThreshold";
            else if (label.equals("Hydration Threshold")) inputType = "nannyHydrationThreshold";
            if (inputType == null) return;
            plugin.setAwaitingInput(player.getUniqueId(), inputType);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type the new value in chat:");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(TITLE_SUPPLIES)) return;
        Player player = (Player) event.getPlayer();
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return;
        NannyData data = mgr.getNannyForOwner(player.getUniqueId());
        if (data == null) return;

        Inventory inv = event.getInventory();
        ItemStack[] saved = new ItemStack[18];
        for (int i = 0; i < 18; i++) saved[i] = inv.getItem(18 + i);
        data.setPersonalInventory(saved);
        data.save(plugin.getDataFolder());
    }

    private void handleGeneralClick(InventoryClickEvent event, Player player, NannyData data,
                                    UUID nannyUUID, NannyManager mgr, String displayName) {
        Material type = event.getCurrentItem().getType();

        if (type == Material.NAME_TAG) {
            plugin.setAwaitingInput(player.getUniqueId(), "nannyName");
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type the new Nanny name in chat:");
        } else if (type == Material.PLAYER_HEAD) {
            plugin.setAwaitingInput(player.getUniqueId(), "nannySkinUrl");
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type a player name to copy their skin (or 'default' to reset):");
        } else if (type == Material.LEAD || type == Material.STRING) {
            boolean newFollow = !data.isFollowMode();
            data.setFollowMode(newFollow);
            data.save(plugin.getDataFolder());
            mgr.setFollowMode(nannyUUID, newFollow, player);
            player.sendMessage("Follow mode: " + (newFollow ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
            openGeneral(player, plugin);
        } else if (type == Material.RED_BED) {
            mgr.setHome(nannyUUID, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Nanny home set to your location.");
            openGeneral(player, plugin);
        } else if (type == Material.ENDER_PEARL) {
            mgr.summonToPlayer(nannyUUID, player);
            player.sendMessage(ChatColor.GREEN + "Nanny summoned.");
        }
    }

    private void handleWardsClick(InventoryClickEvent event, Player player, NannyData data,
                                  UUID nannyUUID, NannyManager mgr, ItemStack clicked, String displayName) {
        Material type = clicked.getType();
        int page = wardsPage.getOrDefault(player.getUniqueId(), 0);

        if (type == Material.RED_STAINED_GLASS_PANE) {
            if (page > 0) openWards(player, plugin, page - 1);
        } else if (type == Material.GREEN_STAINED_GLASS_PANE) {
            openWards(player, plugin, page + 1);
        } else if (type == Material.PLAYER_HEAD) {
            UUID targetUUID = wardHeadMap.get(displayName);
            if (targetUUID == null) return;
            if (data.getWards().contains(targetUUID)) {
                mgr.removeWard(nannyUUID, targetUUID);
                player.sendMessage("Removed " + displayName + " as a ward.");
            } else {
                mgr.addWard(nannyUUID, targetUUID);
                player.sendMessage("Added " + displayName + " as a ward.");
            }
            openWards(player, plugin, page);
        }
    }

    private void handleBehaviorClick(InventoryClickEvent event, Player player, NannyData data,
                                     UUID nannyUUID, NannyManager mgr, String displayName) {
        Material type = event.getCurrentItem().getType();

        // Mood tier wool clicks
        for (NannyData.MoodTier tier : NannyData.MoodTier.values()) {
            if (displayName.equals(tier.name())) {
                data.setMoodTier(tier);
                data.save(plugin.getDataFolder());
                player.sendMessage("Mood tier: " + ChatColor.YELLOW + tier.name());
                openBehavior(player, plugin);
                return;
            }
        }

        if (type == Material.PAPER) {
            data.setChatEnabled(!data.isChatEnabled());
            data.save(plugin.getDataFolder());
            player.sendMessage("Chat: " + (data.isChatEnabled() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
            openBehavior(player, plugin);
        } else if (type == Material.OAK_SIGN) {
            NannyData.ChatRespondTo[] order = NannyData.ChatRespondTo.values();
            int next = (data.getChatRespondTo().ordinal() + 1) % order.length;
            data.setChatRespondTo(order[next]);
            data.save(plugin.getDataFolder());
            player.sendMessage("Chat scope: " + ChatColor.YELLOW + order[next].name());
            openBehavior(player, plugin);
        } else if (type == Material.COMPASS) {
            data.setSeekEnabled(!data.isSeekEnabled());
            data.save(plugin.getDataFolder());
            player.sendMessage("Hide and seek: "
                    + (data.isSeekEnabled() ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
            openBehavior(player, plugin);
        } else if (type == Material.WRITABLE_BOOK || type == Material.BOOK) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            String label = meta != null && meta.hasDisplayName()
                    ? ChatColor.stripColor(meta.getDisplayName()) : "";
            if (!"Chat Tier".equals(label)) return;
            NannyData.ChatTier[] order = NannyData.ChatTier.values();
            int next = (data.getChatTier().ordinal() + 1) % order.length;
            data.setChatTier(order[next]);
            data.save(plugin.getDataFolder());
            player.sendMessage("Chat tier: " + ChatColor.YELLOW + order[next].name());
            openBehavior(player, plugin);
        } else if (type == Material.IRON_BARS) {
            if (!com.storynook.nanny.NannyPolicy.allows(data, com.storynook.nanny.Capability.ARMOR_LOCK)) {
                player.sendMessage(ChatColor.RED + "Armor lock requires a stricter mood tier.");
                return;
            }
            boolean anyLocked = false;
            for (Boolean b : data.getLockedArmor().values()) {
                if (Boolean.TRUE.equals(b)) { anyLocked = true; break; }
            }
            boolean newState = !anyLocked;
            data.getLockedArmor().clear();
            for (UUID ward : data.getWards()) data.getLockedArmor().put(ward, newState);
            data.getLockedArmor().put(data.getOwnerUUID(), newState);
            data.save(plugin.getDataFolder());
            player.sendMessage("Armor lock: " + (newState ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
            openBehavior(player, plugin);
        } else if (type == Material.OAK_FENCE) {
            openBehavior(player, plugin);
        } else if (type == Material.BARRIER) {
            data.setBlockOtherCaregivers(!data.isBlockOtherCaregivers());
            data.save(plugin.getDataFolder());
            player.sendMessage("Block other caregivers: " + (data.isBlockOtherCaregivers()
                    ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));
            openBehavior(player, plugin);
        }
    }
}
