package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class DisciplineDispatcherTest {

    @Test
    public void band_aboveWarn_returnsNone() {
        assertEquals(DisciplineDispatcher.Band.NONE,
                DisciplineDispatcher.computeBand(-19, thresholds()));
    }

    @Test
    public void band_atWarn_returnsWarn() {
        assertEquals(DisciplineDispatcher.Band.WARN,
                DisciplineDispatcher.computeBand(-20, thresholds()));
    }

    @Test
    public void band_atModerate_returnsModerate() {
        assertEquals(DisciplineDispatcher.Band.MODERATE,
                DisciplineDispatcher.computeBand(-50, thresholds()));
    }

    @Test
    public void band_atSerious_returnsSerious() {
        assertEquals(DisciplineDispatcher.Band.SERIOUS,
                DisciplineDispatcher.computeBand(-70, thresholds()));
    }

    @Test
    public void band_atSevere_returnsSevere() {
        assertEquals(DisciplineDispatcher.Band.SEVERE,
                DisciplineDispatcher.computeBand(-95, thresholds()));
    }

    @Test
    public void band_atFloor_returnsFloor() {
        assertEquals(DisciplineDispatcher.Band.FLOOR,
                DisciplineDispatcher.computeBand(-100, thresholds()));
    }

    @Test
    public void slotCap_followsBand() {
        assertEquals(0, DisciplineDispatcher.persistentSlotCap(DisciplineDispatcher.Band.WARN));
        assertEquals(1, DisciplineDispatcher.persistentSlotCap(DisciplineDispatcher.Band.MODERATE));
        assertEquals(2, DisciplineDispatcher.persistentSlotCap(DisciplineDispatcher.Band.SERIOUS));
        assertEquals(3, DisciplineDispatcher.persistentSlotCap(DisciplineDispatcher.Band.SEVERE));
        assertEquals(3, DisciplineDispatcher.persistentSlotCap(DisciplineDispatcher.Band.FLOOR));
    }

    @Test
    public void severityRank_orderingForUnstack() {
        assertTrue(DisciplineDispatcher.severityRank("LEASH_WARD")
                < DisciplineDispatcher.severityRank("BINDING_LEGGINGS"));
        assertTrue(DisciplineDispatcher.severityRank("BINDING_LEGGINGS")
                < DisciplineDispatcher.severityRank("DIAPER_PUNISHMENT"));
    }

    @Test
    public void leastSevereActiveIsFirstToLift() {
        java.util.List<String> active = new java.util.ArrayList<>(
                java.util.List.of("BINDING_LEGGINGS", "LEASH_WARD", "DIAPER_PUNISHMENT"));
        String toLift = DisciplineDispatcher.pickLeastSevere(active);
        assertEquals("LEASH_WARD", toLift);
    }

    private Map<String, Integer> thresholds() {
        Map<String, Integer> t = new HashMap<>();
        t.put("warn", -20);
        t.put("moderate", -40);
        t.put("serious", -65);
        t.put("severe", -90);
        t.put("floor", -100);
        return t;
    }
}
