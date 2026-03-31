package com.cookieukw.SimTale.logic;

/**
 * Represents the current job/task of an NPC.
 */
public enum JobType {
    NONE(0),
    MINE(300), // Duration in seconds
    FARM(240),
    GATHER(180),
    FISH(200),
    EXPLORE(400);

    private final int durationSeconds;

    JobType(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }
}
