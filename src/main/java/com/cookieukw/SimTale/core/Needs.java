package com.cookieukw.SimTale.core;

/**
 * Tracks the biological and social needs of a SimNPC.
 */
public class Needs {
    public float hunger = 100f;
    public float energy = 100f;
    public float social = 100f;
    public float fun = 100f;

    public void tickDecay() {
        this.hunger = Math.max(0, this.hunger - 0.01f);
        this.energy = Math.max(0, this.energy - 0.02f);
        this.social = Math.max(0, this.social - 0.015f);
        this.fun = Math.max(0, this.fun - 0.01f);
    }

    public boolean isMiserable() {
        return hunger < 10 || energy < 10 || social < 10 || fun < 10;
    }
}
