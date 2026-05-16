package com.storynook.nanny.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class NannyTaskInterfaceTest {

    @Test
    public void candidateRecord_storesAllFields() {
        // Location and Player are Bukkit types — we'll use nulls here just to verify the record shape.
        Candidate c = new Candidate(90, null, null, "test");
        assertEquals(90, c.priority());
        assertNull(c.ward());
        assertEquals("test", c.reasonTag());
    }

    @Test
    public void result_hasAllFourVariants() {
        assertEquals(4, Result.values().length);
    }
}
