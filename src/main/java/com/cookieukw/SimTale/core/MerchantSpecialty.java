package com.cookieukw.SimTale.core;

import java.util.List;

/** What a travelling merchant chooses to carry on a given visit. */
public enum MerchantSpecialty {
    GENERAL("general", new TradingCatalog(List.of(
            TradingCatalog.item("Food_Bread", 6, 16, 4, 2),
            TradingCatalog.item("Plant_Seeds_Carrot", 6, 18, 3, 1),
            TradingCatalog.item("Ingredient_Bar_Iron", 1, 4, 14, 7)))),
    PROVISIONS("provisions", new TradingCatalog(List.of(
            TradingCatalog.item("Food_Cheese", 4, 10, 6, 3),
            TradingCatalog.item("Food_Pie_Apple", 2, 6, 9, 4),
            TradingCatalog.item("Food_Fish_Raw", 5, 14, 5, 2)))),
    ORE("ore", new TradingCatalog(List.of(
            TradingCatalog.item("Ore_Copper", 8, 20, 4, 2),
            TradingCatalog.item("Ore_Iron", 5, 14, 7, 3),
            TradingCatalog.item("Ingredient_Charcoal", 4, 12, 5, 2)))),
    MEDIC("medic", new TradingCatalog(List.of(
            TradingCatalog.item("Food_Health_Regen_Medium", 1, 4, 16, 8),
            TradingCatalog.item("Food_Salad_Berry", 3, 8, 7, 3),
            TradingCatalog.item("Plant_Flower_Common_Yellow", 4, 12, 3, 1)))),
    CURIOS("curios", new TradingCatalog(List.of(
            TradingCatalog.item("Rock_Gem_Emerald", 1, 3, 24, 12),
            TradingCatalog.item("Deco_Lantern", 1, 3, 12, 5),
            TradingCatalog.item("Objective_Treasure_Map", 1, 2, 18, 8)))),
    SEEDS("seeds", new TradingCatalog(List.of(
            TradingCatalog.item("Plant_Seeds_Wheat", 6, 18, 3, 1),
            TradingCatalog.item("Plant_Seeds_Corn", 6, 18, 4, 2),
            TradingCatalog.item("Plant_Seeds_Tomato", 4, 14, 4, 2))));

    private final String key;
    private final TradingCatalog catalog;

    MerchantSpecialty(String key, TradingCatalog catalog) {
        this.key = key;
        this.catalog = catalog;
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
