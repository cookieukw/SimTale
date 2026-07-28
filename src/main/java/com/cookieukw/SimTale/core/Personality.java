package com.cookieukw.SimTale.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines the personality traits of a SimNPC.
 */
public class Personality {

    /** How many random traits a freshly generated NPC gets. */
    private static final int TRAITS_PER_NPC = 3;

    public int kindness;
    public int humor;
    public int aggression;
    public int charisma;
    public Set<Trait> traits = new HashSet<>();

    public Personality(int kindness, int humor, int aggression, int charisma) {
        this.kindness = kindness;
        this.humor = humor;
        this.aggression = aggression;
        this.charisma = charisma;
    }

    /**
     * No-op constructor for codecs.
     */
    protected Personality() {
    }

    /**
     * Creates a default balanced personality.
     */
    public static Personality createDefault() {
        Personality p = new Personality(50, 50, 20, 50);
        List<Trait> traitList = new ArrayList<>(Arrays.asList(Trait.values()));
        Collections.shuffle(traitList);
        // Bounded by the enum size: the old code indexed 0/1/2 unconditionally and would throw
        // IndexOutOfBounds if Trait ever dropped below three constants.
        int count = Math.min(TRAITS_PER_NPC, traitList.size());
        for (int i = 0; i < count; i++) {
            p.traits.add(traitList.get(i));
        }
        return p;
    }
}
