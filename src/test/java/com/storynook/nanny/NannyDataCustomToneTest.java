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

public class NannyDataCustomToneTest {

    /**
     * We avoid {@code MockBukkit.load(Plugin.class)} because triggering
     * {@code Plugin.onEnable} causes MockBukkit to throw
     * {@code UnimplementedOperationException} on recipe registration,
     * which the framework reports as a JUnit skip.
     *
     * Instead we just initialise MockBukkit's static server (so
     * {@link YamlConfiguration} works) and pass {@code null} for the
     * plugin reference. {@link NannyData} is null-safe on
     * {@code plugin.getGlobalConfig()} as of this change.
     */
    private File dataFolder;

    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();
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
        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        assertEquals(NannyData.MoodTier.CARING, data.getCustomTone());
    }

    @Test
    public void customToneRoundTripsThroughSaveLoad() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.setCustomTone(NannyData.MoodTier.WARDEN);
        data.save(dataFolder);

        NannyData loaded = NannyData.load(nid, dataFolder, null);
        assertNotNull(loaded);
        assertEquals(NannyData.MoodTier.WARDEN, loaded.getCustomTone());
    }

    @Test
    public void customToneNullDefaultsToCaring() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.setCustomTone(null);
        assertEquals(NannyData.MoodTier.CARING, data.getCustomTone());
    }

    @Test
    public void customToneMissingInYamlLoadsAsCaring() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.save(dataFolder);

        // Strip the customTone key from the saved file
        File file = new File(dataFolder, "nannies/" + nid + ".yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("customTone", null);
        try { y.save(file); } catch (Exception e) { throw new RuntimeException(e); }

        NannyData loaded = NannyData.load(nid, dataFolder, null);
        assertEquals(NannyData.MoodTier.CARING, loaded.getCustomTone());
    }
}
