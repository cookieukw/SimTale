package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Handles seating routine: NPCs find unoccupied chairs, walk to them, sit to rest,
 * recover energy/fun gradually, and stand up once rested or when needed elsewhere.
 */
public final class NPCSeatingHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCSeatingHelper.class);

    public static final double CHAIR_SEARCH_RADIUS = 32.0;
    public static final int CHAIR_SEARCH_COOLDOWN_TICKS = 100;
    public static final int CHAIR_SIT_DURATION_TICKS = 600; // 30 seconds
    public static final int MOVE_TIMEOUT_TICKS = 400;       // 20 seconds
    private static final double REACH_DISTANCE_SQ = 2.0 * 2.0;

    /** Energy recovered per tick while sitting (~1.6 energy/sec). */
    public static final float ENERGY_PER_TICK = 0.08f;
    /** Fun recovered per tick while relaxing in a chair. */
    public static final float FUN_PER_TICK = 0.04f;

    private NPCSeatingHelper() {}

    public static void handleSeatingLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (ai.currentTask == TaskType.FINDING_CHAIR) {
            handleFindingChair(ref, npc, ai, transform, world, store);
        } else if (ai.currentTask == TaskType.MOVING_TO_CHAIR) {
            handleMovingToChair(ref, npc, ai, transform, world, store, commandBuffer);
        } else if (ai.currentTask == TaskType.SITTING) {
            handleSitting(ref, npc, ai, transform, world, store, commandBuffer);
        }
    }

    private static void handleFindingChair(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        Vector3d pos = transform.getPosition();
        Vector3i chair = ChairRegistry.findNearestUnoccupied(pos.x, pos.y, pos.z, CHAIR_SEARCH_RADIUS);

        if (chair != null && ChairRegistry.claimChair(chair, npc.entityId)) {
            ai.targetChairPos = new Vector3i(chair);
            ai.targetBlockPosition = new Vector3i(chair);
            ai.currentTask = TaskType.MOVING_TO_CHAIR;
            ai.taskStartTime = world.getTick();

            Vector3i standPos = NPCMovementHelper.findStandableBeside(chair, transform, world);
            Vector3d target = standPos != null
                    ? new Vector3d(standPos.x + 0.5, standPos.y, standPos.z + 0.5)
                    : new Vector3d(chair.x + 0.5, chair.y, chair.z + 0.5);

            NPCMovementHelper.moveTo(ref, ai, world, target);
            NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            LOGGER.debug("[SimTale] NPC '{}' claimed chair at ({},{},{}) and is moving to it",
                    npc.name, chair.x, chair.y, chair.z);
        } else {
            ai.nextChairSearchTick = world.getTick() + CHAIR_SEARCH_COOLDOWN_TICKS;
            ai.currentTask = TaskType.IDLE;
        }
    }

    private static void handleMovingToChair(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (ai.targetChairPos == null || !ChairRegistry.CHAIRS.contains(ai.targetChairPos)) {
            exitSitting(ref, store, commandBuffer, npc, ai);
            ai.currentTask = TaskType.IDLE;
            return;
        }

        if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
            exitSitting(ref, store, commandBuffer, npc, ai);
            ai.nextChairSearchTick = world.getTick() + CHAIR_SEARCH_COOLDOWN_TICKS;
            ai.currentTask = TaskType.IDLE;
            return;
        }

        Vector3d chairCenter = new Vector3d(ai.targetChairPos.x + 0.5, ai.targetChairPos.y, ai.targetChairPos.z + 0.5);
        double distSq = transform.getPosition().distanceSquared(chairCenter);

        if (distSq <= REACH_DISTANCE_SQ) {
            NPCMovementHelper.clearMoveTarget(ref, ai);

            Vector3d hitOffset = new Vector3d(ai.targetChairPos.x + 0.5, ai.targetChairPos.y + 0.5, ai.targetChairPos.z + 0.5);
            BlockMountAPI.BlockMountResult res = BlockMountAPI.mountOnBlock(ref, commandBuffer, ai.targetChairPos, hitOffset);

            if (!(res instanceof BlockMountAPI.Mounted)) {
                // If block does not declare native Hytale seat mount points, settle transform manually
                transform.setPosition(new Vector3d(ai.targetChairPos.x + 0.5, ai.targetChairPos.y + 0.4, ai.targetChairPos.z + 0.5));
            }

            NPCMovementHelper.pinLeashAt(ref, ai, transform.getPosition());
            NPCMovementHelper.setSittingState(ref, store, commandBuffer, true);

            ai.currentTask = TaskType.SITTING;
            ai.taskStartTime = world.getTick();
            npc.setEmotion(Mood.HAPPY, 0.6f, "resting", world.getTick());
            LOGGER.info("[SimTale] NPC '{}' is now sitting on chair at ({},{},{})",
                    npc.name, ai.targetChairPos.x, ai.targetChairPos.y, ai.targetChairPos.z);
        } else {
            Vector3i standPos = NPCMovementHelper.findStandableBeside(ai.targetChairPos, transform, world);
            Vector3d target = standPos != null
                    ? new Vector3d(standPos.x + 0.5, standPos.y, standPos.z + 0.5)
                    : chairCenter;
            NPCMovementHelper.moveTo(ref, ai, world, target);
        }
    }

    private static void handleSitting(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        if (ai.targetChairPos == null) {
            exitSitting(ref, store, commandBuffer, npc, ai);
            ai.currentTask = TaskType.IDLE;
            return;
        }

        // Verify chair still exists in the world
        BlockType currentBlock = world.getBlockType(ai.targetChairPos.x, ai.targetChairPos.y, ai.targetChairPos.z);
        if (currentBlock == null || !ChairRegistry.isChair(currentBlock.getId())) {
            exitSitting(ref, store, commandBuffer, npc, ai);
            ai.currentTask = TaskType.IDLE;
            return;
        }

        // Recover energy and fun while seated
        NeedsHelper.setNeed(store, ref, NeedsHelper.ENERGY_ID,
                NeedsHelper.getNeed(store, ref, NeedsHelper.ENERGY_ID) + ENERGY_PER_TICK);
        NeedsHelper.setNeed(store, ref, NeedsHelper.FUN_ID,
                NeedsHelper.getNeed(store, ref, NeedsHelper.FUN_ID) + FUN_PER_TICK);

        // Keep sitting pose settled periodically
        if (world.getTick() % 40 == 0) {
            NPCMovementHelper.setSittingState(ref, store, commandBuffer, true);
        }

        boolean durationExpired = (world.getTick() - ai.taskStartTime) >= CHAIR_SIT_DURATION_TICKS;
        boolean energyRested = NeedsHelper.getNeed(store, ref, NeedsHelper.ENERGY_ID) >= 85f;

        if (durationExpired || energyRested) {
            exitSitting(ref, store, commandBuffer, npc, ai);
            ai.nextChairSearchTick = world.getTick() + 400; // 20s cooldown before next chair search
            ai.currentTask = TaskType.IDLE;
            LOGGER.info("[SimTale] NPC '{}' finished resting on chair, standing up", npc.name);
        }
    }

    public static void exitSitting(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            SimNPCComponent npc,
            RoutineAIComponent ai
    ) {
        if (commandBuffer != null && ref != null && ref.isValid()) {
            commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
        }
        if (ref != null && store != null && ref.isValid()) {
            NPCMovementHelper.setSittingState(ref, store, commandBuffer, false);
        }
        if (ai != null) {
            if (ai.targetChairPos != null) {
                ChairRegistry.releaseChair(ai.targetChairPos);
                ai.targetChairPos = null;
            }
            ai.targetBlockPosition = null;
        }
    }
}
