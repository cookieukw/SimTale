package com.cookieukw.SimTale.core;

import java.util.Set;

/**
 * Tracks the biological and social needs of a SimNPC.
 */
public class Needs {
    public float hunger = 100f;
    public float energy = 100f;
    public float social = 100f;
    public float fun = 100f;

    public void tickDecay(Set<Trait> traits) {
        float energyDecay = traits.contains(Trait.LAZY) ? 0.0004f : 0.0002f;
        float funDecay = traits.contains(Trait.FUNNY) ? 0.00005f : 0.0001f;
        
        this.hunger = Math.max(0, this.hunger - 0.0001f);
        this.energy = Math.max(0, this.energy - energyDecay);
        this.social = Math.max(0, this.social - 0.00015f);
        this.fun = Math.max(0, this.fun - funDecay);
    }

    public void healEnergy(float amount) {
        this.energy = Math.min(100f, this.energy + amount);
    }

    public boolean isMiserable() {
        return hunger < 10 || energy < 10 || social < 10 || fun < 10;
    }
}
