package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimLog;

import java.util.ArrayList;
import java.util.List;
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

    /** Two houses this close (in blocks, on the XZ plane) are neighbours. */
    private static final double LINK_DISTANCE = 40.0;
    private static final double LINK_DISTANCE_SQ = LINK_DISTANCE * LINK_DISTANCE;

    /**
     * Breathing room added to the furthest house when measuring a village's radius.
     *
     * <p>Without it a one-house village would have radius zero, and its resident would be pinned to
     * the doorstep.
     */
    private static final double RADIUS_MARGIN = 16.0;

    private VillageManager() {
    }

    /**
     * A cluster of houses.
     *
     * @param centerX  midpoint of the member beds
     * @param centerZ  midpoint of the member beds
     * @param radius   distance from the centre to the furthest member, plus {@link #RADIUS_MARGIN}
     * @param houses   how many houses formed it
     */
    public record Village(double centerX, double centerZ, double radius, int houses) {

        public double distanceSqTo(double x, double z) {
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz;
        }

        public boolean contains(double x, double z) {
            return distanceSqTo(x, z) <= radius * radius;
        }
    }

    private static volatile List<Village> villages = List.of();

    /**
     * Set whenever the house set changes; the rebuild happens on the next query instead of inside
     * the mutation.
     *
     * <p>Claiming a bed can register a house, and that runs inside an NPC's tick — recomputing the
     * clustering there would put the cost on whichever NPC happened to move in.
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
     * Single-linkage clustering over the house beds.
     *
     * <p>O(n²) on purpose. Houses are counted in dozens, this runs only when they change, and a
     * spatial index would be more code than the problem deserves.
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

            /* Flood fill: seed with one house, then keep absorbing any house close to one already
            absorbed. This is what makes the chaining work.
            */
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
        LOGGER.debug("[SimTale] Vilas recalculadas: {} vila(s) a partir de {} casa(s)",
                built.size(), beds.size());
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

        return new Village(centerX, centerZ, Math.sqrt(furthestSq) + RADIUS_MARGIN, cluster.size());
    }
}
