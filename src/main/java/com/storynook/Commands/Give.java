package com.storynook.Commands;
import com.storynook.items.underwear;
import com.storynook.items.Nanny;
import com.storynook.items.ItemManager;
import com.storynook.items.cribs;
import com.storynook.items.pants;
import com.storynook.furniture.highchair.HighchairItem;
import com.storynook.furniture.highchair.HighchairRegistry;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class Give {
    public static void GiveItem(Player player, String item, String variation, int quantity) {
        ArrayList<ItemStack> itemsToAdd = new ArrayList<>();
        switch (item.toLowerCase()) {
            case "underwear":
                addUnderwearItem(itemsToAdd, 0, variation, quantity, underwear.Underwear(), player);
                break;
            case "pullup":
                addUnderwearItem(itemsToAdd, 1, variation, quantity, underwear.Pullup(), player);
                break;
            case "diaper":
                addUnderwearItem(itemsToAdd, 2, variation, quantity, underwear.Diaper(), player);
                break;
            case "thick_diaper":
                addUnderwearItem(itemsToAdd, 3, variation, quantity, underwear.ThickDiaper(), player);
                break;
            case "laxative":
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(ItemManager.Laxative());
                }
                break;
            case "diaper_pail":
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(ItemManager.Diaperpail());
                }
                break;
            case "washer":
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(ItemManager.Washer());
                }
                break;
            case "toilet":
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(ItemManager.Toilet());
                }
                break;
            case "crib":
                Material cribmaterial = Material.getMaterial(variation + "_SLAB");
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(cribs.createCrib(cribmaterial));
                }
                break;
            case "pants":
                Material pantswool = Material.getMaterial(variation + "_WOOL");
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(pants.createPants(pantswool));
                }
                break;
            case "nanny":

                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(Nanny.createNannyEgg(variation));
                }
                break;
            case "paci":
                com.storynook.PaciRegistry.PaciDef paciDef =
                        com.storynook.PaciRegistry.findByGiveKey(variation);
                if (paciDef == null) {
                    player.sendMessage("Unknown paci design: " + variation);
                    return;
                }
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(com.storynook.PaciRegistry.createItem(paciDef));
                }
                break;
            case "highchair":
                int colorIndex = HighchairRegistry.colorIndex(variation == null ? "" : variation.toLowerCase());
                if (colorIndex < 0) {
                    player.sendMessage("Unknown highchair colour: " + variation);
                    return;
                }
                ItemStack highchairStack = HighchairItem.make(colorIndex);
                if (highchairStack == null) {
                    player.sendMessage("Highchair item could not be built.");
                    return;
                }
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(highchairStack);
                }
                break;
            case "changing_table":
                int ctColorIndex = com.storynook.furniture.changingtable.ChangingTableRegistry
                        .colorIndex(variation == null ? "" : variation.toLowerCase());
                if (ctColorIndex < 0) {
                    player.sendMessage("Unknown changing table colour: " + variation);
                    return;
                }
                ItemStack ctStack = com.storynook.furniture.changingtable.ChangingTableItem.make(ctColorIndex);
                if (ctStack == null) {
                    player.sendMessage("Changing table item could not be built.");
                    return;
                }
                for (int i = 0; i < quantity; i++) {
                    itemsToAdd.add(ctStack);
                }
                break;
            default:
                // Add other items as needed
                return;
        }
        Inventory inventory = player.getInventory();
        int availableSpace = 0;

        for (ItemStack stack : inventory) {
            if (stack == null || stack.getType() == Material.AIR) {
                availableSpace++;
            }
        }

        // Calculate how many items can be added to the inventory
        int totalItemsToAdd = quantity;
        int itemsToDrop = Math.max(0, quantity - (availableSpace * 64));

        // Add as many as possible to the inventory
        for (ItemStack stack : itemsToAdd) {
            if (inventory.firstEmpty() != -1 && quantity > 0) {
                int addQuantity = Math.min(64, quantity);
                ItemStack newStack = stack.clone();
                newStack.setAmount(addQuantity);
                inventory.addItem(newStack);
                quantity -= addQuantity;
            } else {
                // Drop remaining items
                for (int i = 0; i < quantity; i++) {
                    Location dropLocation = player.getLocation().add(0, 1, 0);
                    Player targetPlayer = player;
                    targetPlayer.getWorld().dropItem(dropLocation, stack.clone());
                }
                break;
            }
        }
    }

    /**
     * Resolve a category item (underwear/pullup/diaper/thick_diaper) into an
     * ItemStack, honoring the optional design `variation`. An empty/null/"default"
     * variation produces the legacy item via the supplied {@code defaultItem}.
     * Otherwise looks up the design in {@link com.storynook.DesignRegistry} by
     * giveKey; unknown variation messages the player and adds nothing.
     */
    private static void addUnderwearItem(ArrayList<ItemStack> itemsToAdd, int category,
                                         String variation, int quantity,
                                         ItemStack defaultItem, Player player) {
        boolean wantsDefault = variation == null || variation.isEmpty()
                || variation.equalsIgnoreCase("default");
        ItemStack stack;
        if (wantsDefault) {
            stack = defaultItem;
        } else {
            com.storynook.DesignRegistry.DesignDef def =
                    com.storynook.DesignRegistry.findByGiveKey(category, variation);
            if (def == null) {
                player.sendMessage("Unknown design variation: " + variation);
                return;
            }
            stack = com.storynook.DesignRegistry.createItem(def);
        }
        for (int i = 0; i < quantity; i++) {
            itemsToAdd.add(stack);
        }
    }
}