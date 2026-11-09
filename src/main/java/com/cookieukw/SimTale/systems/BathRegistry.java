package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BathRegistry {
    private BathRegistry() {}

    private static final Map<World, Set<HouseBlockPos>> BATHS_BY_WORLD = new ConcurrentHashMap<>();

    /** World-scoped registered baths. The returned set is synchronized for safe tick/debug access. */
    public static Set<HouseBlockPos> baths(World world) {
        if (world == null) return Collections.emptySet();
        return BATHS_BY_WORLD.computeIfAbsent(world,
                ignored -> Collections.synchronizedSet(new HashSet<>()));
    }

    public static int size(World world) {
        return baths(world).size();
    }

    public static boolean isBathId(String id) {
        if (id == null) return false;
        String lower = id.toLowerCase();
        return lower.contains("bath") || lower.contains("tub");
    }

    public static void add(World world, int x, int y, int z) {
        if (world == null) return;
        baths(world).add(new HouseBlockPos(x, y, z));
    }

    public static void removeAt(World world, int x, int y, int z) {
        if (world == null) return;
        baths(world).remove(new HouseBlockPos(x, y, z));
    }

    /**
     * Same-world search that skips a tile another NPC is already headed to. Without this, two dirty
     * NPCs near the same tub both got handed the identical nearest tile (RoutineTaskHelpers'
     * handleFindingBath just took whatever this returned) and converged on top of each other --
     * the same class of bug NPCWorkHelper's crop/farmland scan had before a selfId parameter
     * was added there.
     */
    public static Vector3i nearestTo(World world, double x, double y, double z, UUID selfId) {
        if (world == null) return null;
        HouseBlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;
        Set<HouseBlockPos> worldBaths = baths(world);
        synchronized (worldBaths) {
            for (HouseBlockPos p : worldBaths) {
                if (selfId != null && isClaimedByAnotherNpc(world, p, selfId)) continue;
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

    private static boolean isClaimedByAnotherNpc(World world, HouseBlockPos tile, UUID selfId) {
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other.entityId == null || other.entityId.equals(selfId)) continue;
            if (other.entityRef == null || !other.entityRef.isValid()) continue;
            if (other.entityRef.getStore().getExternalData().getWorld() != world) continue;
            RoutineAIComponent otherAi = other.entityRef.getStore().getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (otherAi == null || otherAi.targetBlockPosition == null) continue;
            /* Unlike a harvested crop (which vanishes from the block scan the instant it's taken),
            a bath tile stays a valid bath the whole time someone is in it -- so BATHING has to
            count as a claim too, not just the MOVING_TO_BATH approach, or a second NPC's search
            would walk right into an occupied tub the moment the first one finished arriving.
            */
            if ((otherAi.currentTask == RoutineAIComponent.TaskType.MOVING_TO_BATH
                    || otherAi.currentTask == RoutineAIComponent.TaskType.BATHING)
                    && otherAi.targetBlockPosition.x == tile.x
                    && otherAi.targetBlockPosition.y == tile.y
                    && otherAi.targetBlockPosition.z == tile.z) {
                return true;
            }
        }
        return false;
    }
}
