package com.storynook.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.storynook.Plugin;
import com.storynook.items.Nanny;
import com.storynook.menus.NannyMenu;
import com.storynook.nanny.NannyData;
import com.storynook.nanny.NannyManager;

import net.md_5.bungee.api.ChatColor;

public class NannyCommand {

    private final Plugin plugin;

    public NannyCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give":     return handleGive(sender, args);
            case "list":     return handleList(sender);
            case "remove":   return handleRemove(sender, args);
            case "info":     return handleInfo(sender, args);
            case "settings": return handleSettings(sender);
            case "sethome":  return handleSetHome(sender);
            case "summon":   return handleSummon(sender);
            case "reload":   return handleReload(sender);
            case "lockroom":   return handleLockRoom(sender);
            case "unlockroom": return handleUnlockRoom(sender);
            case "link":    return handleLink(sender, args);
            case "unlink":  return handleUnlink(sender);
            case "refresh": return handleRefresh(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : Arrays.asList("give", "list", "remove", "info", "settings", "sethome", "summon", "reload", "lockroom", "unlockroom", "link", "unlink", "refresh")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        String sub = args[0].toLowerCase();
        if ((sub.equals("give") || sub.equals("remove") || sub.equals("info")) && args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
        }
        if (sub.equals("link") && args.length == 2) {
            String prefix = args[1].toLowerCase();
            for (String p : java.util.Arrays.asList("patreon", "subscribestar")) {
                if (p.startsWith(prefix)) out.add(p);
            }
        }
        return out;
    }

    // ---------- Subcommand handlers ----------

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accidentprone.nanny.give") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (!plugin.citizensEnabled) {
            sender.sendMessage(ChatColor.RED + "Citizens2 is required for the Nanny system.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /nanny give <player> [name]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        String name = args.length >= 3 ? joinFromIndex(args, 2) : null;
        target.getInventory().addItem(Nanny.createNannyEgg(name));
        sender.sendMessage(ChatColor.GREEN + "Gave a Nanny Egg to " + target.getName() + ".");
        target.sendMessage(ChatColor.GOLD + "You received a Nanny Egg!");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("accidentprone.nanny.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) { sender.sendMessage(ChatColor.RED + "Nanny system not initialised."); return true; }
        if (mgr.getAllNannies().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No Nannies on this server.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Nannies (" + mgr.getAllNannies().size() + "):");
        for (NannyData data : mgr.getAllNannies().values()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(data.getOwnerUUID());
            String ownerName = owner.getName() != null ? owner.getName() : data.getOwnerUUID().toString();
            String status = data.isDormant() ? ChatColor.RED + "dormant" :
                    (mgr.getActiveNannies().containsKey(data.getNannyUUID())
                        ? ChatColor.GREEN + "active"
                        : ChatColor.GRAY + "offline");
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + data.getName()
                    + ChatColor.GRAY + " (owner: " + ownerName + ", wards: " + data.getWards().size()
                    + ", " + status + ChatColor.GRAY + ")");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accidentprone.nanny.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /nanny remove <player>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) { sender.sendMessage(ChatColor.RED + "Nanny system not initialised."); return true; }
        NannyData data = mgr.getNannyForOwner(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + " has no Nanny.");
            return true;
        }
        mgr.deleteNanny(data.getNannyUUID());
        sender.sendMessage(ChatColor.GREEN + "Removed Nanny " + data.getName() + ".");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /nanny info <player>");
            return true;
        }
        UUID lookupUUID;
        String lookupName;
        if (args.length >= 2) {
            if (!sender.hasPermission("accidentprone.nanny.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "No permission to view others.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            lookupUUID = target.getUniqueId();
            lookupName = target.getName() != null ? target.getName() : args[1];
        } else {
            Player p = (Player) sender;
            lookupUUID = p.getUniqueId();
            lookupName = p.getName();
        }
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) { sender.sendMessage(ChatColor.RED + "Nanny system not initialised."); return true; }
        NannyData data = mgr.getNannyForOwner(lookupUUID);
        if (data == null) {
            sender.sendMessage(ChatColor.GRAY + lookupName + " has no Nanny.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "=== " + data.getName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Owner: " + ChatColor.WHITE + lookupName);
        sender.sendMessage(ChatColor.GRAY + "Wards: " + ChatColor.WHITE + data.getWards().size());
        sender.sendMessage(ChatColor.GRAY + "Mood: " + ChatColor.WHITE + data.getMoodTier().name());
        sender.sendMessage(ChatColor.GRAY + "Home: " + ChatColor.WHITE + data.getHomeWorld()
                + " " + (int) data.getHomeX() + "," + (int) data.getHomeY() + "," + (int) data.getHomeZ());
        sender.sendMessage(ChatColor.GRAY + "Dormant: " + ChatColor.WHITE + data.isDormant());
        sender.sendMessage(ChatColor.GRAY + "Active: " + ChatColor.WHITE
                + mgr.getActiveNannies().containsKey(data.getNannyUUID()));
        return true;
    }

    private boolean handleSettings(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        NannyMenu.open((Player) sender, plugin);
        return true;
    }

    private boolean handleSetHome(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Player p = (Player) sender;
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) { p.sendMessage(ChatColor.RED + "Nanny system not initialised."); return true; }
        NannyData data = mgr.getNannyForOwner(p.getUniqueId());
        if (data == null) { p.sendMessage(ChatColor.RED + "You don't own a Nanny."); return true; }
        mgr.setHome(data.getNannyUUID(), p.getLocation());
        p.sendMessage(ChatColor.GREEN + "Nanny home set to your current location.");
        return true;
    }

