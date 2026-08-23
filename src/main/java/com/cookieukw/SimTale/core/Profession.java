package com.cookieukw.SimTale.core;


import java.util.Locale;
import com.cookieukw.SimTale.logic.JobType;
import com.hypixel.hytale.server.core.Message;
import java.util.EnumSet;

public enum Profession {
    UNEMPLOYED("Desempregado", EnumSet.noneOf(JobType.class), "", true),
    MINER("Minerador", EnumSet.of(JobType.MINE), "pickaxe", false),
    FARMER("Fazendeiro", EnumSet.of(JobType.FARM, JobType.GATHER), "hoe", true),
    FISHERMAN("Pescador", EnumSet.of(JobType.FISH), "fishing_trap", true),
    LUMBERJACK("Lenhador", EnumSet.of(JobType.GATHER), "hatchet", true),
    GUARD("Guarda", EnumSet.noneOf(JobType.class), "sword", false),
    EXPLORER("Explorador", EnumSet.of(JobType.EXPLORE), "map", true),
    BUILDER("Construtor", EnumSet.of(JobType.BUILD), "hammer", true),
    HUNTER("Caçador", EnumSet.of(JobType.HUNT), "bow", false);

    public final String ptName;
    private final EnumSet<JobType> allowedJobs;
    public final String triggerItemKeyword;

    /**
     * Whether a child may actually do this job.
     *
     * <p>Every NPC rolls a random profession at birth and nothing gated work by age, so a child
     * could come out a Guard and go patrol the village perimeter looking for skeletons at 0.55
     * scale, or a Hunter and vanish on a two-minute expedition. A child helping on the farm is
     * charming; those two are not.
     *
     * <p>The three exclusions are exactly the ones with a mechanic behind them rather than a
     * flavour difference: GUARD fights, and MINER and HUNTER shrink the NPC out of the world for
     * an expedition. Everything else is walk somewhere, play an animation, come back with an item.
     *
     * <p>Restricted only means "not yet" — the profession stays assigned and starts working the
     * day the NPC reaches TEEN, so nobody loses their job for being young.
     */
    private final boolean safeForChildren;

    Profession(String ptName, EnumSet<JobType> allowedJobs, String triggerItemKeyword, boolean safeForChildren) {
        this.ptName = ptName;
        this.allowedJobs = allowedJobs;
        this.triggerItemKeyword = triggerItemKeyword;
        this.safeForChildren = safeForChildren;
    }

    /** @see #safeForChildren */
    public boolean isSafeForChildren() {
        return safeForChildren;
    }

    public boolean canDoJob(JobType job) {
        return allowedJobs.contains(job);
    }

    /**
     * Key for the localized profession name, e.g. {@code ui.prof.guard}.
     *
     * <p>Player-facing text must go through this instead of {@link #ptName}. Using the raw field
     * produced sentences like "gives you back your Pescador tools" for a player running the game
     * in English — an English sentence with a Portuguese word dropped in the middle.
     */
    public String translationKey() {
        return "ui.prof." + name().toLowerCase(Locale.ROOT);
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

    /** Localized, comma-separated list of the jobs this profession can perform. */
    public Message getJobList() {
        if (allowedJobs.isEmpty()) {
            return Message.translation("ui.job.none_specific");
        }
        Message list = Message.raw("");
        boolean first = true;
        for (JobType j : allowedJobs) {
            if (!first) list = list.insert(Message.raw(", "));
            list = list.insert(Message.translation(j.translationKey()));
            first = false;
        }
        return list;
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
