package com.storynook.nanny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NannyChatEngineTagParsingTest {

    @Test
    public void noTagReturnsTextUnchanged() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest("Just a chat reply.");
        assertEquals("Just a chat reply.", r.cleanedText);
        assertTrue(r.tags.isEmpty());
    }

    @Test
    public void singleTagStripped() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest(
                "That's it. <PUNISH:laxative>");
        assertEquals("That's it.", r.cleanedText.trim());
        assertEquals(1, r.tags.size());
        assertEquals("PUNISH", r.tags.get(0).type);
        assertEquals("laxative", r.tags.get(0).action);
    }

    @Test
    public void diaperTagWithDurationParses() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest(
                "Three days in a diaper for you. <PUNISH:diaper:3d>");
        assertEquals(1, r.tags.size());
        assertEquals("diaper", r.tags.get(0).action);
        assertEquals(Integer.valueOf(3), r.tags.get(0).duration);
    }

    @Test
    public void diaperTagWithoutDurationParses() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest(
                "Diaper punishment. <PUNISH:diaper>");
        assertEquals(1, r.tags.size());
        assertEquals("diaper", r.tags.get(0).action);
        assertEquals(null, r.tags.get(0).duration);
    }

    @Test
    public void multipleTagsAllParsed() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest(
                "Enough. <PUNISH:leash> <PUNISH:binding>");
        assertEquals(2, r.tags.size());
        assertEquals("leash", r.tags.get(0).action);
        assertEquals("binding", r.tags.get(1).action);
    }

    @Test
    public void rewardTagParses() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest(
                "Such a good little one. <REWARD:praise>");
        assertEquals(1, r.tags.size());
        assertEquals("REWARD", r.tags.get(0).type);
        assertEquals("praise", r.tags.get(0).action);
    }

    @Test
    public void unknownTagShapeIgnoredButStrippedFromText() {
        NannyChatEngine.ParseResult r = NannyChatEngine.parseTagsForTest("hello <random>");
        assertEquals("hello <random>", r.cleanedText);
        assertTrue(r.tags.isEmpty());
    }
}
