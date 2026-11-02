package com.cookieukw.SimTale.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A single merchant visit's mutable, thread-safe trading state. */
public final class MerchantStock {
    private final Map<String, Offer> offers = new LinkedHashMap<>();

    public MerchantStock(List<Offer> offers) {
        if (offers == null) throw new NullPointerException("offers");
        for (Offer offer : offers) {
            if (this.offers.put(offer.itemId(), offer) != null) throw new IllegalArgumentException("Duplicate item");
        }
    }

    public synchronized List<Offer> offers() { return Collections.unmodifiableList(new ArrayList<>(offers.values())); }
    public synchronized Offer offer(String itemId) { return offers.get(itemId); }

    /** Buys from the merchant. No state changes occur when funds or stock are insufficient. */
    public synchronized TradeResult buy(String itemId, int quantity, long availableFunds) {
        if (quantity <= 0) return TradeResult.rejected(TradeResult.Reason.INVALID_QUANTITY);
        Offer offer = offers.get(itemId);
        if (offer == null) return TradeResult.rejected(TradeResult.Reason.UNKNOWN_ITEM);
        long total;
        try { total = Math.multiplyExact(offer.buyPrice(), quantity); }
        catch (ArithmeticException e) { return TradeResult.rejected(TradeResult.Reason.OVERFLOW); }
        if (offer.quantity() < quantity) return TradeResult.rejected(TradeResult.Reason.OUT_OF_STOCK);
        if (availableFunds < total) return TradeResult.rejected(TradeResult.Reason.INSUFFICIENT_FUNDS);
        offer.quantity -= quantity;
        return TradeResult.accepted(itemId, quantity, total);
    }

    /** Buys items from the player at the offer's sell price and replenishes merchant stock. */
    public synchronized TradeResult sell(String itemId, int quantity) {
        if (quantity <= 0) return TradeResult.rejected(TradeResult.Reason.INVALID_QUANTITY);
        Offer offer = offers.get(itemId);
        if (offer == null) return TradeResult.rejected(TradeResult.Reason.UNKNOWN_ITEM);
        long total;
        try { total = Math.multiplyExact(offer.sellPrice(), quantity); Math.addExact(offer.quantity(), quantity); }
        catch (ArithmeticException e) { return TradeResult.rejected(TradeResult.Reason.OVERFLOW); }
        offer.quantity += quantity;
        return TradeResult.accepted(itemId, quantity, total);
    }

    public static final class Offer {
        private final String itemId;
        private int quantity;
        private final long buyPrice;
        private final long sellPrice;
        public Offer(String itemId, int quantity, long buyPrice, long sellPrice) {
            if (itemId == null || itemId.isBlank() || quantity < 0 || buyPrice <= 0 || sellPrice <= 0 || sellPrice >= buyPrice)
                throw new IllegalArgumentException("Invalid offer");
            this.itemId = itemId; this.quantity = quantity; this.buyPrice = buyPrice; this.sellPrice = sellPrice;
        }
        public String itemId() { return itemId; }
        public int quantity() { return quantity; }
        public long buyPrice() { return buyPrice; }
        public long sellPrice() { return sellPrice; }
    }
}
