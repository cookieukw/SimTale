package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Marks a temporary travelling merchant and stores the visit's rolled identity and stock. */
public final class MerchantVisitorComponent implements Component<EntityStore> {
    public String visitId;
    public long seed;
    public String merchantName;
    public String specialty;
    public String gender;
    public int variant;
    public long departureTick;
    public MerchantStock stock;

    public MerchantVisitorComponent() {
    }

    public MerchantVisitorComponent(String visitId, long seed, String merchantName,
                                    String specialty, String gender, int variant,
                                    long departureTick, MerchantStock stock) {
        this.visitId = visitId;
        this.seed = seed;
        this.merchantName = merchantName;
        this.specialty = specialty;
        this.gender = gender;
        this.variant = variant;
        this.departureTick = departureTick;
        this.stock = stock;
    }

    @Override
    public MerchantVisitorComponent clone() {
        return new MerchantVisitorComponent(visitId, seed, merchantName, specialty, gender, variant,
                departureTick,
                stock == null ? null : new MerchantStock(stock.offers().stream()
                        .map(offer -> new MerchantStock.Offer(offer.itemId(), offer.quantity(),
                                offer.buyPrice(), offer.sellPrice())).toList()));
    }
}
