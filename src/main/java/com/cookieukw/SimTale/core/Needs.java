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
    public float hygiene = 100f;

    /**
     * Health already lost to starvation, in points.
     *
     * <p>Death is driven by this rather than by hunger hitting zero, so an NPC takes the full
     * starvation window to die instead of dropping the instant its belly empties. Lives on Needs
     * because Needs is what gets persisted, so the countdown survives a relog.
     */
    public float starvationDamage = 0f;

    public void tickDecay(Set<Trait> traits) {
        // traits comes straight off a deserialized Personality and can legitimately be null.
        boolean lazy = traits != null && traits.contains(Trait.LAZY);
        boolean funny = traits != null && traits.contains(Trait.FUNNY);
        float energyDecay = lazy ? 0.0004f : 0.0002f;
        float funDecay = funny ? 0.00005f : 0.0001f;
        
        this.hunger = Math.max(0, this.hunger - 0.0001f);
        this.energy = Math.max(0, this.energy - energyDecay);
        this.social = Math.max(0, this.social - 0.00015f);
        this.fun = Math.max(0, this.fun - funDecay);
        this.hygiene = Math.max(0, this.hygiene - 0.0002f);
    }

    public void healEnergy(float amount) {
        this.energy = Math.min(100f, this.energy + amount);
    }

    public boolean isMiserable() {
        return hunger < 10 || energy < 10 || social < 10 || fun < 10 || hygiene < 10;
    }
}
