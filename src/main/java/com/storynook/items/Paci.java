package com.storynook.items;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

import com.storynook.PaciRegistry;

public final class Paci {
    private Paci() {}

    public static ItemStack createPaci(PaciRegistry.PaciDef def) {
        ItemStack item = new ItemStack(Material.LEATHER_HELMET, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setDisplayName(def.displayName);
        meta.setCustomModelData(def.cmd);
        EquippableComponent equip = meta.getEquippable();
        equip.setSlot(EquipmentSlot.HEAD);
        equip.setModel(NamespacedKey.minecraft(def.equippableKey));
        meta.setEquippable(equip);
        meta.setUnbreakable(true);
        AttributeModifier modifier = new AttributeModifier(
            UUID.randomUUID(), "generic.armor", 0,
            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD);
        meta.addAttributeModifier(Attribute.ARMOR, modifier);
        item.setItemMeta(meta);
        return item;
    }
}
