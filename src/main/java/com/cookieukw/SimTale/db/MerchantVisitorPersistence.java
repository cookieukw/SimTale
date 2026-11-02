package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Shell;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Isolated persistence API for temporary merchant visitors. */
public final class MerchantVisitorPersistence {
    private static final String SHELL_NAME = "simtale_merchants";

    private MerchantVisitorPersistence() {
    }

    public static Shell worldShell() {
        return worldShell(WorldUtil.first());
    }

    public static Shell worldShell(World world) {
        return world != null ? Caskara.shell(world, SHELL_NAME) : Caskara.shell(SHELL_NAME);
    }

    public static void save(UUID merchantId, MerchantVisitorComponent visitor) {
        if (merchantId == null || visitor == null || visitor.stock == null) return;
        saveData(merchantId.toString(), toData(merchantId.toString(), visitor));
    }

    public static MerchantVisitorComponent load(UUID merchantId) {
        if (merchantId == null) return null;
        MerchantVisitorData data;
        try {
            data = worldShell().core(MerchantVisitorData.class)
                    .extract(merchantId.toString()).sync().orElse(null);
        } catch (Exception e) {
            warn("ler o mercador " + merchantId, e);
            return null;
        }
        return data == null ? null : fromData(data);
    }

    public static List<MerchantVisitorData> listAll() {
        try {
            List<MerchantVisitorData> data = worldShell().core(MerchantVisitorData.class).extractAll();
            return data == null ? new ArrayList<>() : data;
        } catch (Exception e) {
            warn("listar mercadores", e);
            return new ArrayList<>();
        }
    }

    public static void delete(UUID merchantId) {
        if (merchantId == null) return;
        try {
            worldShell().core(MerchantVisitorData.class).discard(merchantId.toString());
        } catch (Exception e) {
            warn("apagar o mercador " + merchantId, e);
        }
    }

    private static void saveData(String id, MerchantVisitorData data) {
        try {
            worldShell().core(MerchantVisitorData.class).preserve(id, data);
        } catch (Exception e) {
            warn("salvar o mercador " + id, e);
        }
    }

    private static MerchantVisitorData toData(String id, MerchantVisitorComponent visitor) {
        MerchantVisitorData data = new MerchantVisitorData();
        data.id = id;
        data.departureTick = visitor.departureTick;
        for (MerchantStock.Offer offer : visitor.stock.offers()) {
            data.offers.add(new MerchantVisitorData.OfferData(offer.itemId(), offer.quantity(),
                    offer.buyPrice(), offer.sellPrice()));
        }
        return data;
    }

    private static MerchantVisitorComponent fromData(MerchantVisitorData data) {
        List<MerchantStock.Offer> offers = new ArrayList<>();
        if (data.offers != null) {
            for (MerchantVisitorData.OfferData offer : data.offers) {
                if (offer != null) {
                    offers.add(new MerchantStock.Offer(offer.itemId, offer.quantity,
                            offer.buyPrice, offer.sellPrice));
                }
            }
        }
        return new MerchantVisitorComponent(data.departureTick, new MerchantStock(offers));
    }

    private static void warn(String action, Exception e) {
        HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao " + action + ": " + e.getMessage());
    }
}
