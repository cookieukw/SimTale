package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FarmlandRegistry {
    private FarmlandRegistry() {}

    public static final Set<HouseBlockPos> FARMLAND = Collections.synchronizedSet(new HashSet<>());

    public static boolean isFarmlandId(String id) {
        if (id == null) return false;
        return id.toLowerCase().contains("tilled");
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
