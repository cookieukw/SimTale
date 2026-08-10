package com.cookieukw.SimTale.core;

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
     * Lowercases and drops every character that is not an ASCII letter or digit.
     *
     * <p>Written as a character loop rather than
     * {@code toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")}, which is what this was.
     * {@code String.replaceAll} compiles the regular expression on every single call, and these
     * predicates run inside the radius scan — around 139 thousand blocks at radius 32, each one
     * asking whether it is a bed, a chest, a crop, farmland and three kinds of work post. That is
     * hundreds of thousands of regex compilations for a job that is a handful of character
     * comparisons.
     *
     * <p>Doing the case fold by hand also removes the Turkish-locale hazard entirely instead of
     * merely guarding against it: {@code 'A'..'Z'} is mapped arithmetically, so no locale is
     * consulted at all. Non-ASCII characters are dropped, exactly as the character class did.
     */
    public static String normalize(String id) {
        if (id == null || id.isEmpty()) return "";

        StringBuilder out = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                out.append((char) (c + ('a' - 'A')));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            }
        }
        return out.toString();
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
