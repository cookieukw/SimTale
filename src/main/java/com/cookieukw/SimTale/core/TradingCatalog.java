package com.cookieukw.SimTale.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/** Immutable source of the items a travelling merchant may carry. */
public final class TradingCatalog {
    /** The trade screen has five rows; a visit always offers exactly this many entries. */
    public static final int OFFERS_PER_VISIT = 5;

    private final List<Entry> entries;

    public TradingCatalog(List<Entry> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entries() { return entries; }

    /** Rolls a visit without using global/random mutable state. The same seed gives the same stock. */
    public MerchantStock roll(long visitSeed) {
        SplittableRandom random = new SplittableRandom(visitSeed);
        List<Entry> pool = new ArrayList<>(entries);
        List<Entry> chosen = new ArrayList<>();
        int offerCount = Math.min(OFFERS_PER_VISIT, pool.size());

        /* A general or curios visit should reliably offer something worth looking at, not five
        loaves of bread. One featured gear/gem/bar entry is guaranteed when the pool has one, then
        one ordinary entry, and the remaining slots are filled from whatever is left.
        */
        pick(pool, chosen, random, true);
        pick(pool, chosen, random, false);
        while (chosen.size() < offerCount && !pool.isEmpty()) {
            chosen.add(pool.remove(random.nextInt(pool.size())));
        }

        List<MerchantStock.Offer> offers = new ArrayList<>();
        for (Entry entry : chosen) {
            int quantity = entry.minimumQuantity == entry.maximumQuantity
                    ? entry.minimumQuantity
                    : random.nextInt(entry.minimumQuantity, entry.maximumQuantity + 1);
            long buyPrice = Math.max(2L, price(random, entry.buyPrice));
            long sellPrice = Math.max(1L, Math.min(buyPrice - 1, price(random, entry.sellPrice)));
            offers.add(new MerchantStock.Offer(entry.itemId, quantity, buyPrice, sellPrice));
        }
        return new MerchantStock(offers);
    }

    private static void pick(List<Entry> pool, List<Entry> chosen, SplittableRandom random,
                             boolean featured) {
        List<Entry> candidates = new ArrayList<>();
        for (Entry entry : pool) {
            if (entry.featured == featured) candidates.add(entry);
        }
        if (candidates.isEmpty()) return;
        Entry selected = candidates.get(random.nextInt(candidates.size()));
        pool.remove(selected);
        chosen.add(selected);
    }

    private static long price(SplittableRandom random, long base) {
        long variance = Math.max(1L, base / 10L);
        long low = Math.max(1L, base - variance);
        long high = Math.addExact(base, variance);
        return random.nextLong(low, high + 1);
    }

    public static Entry item(String itemId, int minimumQuantity, int maximumQuantity,
                             long buyPrice, long sellPrice) {
        return new Entry(itemId, minimumQuantity, maximumQuantity, buyPrice, sellPrice, false);
    }

    /** Same as {@link #item} but marks an entry as premium stock for the visit roller. */
    public static Entry featured(String itemId, int minimumQuantity, int maximumQuantity,
                                 long buyPrice, long sellPrice) {
        return new Entry(itemId, minimumQuantity, maximumQuantity, buyPrice, sellPrice, true);
    }

    public static final class Entry {
        private final String itemId;
        private final int minimumQuantity;
        private final int maximumQuantity;
        private final long buyPrice;
        private final long sellPrice;
        private final boolean featured;

        public Entry(String itemId, int minimumQuantity, int maximumQuantity,
                     long buyPrice, long sellPrice) {
            this(itemId, minimumQuantity, maximumQuantity, buyPrice, sellPrice, false);
        }

        public Entry(String itemId, int minimumQuantity, int maximumQuantity,
                     long buyPrice, long sellPrice, boolean featured) {
            if (itemId == null || itemId.isBlank() || minimumQuantity < 0
                    || maximumQuantity < minimumQuantity || buyPrice <= 0 || sellPrice <= 0
                    || sellPrice >= buyPrice) throw new IllegalArgumentException("Invalid trading entry");
            this.itemId = itemId;
            this.minimumQuantity = minimumQuantity;
            this.maximumQuantity = maximumQuantity;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.featured = featured;
        }

        public String itemId() { return itemId; }
        public int minimumQuantity() { return minimumQuantity; }
        public int maximumQuantity() { return maximumQuantity; }
        public long buyPrice() { return buyPrice; }
        public long sellPrice() { return sellPrice; }
        public boolean featured() { return featured; }
    }
}
