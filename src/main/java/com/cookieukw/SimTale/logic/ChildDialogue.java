package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.ParentChildBond;

import java.util.UUID;

/**
 * Picks which set of lines a young NPC should answer with.
 *
 * <p>Four buckets, because two things change the voice independently: <em>how old she is</em> and
 * <em>whether she is talking to her own parent</em>. A village child and your daughter do not sound
 * the same even at the same age, and your daughter at fifteen does not sound like she did at eight.
 *
 * <p>Everything resolves to a key prefix rather than to text, so the lines live in the {@code .lang}
 * files with the rest and can be translated and expanded without touching this class.
 */
public final class ChildDialogue {

    /** Which voice to use. {@link #NONE} means "an ordinary adult NPC — use the existing lines". */
    public enum Voice {
        NONE(null),
        /** A child of the village who is not the player's. */
        VILLAGE_CHILD("village_child"),
        OWN_CHILD("own_child"),
        OWN_TEEN("own_teen"),
        OWN_ADULT("own_adult");

        private final String suffix;

        Voice(String suffix) {
            this.suffix = suffix;
        }

        public boolean isSpecial() {
            return suffix != null;
        }
    }

    private ChildDialogue() {
    }

    /**
     * Works out which voice applies.
     *
     * <p>Being the player's child wins over being a village child: an eight-year-old daughter is
     * both, and the parent relationship is the more interesting of the two.
     */
    public static Voice voiceFor(SimNPCComponent npc, UUID playerUuid) {
        GrowthStage stage = ParentChildBond.stageOf(npc, playerUuid);
        if (stage != null) {
            return switch (stage) {
                case TEEN -> Voice.OWN_TEEN;
                case ADULT -> Voice.OWN_ADULT;
                // BABY and TODDLER cannot open this panel, but if they ever do, the child voice is
                // the right one — certainly more right than an adult's.
                default -> Voice.OWN_CHILD;
            };
        }

        return InteractionManager.isNpcAChild(npc) ? Voice.VILLAGE_CHILD : Voice.NONE;
    }

    /**
     * Key prefix for {@code intent} in this voice, or null when the ordinary lines should be used.
     *
     * @param intent short name matching the {@code .lang} section, e.g. {@code "chat"}
     */
    public static String keyFor(Voice voice, String intent) {
        if (voice == null || !voice.isSpecial()) return null;
        return "npc-dialogues.young." + intent + "." + voice.suffix;
    }

    /** Convenience for the common case: resolve the voice and the prefix in one step. */
    public static String keyFor(SimNPCComponent npc, UUID playerUuid, String intent) {
        return keyFor(voiceFor(npc, playerUuid), intent);
    }
}
