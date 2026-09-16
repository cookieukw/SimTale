package com.cookieukw.SimTale.systems;

import org.joml.Vector3i;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registry for seating furniture (chairs, stools, benches, sofas, couches).
 * <p>
 * Unlike beds, which are assigned to specific NPCs or family members, chairs are
 * communal: any NPC can sit in any unoccupied chair in the village or home.
 */
public final class ChairRegistry {

    private ChairRegistry() {}

    public static final Set<Vector3i> CHAIRS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<Vector3i, UUID> OCCUPIED = Collections.synchronizedMap(new HashMap<>());

    public static boolean isChair(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase();
        // Exclude crafting/processing benches
        if (lower.contains("workbench") || lower.contains("alchemybench")
                || lower.contains("cookingbench") || lower.contains("bench_lumbermill")) {
            return false;
        }
        return lower.contains("chair") || lower.contains("stool") || lower.contains("bench")
                || lower.contains("seat") || lower.contains("sofa") || lower.contains("couch");
    }

    public static void add(int x, int y, int z) {
        CHAIRS.add(new Vector3i(x, y, z));
    }

    public static void removeAt(int x, int y, int z) {
        Vector3i pos = new Vector3i(x, y, z);
        CHAIRS.remove(pos);
        OCCUPIED.remove(pos);
    }

    public static boolean isOccupied(Vector3i pos) {
        if (pos == null) return false;
        UUID holder = OCCUPIED.get(pos);
        if (holder == null) return false;
        if (isStale(holder)) {
            // Self-heal: whoever claimed this chair is gone with nothing left to call
            // releaseChair for her -- most commonly a player leaving and rejoining the same
            // server, which leaves ACTIVE_NPCS/OCCUPIED exactly as they were (nothing clears
            // either automatically, only the manual /simtale clearall command does), while the
            // entity she was sitting on no longer exists in the reloaded world. Without this the
            // chair reads "ocupada" forever and no NPC -- and no /simtale chaircheck -- can ever
            // tell why, since nobody is actually mounted on it. Same "the world is the source of
            // truth, the registry is just a cache" rule BedWorldBootstrap already applies to
            // chests/farmland/posts, applied here to occupancy instead of existence.
            OCCUPIED.remove(pos);
            return false;
        }
        return true;
    }

    /** Test hook to bypass live NPC entity validation in isolated unit test environments. */
    public static boolean staleCheckEnabled = true;

    /** Whether {@code holder} no longer names a live, tracked NPC -- a claim nobody can release. */
    private static boolean isStale(UUID holder) {
        if (!staleCheckEnabled) return false;
        SimNPCComponent npc = SimTale.findNpc(holder);
        return npc == null || npc.entityRef == null || !npc.entityRef.isValid();
    }

    public static synchronized boolean claimChair(Vector3i pos, UUID npcId) {
        if (pos == null || npcId == null) return false;
        UUID current = OCCUPIED.get(pos);
        if (current == null || current.equals(npcId) || isStale(current)) {
            OCCUPIED.put(new Vector3i(pos), npcId);
            return true;
        }
        return false;
    }

    public static synchronized void releaseChair(Vector3i pos) {
        if (pos == null) return;
        OCCUPIED.remove(pos);
    }

    public static synchronized void releaseAllForNpc(UUID npcId) {
        if (npcId == null) return;
        OCCUPIED.values().removeIf(id -> id.equals(npcId));
    }

    public static Vector3i findNearestUnoccupied(double x, double y, double z, double maxRadius) {
        Vector3i best = null;
        double bestDistSq = maxRadius * maxRadius;

        synchronized (CHAIRS) {
            for (Vector3i pos : CHAIRS) {
                if (isOccupied(pos)) continue;

                double dx = pos.x + 0.5 - x;
                double dy = pos.y - y;
                double dz = pos.z + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = pos;
                }
            }
        }
        return best;
    }

    public static void clear() {
        CHAIRS.clear();
        OCCUPIED.clear();
    }
}
