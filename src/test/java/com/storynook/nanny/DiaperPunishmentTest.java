package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class DiaperPunishmentTest {

    @BeforeEach
    public void setUp() { MockBukkit.mock(); }
    @AfterEach
    public void tearDown() { MockBukkit.unmock(); }

    @Test
    public void cursedPantsHasBindingCurseAndUnbreakable() {
        ItemStack pants = DiaperPunishment.buildCursedPants();
        assertEquals(Material.LEATHER_LEGGINGS, pants.getType());
        ItemMeta meta = pants.getItemMeta();
        assertTrue(meta.hasCustomModelData());
        assertEquals(626015, meta.getCustomModelData());
        assertTrue(meta.hasEnchant(Enchantment.BINDING_CURSE));
        assertTrue(meta.isUnbreakable());
    }

    @Test
    public void daysToTickConversion() {
        assertEquals(24000L, DiaperPunishment.daysToTicks(1));
        assertEquals(72000L, DiaperPunishment.daysToTicks(3));
        assertEquals(720000L, DiaperPunishment.daysToTicks(30));
    }

    @Test
    public void daysClampedToValidRange() {
        assertEquals(1, DiaperPunishment.clampDays(0, 1, 30));
        assertEquals(30, DiaperPunishment.clampDays(50, 1, 30));
        assertEquals(15, DiaperPunishment.clampDays(15, 1, 30));
    }

    @Test
    public void violationCountdownReachesEscalationAtZero() {
        assertFalse(DiaperPunishment.shouldEscalate(3, -50, -100));
        assertFalse(DiaperPunishment.shouldEscalate(2, -50, -100));
        assertFalse(DiaperPunishment.shouldEscalate(1, -50, -100));
        assertTrue(DiaperPunishment.shouldEscalate(0, -50, -100));
    }

    @Test
    public void escalationAtScoreFloor() {
        assertTrue(DiaperPunishment.shouldEscalate(3, -100, -100));
        assertTrue(DiaperPunishment.shouldEscalate(2, -100, -100));
    }
}
