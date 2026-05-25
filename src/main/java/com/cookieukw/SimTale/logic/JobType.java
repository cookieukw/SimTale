package com.cookieukw.SimTale.logic;

/**
 * Represents the current job/task of an NPC.
 */
public enum JobType {
    NONE(0, "nenhum"),
    MINE(300, "minerar"), // Duration in seconds
    FARM(240, "farmar"),
    GATHER(180, "coletar"),
    FISH(200, "pescar"),
    EXPLORE(400, "explorar");

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
