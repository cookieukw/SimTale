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
            // Same staggering as the other professions' IDLE dispatch — bypassed instantly by a
            // debug command forcing this NPC.
            if (world.getTick() % 20 != 0 && !ai.forcedByDebug) return;

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

    /** Nearest hostile within {@link #GUARD_SCAN_RADIUS} of {@code center}, or null. Iterates
     *  every modelled entity in the world (there is no dedicated hostile/faction component to
     *  query against) via {@code store.forEachChunk}, which — unlike the tick systems elsewhere
     *  in this codebase — can be called from a one-shot context, not just from inside another
     *  system's own tick. */
    private static UUID scanForHostile(Vector3d center, World world, Store<EntityStore> store) {
        double[] bestDistSq = { GUARD_SCAN_RADIUS * GUARD_SCAN_RADIUS };
        UUID[] best = { null };

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

                double dx = t.getPosition().x - center.x;
                double dy = t.getPosition().y - center.y;
                double dz = t.getPosition().z - center.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq[0]) {
                    bestDistSq[0] = distSq;
                    best[0] = uuidComp.getUuid();
                }
            }
        });

        return best[0];
    }
}
