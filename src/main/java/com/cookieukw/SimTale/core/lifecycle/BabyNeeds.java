package com.cookieukw.SimTale.core.lifecycle;

/**
 * Specific needs of a growing baby/child.
 * Influences personality development upon growing up.
 * Two children of the same parents can grow up in different ways
 * depending on how they were cared for.
 */
public class BabyNeeds {

    public float hunger = 100f;
    public float affection = 100f;
    public float health = 100f;
    public float positiveExperiences = 0f;
    public float negativeExperiences = 0f;

    public BabyNeeds() {}
    public void tickDecay() {
        this.hunger = Math.max(0f, this.hunger - 0.0003f);
        this.affection = Math.max(0f, this.affection - 0.0002f);

        float healthDecay = this.hunger < 20f ? 0.0004f : 0.0001f;
        this.health = Math.max(0f, this.health - healthDecay);

        if (this.hunger < 30f || this.affection < 30f || this.health < 30f) {
            this.negativeExperiences += 0.001f;
        }
    }
    public void feed(float amount) {
        this.hunger = Math.min(100f, this.hunger + amount);
        this.positiveExperiences += 0.5f;
    }

    public void showAffection(float amount) {
        this.affection = Math.min(100f, this.affection + amount);
        this.positiveExperiences += 0.3f;
    }
    public void heal(float amount) {
        this.health = Math.min(100f, this.health + amount);
    }

   
    public boolean isCritical() {
        return hunger < 10f || health < 10f;
    }
    public boolean isCrying() {
        return hunger < 30f || affection < 20f || health < 25f;
    }

  
    public float getWellbeingScore() {
        float totalPositive = positiveExperiences + 1f; // evita divisão por zero
        float totalNegative = negativeExperiences + 1f;
        return Math.min(1.0f, totalPositive / (totalPositive + totalNegative));
    }

    /**
     * Returns personality tendency based on care received.
     * > 0.7 = happy/sociable child
     * 0.4-0.7 = neutral child
     * < 0.4 = shy/aggressive child
     */
    public PersonalityTendency getPersonalityTendency() {
        float score = getWellbeingScore();
        if (score > 0.5f) return PersonalityTendency.SOCIABLE;
        return PersonalityTendency.WITHDRAWN;
    }

    public enum PersonalityTendency {
        SOCIABLE,
        WITHDRAWN
    }
}
