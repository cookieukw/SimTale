package com.cookieukw.SimTale.core;

import java.util.HashSet;
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
        // Dá um trait aleatório no nascimento (Pode ser alterado depois no banco)
        Trait[] allTraits = Trait.values();
        p.traits.add(allTraits[(int)(Math.random() * allTraits.length)]);
        return p;
    }
}
