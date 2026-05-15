package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

public class NannyDataBehaviorPersistenceTest {

    private File dataFolder;

    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();
        dataFolder = Files.createTempDirectory("nanny-behavior-test").toFile();
        new File(dataFolder, "nannies").mkdirs();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void behaviorScoreRoundTrips() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID wardA = UUID.randomUUID();
        UUID wardB = UUID.randomUUID();

        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.getBehaviorScore().put(wardA, -42);
        data.getBehaviorScore().put(wardB, 15);
        data.getBehaviorStreak().put(wardA, -8);
        data.save(dataFolder);

        NannyData loaded = NannyData.load(nid, dataFolder, null);
        assertNotNull(loaded);
        assertEquals(Integer.valueOf(-42), loaded.getBehaviorScore().get(wardA));
        assertEquals(Integer.valueOf(15),  loaded.getBehaviorScore().get(wardB));
        assertEquals(Integer.valueOf(-8),  loaded.getBehaviorStreak().get(wardA));
    }

    @Test
    public void disciplineCooldownsNestedRoundTrip() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID ward = UUID.randomUUID();

        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        Map<String, Long> wardCooldowns = new HashMap<>();
        wardCooldowns.put("LEASH_WARD", 1779999999000L);
        wardCooldowns.put("FORCE_FEED_LAXATIVE", 1780000099000L);
        data.getDisciplineCooldowns().put(ward, wardCooldowns);
        data.save(dataFolder);

        NannyData loaded = NannyData.load(nid, dataFolder, null);
        Map<String, Long> got = loaded.getDisciplineCooldowns().get(ward);
        assertNotNull(got);
        assertEquals(Long.valueOf(1779999999000L), got.get("LEASH_WARD"));
        assertEquals(Long.valueOf(1780000099000L), got.get("FORCE_FEED_LAXATIVE"));
    }

    @Test
    public void activePersistentPunishmentsRoundTrip() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID ward = UUID.randomUUID();

        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.getActivePersistentPunishments()
                .put(ward, new java.util.ArrayList<>(List.of("LEASH_WARD", "BINDING_LEGGINGS")));
        data.save(dataFolder);

        NannyData loaded = NannyData.load(nid, dataFolder, null);
        List<String> got = loaded.getActivePersistentPunishments().get(ward);
        assertNotNull(got);
        assertEquals(2, got.size());
        assertEquals("LEASH_WARD", got.get(0));
        assertEquals("BINDING_LEGGINGS", got.get(1));
    }

    @Test
    public void emptyMapsDefaultOnLoad() {
        UUID nid = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        NannyData data = new NannyData(nid, owner, "TestNanny", null);
        data.save(dataFolder);
        NannyData loaded = NannyData.load(nid, dataFolder, null);
        assertNotNull(loaded.getBehaviorScore());
        assertNotNull(loaded.getBehaviorStreak());
        assertNotNull(loaded.getDisciplineCooldowns());
        assertEquals(0, loaded.getBehaviorScore().size());
    }
}
