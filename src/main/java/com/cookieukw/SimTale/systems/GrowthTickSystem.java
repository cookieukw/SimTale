package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

/**
 * Tick system for child growth.
 */
public class GrowthTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int TICK_INTERVAL = 100; 
    private long lastTick = 0;

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        // Needs a valid query to be registered.
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Run only once per interval, at the first index
        if (index != 0) return;

        World world = WorldUtil.first();
        if (world == null) return;
        long worldTick = world.getTick();

        // Throttle: Only runs every TICK_INTERVAL ticks
        if (worldTick - lastTick < TICK_INTERVAL) return;
        lastTick = worldTick;

        /* Iterate all active children

        One promotion per pass. A batch of records loaded from disk is typically far past due —
        their birthTick is old, so every one of them qualifies to grow the instant the list is
        populated. Promoting them all in a single tick spawned five entities at once, on top of
        each other, and printed five "your baby grew up" lines in the same frame. Spreading them
        across passes costs nothing (this system is already throttled) and keeps a backlog
        looking like a sequence of events rather than one glitch.
        */
        boolean promotedThisPass = false;

        for (int i = LifecycleManager.ACTIVE_CHILDREN.size() - 1; i >= 0; i--) {
            GrowthComponent child = LifecycleManager.ACTIVE_CHILDREN.get(i);

            boolean willPromote = child.wouldChangeStage(worldTick);
            if (willPromote && promotedThisPass) {
                continue;
            }
            if (willPromote) {
                promotedThisPass = true;
            }

            LifecycleManager.tickGrowth(child, worldTick);

            // Proximity AI: Children and teenagers follow their parents
            if (child.stage != GrowthStage.BABY && !child.isAdult()) {
                Ref<EntityStore> childRef = world.getEntityStore().getRefFromUUID(child.childId);
                if (childRef != null && childRef.isValid()) {
                    RoutineAIComponent ai = store.getComponent(childRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (ai != null && (ai.currentTask == RoutineAIComponent.TaskType.SLEEPING
                            || ai.currentTask == RoutineAIComponent.TaskType.ENTERING_BED
                            || ai.currentTask == RoutineAIComponent.TaskType.MOVING_TO_BED)) {
                        continue;
                    }

                    Ref<EntityStore> parentRef = world.getEntityStore().getRefFromUUID(child.motherId);
                    if (parentRef == null) {
                        parentRef = world.getEntityStore().getRefFromUUID(child.fatherId);
                    }
                    
                    if (parentRef != null && parentRef.isValid()) {
                        TransformComponent childT = store.getComponent(childRef, TransformComponent.getComponentType());
                        TransformComponent parentT = store.getComponent(parentRef, TransformComponent.getComponentType());
                        if (childT != null && parentT != null) {
                            double distSq = childT.getPosition().distanceSquared(parentT.getPosition());
                            if (distSq > 36.0) { // More than 6 blocks away -> follow parent with individual offset
                                int hash = child.childId != null ? child.childId.hashCode() : 0;
                                double angle = (hash & 0xFFFF) * (Math.PI * 2.0 / 65536.0);
                                double offsetDist = 2.0 + ((hash >> 16) & 1); // 2 to 3 blocks
                                double targetX = parentT.getPosition().x + Math.cos(angle) * offsetDist;
                                double targetZ = parentT.getPosition().z + Math.sin(angle) * offsetDist;
                                Vector3d targetPos = new Vector3d(targetX, parentT.getPosition().y, targetZ);

                                if (ai != null) {
                                    NPCMovementHelper.moveTo(childRef, ai, world, targetPos);
                                } else {
                                    NPCEntity npcEntity = store.getComponent(childRef, Objects.requireNonNull(NPCEntity.getComponentType()));
                                    if (npcEntity != null) {
                                        npcEntity.setLeashPoint(targetPos);
                                        StateSupport stateSupport = StateSupport.get(childRef, store);
                                        if (stateSupport != null) {
                                            stateSupport.setState(childRef, NPCMovementHelper.STATE_MOVING, null, store);
                                        }
                                    }
                                }
                            } else if (distSq <= 16.0) { // Within 4 blocks -> arrived, return to idle
                                StateSupport stateSupport = StateSupport.get(childRef, store);
                                if (stateSupport != null && stateSupport.getStateName() != null
                                        && stateSupport.getStateName().startsWith(NPCMovementHelper.STATE_MOVING)) {
                                    if (ai != null) {
                                        NPCMovementHelper.clearMoveTarget(childRef, ai);
                                    } else {
                                        NPCEntity npcEntity = store.getComponent(childRef, Objects.requireNonNull(NPCEntity.getComponentType()));
                                        if (npcEntity != null) {
                                            npcEntity.setLeashPoint(new Vector3d(childT.getPosition().x, childT.getPosition().y, childT.getPosition().z));
                                            stateSupport.setState(childRef, "Idle", null, store);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* If they became an adult, drop them from the list. Remove by identity, not by
            index: onBecameAdult() already removed the child itself, so remove(i) was
            evicting a *different, still-growing* child that had shifted into that slot.
            */
            if (child.isAdult()) {
                LifecycleManager.ACTIVE_CHILDREN.remove(child);
            }
        }

        // Mother AI tick for each mother with children needing care
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId == null) continue;
            List<GrowthComponent> needingCare =
                LifecycleManager.findChildrenNeedingCare(npc.entityId);
            for (GrowthComponent baby : needingCare) {
                LifecycleManager.tickMotherAI(npc, baby, worldTick);
            }
        }
    }
}
