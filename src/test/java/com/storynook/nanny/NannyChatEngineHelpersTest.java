package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class NannyChatEngineHelpersTest {

    /**
     * MockBukkit.mock() initialises the static Bukkit server so
     * {@link YamlConfiguration} loads correctly. We do NOT call
     * {@code MockBukkit.load(Plugin.class)} — that triggers Plugin.onEnable's
     * recipe registration which hits MockBukkit's UnsafeValues limitations and
     * causes tests to be silently skipped.
     */
    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    // --- loadPersonalities ---

    @Test
    public void loadPersonalitiesReturnsFlatMapOfBothSections() throws Exception {
        File f = Files.createTempFile("personalities", ".yml").toFile();
        Files.writeString(f.toPath(),
            "always_on:\n" +
            "  BASIC_CARE: |\n" +
            "    Always-on care prose.\n" +
            "capabilities:\n" +
            "  FORCE_FEED_LAXATIVE: |\n" +
            "    Punitive prose.\n" +
            "  HYPNOSIS_USE: |\n" +
            "    Triggers: {hypno_triggers}.\n",
            StandardCharsets.UTF_8);

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Map<String, String> result = NannyChatEngine.loadPersonalitiesFromConfig(y);
        assertEquals(3, result.size());
        assertTrue(result.get("BASIC_CARE").contains("Always-on care prose"));
        assertTrue(result.get("FORCE_FEED_LAXATIVE").contains("Punitive prose"));
        assertTrue(result.get("HYPNOSIS_USE").contains("{hypno_triggers}"));
        f.delete();
    }

    @Test
    public void loadPersonalitiesEmptyYamlReturnsEmptyMap() throws Exception {
        File f = Files.createTempFile("personalities-empty", ".yml").toFile();
        Files.writeString(f.toPath(), "", StandardCharsets.UTF_8);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Map<String, String> result = NannyChatEngine.loadPersonalitiesFromConfig(y);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        f.delete();
    }

    @Test
    public void loadPersonalitiesMissingSectionsAreOk() throws Exception {
        File f = Files.createTempFile("personalities-partial", ".yml").toFile();
        Files.writeString(f.toPath(),
            "capabilities:\n" +
            "  POTTY_REMINDERS: |\n" +
            "    Just one cap, no always_on section.\n",
            StandardCharsets.UTF_8);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Map<String, String> result = NannyChatEngine.loadPersonalitiesFromConfig(y);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("POTTY_REMINDERS"));
        assertFalse(result.containsKey("BASIC_CARE"));
        f.delete();
    }
}
