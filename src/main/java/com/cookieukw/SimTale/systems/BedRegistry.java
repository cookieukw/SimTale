package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.core.SimLog;

import java.util.Collections;
import java.util.HashSet;
import com.cookieukw.SimTale.core.AssetIds;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.hypixel.hytale.server.core.universe.world.World;

public final class BedRegistry {
    private BedRegistry() {}

    private static final SimLog LOGGER = SimLog.forClass(BedRegistry.class);

    /** A double bed occupies adjacent blocks; only the first part is registered. */
    private static final int DEDUPE_RADIUS_XZ = 2;
    private static final int DEDUPE_RADIUS_Y = 1;

    public static final Set<BedPos> BEDS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Set<BedPos>> BEDS_BY_WORLD = new ConcurrentHashMap<>();

    private static String worldKey(World world) { return world == null ? "" : world.getName(); }
    private static Set<BedPos> beds(World world) {
        return BEDS_BY_WORLD.computeIfAbsent(worldKey(world), _ -> Collections.synchronizedSet(new HashSet<>()));
    }
    /** Snapshot of beds belonging to this world. */
    public static Set<BedPos> registeredBeds(World world) { return Set.copyOf(beds(world)); }

    public static boolean isBedId(String id) {
        return AssetIds.containsNone(id, "bedrock") && AssetIds.containsAny(id, "bed");
    }

    public static void addOrReplace(int x, int y, int z, float yaw) {
        addOrReplace(null, x, y, z, yaw);
    }

    public static void addOrReplace(World world, int x, int y, int z, float yaw) {
        Set<BedPos> target = world == null ? BEDS : beds(world);
        synchronized (target) {
            /* Exact position: genuinely replace, so a re-registration refreshes the yaw.
            The old code returned early from the proximity loop below before ever reaching
            the removeIf/add pair, so "addOrReplace" never actually replaced anything and
            a bed's yaw could never be corrected.
            */
            BedPos existing = null;
            for (BedPos b : target) {
                if (b.x == x && b.y == y && b.z == z) {
                    existing = b;
                    break;
                }
            }
            if (existing != null) {
                if (existing.yaw != yaw) {
                    target.remove(existing);
                    target.add(new BedPos(x, y, z, yaw));
                    LOGGER.debug("[SimTale] Bed at ({},{},{}) re-registered with yaw={}", x, y, z, yaw);
                }
                return;
            }

            target.add(new BedPos(x, y, z, yaw));
            LOGGER.debug("[SimTale] Bed registered at ({},{},{}) yaw={}. Total: {}", x, y, z, yaw, target.size());
        }
    }

    public static void removeAt(int x, int y, int z) {
        removeAt(null, x, y, z);
    }
    public static void removeAt(World world, int x, int y, int z) {
        Set<BedPos> target = world == null ? BEDS : beds(world);
        synchronized (target) {
            target.removeIf(b -> b.x == x && b.y == y && b.z == z);
        }
    }

    /** The registered bed at this exact position, or null. */
    public static BedPos find(int x, int y, int z) {
        return find(null, x, y, z);
    }
    public static BedPos find(World world, int x, int y, int z) {
        Set<BedPos> target = world == null ? BEDS : beds(world);
        synchronized (target) {
            for (BedPos b : target) {
                if (b.x == x && b.y == y && b.z == z) return b;
            }
        }
        return null;
    }

    /** Whether a bed is still registered at this exact position. */
    public static boolean exists(int x, int y, int z) {
        return exists(null, x, y, z);
    }
    public static boolean exists(World world, int x, int y, int z) {
        Set<BedPos> target = world == null ? BEDS : beds(world);
        synchronized (target) {
            for (BedPos b : target) {
                if (b.x == x && b.y == y && b.z == z) return true;
            }
        }
        return false;
    }

    public static int size() {
        return BEDS.size();
    }
    public static int size(World world) { return beds(world).size(); }

    public static void clear() {
        synchronized (BEDS) {
            BEDS.clear();
        }
    }
    public static void clear(World world) { beds(world).clear(); }
}
