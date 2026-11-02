package com.cookieukw.SimTale.db;

import java.util.ArrayList;
import java.util.List;

/** Plain Caskara DTO for a travelling merchant's visit state. */
public final class MerchantVisitorData {
    public String id;
    public long departureTick;
    public List<OfferData> offers = new ArrayList<>();

    public MerchantVisitorData() {
    }

    public static final class OfferData {
        public String itemId;
        public int quantity;
        public long buyPrice;
        public long sellPrice;

        public OfferData() {
        }

        public OfferData(String itemId, int quantity, long buyPrice, long sellPrice) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }
}
