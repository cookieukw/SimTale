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
        if (lower.contains("leisure_fishing") || lower.contains("leisure_water") || lower.contains("fishing_chair")) return Hobby.FISHING;
        if (lower.contains("leisure_mining") || lower.contains("leisure_stone") || lower.contains("target_practice")) return Hobby.MINING;
        if (lower.contains("leisure_gardening") || lower.contains("leisure_crop") || lower.contains("flower_pot")) return Hobby.GARDENING;
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

    public static Vector3i nearestTo(double x, double y, double z, Hobby requiredHobby) {
        LeisureBlock closest = null;
        double closestDistSq = Double.MAX_VALUE;
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
