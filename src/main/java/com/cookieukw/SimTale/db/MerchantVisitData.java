package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.MerchantIdentity;
import com.cookieukw.SimTale.core.MerchantSpecialty;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;

/** Plain Caskara DTO for one travelling merchant visit. */
public final class MerchantVisitData {
    public String visitId;
    public long seed;
    public String merchantName;
    public String specialty;
    public String gender;
    public int variant;
    public String worldName;
    public long departureTick;
    public List<OfferData> offers = new ArrayList<>();

    public MerchantVisitData() {
    }

    public MerchantVisitData(String visitId, long seed, String merchantName, String specialty,
                             String gender, int variant, String worldName,
                             long departureTick, MerchantStock stock) {
        this.visitId = visitId;
        this.seed = seed;
        this.merchantName = merchantName;
        this.specialty = specialty;
        this.gender = gender;
        this.variant = variant;
        this.worldName = worldName;
        this.departureTick = departureTick;
        if (stock != null) {
            for (MerchantStock.Offer offer : stock.offers()) {
                offers.add(new OfferData(offer.itemId(), offer.quantity(),
                        offer.buyPrice(), offer.sellPrice()));
            }
        }
    }

    public static MerchantVisitData of(MerchantVisitorComponent visitor, World world) {
        if (visitor == null) return null;
        return new MerchantVisitData(visitor.visitId, visitor.seed, visitor.merchantName,
                visitor.specialty, visitor.gender, visitor.variant,
                world == null ? "" : world.getName(), visitor.departureTick, visitor.stock);
    }

    /** Identity rebuilt from the stored visit, falling back to the stored name. */
    public MerchantIdentity toIdentity() {
        MerchantIdentity rolled = MerchantIdentity.roll(seed);
        String name = merchantName == null || merchantName.isBlank() ? rolled.name() : merchantName;
        MerchantSpecialty storedSpecialty = MerchantSpecialty.fromKey(specialty);
        Gender storedGender;
        try {
            storedGender = gender == null ? rolled.gender() : Gender.valueOf(gender);
        } catch (IllegalArgumentException e) {
            storedGender = rolled.gender();
        }
        int storedVariant = variant >= 1 && variant <= 200 ? variant : rolled.variant();
        return new MerchantIdentity(name, storedSpecialty, storedGender, storedVariant);
    }

    /** Rebuilds the mutable stock, preserving quantities already sold or bought. */
    public MerchantStock toStock() {
        List<MerchantStock.Offer> remaining = new ArrayList<>();
        if (offers != null) {
            for (OfferData offer : offers) {
                if (offer == null || offer.itemId == null || offer.itemId.isBlank()) continue;
                try {
                    remaining.add(new MerchantStock.Offer(offer.itemId, offer.quantity,
                            offer.buyPrice, offer.sellPrice));
                } catch (IllegalArgumentException ignored) {
                    // A corrupted row must not abort every other merchant on the server.
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
