package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

public class CribKillSwitchTest {

    @Test
    public void defaultsToTrueWhenAbsent() {
        YamlConfiguration cfg = new YamlConfiguration();
        boolean v = cfg.getBoolean("Settings_Menu.Crib_New_System", true);
        assertTrue(v, "Default should be true");
    }

    @Test
    public void readsExplicitFalse() throws Exception {
        File tmp = File.createTempFile("crib-killswitch", ".yml");
        tmp.deleteOnExit();
        try (PrintWriter w = new PrintWriter(tmp)) {
            w.println("Settings_Menu:");
            w.println("  Crib_New_System: false");
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(tmp);
        assertEquals(false, cfg.getBoolean("Settings_Menu.Crib_New_System", true));
    }

    @Test
    public void readsExplicitTrue() throws Exception {
        File tmp = File.createTempFile("crib-killswitch", ".yml");
        tmp.deleteOnExit();
        try (PrintWriter w = new PrintWriter(tmp)) {
            w.println("Settings_Menu:");
            w.println("  Crib_New_System: true");
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(tmp);
        assertTrue(cfg.getBoolean("Settings_Menu.Crib_New_System", false));
    }
}
