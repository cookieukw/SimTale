package com.cookieukw.SimTale.core;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Single source of truth for "give me the world".
 * <p>
 * SimTale is a single-world mod, but this lookup was duplicated in ~20 places, five of
 * which used {@code getWorlds().values().iterator().next()} and threw
 * {@link java.util.NoSuchElementException} whenever they ran before any world was loaded
 * (server boot, world unload, shutdown).
 */
public final class WorldUtil {

    private WorldUtil() {
    }

    /**
     * @return the first loaded world, or {@code null} if none is loaded yet.
     *         Callers must null-check instead of assuming a world exists.
     */
    public static World first() {
        for (World w : Universe.get().getWorlds().values()) {
            return w;
        }
        return null;
    }

    /** @return the current tick of the first loaded world, or {@code 0} when there is none. */
    public static long tick() {
        World world = first();
        return world != null ? world.getTick() : 0L;
    }
}
