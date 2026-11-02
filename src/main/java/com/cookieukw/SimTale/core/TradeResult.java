package com.cookieukw.SimTale.core;

/** Value-only result of a trade; inventory and currency integration stays with the caller. */
public record TradeResult(boolean accepted, String itemId, int quantity, long amount, Reason reason) {
    public enum Reason { NONE, INVALID_QUANTITY, UNKNOWN_ITEM, OUT_OF_STOCK, INSUFFICIENT_FUNDS, OVERFLOW }
    static TradeResult accepted(String itemId, int quantity, long amount) {
        return new TradeResult(true, itemId, quantity, amount, Reason.NONE);
    }
    static TradeResult rejected(Reason reason) { return new TradeResult(false, null, 0, 0, reason); }
}
