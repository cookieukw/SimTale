package com.cookieukw.SimTale.core;


import java.util.Locale;
import com.cookieukw.SimTale.logic.JobType;
import com.hypixel.hytale.server.core.Message;
import java.util.EnumSet;

public enum Profession {
    UNEMPLOYED("Desempregado", EnumSet.noneOf(JobType.class), "", true),
    MINER("Minerador", EnumSet.of(JobType.MINE), "pickaxe", false),
    FARMER("Fazendeiro", EnumSet.of(JobType.FARM, JobType.GATHER), "hoe", true),
    FISHERMAN("Pescador", EnumSet.of(JobType.FISH), "fishingtrap", true),
    LUMBERJACK("Lenhador", EnumSet.of(JobType.GATHER), "hatchet", true),

    /**
     * Any weapon makes a Guard, not just a sword.
     *
     * <p>{@code triggerItemKeyword} here is a display-only placeholder (non-empty just so the
     * "returning your gear" flavor text in {@code InteractionManager} still fires) — the real
     * check is {@link #matches}, which defers to {@link WeaponCategoryRegistry} so any melee
     * <em>or</em> ranged weapon works, including ones a mod adds later. Checked after
     * {@link #HUNTER} in {@link #MATCH_PRIORITY} so a bow still makes a Hunter as before; anything
     * else weapon-shaped (swords, axes, a mod's new firearm) falls through to here instead.
     */
    GUARD("Guarda", EnumSet.noneOf(JobType.class), "weapon", false) {
        @Override
        boolean matches(String normalizedItemId) {
            return WeaponCategoryRegistry.of(normalizedItemId) != null;
        }
    },

    /**
     * "map" alone also matched "Maple" (wood/sapling/leaves) — {@code toolmap} is the normalized
     * form of the actual {@code Tool_Map} item and does not have that problem.
     */
    EXPLORER("Explorador", EnumSet.of(JobType.EXPLORE), "toolmap", true),

    /**
     * "hammer" alone also matched "Hammerhead" (the shark) — {@code toolhammer} is the normalized
     * form of the actual {@code Tool_Hammer_*} items and does not have that problem.
     */
    BUILDER("Construtor", EnumSet.of(JobType.BUILD), "toolhammer", true),

    /**
     * Unchanged in spirit — still specifically a bow, not "any ranged weapon" — but matched
     * against the real item names ({@code shortbow}/{@code crossbow}) instead of the bare word
     * "bow", which also matched "Rainbow" (the trout).
     */
    HUNTER("Caçador", EnumSet.of(JobType.HUNT), "shortbow", false) {
        @Override
        boolean matches(String normalizedItemId) {
            return normalizedItemId.contains("weaponshortbow") || normalizedItemId.contains("weaponcrossbow");
        }
    };

    /**
     * Professions are checked against a held item in this order, independent of the declaration
     * order above (which must not change — it is the enum's ordinal, and existing saved NPCs may
     * depend on it). {@link #HUNTER} comes before {@link #GUARD} on purpose: both can now match a
     * ranged weapon, and the bow should keep making a Hunter, the same as before this change.
     */
    private static final Profession[] MATCH_PRIORITY = {
            MINER, FARMER, FISHERMAN, LUMBERJACK, HUNTER, GUARD, EXPLORER, BUILDER
    };

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
        String normalized = AssetIds.normalize(itemId);
        if (normalized.isEmpty()) return null;
        for (Profession p : MATCH_PRIORITY) {
            if (p.matches(normalized)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Whether this profession is triggered by the (already normalized, per {@link AssetIds})
     * item id. The default is the plain keyword-contains check every profession used to share;
     * {@link #GUARD} and {@link #HUNTER} override it to consult {@link WeaponCategoryRegistry}
     * instead of a single hardcoded word.
     */
    boolean matches(String normalizedItemId) {
        return !triggerItemKeyword.isEmpty() && normalizedItemId.contains(triggerItemKeyword);
    }
}
