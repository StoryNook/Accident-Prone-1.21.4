package com.storynook.furniture;

import org.bukkit.entity.ArmorStand;

/**
 * Result of {@code CribRegistry.findNearestNewCrib} or
 * {@code CribRegistry.findNearestCrib}. Sealed so callers must
 * handle every case via instanceof / pattern matching.
 */
public sealed interface CribLookupResult
    permits CribLookupResult.NewCribResult,
            CribLookupResult.LegacyCribResult,
            CribLookupResult.None {

    /** New display-entity crib. */
    record NewCribResult(Crib crib) implements CribLookupResult {}

    /** Legacy invisible-armor-stand crib (pre-redesign). */
    record LegacyCribResult(ArmorStand armorStand) implements CribLookupResult {}

    /** No crib found in range. */
    record None() implements CribLookupResult {
        public static final None INSTANCE = new None();
    }
}
