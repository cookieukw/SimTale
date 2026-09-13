package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.NPCPreferences.Hobby;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LeisureRegistry {
    private LeisureRegistry() {}

    public record LeisureBlock(int x, int y, int z, Hobby hobby) {}

    public static final Set<LeisureBlock> LEISURE_BLOCKS = Collections.synchronizedSet(new HashSet<>());

    public static Hobby getHobbyForId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase();
        
        // Fishing spots marked by buckets or stools
        if (lower.contains("bucket") || lower.contains("stool")) return Hobby.FISHING;
        
        // Mining spots marked by lanterns or campfires
        if (lower.contains("lantern") || lower.contains("campfire")) return Hobby.MINING;
        
        // Gardening spots marked by pots or planters
        if (lower.contains("pot") || lower.contains("planter")) return Hobby.GARDENING;
        
        return null;
    }

    public static boolean isLeisureId(String id) {
        return getHobbyForId(id) != null;
    }

    public static void add(int x, int y, int z, Hobby hobby) {
        if (hobby != null) {
            LEISURE_BLOCKS.add(new LeisureBlock(x, y, z, hobby));
        }
    }

    public static void removeAt(int x, int y, int z) {
        synchronized (LEISURE_BLOCKS) {
            LEISURE_BLOCKS.removeIf(p -> p.x() == x && p.y() == y && p.z() == z);
        }
    }

    /**
     * How far an NPC will look for a leisure block for its hobby.
     *
     * <p>Unlike {@code BathRegistry.nearestTo} (whose one caller in {@code RoutineAISystem}
     * already checks the distance itself before committing to walk), {@code NPCLeisureHelper}
     * commits to {@code MOVING_TO_LEISURE} as soon as this returns non-null, with no distance
     * check of its own -- the same unguarded shape that let a farmer walk off toward a scarecrow
     * on the far side of the map before {@code FarmPostRegistry.CLAIM_SEARCH_RADIUS} existed.
     * Added during the 13/09 optimization pass.
     */
    private static final double SEARCH_RADIUS = 48.0;

    public static Vector3i nearestTo(double x, double y, double z, Hobby requiredHobby) {
        LeisureBlock closest = null;
        double closestDistSq = SEARCH_RADIUS * SEARCH_RADIUS;
        synchronized (LEISURE_BLOCKS) {
            for (LeisureBlock p : LEISURE_BLOCKS) {
                if (p.hobby() != requiredHobby) continue;

                double dx = p.x() + 0.5 - x;
                double dy = p.y() - y;
                double dz = p.z() + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = p;
                }
            }
        }
        if (closest != null) {
            return new Vector3i(closest.x(), closest.y(), closest.z());
        }
        return null;
    }
}
