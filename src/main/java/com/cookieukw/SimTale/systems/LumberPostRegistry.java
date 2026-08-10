package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

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

    /** Removes whichever post pointed at this exact tree — called when the trunk itself is
     *  chopped down, so the post doesn't keep sending lumberjacks at an empty spot. Re-placing
     *  the bench (or a future retarget command) is how the post finds a new tree. */
    public static void removeByTree(int treeX, int treeY, int treeZ) {
        synchronized (POSTS) {
            POSTS.removeIf(p -> {
                boolean match = p.treeX() == treeX && p.treeY() == treeY && p.treeZ() == treeZ;
                if (match) CLAIMED_BY.remove(key(p.postX(), p.postY(), p.postZ()));
                return match;
            });
        }
    }

    public static LumberPost claimNearest(double x, double y, double z, UUID npcId) {
        LumberPost chosen = null;
        double closestDistSq = Double.MAX_VALUE;
        synchronized (POSTS) {
            for (LumberPost p : POSTS) {
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

    public static void release(int postX, int postY, int postZ, UUID npcId) {
        if (npcId == null) return;
        CLAIMED_BY.remove(key(postX, postY, postZ), npcId);
    }

    private static Vector3i findNearestTree(World world, int cx, int cy, int cz) {
        Vector3i closest = null;
        int closestDistSq = Integer.MAX_VALUE;
        for (int dx = -TREE_SEARCH_XZ; dx <= TREE_SEARCH_XZ; dx++) {
            for (int dz = -TREE_SEARCH_XZ; dz <= TREE_SEARCH_XZ; dz++) {
                for (int dy = -TREE_SEARCH_Y; dy <= TREE_SEARCH_Y; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (isTreeTrunk(world.getBlockType(x, y, z))) {
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
