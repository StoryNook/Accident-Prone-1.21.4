package com.storynook.nanny;

/**
 * Classifies who is talking to (or at) a Nanny relative to that Nanny's owner /
 * ward roster. Used by AI prompt assembly (so the Nanny addresses the right
 * person) and chat scoring (so visitor / owner words don't warp the little's
 * behavior record).
 */
public enum SpeakerRelationship {
    /** The Nanny's owner (the human who placed her). */
    OWNER,
    /** A ward of this Nanny — the "little" she is responsible for. */
    LITTLE,
    /** Anyone else in earshot — bystander, visitor, another player's little. */
    VISITOR
}
