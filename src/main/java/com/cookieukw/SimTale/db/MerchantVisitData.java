package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.MerchantStock;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;

/** Plain Caskara DTO for one travelling merchant visit. */
public final class MerchantVisitData {
    public long seed;
    public String merchantName;
    public String worldName;
    public long departureTick;
    public List<OfferData> offers = new ArrayList<>();

    public MerchantVisitData() {
    }

    public MerchantVisitData(long seed, String merchantName, String worldName,
                             long departureTick, MerchantStock stock) {
        this.seed = seed;
        this.merchantName = merchantName;
        this.worldName = worldName;
        this.departureTick = departureTick;
        if (stock != null) {
            for (MerchantStock.Offer offer : stock.offers()) {
                offers.add(new OfferData(offer.itemId(), offer.quantity(),
                        offer.buyPrice(), offer.sellPrice()));
            }
        }
    }

    public static MerchantVisitData of(long seed, String merchantName, World world,
                                       long departureTick, MerchantStock stock) {
        return new MerchantVisitData(seed, merchantName,
                world == null ? "" : world.getName(), departureTick, stock);
    }

    /** Rebuilds the mutable stock, preserving quantities already sold or bought. */
    public MerchantStock toStock() {
        List<MerchantStock.Offer> remaining = new ArrayList<>();
        if (offers != null) {
            for (OfferData offer : offers) {
                if (offer != null) {
                    remaining.add(new MerchantStock.Offer(offer.itemId, offer.quantity,
                            offer.buyPrice, offer.sellPrice));
                }
            }
        }
        return new MerchantStock(remaining);
    }

    /** Serializable offer snapshot; fields are public intentionally for Caskara. */
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
