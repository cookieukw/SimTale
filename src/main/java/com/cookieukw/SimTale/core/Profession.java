package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.logic.JobType;
import java.util.EnumSet;

public enum Profession {
    UNEMPLOYED("Desempregado", EnumSet.noneOf(JobType.class), ""),
    MINER("Minerador", EnumSet.of(JobType.MINE), "pickaxe"),
    FARMER("Fazendeiro", EnumSet.of(JobType.FARM, JobType.GATHER), "hoe"),
    FISHERMAN("Pescador", EnumSet.of(JobType.FISH), "fishing_trap"),
    LUMBERJACK("Lenhador", EnumSet.of(JobType.GATHER), "hatchet"),
    GUARD("Guarda", EnumSet.noneOf(JobType.class), "sword"),
    EXPLORER("Explorador", EnumSet.of(JobType.EXPLORE), "map"),
    BUILDER("Construtor", EnumSet.of(JobType.BUILD), "hammer"),
    HUNTER("Caçador", EnumSet.of(JobType.HUNT), "bow");

    public final String ptName;
    private final EnumSet<JobType> allowedJobs;
    public final String triggerItemKeyword;

    Profession(String ptName, EnumSet<JobType> allowedJobs, String triggerItemKeyword) {
        this.ptName = ptName;
        this.allowedJobs = allowedJobs;
        this.triggerItemKeyword = triggerItemKeyword;
    }

    public boolean canDoJob(JobType job) {
        return allowedJobs.contains(job);
    }

    /** Returns a comma-separated list of Portuguese job names this profession can perform. */
    public String getJobListPt() {
        if (allowedJobs.isEmpty()) return "nenhum trabalho específico";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (JobType j : allowedJobs) {
            if (!first) sb.append(", ");
            sb.append(j.getPortugueseName());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Finds a profession based on the item ID the player is holding.
     * Matches by checking if the item ID contains the trigger keyword.
     * Returns null if no profession matches.
     */
    public static Profession fromItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        String lower = itemId.toLowerCase();
        for (Profession p : values()) {
            if (p == UNEMPLOYED || p.triggerItemKeyword.isEmpty()) continue;
            if (lower.contains(p.triggerItemKeyword)) {
                return p;
            }
        }
        return null;
    }
}
