package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class BehaviorSignalsWordMatchTest {

    @Test
    public void exactWordMatches() {
        assertTrue(BehaviorSignals.containsAnyPhrase(
                "you are stupid", List.of("stupid")));
    }

    @Test
    public void caseInsensitiveMatch() {
        assertTrue(BehaviorSignals.containsAnyPhrase(
                "YOU ARE STUPID", List.of("stupid")));
    }

    @Test
    public void wordBoundaryRequired_passageDoesNotMatchAss() {
        assertFalse(BehaviorSignals.containsAnyPhrase(
                "this is a long passage", List.of("ass")));
    }

    @Test
    public void multiWordPhraseMatches() {
        assertTrue(BehaviorSignals.containsAnyPhrase(
                "i really hate you nanny", List.of("hate you")));
    }

    @Test
    public void emptyListReturnsFalse() {
        assertFalse(BehaviorSignals.containsAnyPhrase("anything", List.of()));
    }

    @Test
    public void nullMessageReturnsFalse() {
        assertFalse(BehaviorSignals.containsAnyPhrase(null, List.of("anything")));
    }
}