    private boolean handleSummon(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        Player p = (Player) sender;
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) { p.sendMessage(ChatColor.RED + "Nanny system not initialised."); return true; }
        NannyData data = mgr.getNannyForOwner(p.getUniqueId());
        if (data == null) { p.sendMessage(ChatColor.RED + "You don't own a Nanny."); return true; }
        mgr.summonToPlayer(data.getNannyUUID(), p);
        p.sendMessage(ChatColor.GREEN + "Nanny summoned.");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.isOp() && !sender.hasPermission("accidentprone.nanny.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        plugin.loadGlobalConfig();
        NannyManager mgr = plugin.getNannyManager();
        if (mgr != null) mgr.init();
        sender.sendMessage(ChatColor.GREEN + "Nanny config reloaded.");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Nanny commands:");
        sender.sendMessage(ChatColor.GRAY + "  /nanny give <player> [name]");
        sender.sendMessage(ChatColor.GRAY + "  /nanny list");
        sender.sendMessage(ChatColor.GRAY + "  /nanny remove <player>");
        sender.sendMessage(ChatColor.GRAY + "  /nanny info [player]");
        sender.sendMessage(ChatColor.GRAY + "  /nanny settings");
        sender.sendMessage(ChatColor.GRAY + "  /nanny sethome");
        sender.sendMessage(ChatColor.GRAY + "  /nanny summon");
        sender.sendMessage(ChatColor.GRAY + "  /nanny reload");
    }

