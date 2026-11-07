package com.cookieukw.SimTale.core;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the travelling merchant's item pool from
 * {@code Common/SimTale/merchant_catalog.json}.
 *
 * <p>The pool is intentionally much larger than a single visit (400 entries at the time of
 * writing). Each specialty filters it by {@code kind}, and {@link TradingCatalog#roll(long)}
 * picks five distinct entries, guaranteeing one featured item when the specialty has one.
 */
public final class MerchantCatalog {
    private static final String RESOURCE = "/Common/SimTale/merchant_catalog.json";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();
    private static final List<PooledEntry> POOL = load();

    private MerchantCatalog() {
    }

    public static List<PooledEntry> all() {
        return POOL;
    }

    /** Builds a catalog from every entry whose kind is in {@code kinds}. */
    public static TradingCatalog forKinds(Set<String> kinds) {
        List<TradingCatalog.Entry> entries = new ArrayList<>();
        for (PooledEntry pooled : POOL) {
            if (kinds.contains(pooled.kind)) entries.add(pooled.entry);
        }
        if (entries.isEmpty()) {
            // A typo in the JSON must not leave a merchant with an empty shop.
            for (PooledEntry pooled : POOL) entries.add(pooled.entry);
        }
        return new TradingCatalog(entries);
    }

    private static List<PooledEntry> load() {
        List<PooledEntry> loaded = new ArrayList<>();
        try (InputStream stream = MerchantCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.atWarning().log("[SimTale] Merchant catalog not found: " + RESOURCE);
                return fallback();
            }
            CatalogData data = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    CatalogData.class);
            if (data == null || data.entries == null) return fallback();
            Set<String> seen = new HashSet<>();
            for (EntryData raw : data.entries) {
                if (raw == null || raw.itemId == null || !seen.add(raw.itemId)) continue;
                try {
                    TradingCatalog.Entry entry = new TradingCatalog.Entry(
                            raw.itemId, raw.minQuantity, raw.maxQuantity,
                            raw.buyPrice, raw.sellPrice, raw.featured);
                    loaded.add(new PooledEntry(raw.kind == null ? "general" : raw.kind, entry));
                } catch (IllegalArgumentException ignored) {
                    // One malformed row must not discard the other 399.
                }
            }
            LOGGER.atInfo().log("[SimTale] Merchant catalog loaded: " + loaded.size() + " entries");
        } catch (Exception e) {
            LOGGER.atWarning().log("[SimTale] Failed to load merchant catalog: " + e.getMessage());
            return fallback();
        }
        return loaded.isEmpty() ? fallback() : loaded;
    }

    /** Minimal stock if the resource is missing, so the merchant still opens and trades. */
    private static List<PooledEntry> fallback() {
        List<PooledEntry> fallback = new ArrayList<>();
        fallback.add(new PooledEntry("food", TradingCatalog.item("Food_Bread", 6, 16, 4, 2)));
        fallback.add(new PooledEntry("food", TradingCatalog.item("Food_Cheese", 4, 10, 6, 3)));
        fallback.add(new PooledEntry("ore", TradingCatalog.item("Ore_Copper", 8, 20, 4, 2)));
        fallback.add(new PooledEntry("ore", TradingCatalog.item("Ore_Iron", 5, 14, 7, 3)));
        fallback.add(new PooledEntry("seed", TradingCatalog.item("Plant_Seeds_Wheat", 6, 18, 3, 1)));
        fallback.add(new PooledEntry("potion", TradingCatalog.item("Potion_Health", 1, 4, 16, 8)));
        fallback.add(new PooledEntry("gem", TradingCatalog.featured("Rock_Gem_Emerald", 1, 3, 24, 12)));
        fallback.add(new PooledEntry("bar", TradingCatalog.featured("Ingredient_Bar_Iron", 1, 4, 14, 7)));
        return fallback;
    }

    public record PooledEntry(String kind, TradingCatalog.Entry entry) {
    }

    private static final class CatalogData {
        int version;
        List<EntryData> entries;
    }

    private static final class EntryData {
        String itemId;
        String kind;
        boolean featured;
        int minQuantity;
        int maxQuantity;
        long buyPrice;
        long sellPrice;
    }
}
