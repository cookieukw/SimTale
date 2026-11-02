package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/** Marks a temporary travelling merchant and stores the visit's rolled stock. */
public final class MerchantVisitorComponent implements Component<EntityStore> {
    public long departureTick;
    public List<String> stock = new ArrayList<>();

    public MerchantVisitorComponent() {
    }

    public MerchantVisitorComponent(long departureTick, List<String> stock) {
        this.departureTick = departureTick;
        this.stock = new ArrayList<>(stock);
    }

    @Override
    public MerchantVisitorComponent clone() {
        return new MerchantVisitorComponent(departureTick, stock);
    }
}
