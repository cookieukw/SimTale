package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ChestRegistry {
    private static final SimLog LOGGER = SimLog.forClass(ChestRegistry.class);
    private ChestRegistry() {}

    public static final Set<HouseBlockPos> CHESTS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isChestId(String id) {
        if (id == null) return false;
        String name = id.toLowerCase();
        return name.contains("chest") || name.contains("barrel") || name.contains("cupboard") || name.contains("cabinet");
    }

    public static void add(int x, int y, int z) {
        CHESTS.add(new HouseBlockPos(x, y, z));
        LOGGER.debug("[SimTale] Chest registered at (" + x + ", " + y + ", " + z + "). Total: " + CHESTS.size());
    }

    public static void removeAt(int x, int y, int z) {
        CHESTS.remove(new HouseBlockPos(x, y, z));
    }

    public static int size() {
        return CHESTS.size();
    }
}
