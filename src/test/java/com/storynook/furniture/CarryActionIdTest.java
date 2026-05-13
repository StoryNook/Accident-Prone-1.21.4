package com.storynook.furniture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import com.storynook.Integrations.events.ActionId;

public class CarryActionIdTest {

    @Test
    public void carryConstantsExist() {
        assertEquals("accidentprone:carry_pickup", ActionId.CARRY_PICKUP);
        assertEquals("accidentprone:carry_drop", ActionId.CARRY_DROP);
    }

    @Test
    public void carryConstantsAreInAll() {
        Set<String> all = new HashSet<>(Arrays.asList(ActionId.ALL));
        assertTrue(all.contains(ActionId.CARRY_PICKUP));
        assertTrue(all.contains(ActionId.CARRY_DROP));
    }
}
