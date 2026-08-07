package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fishing posts: place {@code Tool_Fishing_Trap} near water and the mod resolves and stores the
 * nearest water block once, at placement time — the same trade a bed makes (register once, read
 * many times) instead of every fisherman NPC scanning outward for water on every work cycle.
 */
public final class FishingPostRegistry {
    private FishingPostRegistry() {}

    private static final SimLog LOGGER = SimLog.forClass(FishingPostRegistry.class);

    /** How far from the post to look for water, horizontally and vertically. */
    private static final int WATER_SEARCH_XZ = 8;
    private static final int WATER_SEARCH_Y = 3;

    public record FishingPost(int postX, int postY, int postZ, int waterX, int waterY, int waterZ) {}

    public static final Set<FishingPost> POSTS = Collections.synchronizedSet(new HashSet<>());

    /** Post marker position (encoded "x,y,z") -> claiming NPC's UUID. One worker per post. */
    private static final Map<String, UUID> CLAIMED_BY = new ConcurrentHashMap<>();

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static boolean isFishingPostId(String id) {
        return id != null && id.equalsIgnoreCase("Tool_Fishing_Trap");
    }

    private static boolean isWater(BlockType type) {
        if (type == null || type.getId() == null) return false;
        return type.getId().toLowerCase(Locale.ROOT).startsWith("fluid_water");
    }

    /** Registers a post at (x,y,z), resolving its water target now. No-ops if already registered. */
    public static void registerAt(World world, int x, int y, int z) {
        synchronized (POSTS) {
            for (FishingPost p : POSTS) {
                if (p.postX() == x && p.postY() == y && p.postZ() == z) return;
            }
        }

        Vector3i water = findNearestWater(world, x, y, z);
        if (water == null) {
            LOGGER.warn("[SimTale] Fishing post placed at ({},{},{}) but no water found within {} blocks — not registered.",
                    x, y, z, WATER_SEARCH_XZ);
            return;
        }

        synchronized (POSTS) {
            POSTS.add(new FishingPost(x, y, z, water.x, water.y, water.z));
        }
        LOGGER.info("[SimTale] Fishing post registered at ({},{},{}), water at ({},{},{})",
                x, y, z, water.x, water.y, water.z);
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (POSTS) {
            POSTS.removeIf(p -> p.postX() == x && p.postY() == y && p.postZ() == z);
        }
        CLAIMED_BY.remove(key(x, y, z));
    }

    /** Nearest registered post to a world position, or null if none registered. */
    public static FishingPost nearestTo(double x, double y, double z) {
        FishingPost closest = null;
        double closestDistSq = Double.MAX_VALUE;
        synchronized (POSTS) {
            for (FishingPost p : POSTS) {
                double dx = p.postX() + 0.5 - x;
                double dy = p.postY() - y;
                double dz = p.postZ() + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = p;
                }
            }
        }
        return closest;
    }

    /**
     * Nearest post not already claimed by another NPC (or already claimed by this same one, so
     * re-evaluating mid-work doesn't bounce the NPC off its own post). Returns null if every
     * post within reach is taken — the caller should just wait for IDLE's next retry instead of
     * queueing, matching how a bed search behaves when nothing is free.
     */
    public static FishingPost claimNearest(double x, double y, double z, UUID npcId) {
        FishingPost chosen = null;
        double closestDistSq = Double.MAX_VALUE;
        synchronized (POSTS) {
            for (FishingPost p : POSTS) {
                UUID holder = CLAIMED_BY.get(key(p.postX(), p.postY(), p.postZ()));
                if (holder != null && !holder.equals(npcId)) continue;

                double dx = p.postX() + 0.5 - x;
                double dy = p.postY() - y;
                double dz = p.postZ() + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    chosen = p;
                }
            }
        }
        if (chosen != null) {
            CLAIMED_BY.put(key(chosen.postX(), chosen.postY(), chosen.postZ()), npcId);
        }
        return chosen;
    }

    /** Releases a claim, but only if it actually belongs to {@code npcId} — never steal someone
     *  else's release call by accident. */
    public static void release(int postX, int postY, int postZ, UUID npcId) {
        if (npcId == null) return;
        CLAIMED_BY.remove(key(postX, postY, postZ), npcId);
    }

    private static Vector3i findNearestWater(World world, int cx, int cy, int cz) {
        Vector3i closest = null;
        int closestDistSq = Integer.MAX_VALUE;
        for (int dx = -WATER_SEARCH_XZ; dx <= WATER_SEARCH_XZ; dx++) {
            for (int dz = -WATER_SEARCH_XZ; dz <= WATER_SEARCH_XZ; dz++) {
                for (int dy = -WATER_SEARCH_Y; dy <= WATER_SEARCH_Y; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (isWater(world.getBlockType(x, y, z))) {
                        int distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = new Vector3i(x, y, z);
                        }
                    }
                }
            }
        }
        return closest;
    }
}
