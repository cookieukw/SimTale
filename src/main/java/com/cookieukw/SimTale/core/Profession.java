package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.logic.JobType;
import java.util.EnumSet;

public enum Profession {
    UNEMPLOYED("Desempregado", EnumSet.noneOf(JobType.class)),
    MINER("Minerador", EnumSet.of(JobType.MINE)),
    FARMER("Fazendeiro", EnumSet.of(JobType.FARM, JobType.GATHER)),
    FISHERMAN("Pescador", EnumSet.of(JobType.FISH)),
    LUMBERJACK("Lenhador", EnumSet.of(JobType.GATHER)),
    GUARD("Guarda", EnumSet.noneOf(JobType.class)),
    EXPLORER("Explorador", EnumSet.of(JobType.EXPLORE)),
    BUILDER("Construtor", EnumSet.of(JobType.BUILD));

    public final String ptName;
    private final EnumSet<JobType> allowedJobs;

    Profession(String ptName, EnumSet<JobType> allowedJobs) {
        this.ptName = ptName;
        this.allowedJobs = allowedJobs;
    }

    public boolean canDoJob(JobType job) {
        return allowedJobs.contains(job);
    }
}
