package com.cookieukw.SimTale.logic;

/**
 * Represents the current job/task of an NPC.
 */
public enum JobType {
    NONE(0, "nenhum"),
    MINE(15, "minerar"), // Reduced to 15 seconds for testing
    FARM(15, "farmar"),
    GATHER(15, "coletar"),
    FISH(15, "pescar"),
    EXPLORE(15, "explorar");

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
}
