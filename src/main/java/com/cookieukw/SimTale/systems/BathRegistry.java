package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BathRegistry {
    private BathRegistry() {}

    public static final Set<HouseBlockPos> BATHS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isBathId(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.contains("bath") || lower.contains("tub");
    }

    public static void add(int x, int y, int z) {
        BATHS.add(new HouseBlockPos(x, y, z));
    }

    public static void removeAt(int x, int y, int z) {
        BATHS.remove(new HouseBlockPos(x, y, z));
    }

    public static Vector3i nearestTo(double x, double y, double z) {
        HouseBlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;
        synchronized (BATHS) {
            for (HouseBlockPos p : BATHS) {
                double dx = p.x + 0.5 - x;
                double dy = p.y - y;
                double dz = p.z + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = p;
                }
            }
        }
        if (closest != null) {
            return new Vector3i(closest.x, closest.y, closest.z);
        }
        return null;
    }
}
