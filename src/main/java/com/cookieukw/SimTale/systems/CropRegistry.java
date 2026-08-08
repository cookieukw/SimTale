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
        // Growth is an in-place BlockType state machine, not a separate registered id per stage
        // — a grown crop's runtime getId() comes back as
        // "*plant_crop_carrot_block_state_definitions_stagefinal": a leading "*" (Hytale's own
        // marker for a state-variant block, confirmed via /simtale debugnear output) plus
        // "_state_definitions_<stage>" instead of the bare "..._block" used at plant time.
        // startsWith("plant_crop_") missed every grown stage because of that leading "*" — use
        // contains so the prefix position doesn't matter.
        return name.contains("plant_crop_") && name.contains("_block") && !name.contains("eternal");
    }

    /** A crop only carries "stagefinal" in its runtime id once fully grown — a freshly planted
     *  or still-growing crop matches {@link #isCropId} too, but isn't ready to harvest yet.
     *  Without this check the Farmer harvested her own just-planted seedling immediately, since
     *  the harvest logic never distinguished "occupied by a crop" from "occupied by a RIPE crop"
     *  — endless plant/harvest loop on the same tile, never advancing to the rest of the patch. */
    public static boolean isReadyToHarvest(String id) {
        if (id == null) return false;
        return id.toLowerCase(Locale.ROOT).contains("stagefinal");
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
