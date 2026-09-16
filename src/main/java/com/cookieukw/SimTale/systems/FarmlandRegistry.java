package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class FarmlandRegistry {
    private FarmlandRegistry() {}

    public static final Set<HouseBlockPos> FARMLAND = Collections.synchronizedSet(new HashSet<>());

    /** Matches vanilla Hytale planting, which doesn't require tilled soil — any grass/dirt-type
     *  ground block works, not just Soil_Dirt_Tilled. */
    /* Watered tilled soil comes back as "*soil_dirt_tilled_state_definitions_watered", so the
    match has to ignore both the leading marker and the state suffix.
    */
    public static boolean isFarmlandId(String id) {
        return AssetIds.containsAny(id, "tilled", "grass", "soil_dirt", "soil_mud");
    }

    public static void add(int x, int y, int z) {
        FARMLAND.add(new HouseBlockPos(x, y, z));
    }

    /** Hash lookup instead of a full scan — see {@link CropRegistry#removeAt} for the reasoning. */
    public static void removeAt(int x, int y, int z) {
        FARMLAND.remove(new HouseBlockPos(x, y, z));
    }

    public static int size() {
        return FARMLAND.size();
    }
}
