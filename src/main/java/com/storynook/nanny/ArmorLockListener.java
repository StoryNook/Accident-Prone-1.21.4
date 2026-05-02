package com.storynook.nanny;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import com.storynook.Plugin;

/**
 * Cancels leggings-slot inventory interactions for wards whose armor is
 * locked by a Nanny with {@link Capability#ARMOR_LOCK} permission.
 *
 * <p>Locked state lives on {@link NannyData#getLockedArmor()} —
 * {@code wardUUID → true}. {@link NannyCareEngine} unlocks around its own
 * change action and re-locks afterwards so the Nanny can do her job.
 */
public class ArmorLockListener implements Listener {

    private final Plugin plugin;

    public ArmorLockListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getType() != InventoryType.CRAFTING
                && event.getView().getType() != InventoryType.PLAYER) return;

        boolean leggingsSlot = event.getRawSlot() == 38;
        boolean shiftIntoLeggings = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getCurrentItem() != null
                && isLeggings(event.getCurrentItem());

        if (!leggingsSlot && !shiftIntoLeggings) return;
        if (!isArmorLocked(player.getUniqueId())) return;

        event.setCancelled(true);
    }

    private boolean isArmorLocked(UUID wardUUID) {
        NannyManager mgr = plugin.getNannyManager();
        if (mgr == null) return false;
        for (NannyData data : mgr.getAllNannies().values()) {
            boolean isWard = data.getOwnerUUID().equals(wardUUID)
                    || data.getWards().contains(wardUUID);
            if (!isWard) continue;
            if (!NannyPolicy.allows(data, Capability.ARMOR_LOCK)) continue;
            Boolean locked = data.getLockedArmor().get(wardUUID);
            if (locked != null && locked) return true;
        }
        return false;
    }

    private boolean isLeggings(ItemStack item) {
        if (item == null) return false;
        switch (item.getType()) {
            case LEATHER_LEGGINGS:
            case CHAINMAIL_LEGGINGS:
            case IRON_LEGGINGS:
            case GOLDEN_LEGGINGS:
            case DIAMOND_LEGGINGS:
            case NETHERITE_LEGGINGS:
                return true;
            default:
                return false;
        }
    }
}
