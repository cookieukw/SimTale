package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BathRegistry {
    private BathRegistry() {}

    public static final Set<HouseBlockPos> BATHS = Collections.synchronizedSet(new HashSet<>());

    public static boolean isBathId(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.contains("bath") || lower.contains("tub");
    }

    public static void add(int x, int y, int z) {
        BATHS.add(new HouseBlockPos(x, y, z));
    }

    public static void removeAt(int x, int y, int z) {
        BATHS.remove(new HouseBlockPos(x, y, z));
    }

    public static Vector3i nearestTo(double x, double y, double z) {
        return nearestTo(x, y, z, null);
    }

    /**
     * Same search, but skips a tile another NPC is already headed to. Without this, two dirty
     * NPCs near the same tub both got handed the identical nearest tile (RoutineTaskHelpers'
     * handleFindingBath just took whatever this returned) and converged on top of each other --
     * the same class of bug NPCWorkHelper's crop/farmland scan had before a selfId parameter
     * was added there.
     */
    public static Vector3i nearestTo(double x, double y, double z, UUID selfId) {
        HouseBlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;
        synchronized (BATHS) {
            for (HouseBlockPos p : BATHS) {
                if (selfId != null && isClaimedByAnotherNpc(p, selfId)) continue;
                double dx = p.x + 0.5 - x;
                double dy = p.y - y;
                double dz = p.z + 0.5 - z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = p;
                }
            }
        }
        if (closest != null) {
            return new Vector3i(closest.x, closest.y, closest.z);
        }
        return null;
    }

    private static boolean isClaimedByAnotherNpc(HouseBlockPos tile, UUID selfId) {
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other.entityId == null || other.entityId.equals(selfId)) continue;
            if (other.entityRef == null || !other.entityRef.isValid()) continue;
            RoutineAIComponent otherAi = other.entityRef.getStore().getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (otherAi == null || otherAi.targetBlockPosition == null) continue;
            if (otherAi.currentTask == RoutineAIComponent.TaskType.MOVING_TO_BATH
                    && otherAi.targetBlockPosition.x == tile.x
                    && otherAi.targetBlockPosition.y == tile.y
                    && otherAi.targetBlockPosition.z == tile.z) {
                return true;
            }
        }
        return false;
    }
}
