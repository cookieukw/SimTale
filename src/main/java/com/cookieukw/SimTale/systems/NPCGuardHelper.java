package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Guard combat: detect a hostile mob nearby, walk to it, and take it out. No profession did
 * anything at all for Guard before this — {@code Profession.GUARD} existed only so a sword gift
 * could assign the title, with zero actual behavior behind it.
 * <p>
 * Deliberately not real engine combat (swing timing, hit detection, the NPC's own health at
 * risk) — same reasoning as the Hunter/Miner expedition redesign this session: an NPC losing a
 * fight to a skeleton and needing to be rescued/replaced was more complexity and risk than this
 * project wants for a background villager. Unlike the expedition though, this stays visible and
 * local (walk over, swing, done) rather than a whole vanish-and-return abstraction, since it's
 * meant to read as "the guard handled that thing right next to you," not an offscreen errand.
 */
public class NPCGuardHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCGuardHelper.class);

    private static final double GUARD_SCAN_RADIUS = 10.0;
    private static final double MELEE_RANGE_SQ = 2.5 * 2.5;
    private static final int ATTACK_DURATION_TICKS = 60; // 3s
    private static final int MOVE_TIMEOUT_TICKS = 600; // 30s, same as other MOVING_* states

    /** Model asset id keywords (lowercase) that mark a hostile mob. Confirmed via
     *  {@code /simtale debugnear}'s entity dump — the skeleton's raw model id is exactly
     *  "Skeleton". Add more here once confirmed the same way; guessing got the old Hunter
     *  "contains creature" filter permanently broken (it never matched anything real). */
    private static final String[] HOSTILE_MODEL_KEYWORDS = { "skeleton" };

    private static boolean isHostileModelId(String modelAssetId) {
        if (modelAssetId == null) return false;
        String lower = modelAssetId.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : HOSTILE_MODEL_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    public static void handleGuardLogic(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        if (npc.profession != Profession.GUARD) return;

        if (ai.currentTask == TaskType.IDLE) {
            // Staggered per guard rather than on a shared tick boundary: every guard checking on
            // the same tick meant the whole squad's work landed in one frame, which is the shape
            // that produces a visible hitch even when the total work is modest.
            if (!ai.forcedByDebug && (world.getTick() + guardPhase(npc)) % GUARD_SCAN_PERIOD_TICKS != 0) {
                return;
            }

            Vector3d pos = transform.getPosition();
            UUID hostileId = scanForHostile(pos, world, store);
            if (hostileId != null) {
                ai.workTargetEntityId = hostileId;
                ai.currentTask = TaskType.MOVING_TO_FIGHT;
                ai.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            }
            return;
        }

        if (ai.currentTask == TaskType.MOVING_TO_FIGHT) {
            if (ai.workTargetEntityId == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] Guard {} desistiu de chegar ate o hostil", npc.name);
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            Ref<EntityStore> hostileRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
            TransformComponent hostileTransform = hostileRef != null
                    ? store.getComponent(hostileRef, TransformComponent.getComponentType()) : null;
            if (hostileTransform == null) {
                // Gone already — someone/something else got it, or it wandered out of the world.
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            Vector3d pos = transform.getPosition();
            double dx = hostileTransform.getPosition().x - pos.x;
            double dz = hostileTransform.getPosition().z - pos.z;
            if (dx * dx + dz * dz <= MELEE_RANGE_SQ) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.FIGHTING;
                ai.taskStartTime = world.getTick();
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, hostileTransform.getPosition());
            }
            return;
        }

        if (ai.currentTask == TaskType.FIGHTING) {
            if (ai.workTargetEntityId == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Ref<EntityStore> hostileRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
            if (hostileRef == null || store.getComponent(hostileRef, TransformComponent.getComponentType()) == null) {
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            if (world.getTick() - ai.taskStartTime == 1) {
                // No dedicated sword-swing animation available — same "Smith" stand-in the other
                // professions reuse for "NPC is doing manual work at a fixed spot".
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
            }

            if (world.getTick() - ai.taskStartTime >= ATTACK_DURATION_TICKS) {
                LOGGER.debug("[SimTale] Guard {} derrotou um hostil ({})", npc.name, ai.workTargetEntityId);
                commandBuffer.removeEntity(hostileRef, RemoveReason.REMOVE);
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
            }
        }
    }

    /**
     * Nearest hostile within {@link #GUARD_SCAN_RADIUS} of {@code center}, or null.
     *
     * <p>Reads a shared snapshot instead of sweeping the world. The sweep itself visits every
     * modelled entity there is — mobs, dropped items, projectiles, the other NPCs — because there
     * is no hostile or faction component to query against, so the model id can only be checked
     * after the entity has already been visited. Doing that once per guard per second meant the
     * cost scaled with the number of guards while answering the same question every time.
     *
     * <p>Now the sweep runs at most once per {@link #HOSTILE_CACHE_TICKS} for the whole world and
     * every guard filters the resulting handful of positions, which is the part that legitimately
     * differs between them.
     */
    private static UUID scanForHostile(Vector3d center, World world, Store<EntityStore> store) {
        double bestDistSq = GUARD_SCAN_RADIUS * GUARD_SCAN_RADIUS;
        UUID best = null;

        for (Hostile hostile : hostiles(world, store)) {
            double dx = hostile.x() - center.x;
            double dy = hostile.y() - center.y;
            double dz = hostile.z() - center.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hostile.id();
            }
        }

        return best;
    }

    /** A hostile's identity and where it was when the snapshot was taken. */
    private record Hostile(UUID id, double x, double y, double z) {
    }

    /**
     * Snapshot of every hostile in the world, rebuilt at most once per {@link #HOSTILE_CACHE_TICKS}.
     *
     * <p>Deliberately stale by up to a second. A guard reacting to where a skeleton was a moment
     * ago is indistinguishable in play from one reacting instantly, and the target is re-resolved
     * by id before the guard commits to anything.
     *
     * <p>The fields are {@code volatile} and the list is replaced whole rather than mutated, so a
     * reader always sees a complete snapshot. Two ticking threads racing here would at worst each
     * build one — wasteful but harmless, and cheaper than locking on every read.
     */
    private static volatile List<Hostile> hostileCache = List.of();
    private static volatile long hostileCacheTick = Long.MIN_VALUE;
    private static volatile World hostileCacheWorld = null;

    private static List<Hostile> hostiles(World world, Store<EntityStore> store) {
        long tick = world.getTick();
        if (world == hostileCacheWorld && tick - hostileCacheTick < HOSTILE_CACHE_TICKS) {
            return hostileCache;
        }

        List<Hostile> found = new ArrayList<>();
        store.forEachChunk(PersistentModel.getComponentType(), (chunk, cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                PersistentModel pm = chunk.getComponent(i, PersistentModel.getComponentType());
                if (pm == null || pm.getModelReference() == null
                        || !isHostileModelId(pm.getModelReference().getModelAssetId())) {
                    continue;
                }
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                UUIDComponent uuidComp = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (t == null || uuidComp == null) continue;

                Vector3d p = t.getPosition();
                found.add(new Hostile(uuidComp.getUuid(), p.x, p.y, p.z));
            }
        });

        hostileCache = List.copyOf(found);
        hostileCacheTick = tick;
        hostileCacheWorld = world;
        return hostileCache;
    }
}
