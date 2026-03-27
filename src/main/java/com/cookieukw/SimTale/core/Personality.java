package com.cookieukw.SimTale.core;

/**
 * Defines the personality traits of a SimNPC.
 */
public class Personality {
    public int kindness;
    public int humor;
    public int aggression;
    public int charisma;

    public Personality(int kindness, int humor, int aggression, int charisma) {
        this.kindness = kindness;
        this.humor = humor;
        this.aggression = aggression;
        this.charisma = charisma;
    }

    /**
     * Creates a default balanced personality.
     */
    public static Personality createDefault() {
        return new Personality(50, 50, 20, 50);
    }
}
