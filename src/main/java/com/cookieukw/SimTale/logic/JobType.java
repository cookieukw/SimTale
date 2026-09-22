package com.cookieukw.SimTale.logic;


import java.util.Locale;
/**
 * Represents the current job/task of an NPC.
 */
public enum JobType {
    NONE(0, "nenhum"),
    MINE(15, "minerar"), // Reduced to 15 seconds for testing
    FARM(15, "farmar"),
    GATHER(15, "coletar"),
    FISH(15, "pescar"),
    EXPLORE(15, "explorar"),
    BUILD(15, "construir"),
    HUNT(15, "caçar");

    private final int durationSeconds;
    private final String portugueseName;

    JobType(int durationSeconds, String portugueseName) {
        this.durationSeconds = durationSeconds;
        this.portugueseName = portugueseName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getPortugueseName() {
        return portugueseName;
    }

    /**
     * Key for the localized job name, e.g. {@code ui.job.hunt}.
     *
     * <p>Player-facing text must use this rather than {@link #getPortugueseName()}, which exists
     * only as the enum's own label and leaks Portuguese into sentences in other languages.
     */
    public String translationKey() {
        return "ui.job." + name().toLowerCase(Locale.ROOT);
    }
}
