package com.cookieukw.SimTale.core;

import java.util.Set;

/** What a travelling merchant chooses to carry on a given visit. */
public enum MerchantSpecialty {
    GENERAL("general", Set.of("ore", "bar", "gem", "food", "potion", "seed",
            "tool", "weapon", "armor", "deco", "ingredient")),
    PROVISIONS("provisions", Set.of("food")),
    ORE("ore", Set.of("ore", "bar")),
    MEDIC("medic", Set.of("potion", "food")),
    CURIOS("curios", Set.of("gem", "deco", "tool", "weapon", "armor")),
    SEEDS("seeds", Set.of("seed"));

    private final String key;
    private final TradingCatalog catalog;

    MerchantSpecialty(String key, Set<String> kinds) {
        this.key = key;
        this.catalog = MerchantCatalog.forKinds(kinds);
    }

    public String key() { return key; }

    public String translationKey() { return "ui.merchant.specialty." + key; }

    public TradingCatalog catalog() { return catalog; }

    /** Stable look-up used by persistence; unknown keys fall back to a general trader. */
    public static MerchantSpecialty fromKey(String key) {
        if (key != null) {
            for (MerchantSpecialty specialty : values()) {
                if (specialty.key.equals(key)) return specialty;
            }
        }
        return GENERAL;
    }
}
