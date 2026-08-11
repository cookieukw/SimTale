package com.cookieukw.SimTale.logic;

/**
 * Types of social interactions in SimTale.
 */
public enum InteractionType {
    FRIENDLY,
    FUNNY,
    ROMANTIC,
    MEAN,
    /**
     * Telling your own child off. Replaces {@link #MEAN} on the interaction page whenever the NPC
     * is the player's child, at any life stage — the same act between a parent and a child is not
     * the same act as insulting a stranger, and should not score like one.
     */
    SCOLD,
    GIFT,
    ASSIGN_PROFESSION,
    RANDOM
}
