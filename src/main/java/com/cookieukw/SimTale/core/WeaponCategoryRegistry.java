package com.cookieukw.SimTale.core;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a held item's {@link WeaponCategory}, so combat behavior can key off "melee vs ranged"
 * instead of a hardcoded item name.
 *
 * <p>Two layers, checked in this order:
 * <ol>
 *   <li>{@code simtale-weapons.json}, next to the server jar — a flat
 *       {@code {"itemIdOrFragment": "MELEE"|"RANGED"}} map, matched the same substring way as
 *       everything below (an entry does not need to be the item's full id). This is the
 *       mod-integration point: any mod that adds a weapon SimTale does not recognize — or the
 *       server owner, for one it recognizes wrong — registers it here without touching SimTale's
 *       code.</li>
 *   <li>A small built-in keyword list covering vanilla Hytale weapons, matched the way the rest of
 *       the project matches asset ids (see {@link AssetIds}): normalized, substring, never
 *       exact-equals, because the same weapon shows up as {@code Weapon_Sword_Iron},
 *       {@code weapon_sword_iron} or with a namespace prefix depending on which API handed it
 *       over.</li>
 * </ol>
 *
 * <p>The built-in keywords are deliberately more specific than the obvious single word.
 * {@code "bow"} looks right for a bow, but {@code Fish_Trout_Rainbow_Item} also contains "bow" —
 * that exact mistake is what mis-triggered {@link Profession#fromItemId} for a rainbow trout, a
 * maple sapling ("map") and a hammerhead shark ("hammer"). Preferring the fuller, real item name
 * ({@code "shortbow"}, {@code "crossbow"}) or a "weapon"-qualified form ({@code "weaponaxe"}, to
 * stay clear of {@code Tool_Pickaxe}) keeps the same substring style without repeating that
 * mistake here.
 */
public final class WeaponCategoryRegistry {

    private static final SimLog LOGGER = SimLog.forClass(WeaponCategoryRegistry.class);
    private static final File CONFIG_FILE = new File("simtale-weapons.json");

    private static final String[] MELEE_KEYWORDS = {
            "weaponsword", "weaponlongsword", "weaponaxe", "weaponbattleaxe",
            "weapondagger", "weaponspear", "weaponmace"
    };

    private static final String[] RANGED_KEYWORDS = {
            "weaponshortbow", "weaponcrossbow", "weapongun", "weaponhandgun",
            "weaponblowgun", "weaponrifle", "weaponassaultrifle"
    };

    // Normalized keyword/fragment -> category, loaded from simtale-weapons.json. Checked before
    // the built-in lists above, so a server owner can also use it to override a vanilla item.
    private static volatile Map<String, WeaponCategory> configOverrides = new LinkedHashMap<>();

    private WeaponCategoryRegistry() {
    }

    /**
     * Reads {@code simtale-weapons.json} from the server's working directory, if present.
     * Safe to call more than once — replaces the override map atomically, so a future reload
     * command could call this again without restarting the server.
     */
    public static void load() {
        if (!CONFIG_FILE.exists()) {
            configOverrides = new LinkedHashMap<>();
            return;
        }
        try {
            String content = Files.readString(CONFIG_FILE.toPath());
            Map<String, WeaponCategory> parsed = parseFlatStringMap(content);
            configOverrides = parsed;
            LOGGER.info("[SimTale] " + parsed.size() + " categoria(s) de arma carregada(s) de simtale-weapons.json");
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Falha ao ler simtale-weapons.json, mantendo so as categorias nativas: " + e);
            configOverrides = new LinkedHashMap<>();
        }
    }

    /**
     * Best-effort flat-object reader: {@code {"key": "VALUE", ...}}. Deliberately not a general
     * JSON parser — the config shape is fixed to one level of string:string pairs, and this
     * project already prefers a small hand-rolled reader (see
     * {@link com.cookieukw.SimTale.ai.JsonParser}) over pulling in a JSON library for that.
     */
    private static Map<String, WeaponCategory> parseFlatStringMap(String json) {
        Map<String, WeaponCategory> out = new LinkedHashMap<>();
        if (json == null) return out;

        Pattern entry = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = entry.matcher(json);
        while (m.find()) {
            String key = AssetIds.normalize(m.group(1));
            String rawValue = m.group(2).trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty()) continue;
            try {
                out.put(key, WeaponCategory.valueOf(rawValue));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[SimTale] simtale-weapons.json: categoria desconhecida '" + m.group(2)
                        + "' para '" + m.group(1) + "' (use MELEE ou RANGED, ignorando essa entrada)");
            }
        }
        return out;
    }

    /** The category this item belongs to, or {@code null} if it is not recognized as a weapon at all. */
    public static WeaponCategory of(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        String normalized = AssetIds.normalize(itemId);
        if (normalized.isEmpty()) return null;

        for (Map.Entry<String, WeaponCategory> e : configOverrides.entrySet()) {
            if (normalized.contains(e.getKey())) return e.getValue();
        }
        for (String kw : RANGED_KEYWORDS) {
            if (normalized.contains(kw)) return WeaponCategory.RANGED;
        }
        for (String kw : MELEE_KEYWORDS) {
            if (normalized.contains(kw)) return WeaponCategory.MELEE;
        }
        return null;
    }

    /** Whether this item is recognized as a weapon at all, melee or ranged. */
    public static boolean isWeapon(String itemId) {
        return of(itemId) != null;
    }
}
