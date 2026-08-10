package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import java.util.Collections;
import java.util.HashSet;
import com.cookieukw.SimTale.core.AssetIds;
import java.util.Set;

public final class CropRegistry {
    private CropRegistry() {}

    public static final Set<HouseBlockPos> CROPS = Collections.synchronizedSet(new HashSet<>());

    // Growth is an in-place BlockType state machine, not a separate registered id per stage — a
    // grown crop's runtime getId() comes back as
    // "*plant_crop_carrot_block_state_definitions_stagefinal": a leading "*" (Hytale's marker for
    // a state-variant block, confirmed via /simtale debugnear) plus "_state_definitions_<stage>"
    // instead of the bare "..._block" used at plant time. Matching has to survive both shapes.
    public static boolean isCropId(String id) {
        return AssetIds.containsAny(id, "plant_crop")
                && AssetIds.containsAny(id, "block")
                && AssetIds.containsNone(id, "eternal");
    }

    /** A crop only carries "stagefinal" in its runtime id once fully grown — a freshly planted
     *  or still-growing crop matches {@link #isCropId} too, but isn't ready to harvest yet.
     *  Without this check the Farmer harvested her own just-planted seedling immediately, since
     *  the harvest logic never distinguished "occupied by a crop" from "occupied by a RIPE crop"
     *  — endless plant/harvest loop on the same tile, never advancing to the rest of the patch. */
    public static boolean isReadyToHarvest(String id) {
        return AssetIds.containsAny(id, "stagefinal");
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
