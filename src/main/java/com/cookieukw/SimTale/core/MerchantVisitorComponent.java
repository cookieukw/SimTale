package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Marks a temporary travelling merchant and stores the visit's rolled stock. */
public final class MerchantVisitorComponent implements Component<EntityStore> {
    public long departureTick;
    public MerchantStock stock;

    public MerchantVisitorComponent() {
    }

    public MerchantVisitorComponent(long departureTick, MerchantStock stock) {
        this.departureTick = departureTick;
        this.stock = stock;
    }

    @Override
    public MerchantVisitorComponent clone() {
        return new MerchantVisitorComponent(departureTick,
                stock == null ? null : new MerchantStock(stock.offers().stream()
                        .map(offer -> new MerchantStock.Offer(offer.itemId(), offer.quantity(),
                                offer.buyPrice(), offer.sellPrice())).toList()));
    }
}
