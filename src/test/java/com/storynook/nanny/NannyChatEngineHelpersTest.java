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

    // --- sampleVoiceLines ---

    @Test
    public void sampleVoiceLinesPullsFromMatchingCategoriesOnly() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> msgs = new java.util.HashMap<>();
        msgs.put("idle_ambient", java.util.Map.of("SWEET", java.util.List.of("idle-1", "idle-2", "idle-3")));
        msgs.put("keyword_wet",  java.util.Map.of("SWEET", java.util.List.of("wet-1", "wet-2")));
        msgs.put("greeting",     java.util.Map.of("SWEET", java.util.List.of("greet-1")));
        msgs.put("low_supplies", java.util.Map.of("SWEET", java.util.List.of("supplies-1"))); // excluded
        msgs.put("mob_warning",  java.util.Map.of("SWEET", java.util.List.of("mob-1")));      // excluded

        java.util.List<String> got = NannyChatEngine.sampleVoiceLines(
                msgs, "SWEET", 100, new java.util.Random(0));
        // Pool size is 6 (3 idle + 2 keyword + 1 greeting). Excluded categories must not appear.
        assertEquals(6, got.size());
        assertFalse(got.contains("supplies-1"));
        assertFalse(got.contains("mob-1"));
    }

    @Test
    public void sampleVoiceLinesRespectsCount() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> msgs = new java.util.HashMap<>();
        msgs.put("idle_ambient", java.util.Map.of("SWEET",
                java.util.List.of("a", "b", "c", "d", "e", "f", "g", "h")));
        java.util.List<String> got = NannyChatEngine.sampleVoiceLines(
                msgs, "SWEET", 3, new java.util.Random(0));
        assertEquals(3, got.size());
    }

    @Test
    public void sampleVoiceLinesZeroCountReturnsEmpty() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> msgs = new java.util.HashMap<>();
        msgs.put("idle_ambient", java.util.Map.of("SWEET", java.util.List.of("a", "b")));
        java.util.List<String> got = NannyChatEngine.sampleVoiceLines(
                msgs, "SWEET", 0, new java.util.Random(0));
        assertTrue(got.isEmpty());
    }

    @Test
    public void sampleVoiceLinesUnknownTierReturnsEmpty() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> msgs = new java.util.HashMap<>();
        msgs.put("idle_ambient", java.util.Map.of("SWEET", java.util.List.of("a", "b")));
        java.util.List<String> got = NannyChatEngine.sampleVoiceLines(
                msgs, "WARDEN", 5, new java.util.Random(0));
        assertTrue(got.isEmpty());
    }

    @Test
    public void sampleVoiceLinesIsDeterministicWithSeededRandom() {
        java.util.Map<String, java.util.Map<String, java.util.List<String>>> msgs = new java.util.HashMap<>();
        msgs.put("idle_ambient", java.util.Map.of("SWEET",
                java.util.List.of("a", "b", "c", "d", "e", "f")));
        java.util.List<String> a = NannyChatEngine.sampleVoiceLines(
                msgs, "SWEET", 3, new java.util.Random(42));
        java.util.List<String> b = NannyChatEngine.sampleVoiceLines(
                msgs, "SWEET", 3, new java.util.Random(42));
        assertEquals(a, b);
    }
}
