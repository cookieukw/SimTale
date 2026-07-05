package com.cookieukw.SimTale.core.lifecycle;

/**
 * Estágios de crescimento de um NPC filho.
 * Cada estágio define a escala visual e a duração em dias in-game.
 */
public enum GrowthStage {
    BABY    (0.35f, 0,  3,  "Bebê"),
    TODDLER (0.50f, 4,  8,  "Criancinha"),
    CHILD   (0.70f, 9,  20, "Criança"),
    TEEN    (0.90f, 21, 40, "Adolescente"),
    ADULT   (1.00f, 41, -1, "Adulto");

    private final float scale;
    private final int startDay;
    private final int endDay; // -1 = permanente
    private final String displayName;

    GrowthStage(float scale, int startDay, int endDay, String displayName) {
        this.scale = scale;
        this.startDay = startDay;
        this.endDay = endDay;
        this.displayName = displayName;
    }

    public float getScale() {
        return scale;
    }

    public int getStartDay() {
        return startDay;
    }

    public int getEndDay() {
        return endDay;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Determina o estágio de crescimento baseado na idade em dias.
     */
    public static GrowthStage fromAge(int ageDays) {
        if (ageDays >= ADULT.startDay) return ADULT;
        if (ageDays >= TEEN.startDay) return TEEN;
        if (ageDays >= CHILD.startDay) return CHILD;
        if (ageDays >= TODDLER.startDay) return TODDLER;
        return BABY;
    }

    /**
     * Retorna o próximo estágio, ou null se já for ADULT.
     */
    public GrowthStage next() {
        GrowthStage[] stages = values();
        int idx = this.ordinal();
        if (idx + 1 < stages.length) {
            return stages[idx + 1];
        }
        return null;
    }
}
