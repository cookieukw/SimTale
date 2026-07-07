package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BedRegistry {
    private BedRegistry() {}

    private static final Set<String> BED_IDS = Set.of(
        "furniture_ancient_bed",
        "furniture_crude_bed",
        "furniture_desert_bed",
        "furniture_feran_bed",
        "furniture_frozen_castle_bed",
        "furniture_human_ruins_bed",
        "furniture_jungle_bed",
        "furniture_kweebec_bed",
        "furniture_lumberjack_bed",
        "furniture_royal_magic_bed",
        "furniture_tavern_bed",
        "furniture_temple_dark_bed",
        "furniture_temple_emerald_bed",
        "furniture_temple_light_bed",
        "furniture_village_bed"
    );

    public static final Set<BedPos> BEDS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isBedId(String id) {
        if (id == null) return false;
        return BED_IDS.contains(id.toLowerCase());
    }

    public static void addOrReplace(int x, int y, int z, float yaw) {
        synchronized (BEDS) {
            BEDS.removeIf(b -> b.x == x && b.y == y && b.z == z);
            BEDS.add(new BedPos(x, y, z, yaw));
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
