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
        // Auto-recovery so NPCs don't stay miserable forever until we add sleeping/eating AI
        if (this.energy < 20) this.energy = 100f;
        if (this.hunger < 20) this.hunger = 100f;
        if (this.social < 20) this.social = 100f;
        if (this.fun < 20) this.fun = 100f;

        this.hunger = Math.max(0, this.hunger - 0.0001f);
        this.energy = Math.max(0, this.energy - 0.0002f);
        this.social = Math.max(0, this.social - 0.00015f);
        this.fun = Math.max(0, this.fun - 0.0001f);
    }

    public boolean isMiserable() {
        return hunger < 10 || energy < 10 || social < 10 || fun < 10;
    }
}
