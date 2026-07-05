package com.cookieukw.SimTale.core.lifecycle;

/**
 * Necessidades específicas de um bebê/criança em crescimento.
 * Influenciam o desenvolvimento da personalidade ao crescer.
 *
 * Dois filhos dos mesmos pais podem crescer de formas diferentes
 * dependendo de como foram cuidados.
 */
public class BabyNeeds {

    /** Fome (0-100). Diminui com o tempo, aumenta ao ser alimentado. */
    public float hunger = 100f;

    /** Afeto (0-100). Aumenta ao ser carregado, acariciado ou estar perto dos pais. */
    public float affection = 100f;

    /** Saúde (0-100). Depende de alimentação e descanso adequados. */
    public float health = 100f;

    /**
     * Acumulador de experiências positivas.
     * Quanto maior, mais sociável/alegre a personalidade gerada.
     */
    public float positiveExperiences = 0f;

    /**
     * Acumulador de experiências negativas.
     * Quanto maior, mais tímido/agressivo a personalidade gerada.
     */
    public float negativeExperiences = 0f;

    public BabyNeeds() {}

    /**
     * Decay por tick. Chamado a cada tick do GrowthTickSystem.
     * As taxas são baixas porque são chamadas frequentemente.
     */
    public void tickDecay() {
        this.hunger = Math.max(0f, this.hunger - 0.0003f);
        this.affection = Math.max(0f, this.affection - 0.0002f);

        // Saúde decai mais rápido se fome estiver baixa
        float healthDecay = this.hunger < 20f ? 0.0004f : 0.0001f;
        this.health = Math.max(0f, this.health - healthDecay);

        // Acumula experiências negativas se necessidades estão baixas
        if (this.hunger < 30f || this.affection < 30f || this.health < 30f) {
            this.negativeExperiences += 0.001f;
        }
    }

    /**
     * Alimenta o bebê.
     */
    public void feed(float amount) {
        this.hunger = Math.min(100f, this.hunger + amount);
        this.positiveExperiences += 0.5f;
    }

    /**
     * Acaricia/carrega o bebê — aumenta afeto.
     */
    public void showAffection(float amount) {
        this.affection = Math.min(100f, this.affection + amount);
        this.positiveExperiences += 0.3f;
    }

    /**
     * Curar o bebê (descanso, medicina, etc).
     */
    public void heal(float amount) {
        this.health = Math.min(100f, this.health + amount);
    }

    /**
     * Verifica se o bebê está em estado crítico (alguma necessidade muito baixa).
     */
    public boolean isCritical() {
        return hunger < 10f || health < 10f;
    }

    /**
     * Verifica se o bebê está chorando (necessidades insatisfeitas).
     */
    public boolean isCrying() {
        return hunger < 30f || affection < 20f || health < 25f;
    }

    /**
     * Calcula um score de bem-estar (0.0 a 1.0).
     * Usado para determinar traços de personalidade ao crescer.
     */
    public float getWellbeingScore() {
        float totalPositive = positiveExperiences + 1f; // evita divisão por zero
        float totalNegative = negativeExperiences + 1f;
        return Math.min(1.0f, totalPositive / (totalPositive + totalNegative));
    }

    /**
     * Retorna tendência de personalidade baseada nos cuidados recebidos.
     * > 0.7 = criança feliz/sociável
     * 0.4-0.7 = criança neutra
     * < 0.4 = criança tímida/agressiva
     */
    public PersonalityTendency getPersonalityTendency() {
        float score = getWellbeingScore();
        if (score > 0.7f) return PersonalityTendency.SOCIABLE;
        if (score > 0.4f) return PersonalityTendency.NEUTRAL;
        return PersonalityTendency.WITHDRAWN;
    }

    /**
     * Tendência de personalidade derivada dos cuidados.
     */
    public enum PersonalityTendency {
        SOCIABLE,    // Sociável, alegre, aventureiro
        NEUTRAL,     // Equilibrado
        WITHDRAWN    // Tímido, medroso, potencialmente agressivo
    }
}
