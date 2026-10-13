package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lumberjack posts: place {@code Bench_Lumbermill} near trees and the mod resolves and stores the
 * nearest trunk block once, at placement time — same trade as {@link FishingPostRegistry}.
 */
public final class LumberPostRegistry {
    private LumberPostRegistry() {}

    private static final SimLog LOGGER = SimLog.forClass(LumberPostRegistry.class);

    private static final int TREE_SEARCH_XZ = 10;
    private static final int TREE_SEARCH_Y = 6;

    public record LumberPost(int postX, int postY, int postZ, int treeX, int treeY, int treeZ) {}

    public static final Set<LumberPost> POSTS = Collections.synchronizedSet(new HashSet<>());

    private static final Map<String, UUID> CLAIMED_BY = new ConcurrentHashMap<>();

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static boolean isLumberPostId(String id) {
        return AssetIds.matchesAsset(id, "Bench_Lumbermill");
    }

    /** World-generated trunks are {@code Wood_<Species>_Trunk} / {@code _Trunk_Full}. Half-blocks
     *  and stairs are crafted furniture, not trees — excluded by requiring the id to end here. */
    public static boolean isTreeTrunk(BlockType type) {
        if (type == null || type.getId() == null) return false;
        String id = type.getId();
        return id.endsWith("_Trunk") || id.endsWith("_Trunk_Full");
    }

    public static void registerAt(World world, int x, int y, int z) {
        synchronized (POSTS) {
            for (LumberPost p : POSTS) {
                if (p.postX() == x && p.postY() == y && p.postZ() == z) return;
            }
        }

        Vector3i tree = findNearestTree(world, x, y, z);
        if (tree == null) {
            LOGGER.warn("[SimTale] Lumber post placed at ({},{},{}) but no tree found within {} blocks — not registered.",
                    x, y, z, TREE_SEARCH_XZ);
            return;
        }

        synchronized (POSTS) {
            POSTS.add(new LumberPost(x, y, z, tree.x, tree.y, tree.z));
        }
        LOGGER.info("[SimTale] Lumber post registered at ({},{},{}), tree at ({},{},{})",
                x, y, z, tree.x, tree.y, tree.z);
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (POSTS) {
            POSTS.removeIf(p -> p.postX() == x && p.postY() == y && p.postZ() == z);
        }
        CLAIMED_BY.remove(key(x, y, z));
    }

    /**
     * Called when a trunk block is chopped (by the lumberjack NPC or a player). Every post that
     * pointed at it looks for the nearest trunk again, starting from the post, and is removed only
     * when none is left in range. It used to be removed outright, so each lumbermill gave exactly
     * one log until the player placed it again. The chopped block is excluded explicitly: the
     * break event can run before the block is actually gone.
     */
    public static void retargetFromTree(World world, int treeX, int treeY, int treeZ) {
        List<LumberPost> affected = new ArrayList<>();
        synchronized (POSTS) {
            for (LumberPost p : POSTS) {
                if (p.treeX() == treeX && p.treeY() == treeY && p.treeZ() == treeZ) affected.add(p);
            }
        }
        if (affected.isEmpty()) return;

        for (LumberPost p : affected) {
            Vector3i tree = world != null
                    ? findNearestTree(world, p.postX(), p.postY(), p.postZ(), treeX, treeY, treeZ)
                    : null;
            synchronized (POSTS) {
                if (!POSTS.remove(p)) continue; // removed meanwhile (bench broken)
                if (tree != null) {
                    POSTS.add(new LumberPost(p.postX(), p.postY(), p.postZ(), tree.x, tree.y, tree.z));
                }
            }
            if (tree == null) {
                CLAIMED_BY.remove(key(p.postX(), p.postY(), p.postZ()));
                LOGGER.info("[SimTale] Lumber post at ({},{},{}) has no trees left in range; removed.",
                        p.postX(), p.postY(), p.postZ());
            }
        }
    }

    /**
     * How far an NPC will look for a lumber post to work at. Same fix, same reason as
     * {@code FarmPostRegistry.CLAIM_SEARCH_RADIUS} / {@code FishingPostRegistry.CLAIM_SEARCH_RADIUS}
     * -- an unbounded claim let a lumberjack pick a post on the far side of the map and never
     * arrive. Applied during the 13/09 optimization pass.
     */
    private static final double CLAIM_SEARCH_RADIUS = 48.0;

    public static LumberPost claimNearest(double x, double y, double z, UUID npcId) {
        LumberPost chosen = null;
        double closestDistSq = CLAIM_SEARCH_RADIUS * CLAIM_SEARCH_RADIUS;
        synchronized (POSTS) {
            for (LumberPost p : POSTS) {
                UUID holder = CLAIMED_BY.get(key(p.postX(), p.postY(), p.postZ()));
                /* A holder that no longer exists (died, forgotten, respawned under a new UUID)
                never calls release(), so its claim would lock the post forever. */
                if (holder != null && !holder.equals(npcId) && !ChairRegistry.isStale(holder)) continue;

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

    public static void release(int postX, int postY, int postZ, UUID npcId) {
        if (npcId == null) return;
        CLAIMED_BY.remove(key(postX, postY, postZ), npcId);
    }

    private static Vector3i findNearestTree(World world, int cx, int cy, int cz) {
        return findNearestTree(world, cx, cy, cz, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /** Same search, skipping the block at (ex, ey, ez). */
    private static Vector3i findNearestTree(World world, int cx, int cy, int cz, int ex, int ey, int ez) {
        Vector3i closest = null;
        int closestDistSq = Integer.MAX_VALUE;
        for (int dx = -TREE_SEARCH_XZ; dx <= TREE_SEARCH_XZ; dx++) {
            for (int dz = -TREE_SEARCH_XZ; dz <= TREE_SEARCH_XZ; dz++) {
                for (int dy = -TREE_SEARCH_Y; dy <= TREE_SEARCH_Y; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (x == ex && y == ey && z == ez) continue;
                    if (isTreeTrunk(NPCMovementHelper.getBlockTypeSafe(world, x, y, z))) {
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
