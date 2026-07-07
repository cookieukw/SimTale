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
        Trait[] allTraits = Trait.values();
        List<Trait> traitList = new ArrayList<>(Arrays.asList(allTraits));
        Collections.shuffle(traitList);
        p.traits.add(traitList.get(0));
        p.traits.add(traitList.get(1));
        p.traits.add(traitList.get(2));
        return p;
    }
}
