package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import com.storynook.Plugin;

public class NannyDataCustomToneTest {

    private ServerMock server;
    private Plugin plugin;
    private File dataFolder;

    @BeforeEach
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Plugin.class);
        dataFolder = Files.createTempDirectory("nanny-data-test").toFile();
        new File(dataFolder, "nannies").mkdirs();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void customToneDefaultsToCaring() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", plugin);
        assertEquals(NannyData.MoodTier.CARING, data.getCustomTone());
    }

    @Test
    public void customToneRoundTripsThroughSaveLoad() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", plugin);
        data.setCustomTone(NannyData.MoodTier.WARDEN);
        data.save(dataFolder);

        NannyData loaded = NannyData.load(nid, dataFolder, plugin);
        assertNotNull(loaded);
        assertEquals(NannyData.MoodTier.WARDEN, loaded.getCustomTone());
    }

    @Test
    public void customToneNullDefaultsToCaring() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", plugin);
        data.setCustomTone(null);
        assertEquals(NannyData.MoodTier.CARING, data.getCustomTone());
    }

    @Test
    public void customToneMissingInYamlLoadsAsCaring() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", plugin);
        data.save(dataFolder);

        // Strip the customTone key from the saved file
        File file = new File(dataFolder, "nannies/" + nid + ".yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("customTone", null);
        try { y.save(file); } catch (Exception e) { throw new RuntimeException(e); }

        NannyData loaded = NannyData.load(nid, dataFolder, plugin);
        assertEquals(NannyData.MoodTier.CARING, loaded.getCustomTone());
    }
}
