package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BedRegistry {
    private BedRegistry() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(BedRegistry.class);

    /** A double bed occupies adjacent blocks; only the first part is registered. */
    private static final int DEDUPE_RADIUS_XZ = 2;
    private static final int DEDUPE_RADIUS_Y = 1;

    public static final Set<BedPos> BEDS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isBedId(String id) {
        if (id == null) return false;
        String name = id.toLowerCase(Locale.ROOT);
        if (name.contains("bedrock")) return false;
        return name.contains("bed");
    }

    public static void addOrReplace(int x, int y, int z, float yaw) {
        synchronized (BEDS) {
            // Exact position: genuinely replace, so a re-registration refreshes the yaw.
            // The old code returned early from the proximity loop below before ever reaching
            // the removeIf/add pair, so "addOrReplace" never actually replaced anything and
            // a bed's yaw could never be corrected.
            BedPos existing = null;
            for (BedPos b : BEDS) {
                if (b.x == x && b.y == y && b.z == z) {
                    existing = b;
                    break;
                }
            }
            if (existing != null) {
                if (existing.yaw != yaw) {
                    BEDS.remove(existing);
                    BEDS.add(new BedPos(x, y, z, yaw));
                    LOGGER.debug("[SimTale] Bed at ({},{},{}) re-registered with yaw={}", x, y, z, yaw);
                }
                return;
            }

            // Neighbouring position: this is the second half of an already-registered bed.
            for (BedPos b : BEDS) {
                if (Math.abs(b.x - x) <= DEDUPE_RADIUS_XZ
                        && Math.abs(b.y - y) <= DEDUPE_RADIUS_Y
                        && Math.abs(b.z - z) <= DEDUPE_RADIUS_XZ) {
                    return;
                }
            }

            BEDS.add(new BedPos(x, y, z, yaw));
            LOGGER.debug("[SimTale] Bed registered at ({},{},{}) yaw={}. Total: {}", x, y, z, yaw, BEDS.size());
        }
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (BEDS) {
            BEDS.removeIf(b -> b.x == x && b.y == y && b.z == z);
        }
    }

    public static int size() {
        return BEDS.size();
    }
}
