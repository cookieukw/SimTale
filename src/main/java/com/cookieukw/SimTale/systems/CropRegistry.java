package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CropRegistry {
    private CropRegistry() {}

    public static final Set<HouseBlockPos> CROPS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isCropId(String id) {
        if (id == null) return false;
        // Case-insensitive, matching BedRegistry/ChestRegistry/FarmlandRegistry. This was the
        // only registry that compared casing exactly.
        String name = id.toLowerCase(Locale.ROOT);
        return name.startsWith("plant_crop_") && name.endsWith("_block") && !name.contains("eternal");
    }

    public static void add(int x, int y, int z) {
        synchronized (CROPS) {
            HouseBlockPos pos = new HouseBlockPos(x, y, z);
            CROPS.add(pos);
        }
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (CROPS) {
            CROPS.removeIf(b -> b.x == x && b.y == y && b.z == z);
        }
    }

    public static int size() {
        return CROPS.size();
    }
}
