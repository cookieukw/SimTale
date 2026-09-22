package com.cookieukw.SimTale.db;

/**
 * A registered storage block, on disk.
 *
 * <p>{@code ChestRegistry.CHESTS} was memory only, so the registry survived exactly as long as the
 * server process. Two things refilled it — the place event and the boot sweep — and neither is
 * enough on its own: the event only ever fires once, at placement, and the sweep can only see
 * chunks that happen to be loaded when a player joins. A chest in an unloaded corner of the map was
 * therefore invisible to the mod until someone walked past it and placed it again.
 *
 * <p>Beds never showed this because they have a second way back: every NPC record carries its own
 * bed position and re-registers it on load. Chests have no owner to carry them.
 */
public class ChestData {

    /** Storage key, and the position itself. Kept as a string so it doubles as the record id. */
    public String id;

    public int x;
    public int y;
    public int z;

    public boolean shared = false;

    public ChestData() {
    }

    public ChestData(int x, int y, int z) {
        this(x, y, z, false);
    }

    public ChestData(int x, int y, int z, boolean shared) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.shared = shared;
        this.id = key(x, y, z);
    }

    public static String key(int x, int y, int z) {
        return x + "_" + y + "_" + z;
    }
}
