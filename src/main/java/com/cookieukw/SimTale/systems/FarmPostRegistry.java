package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.SimLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Farm posts: place {@code Deco_Scarecrow} to mark a garden's center. Unlike
 * {@link FishingPostRegistry}/{@link LumberPostRegistry}, this doesn't resolve a fixed target at
 * placement time — farmland changes state constantly (tilled -&gt; planted -&gt; harvested -&gt; tilled
 * again), so a snapshot taken once would go stale within minutes. The post is just a known,
 * fixed point a Farmer can walk toward from anywhere; {@code NPCWorkHelper} re-scans for an
 * actual crop/empty farmland around it fresh, every time, the same way it always has — just
 * centered on the post instead of on wherever the NPC happened to be standing.
 * <p>
 * One farmer works a given plot at a time (queue by claim, not by pre-assigned turn order) —
 * matching the same "one worker per post" rule fishing and lumber already use.
 */
public final class FarmPostRegistry {
    private FarmPostRegistry() {}

    private static final SimLog LOGGER = SimLog.forClass(FarmPostRegistry.class);

    public record FarmPost(String worldName, int postX, int postY, int postZ) {}

    public static final Set<FarmPost> POSTS = Collections.synchronizedSet(new HashSet<>());

    private static final Map<String, UUID> CLAIMED_BY = new ConcurrentHashMap<>();

    private static String key(String worldName, int x, int y, int z) {
        return worldName + ":" + x + "," + y + "," + z;
    }

    /* The scarecrow is three blocks tall, so its non-anchor blocks arrive as state variants —
    equalsIgnoreCase only ever matched one of the three.
    */
    public static boolean isFarmPostId(String id) {
        return AssetIds.matchesAsset(id, "Deco_Scarecrow");
    }

    /** Unlike fishing/lumber, no nearby-resource validation: farmland doesn't have to exist yet
     *  when the scarecrow goes up (the player may till the soil around it afterward). */
    public static void registerAt(com.hypixel.hytale.server.core.universe.world.World world, int x, int y, int z) {
        String worldName = world != null ? world.getName() : "";
        synchronized (POSTS) {
            for (FarmPost p : POSTS) {
                if (p.worldName().equals(worldName) && p.postX() == x
                        && p.postY() == y && p.postZ() == z) return;
            }
            POSTS.add(new FarmPost(worldName, x, y, z));
        }
        LOGGER.info("[SimTale] Farm post registered at ({},{},{})", x, y, z);
    }

    public static void removeAt(com.hypixel.hytale.server.core.universe.world.World world, int x, int y, int z) {
        String worldName = world != null ? world.getName() : "";
        synchronized (POSTS) {
            POSTS.removeIf(p -> p.worldName().equals(worldName) && p.postX() == x
                    && p.postY() == y && p.postZ() == z);
        }
        CLAIMED_BY.remove(key(worldName, x, y, z));
    }

    /**
     * How far an NPC will look for a scarecrow to work at.
     * <p>
     * There was no limit at all: the search took the nearest <em>unclaimed post in the world</em>,
     * so a farmer could claim a plot on the far side of the map and set off walking to it. In
     * practice she never arrived — MOVING_TO_WORK times out after 30s — so the visible symptom was
     * a farmer marching off in a straight line and then giving up, over and over.
     */
    private static final double CLAIM_SEARCH_RADIUS = 48.0;

    public static FarmPost claimNearest(com.hypixel.hytale.server.core.universe.world.World world,
                                        double x, double y, double z, UUID npcId) {
        String worldName = world != null ? world.getName() : "";
        FarmPost chosen = null;
        double closestDistSq = CLAIM_SEARCH_RADIUS * CLAIM_SEARCH_RADIUS;
        synchronized (POSTS) {
            for (FarmPost p : POSTS) {
                if (!p.worldName().equals(worldName)) continue;
                UUID holder = CLAIMED_BY.get(key(worldName, p.postX(), p.postY(), p.postZ()));
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
            CLAIMED_BY.put(key(worldName, chosen.postX(), chosen.postY(), chosen.postZ()), npcId);
        }
        return chosen;
    }

    public static void release(com.hypixel.hytale.server.core.universe.world.World world,
                               int postX, int postY, int postZ, UUID npcId) {
        if (npcId == null) return;
        String worldName = world != null ? world.getName() : "";
        CLAIMED_BY.remove(key(worldName, postX, postY, postZ), npcId);
    }
}
