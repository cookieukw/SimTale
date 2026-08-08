package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FarmlandRegistry {
    private FarmlandRegistry() {}

    public static final Set<HouseBlockPos> FARMLAND = Collections.synchronizedSet(new HashSet<>());

    /** Matches vanilla Hytale planting, which doesn't require tilled soil — any grass/dirt-type
     *  ground block works, not just Soil_Dirt_Tilled. */
    public static boolean isFarmlandId(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.contains("tilled") || lower.contains("grass") || lower.startsWith("soil_dirt") || lower.contains("soil_mud");
    }

    public static void add(int x, int y, int z) {
        synchronized (FARMLAND) {
            FARMLAND.add(new HouseBlockPos(x, y, z));
        }
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (FARMLAND) {
            FARMLAND.removeIf(b -> b.x == x && b.y == y && b.z == z);
        }
    }

    public static int size() {
        return FARMLAND.size();
    }
}
