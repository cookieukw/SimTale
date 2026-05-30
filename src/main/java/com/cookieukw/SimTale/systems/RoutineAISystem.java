package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;


import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.SimNPCFactory.NPCType;

import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import java.util.UUID;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Math;

import javax.annotation.Nonnull;

public class RoutineAISystem extends EntityTickingSystem<EntityStore> {

    @Override
    @Nonnull
    @SuppressWarnings("unchecked")
    public Query<EntityStore> getQuery() {
        return (Query<EntityStore>) (Object) SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    @SuppressWarnings({ "null" })
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null || npc.needs == null) return;

        RoutineAIComponent ai = chunk.getComponent(index, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai == null) {
            ai = new RoutineAIComponent();
            commandBuffer.addComponent(chunk.getReferenceTo(index), SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;

        // --- 1. Evaluation Phase ---
        if (ai.currentTask != TaskType.DYING && ai.currentTask != TaskType.DEAD && ai.currentTask != TaskType.REAPING) {
            if (npc.needs.hunger == 0) {
                ai.currentTask = TaskType.DYING;
                ai.taskStartTime = world.getTick();
                AnimationSlot slotToUse = AnimationSlot.Action;
                try { slotToUse = AnimationSlot.valueOf("Base"); } catch (Exception e) {}
                AnimationUtils.playAnimation(ref, slotToUse, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
                
                Ref<EntityStore> reaperRef = SimNPCFactory.spawnNPC(store, new Vector3d(transform.getPosition().x + 5, transform.getPosition().y, transform.getPosition().z + 5), NPCType.REAPER);
                if (reaperRef != null) {
                    UUID reaperId = store.getComponent(reaperRef, UUIDComponent.getComponentType()).getUuid();
                    ai.reaperEntityId = reaperId;
                    RoutineAIComponent reaperAi = store.getComponent(reaperRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (reaperAi != null) {
                        reaperAi.currentTask = TaskType.REAPING;
                        reaperAi.dyingEntityId = npc.entityId;
                        reaperAi.reapTimer = 300; // 15 seconds
                        store.putComponent(reaperRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, reaperAi);
                    }
                }
            }
        }
        
        if (ai.currentTask == TaskType.IDLE) {
            float sleepThreshold = npc.personality.traits.contains(Trait.LAZY) ? 60f : 30f;
            if (npc.needs.energy < sleepThreshold) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.targetBlockPosition = null;
            } else if (npc.needs.hunger < 30) {
                // Future expansion
            } else if (npc.personality.traits.contains(Trait.FUNNY) && Math.random() < 0.005) {
                AnimationSlot slotToUse = AnimationSlot.Action;
                try { slotToUse = AnimationSlot.valueOf("Base"); } catch (Exception e) {}
                AnimationUtils.playAnimation(ref, slotToUse, "Characters/Animations/Actions/Cheer.blockyanim", "Cheer", store);
                npc.needs.fun = Math.min(100f, npc.needs.fun + 10f);
            }
        }

        // --- 2. Action Execution ---
        if (ai.currentTask == TaskType.FINDING_BED) {
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x;
            int sy = (int) pos.y;
            int sz = (int) pos.z;
            boolean found = false;

            // Scan 10 blocks around
            for (int x = sx - 10; x <= sx + 10 && !found; x++) {
                for (int y = sy - 5; y <= sy + 5 && !found; y++) {
                    for (int z = sz - 10; z <= sz + 10 && !found; z++) {
                        WorldChunk chunkAt = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                        if (chunkAt != null) {
                            BlockType bType = chunkAt.getBlockType(new Vector3i(x, y, z));
                            if (bType != null && bType.getId() != null) {
                                String name = bType.getId().toLowerCase();
                                if (name.contains("bed")) {
                                    ai.targetBlockPosition = new Vector3i(x, y, z);
                                    ai.currentTask = TaskType.MOVING_TO_BED;
                                    found = true;
                                }
                            }
                        }
                    }
                }
            }
            if (!found) {
                // No bed found, idle for a bit so we don't spam scan
                ai.currentTask = TaskType.IDLE;
            }
        }

        if (ai.currentTask == TaskType.MOVING_TO_BED) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Vector3d pos = transform.getPosition();
            double targetX = ai.targetBlockPosition.x + 0.5;
            double targetY = ai.targetBlockPosition.y + 0.5;
            double targetZ = ai.targetBlockPosition.z + 0.5;

            double dx = targetX - pos.x;
            double dy = targetY - pos.y;
            double dz = targetZ - pos.z;
            double distanceSq = dx*dx + dy*dy + dz*dz;

            if (distanceSq < 1.5 * 1.5) {
                // Arrived
                ai.currentTask = TaskType.SLEEPING;
                
                // Play sleep animation
                AnimationSlot slotToUse = AnimationSlot.Action;
                try {
                    slotToUse = AnimationSlot.valueOf("Base");
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
                AnimationUtils.playAnimation(ref, slotToUse, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
            } else {
                // Move towards
                double distance = Math.sqrt(distanceSq);
                double speed = 3.0 * dt; // 3 blocks per second
                if (speed > distance) speed = distance;
                
                double nx = pos.x + (dx / distance) * speed;
                double nz = pos.z + (dz / distance) * speed;
                
                transform.teleportPosition(new Vector3d(nx, pos.y, nz));
                
                // Rotation
                float yaw = (float) Math.atan2(dz, dx);
                transform.getRotation().y = yaw;
                
                commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
            }
        }

        if (ai.currentTask == TaskType.SLEEPING) {
            npc.needs.healEnergy(0.5f); // Heal fast!
            
            if (npc.needs.energy >= 100) {
                npc.needs.energy = 100;
                ai.currentTask = TaskType.IDLE;
                
                // Wake up (play Idle)
                AnimationSlot slotToUse = AnimationSlot.Action;
                try {
                    slotToUse = AnimationSlot.valueOf("Base");
                } catch (IllegalArgumentException e) {
                }
                AnimationUtils.playAnimation(ref, slotToUse, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }
        if (ai.currentTask == TaskType.REAPING && ai.dyingEntityId != null) {
            Ref<EntityStore> dyingRef = world.getEntityStore().getRefFromUUID(ai.dyingEntityId);
            if (dyingRef == null) {
                transform.setPosition(new Vector3d(0, -1000, 0));
                commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
                return;
            }
            TransformComponent dyingTransform = store.getComponent(dyingRef, TransformComponent.getComponentType());
            if (dyingTransform == null) return;
            
            double dx = dyingTransform.getPosition().x - transform.getPosition().x;
            double dz = dyingTransform.getPosition().z - transform.getPosition().z;
            double distSq = dx*dx + dz*dz;
            
            if (distSq > 2.0 * 2.0) {
                double dist = Math.sqrt(distSq);
                double speed = 2.0 * dt; 
                if (speed > dist) speed = dist;
                double nx = transform.getPosition().x + (dx/dist)*speed;
                double nz = transform.getPosition().z + (dz/dist)*speed;
                transform.teleportPosition(new Vector3d(nx, transform.getPosition().y, nz));
                transform.getRotation().y = (float) Math.atan2(dz, dx);
                commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
            } else {
                ai.reapTimer--;
                if (ai.reapTimer <= 0) {
                    SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    String deceasedName = dyingNpc != null ? dyingNpc.name : "Alguém";
                    
                    for (PlayerRef p : Universe.get().getPlayers()) {
                        p.sendMessage(Message.raw("§cO tempo de " + deceasedName + " acabou. A Dona Morte levou sua alma."));
                    }
                    
                    dyingTransform.setPosition(new Vector3d(0, -1000, 0));
                    commandBuffer.replaceComponent(dyingRef, TransformComponent.getComponentType(), dyingTransform);
                    
                    transform.setPosition(new Vector3d(0, -1000, 0));
                    commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
                }
            }
        }
        commandBuffer.replaceComponent(ref, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
    }
}
