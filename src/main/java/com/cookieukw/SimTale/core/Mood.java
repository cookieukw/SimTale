package com.cookieukw.SimTale.core;

public enum Mood {
    NEUTRAL("Neutro"),
    HAPPY("Feliz"),
    ANGRY("Bravo"),
    SAD("Triste"),
    SCARED("Assustado"),
    SLEEPY("Sonolento"),
    EXCITED("Eufórico");

    public final String ptName;

    Mood(String ptName) {
        this.ptName = ptName;
    }
}