    private static String joinFromIndex(String[] arr, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < arr.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * /nanny lockroom — collects every door, trapdoor, and lever within a
     * 16-block cube around the issuing owner into the owner's Nanny's
     * lockedRoomBlocks list. Requires ROOM_LOCKDOWN capability.
     */
    private boolean handleLockRoom(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Players only.");
            return true;
        }
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        com.storynook.nanny.NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Nanny system not initialised.");
            return true;
        }
        com.storynook.nanny.NannyData data = mgr.getNannyForOwner(player.getUniqueId());
        if (data == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "You don't own a Nanny.");
            return true;
        }
        if (!com.storynook.nanny.NannyPolicy.allows(data, com.storynook.nanny.Capability.ROOM_LOCKDOWN)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Room lockdown requires WARDEN mood (or CUSTOM with the toggle on).");
            return true;
        }

        java.util.List<String> blocks = data.getLockedRoomBlocks();
        blocks.clear();
        org.bukkit.Location origin = player.getLocation();
        org.bukkit.World w = origin.getWorld();
        if (w == null) return true;
        int r = 16;
        int hx = origin.getBlockX(), hy = origin.getBlockY(), hz = origin.getBlockZ();
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    org.bukkit.block.Block b = w.getBlockAt(hx + dx, hy + dy, hz + dz);
                    if (isDoorOrLever(b.getType())) {
                        blocks.add(w.getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ());
                        count++;
                    }
                }
            }
        }
        data.save(plugin.getDataFolder());
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Room locked: " + count + " block(s) sealed.");
        return true;
    }

    private boolean handleUnlockRoom(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Players only.");
            return true;
        }
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        com.storynook.nanny.NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return true;
        com.storynook.nanny.NannyData data = mgr.getNannyForOwner(player.getUniqueId());
        if (data == null) {
            player.sendMessage(org.bukkit.ChatColor.RED + "You don't own a Nanny.");
            return true;
        }
        int n = data.getLockedRoomBlocks().size();
        data.getLockedRoomBlocks().clear();
        data.save(plugin.getDataFolder());
        player.sendMessage(org.bukkit.ChatColor.GREEN + "Room unlocked: " + n + " block(s) released.");
        return true;
    }

    private static boolean isDoorOrLever(org.bukkit.Material m) {
        switch (m) {
            case OAK_DOOR: case SPRUCE_DOOR: case BIRCH_DOOR: case JUNGLE_DOOR:
            case ACACIA_DOOR: case DARK_OAK_DOOR: case CRIMSON_DOOR: case WARPED_DOOR:
            case IRON_DOOR:
            case OAK_TRAPDOOR: case SPRUCE_TRAPDOOR: case BIRCH_TRAPDOOR: case JUNGLE_TRAPDOOR:
            case ACACIA_TRAPDOOR: case DARK_OAK_TRAPDOOR: case CRIMSON_TRAPDOOR: case WARPED_TRAPDOOR:
            case IRON_TRAPDOOR:
            case LEVER:
                return true;
            default:
                return false;
        }
    }

    private boolean handleLink(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can link memberships.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("Nanny.Membership.Allow_Linking", true)) {
            sender.sendMessage(ChatColor.GRAY + "Membership linking is not enabled on this server.");
            return true;
        }
        Player p = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /nanny link <patreon|subscribestar> [code|state]");
            return true;
        }
        String provider = args[1].toLowerCase();

        // Find the concrete provider from the composite
        com.storynook.nanny.MembershipProvider root = plugin.getNannyManager().getMembershipProvider();
        com.storynook.nanny.membership.PatreonMembershipProvider patreon = null;
        com.storynook.nanny.membership.SubscribestarMembershipProvider ss = null;
        if (root instanceof com.storynook.nanny.membership.CompositeMembershipProvider) {
            for (com.storynook.nanny.MembershipProvider mp :
                    ((com.storynook.nanny.membership.CompositeMembershipProvider) root).getProviders()) {
                if (mp instanceof com.storynook.nanny.membership.PatreonMembershipProvider)
                    patreon = (com.storynook.nanny.membership.PatreonMembershipProvider) mp;
                if (mp instanceof com.storynook.nanny.membership.SubscribestarMembershipProvider)
                    ss = (com.storynook.nanny.membership.SubscribestarMembershipProvider) mp;
            }
        }

        if (args.length == 2) {
            // Send the authorize URL
            String url;
            if ("patreon".equals(provider) && patreon != null) {
                url = patreon.buildAuthorizeUrl(p.getUniqueId());
            } else if ("subscribestar".equals(provider) && ss != null) {
                url = ss.buildAuthorizeUrl(p.getUniqueId());
            } else {
                sender.sendMessage(ChatColor.RED + "That provider is not enabled on this server.");
                return true;
            }
            net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                    ChatColor.AQUA + "[Click here to link your " + provider + " account]");
            msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
            p.spigot().sendMessage(msg);
            p.sendMessage(ChatColor.GRAY + "After authorizing, copy the code from the redirect page,");
            p.sendMessage(ChatColor.GRAY + "then run " + ChatColor.WHITE + "/nanny link " + provider + " <code>");
            return true;
        }

        // Code-paste path: args[2] = "code|state"
        String combined = args[2];
        int pipe = combined.indexOf('|');
        if (pipe < 0) {
            sender.sendMessage(ChatColor.RED + "Bad code format. Paste the full code from the redirect page.");
            return true;
        }
        final String code = combined.substring(0, pipe);
        final String state = combined.substring(pipe + 1);

        if ("patreon".equals(provider) && patreon != null) {
            final com.storynook.nanny.membership.PatreonMembershipProvider pp = patreon;
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override public void run() {
                    try {
                        boolean ok = pp.linkFromCode(p.getUniqueId(), code, state);
                        final String msg2 = ok
                                ? ChatColor.GREEN + "[Nanny] Linked! AI tier unlocked for active patrons."
                                : ChatColor.YELLOW + "[Nanny] Linked, but no active subscription was found.";
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override public void run() { if (p.isOnline()) p.sendMessage(msg2); }
                        });
                    } catch (Exception e) {
                        final String err = e.getMessage();
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override public void run() { if (p.isOnline()) p.sendMessage(ChatColor.RED + "[Nanny] Link failed: " + err); }
                        });
                    }
                }
            });
            return true;
        }
        if ("subscribestar".equals(provider) && ss != null) {
            final com.storynook.nanny.membership.SubscribestarMembershipProvider sp = ss;
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override public void run() {
                    try {
                        boolean ok = sp.linkFromCode(p.getUniqueId(), code, state);
                        final String msg2 = ok
                                ? ChatColor.GREEN + "[Nanny] Linked! AI tier unlocked."
                                : ChatColor.YELLOW + "[Nanny] Linked, but no active subscription was found.";
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override public void run() { if (p.isOnline()) p.sendMessage(msg2); }
                        });
                    } catch (Exception e) {
                        final String err = e.getMessage();
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override public void run() { if (p.isOnline()) p.sendMessage(ChatColor.RED + "[Nanny] Link failed: " + err); }
                        });
                    }
                }
            });
            return true;
        }
        sender.sendMessage(ChatColor.RED + "That provider is not enabled on this server.");
        return true;
    }

    private boolean handleUnlink(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can unlink.");
            return true;
        }
        Player p = (Player) sender;
        com.storynook.PlayerStatsManagement.PlayerStats stats = plugin.getPlayerStats(p.getUniqueId());
        if (stats == null) {
            sender.sendMessage(ChatColor.RED + "Stats not loaded.");
            return true;
        }
        stats.setNannyMembershipProvider("");
        stats.setNannyMembershipEmail("");
        stats.setNannyMembershipRefreshToken("");
        stats.setNannyMembershipTier("");
        stats.setNannyMembershipStatus("UNLINKED");
        stats.setNannyMembershipLastCheck("");
        com.storynook.PlayerStatsManagement.SavePlayerStats.savePlayerStats(p);
        sender.sendMessage(ChatColor.GREEN + "[Nanny] Membership unlinked.");
        return true;
    }

    private boolean handleRefresh(org.bukkit.command.CommandSender sender, String[] args) {
        if (!sender.hasPermission("accidentprone.nanny.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "/nanny refresh requires admin permission.");
            return true;
        }
        java.util.UUID target = null;
        if (args.length >= 2) {
            Player tp = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (tp != null) target = tp.getUniqueId();
        } else if (sender instanceof Player) {
            target = ((Player) sender).getUniqueId();
        }
        if (target == null) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /nanny refresh [player]");
            return true;
        }
        plugin.getNannyManager().getMembershipProvider().refresh(target);
        sender.sendMessage(ChatColor.GRAY + "Refresh dispatched.");
        return true;
    }
}
