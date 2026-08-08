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
        // "plant_crop_carrot_block_state_definitions_stagefinal", not bare "..._block". This was
        // only ever called with the bare id (at plant time), so the stricter endsWith("_block")
        // never actually misfired, but it was one re-check against a grown crop away from
        // silently failing. "_block" appearing anywhere covers every stage.
        return name.startsWith("plant_crop_") && name.contains("_block") && !name.contains("eternal");
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
