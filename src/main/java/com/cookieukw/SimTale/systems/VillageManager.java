package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Villages, derived from houses rather than stored.
 *
 * <p>Nothing here is persisted and nothing is placed by the player. A village is a fact computed
 * from {@link HouseManager}'s current houses: build a house next to others and the village grows to
 * include it; break the beds and the houses stop existing, so the village shrinks and eventually
 * ceases to be. That is the whole point of the design — Minecraft's village centre is a placed
 * marker that survives the village being levelled, and this deliberately cannot.
 *
 * <p>Grouping is single-linkage: two houses belong to the same village when their beds are within
 * {@link #LINK_DISTANCE} of each other, and that relation chains. A row of houses each 30 blocks
 * from the next is one village however long the row gets, which matches how people actually build —
 * outward from what is already there, not inside a circle drawn in advance.
 */
public final class VillageManager {

    private static final SimLog LOGGER = SimLog.forClass(VillageManager.class);

    /** Two houses this close (in blocks, on the XZ plane) are neighbours (2 chunks). */
    private static final double LINK_DISTANCE = 64.0;
    private static final double LINK_DISTANCE_SQ = LINK_DISTANCE * LINK_DISTANCE;

    /**
     * Breathing room added to the furthest house when measuring a village's radius.
     *
     * <p>Without it a one-house village would have radius zero, and its resident would be pinned to
     * the doorstep.
     */
    private static final double RADIUS_MARGIN = 24.0;

    private VillageManager() {
    }

    /**
     * A cluster of houses.
     *
     * @param centerX       midpoint of the member beds
     * @param centerZ       midpoint of the member beds
     * @param radius        distance from the centre to the furthest member, plus {@link #RADIUS_MARGIN}
     * @param houses        how many houses formed it
     * @param chunkIndices  the set of all chunk indices covered by this village territory
     */
    public record Village(double centerX, double centerZ, double radius, int houses, Set<Long> chunkIndices) {

        public Village(double centerX, double centerZ, double radius, int houses) {
            this(centerX, centerZ, radius, houses, computeChunks(centerX, centerZ, radius, List.of()));
        }

        public double distanceSqTo(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz;
        }

        public boolean contains(double x, double z) {
            return distanceSqTo(x, z) <= radius * radius;
        }

        public boolean containsChunk(long chunkIndex) {
            return chunkIndices != null && chunkIndices.contains(chunkIndex);
        }
    }

    private static volatile List<Village> villages = List.of();
    private static volatile Set<Long> lastKnownVillageChunks = Set.of();

    /**
     * Set whenever the house set changes; the rebuild happens on the next query instead of inside
     * the mutation.
     */
    private static final AtomicBoolean dirty = new AtomicBoolean(true);

    /** Called by {@link HouseManager} whenever a house is added, changed or removed. */
    public static void markDirty() {
        dirty.set(true);
    }

    public static List<Village> villages() {
        if (dirty.compareAndSet(true, false)) {
            rebuild();
        }
        return villages;
    }

    /** The village containing {@code (x, z)}, or the nearest one, or null when there are none. */
    public static Village nearest(double x, double z) {
        Village best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Village village : villages()) {
            double distSq = village.distanceSqTo(x, z);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = village;
            }
        }
        return best;
    }

    /**
     * Returns the village that owns the given chunk coordinates, or {@code null} if unowned.
     */
    public static Village getVillageForChunk(int chunkX, int chunkZ) {
        long idx = ChunkUtil.indexChunk(chunkX, chunkZ);
        for (Village v : villages()) {
            if (v.containsChunk(idx)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Checks whether the specified chunk belongs to the same given village.
     */
    public static boolean isSameVillageChunk(Village village, int chunkX, int chunkZ) {
        if (village == null) return true;
        long idx = ChunkUtil.indexChunk(chunkX, chunkZ);
        return !village.containsChunk(idx);
    }

    /**
     * Single-linkage clustering over the house beds.
     */
    private static void rebuild() {
        List<HouseBlockPos> houseAnchors = new ArrayList<>();
        for (HouseData house : HouseManager.HOUSES_BY_ID.values()) {
            if (house == null) continue;
            HouseBlockPos anchor = house.getAnchor();
            if (anchor != null) {
                houseAnchors.add(anchor);
            }
        }

        List<Village> built = new ArrayList<>();
        boolean[] taken = new boolean[houseAnchors.size()];

        for (int i = 0; i < houseAnchors.size(); i++) {
            if (taken[i]) continue;

            List<HouseBlockPos> cluster = new ArrayList<>();
            cluster.add(houseAnchors.get(i));
            taken[i] = true;

            for (int scan = 0; scan < cluster.size(); scan++) {
                HouseBlockPos from = cluster.get(scan);
                for (int j = 0; j < houseAnchors.size(); j++) {
                    if (taken[j]) continue;
                    HouseBlockPos candidate = houseAnchors.get(j);
                    double dx = candidate.x - from.x;
                    double dz = candidate.z - from.z;
                    if (dx * dx + dz * dz <= LINK_DISTANCE_SQ) {
                        taken[j] = true;
                        cluster.add(candidate);
                    }
                }
            }

            built.add(toVillage(cluster));
        }

        villages = List.copyOf(built);

        // Collect all chunks currently occupied by villages
        Set<Long> currentAllChunks = new HashSet<>();
        for (Village v : built) {
            if (v.chunkIndices() != null) {
                currentAllChunks.addAll(v.chunkIndices());
            }
        }

        // Detect modified chunks to invalidate map cache
        Set<Long> changedChunks = new HashSet<>();
        for (Long c : lastKnownVillageChunks) {
            if (!currentAllChunks.contains(c)) changedChunks.add(c);
        }
        for (Long c : currentAllChunks) {
            if (!lastKnownVillageChunks.contains(c)) changedChunks.add(c);
        }
        lastKnownVillageChunks = Set.copyOf(currentAllChunks);

        if (!changedChunks.isEmpty()) {
            invalidateMapChunks(changedChunks);
        }

        LOGGER.debug("[SimTale] Villages recalculated: {} village(s) from {} house(s)",
                built.size(), houseAnchors.size());
    }

    private static void invalidateMapChunks(Set<Long> chunkIndices) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            LongSet set = new LongOpenHashSet(chunkIndices);
            for (World world : universe.getWorlds().values()) {
                if (world != null) {
                    world.getWorldMapManager().clearImagesInChunks(set);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[SimTale] Map chunk invalidation skipped: {}", t.toString());
        }
    }

    private static Village toVillage(List<HouseBlockPos> cluster) {
        double sumX = 0;
        double sumZ = 0;
        for (HouseBlockPos bed : cluster) {
            sumX += bed.x;
            sumZ += bed.z;
        }
        double centerX = sumX / cluster.size();
        double centerZ = sumZ / cluster.size();

        double furthestSq = 0;
        for (HouseBlockPos bed : cluster) {
            double dx = bed.x - centerX;
            double dz = bed.z - centerZ;
            furthestSq = Math.max(furthestSq, dx * dx + dz * dz);
        }

        double radius = Math.sqrt(furthestSq) + RADIUS_MARGIN;
        Set<Long> chunks = computeChunks(centerX, centerZ, radius, cluster);

        return new Village(centerX, centerZ, radius, cluster.size(), chunks);
    }

    private static Set<Long> computeChunks(double centerX, double centerZ, double radius,
            List<HouseBlockPos> cluster) {
        Set<Long> chunks = new HashSet<>();

        // Always include chunks that contain any house anchor bed
        for (HouseBlockPos bed : cluster) {
            int cx = ChunkUtil.chunkCoordinate(bed.x);
            int cz = ChunkUtil.chunkCoordinate(bed.z);
            chunks.add(ChunkUtil.indexChunk(cx, cz));
        }

        int minChunkX = ChunkUtil.chunkCoordinate(centerX - radius);
        int maxChunkX = ChunkUtil.chunkCoordinate(centerX + radius);
        int minChunkZ = ChunkUtil.chunkCoordinate(centerZ - radius);
        int maxChunkZ = ChunkUtil.chunkCoordinate(centerZ + radius);

        double rSq = radius * radius;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                double minX = cx * 32.0;
                double maxX = minX + 32.0;
                double minZ = cz * 32.0;
                double maxZ = minZ + 32.0;

                double closestX = Math.clamp(centerX, minX, maxX);
                double closestZ = Math.clamp(centerZ, minZ, maxZ);

                double dx = centerX - closestX;
                double dz = centerZ - closestZ;
                if (dx * dx + dz * dz <= rSq) {
                    chunks.add(ChunkUtil.indexChunk(cx, cz));
                }
            }
        }

        return Collections.unmodifiableSet(chunks);
    }
}
