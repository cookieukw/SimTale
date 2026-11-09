package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Shell;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;

/** Caskara persistence for merchant visits, isolated per Hytale world. */
public final class MerchantVisitPersistence {
    private static final String SHELL_NAME = "simtale_merchants";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private MerchantVisitPersistence() {
    }

    public static Shell shell(World world) {
        return world == null ? Caskara.shell(SHELL_NAME) : Caskara.shell(world, SHELL_NAME);
    }

    public static void save(World world, String visitId, MerchantVisitData visit) {
        if (visitId == null || visitId.isBlank() || visit == null) return;
        try {
            shell(world).core(MerchantVisitData.class).preserve(visitId, visit);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to save merchant visit: " + e.getMessage());
        }
    }

    public static MerchantVisitData load(World world, String visitId) {
        if (visitId == null || visitId.isBlank()) return null;
        try {
            return shell(world).core(MerchantVisitData.class).extract(visitId).sync().orElse(null);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to load merchant visit: " + e.getMessage());
            return null;
        }
    }

    public static List<MerchantVisitData> list(World world) {
        try {
            List<MerchantVisitData> visits = shell(world).core(MerchantVisitData.class).extractAll();
            return visits == null ? new ArrayList<>() : visits;
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to list merchant visits: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void delete(World world, String visitId) {
        if (visitId == null || visitId.isBlank()) return;
        try {
            shell(world).core(MerchantVisitData.class).discard(visitId);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to delete merchant visit: " + e.getMessage());
        }
    }

    /** Deletes every temporary merchant visit for a world, e.g. for the explicit clearall command. */
    public static int deleteAll(World world) {
        List<MerchantVisitData> visits = list(world);
        int deleted = 0;
        for (MerchantVisitData visit : visits) {
            if (visit == null) continue;
            String id = visit.visitId != null && !visit.visitId.isBlank()
                    ? visit.visitId : visit.merchantName;
            if (id == null || id.isBlank()) continue;
            delete(world, id);
            deleted++;
        }
        return deleted;
    }
}
