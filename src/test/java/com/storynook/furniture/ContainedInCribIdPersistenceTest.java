package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import com.storynook.Plugin;
import com.storynook.PlayerStatsManagement.LoadSelectedSounds;
import com.storynook.PlayerStatsManagement.LoadStats;
import com.storynook.PlayerStatsManagement.PlayerStats;

public class ContainedInCribIdPersistenceTest {

    @Test
    public void defaultsToNull() {
        PlayerStats stats = new PlayerStats();
        assertNull(stats.getContainedInCribId());
    }

    @Test
    public void roundTripsThroughYaml() throws Exception {
        UUID id = UUID.randomUUID();
        File tmp = File.createTempFile("contained-roundtrip", ".yml");
        tmp.deleteOnExit();

        YamlConfiguration out = new YamlConfiguration();
        out.set("containedInCribId", id.toString());
        out.save(tmp);

        YamlConfiguration in = YamlConfiguration.loadConfiguration(tmp);
        assertEquals(id.toString(), in.getString("containedInCribId"));
        assertEquals(id, UUID.fromString(in.getString("containedInCribId")));
    }

    @Test
    public void omitsKeyWhenNull() throws Exception {
        File tmp = File.createTempFile("contained-null", ".yml");
        tmp.deleteOnExit();

        YamlConfiguration out = new YamlConfiguration();
        out.set("containedInCribId", null);
        out.save(tmp);

        YamlConfiguration in = YamlConfiguration.loadConfiguration(tmp);
        assertFalse(in.contains("containedInCribId"));
    }

    /**
     * Exercises the LoadStats.loadPlayerStatsFromConfig catch block for a corrupted
     * containedInCribId value. The previous version only tested UUID.fromString in
     * isolation — this version drives LoadStats directly so the test would fail if
     * the try/catch were deleted.
     *
     * <p>Implementation note (Option A from spec): LoadStats.loadPlayerStatsFromConfig
     * requires plugin.getGlobalConfig() to return a feature-flag map. Because Plugin
     * is a JavaPlugin subclass (not easily instantiable in unit tests), we use
     * sun.misc.Unsafe.allocateInstance to obtain an uninitialised Plugin shell, then
     * set its private {@code globalConfig} field via reflection so that the Caregivers
     * gate is open. We also clear Plugin.soundConfig (public static) and inject the
     * same stub into LoadSelectedSounds.plugin so the tail of loadPlayerStatsFromConfig
     * does not NPE on sound loading.
     */
    @Test
    public void corruptedUuidLeavesNull() throws Exception {
        // --- Build a minimal globalConfig map with Caregivers enabled so the
        //     containedInCribId load branch is reached. ---
        Map<String, Object> fakeGlobalConfig = new HashMap<>();
        fakeGlobalConfig.put("Caregivers", true);
        // All other flags default to null → the else-branches run (safe).

        // --- Obtain Unsafe to allocate an uninitialised Plugin instance. ---
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        Plugin pluginStub = (Plugin) unsafe.allocateInstance(Plugin.class);

        // Inject the fake globalConfig into the stub (private instance field).
        Field gcField = Plugin.class.getDeclaredField("globalConfig");
        gcField.setAccessible(true);
        gcField.set(pluginStub, fakeGlobalConfig);

        // Ensure Plugin.soundConfig (public static) is an empty map so that
        // LoadSelectedSounds.loadStoredSounds does not NPE when iterating categories.
        Plugin.soundConfig = new HashMap<>();

        // Inject the stub into LoadStats.plugin (private static).
        Field lsPluginField = LoadStats.class.getDeclaredField("plugin");
        lsPluginField.setAccessible(true);
        lsPluginField.set(null, pluginStub);

        // Inject the stub into LoadSelectedSounds.plugin (private static) for the
        // tail call new LoadSelectedSounds().loadStoredSounds(...).
        Field lssPluginField = LoadSelectedSounds.class.getDeclaredField("plugin");
        lssPluginField.setAccessible(true);
        lssPluginField.set(null, pluginStub);

        // --- Build the minimal YAML config that triggers the corrupted-UUID path. ---
        YamlConfiguration config = new YamlConfiguration();
        config.set("containedInCribId", "not-a-uuid");
        // Provide the keys LoadStats touches before reaching containedInCribId so
        // it does not fail on an unrelated deserialization.
        // (Messing/Accidents/Binding_Diapers all absent → else-branch defaults, safe.)

        // --- Run LoadStats and assert the catch block leaves the field null. ---
        PlayerStats stats = new PlayerStats();
        LoadStats.loadPlayerStatsFromConfig(stats, config);

        assertNull(stats.getContainedInCribId(),
                "LoadStats catch block must leave containedInCribId null for a corrupted UUID string");

        // --- Tear down: restore static fields to a clean state for sibling tests. ---
        lsPluginField.set(null, null);
        lssPluginField.set(null, null);
        Plugin.soundConfig = new HashMap<>();
    }
}
