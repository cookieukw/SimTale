package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BedRegistry {
    private BedRegistry() {}



    public static final Set<BedPos> BEDS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isBedId(String id) {
        if (id == null) return false;
        String name = id.toLowerCase();
        if (name.contains("bedrock")) return false;
        return name.contains("bed");
    }

    public static void addOrReplace(int x, int y, int z, float yaw) {
        synchronized (BEDS) {
            // Deduplicate: Don't register multiple parts of the same bed (within 2 blocks horizontally)
            for (BedPos b : BEDS) {
                if (Math.abs(b.x - x) <= 2 && Math.abs(b.y - y) <= 1 && Math.abs(b.z - z) <= 2) {
                    return;
                }
            }
            BEDS.removeIf(b -> b.x == x && b.y == y && b.z == z);
            BEDS.add(new BedPos(x, y, z, yaw));
            System.out.println("[SimTale] Bed registered at (" + x + ", " + y + ", " + z + ") yaw=" + yaw + ". Total: " + BEDS.size());
        }
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (BEDS) {
            BEDS.removeIf(b -> b.x == x && b.y == y && b.z == z);
        }
    }

    public static boolean containsAt(int x, int y, int z) {
        synchronized (BEDS) {
            for (BedPos b : BEDS) {
                if (b.x == x && b.y == y && b.z == z) return true;
            }
            return false;
        }
    }

    public static int size() {
        return BEDS.size();
    }
}
