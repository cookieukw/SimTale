package com.cookieukw.SimTale.core;

import java.util.Locale;

/**
 * One way to compare a Hytale asset id.
 *
 * <p>There were nine of these scattered across the project, each written from scratch and each
 * subtly different: some lowercased with {@code Locale.ROOT} and some without, some used
 * {@code contains}, some {@code equalsIgnoreCase}, two stripped punctuation. Every id-matching bug
 * this project has hit came out of that spread, because an id almost never arrives in the shape
 * the asset file suggests:
 *
 * <ul>
 *   <li>a state-variant block is prefixed with {@code *} —
 *       {@code *plant_crop_carrot_block_state_definitions_stagefinal}</li>
 *   <li>state and rotation variants append suffixes that are absent at placement time</li>
 *   <li>the same logical thing appears as {@code WeddingRing}, {@code wedding_ring} and
 *       {@code simtale:WeddingRing} depending on which API returned it</li>
 * </ul>
 *
 * <p>So the rule here is: throw away everything that is not a letter or a digit, lowercase what is
 * left, and ask whether the keyword is in there. That collapses all four shapes above into one
 * comparison, and it is the only form that has survived contact with the engine.
 */
public final class AssetIds {

    private AssetIds() {
    }

    /**
     * Lowercases and drops every character that is not a letter or a digit.
     *
     * <p>{@code Locale.ROOT} is not decoration: the default-locale {@code toLowerCase()} maps
     * {@code I} to a dotless {@code ı} under a Turkish locale, so a server started with that
     * locale would stop recognising every id containing an uppercase I.
     */
    public static String normalize(String id) {
        if (id == null) return "";
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** True when {@code id} contains any of {@code keywords}, comparing normalized forms. */
    public static boolean containsAny(String id, String... keywords) {
        if (id == null || keywords == null) return false;
        String normalized = normalize(id);
        if (normalized.isEmpty()) return false;
        for (String keyword : keywords) {
            String needle = normalize(keyword);
            if (!needle.isEmpty() && normalized.contains(needle)) return true;
        }
        return false;
    }

    /** True when {@code id} contains none of {@code keywords}. Reads better than negating at the call site. */
    public static boolean containsNone(String id, String... keywords) {
        return !containsAny(id, keywords);
    }

    /**
     * True when {@code id} refers to {@code assetName}, tolerating decoration around it.
     *
     * <p>Deliberately not {@code equalsIgnoreCase}. That is what the fishing, lumber and farm post
     * checks used, and it is the exact mistake that made the blueprint marker silently do nothing:
     * the moment the engine hands back a state or rotation variant, an equality test stops
     * matching and there is no error to notice — the block simply is not recognised.
     */
    public static boolean matchesAsset(String id, String assetName) {
        return containsAny(id, assetName);
    }
}
