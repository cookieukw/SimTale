package com.cookieukw.SimTale.core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Visual thought bubble types based on the classic Terraria/Sims emote bubbles,
 * rendered using kawaii emoji designs.
 */
public enum ThoughtType {
    HAPPY("Thought_HAPPY"),
    CHEERFUL("Thought_CHEERFUL"),
    LOVE("Thought_LOVE"),
    SURPRISED("Thought_SURPRISED"),
    SAD("Thought_SAD"),
    SLEEPY("Thought_SLEEPY"),
    ANGRY("Thought_ANGRY"),
    COLD("Thought_COLD"),
    CRYING("Thought_CRYING"),
    MASK("Thought_MASK"),
    WINK("Thought_WINK"),
    NEUTRAL("Thought_NEUTRAL"),
    SERIOUS("Thought_SERIOUS"),
    NERVOUS("Thought_NERVOUS"),
    SICK("Thought_SICK"),
    ANGEL("Thought_ANGEL"),
    PARTY("Thought_PARTY"),
    SHY("Thought_SHY"),
    CAT_UWU("Thought_CAT_UWU"),
    DIZZY("Thought_DIZZY"),
    COOL("Thought_COOL"),
    KISS("Thought_KISS"),
    JOY("Thought_JOY"),
    HUNGRY("Thought_HUNGRY"),
    MUTE("Thought_MUTE"),
    NERD("Thought_NERD"),
    STAR("Thought_STAR"),
    SCARED("Thought_SCARED"),
    INJURED("Thought_INJURED"),
    SIGH("Thought_SIGH"),
    MAN_FACE("Thought_MAN_FACE"),
    MANIC("Thought_MANIC"),
    SHOCKED("Thought_SHOCKED"),
    TIRED("Thought_TIRED"),
    SOULLESS("Thought_SOULLESS"),
    SMUG("Thought_SMUG");

    private final String modelName;

    ThoughtType(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    private static final List<ThoughtType> CASUAL_THOUGHTS = List.of(
            HAPPY, CHEERFUL, WINK, COOL, CAT_UWU, SIGH, JOY
    );

    /**
     * Picks a random pleasant thought for idling or wandering NPCs.
     */
    public static ThoughtType randomCasual() {
        int idx = ThreadLocalRandom.current().nextInt(CASUAL_THOUGHTS.size());
        return CASUAL_THOUGHTS.get(idx);
    }
}
