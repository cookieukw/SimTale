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
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import com.cookieukw.SimTale.db.SimBedData.BedPos;


import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Rotation3f;

import com.cookieukw.SimTale.core.SimNPCFactory.NPCType;

import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import java.util.UUID;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Math;

import javax.annotation.Nonnull;

public class RoutineAISystem extends EntityTickingSystem<EntityStore> {

    private static final int FOOD_SEARCH_COOLDOWN_TICKS = 60;
    private static final int BATH_SEARCH_COOLDOWN_TICKS = 60; 
    
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
                playAnim(ref, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
            }
        }
        
        if (ai.currentTask == TaskType.IDLE) {
            float sleepThreshold = npc.personality.traits.contains(Trait.LAZY) ? 60f : 30f;
            if (npc.needs.energy < sleepThreshold) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.targetBlockPosition = null;
            } else if (npc.profession == Profession.BUILDER || npc.profession == Profession.UNEMPLOYED) {
                for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                    if (site.isBuilding) {
                        ai.currentTask = TaskType.MOVING_TO_CONSTRUCTION;
                        playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);

                        ai.targetBlockPosition = new Vector3i(site.anchor);
                        break;
                    }
                }
            }
if (ai.currentTask == TaskType.IDLE && npc.needs.hunger < 50) {
                ai.currentTask = TaskType.FINDING_FOOD;
                ai.targetBlockPosition = null;
                // Garante que o primeiro scan rode já no próximo tick, sem esperar o cooldown
                ai.taskStartTime = world.getTick() - FOOD_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.hygiene < 40) {
                ai.currentTask = TaskType.FINDING_BATH;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - BATH_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.social < 50 && java.lang.Math.random() < 0.05) {
                SimNPCComponent bestTarget = null;
                double bestDist = 400.0;
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other != npc && other.entityRef != null) {
                        TransformComponent otherTransform = store.getComponent(other.entityRef, TransformComponent.getComponentType());
                        if (otherTransform != null) {
                            double distSq = transform.getPosition().distanceSquared(otherTransform.getPosition());
                            if (distSq < bestDist) {
                                bestDist = distSq;
                                bestTarget = other;
                            }
                        }
                    }
                }
                if (bestTarget != null) {
                    ai.currentTask = TaskType.MOVING_TO_SOCIALIZE;
                    playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);

                    ai.socializeTargetId = bestTarget.entityId;
                }
            } else if (ai.currentTask == TaskType.IDLE && java.lang.Math.random() < 0.02) {
                ai.currentTask = TaskType.WANDERING;
                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);

                ai.targetBlockPosition = new Vector3i(
                    (int)(transform.getPosition().x + (java.lang.Math.random() - 0.5) * 20),
                    (int)transform.getPosition().y,
                    (int)(transform.getPosition().z + (java.lang.Math.random() - 0.5) * 20)
                );
            } else if (npc.personality.traits.contains(Trait.FUNNY) && java.lang.Math.random() < 0.005) {
                playAnim(ref, "Characters/Animations/Actions/Cheer.blockyanim", "Cheer", store);
                npc.needs.fun = java.lang.Math.min(100f, npc.needs.fun + 10f);
            }
        }

        // --- 2. Action Execution ---
        if (ai.currentTask == TaskType.FINDING_BED) {
            System.out.println("[DEBUG SIMTALE] FINDING_BED task started for NPC");
            if (npc.bedLocation != null) {
                // NPC already owns a bed
                System.out.println("[DEBUG SIMTALE] NPC already owns a bed at: " + npc.bedLocation.x + ", " + npc.bedLocation.y + ", " + npc.bedLocation.z);
                ai.targetBlockPosition = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.currentTask = TaskType.MOVING_TO_BED;
                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else {
                // Procurar cama (Prefab/Entidade) nas proximidades
                BedPos bestBed = null;
                double closestDistSq = Double.MAX_VALUE;
                org.joml.Vector3d myPos = transform.getPosition();

                List<BedPos> claimedBeds = new ArrayList<>();
                for (SimNPCComponent otherNpc : SimTale.ACTIVE_NPCS) {
                    if (otherNpc.bedLocation != null) {
                        claimedBeds.add(otherNpc.bedLocation);
                    }
                }

                if (world != null) {
                    int sx = (int) myPos.x;
                    int sz = (int) myPos.z;
                    Set<Long> visitedChunks = new HashSet<>();

                    for (int x = sx - 16; x <= sx + 16; x += 16) {
                        for (int z = sz - 16; z <= sz + 16; z += 16) {
                            long chunkIdx = ChunkUtil.indexChunkFromBlock(x, z);
                            if (visitedChunks.contains(chunkIdx)) continue;
                            visitedChunks.add(chunkIdx);
                            
                            WorldChunk chunkAt = world.getChunk(chunkIdx);
                            if (chunkAt != null && chunkAt.getEntityChunk() != null) {
                                for (Ref<EntityStore> er : chunkAt.getEntityChunk().getEntityReferences()) {
                                    PersistentModel pm = store.getComponent(er, PersistentModel.getComponentType());
                                    if (pm != null && pm.getModelReference() != null && pm.getModelReference().getModelAssetId() != null) {
                                        String mName = pm.getModelReference().getModelAssetId().toLowerCase();
                                        if (mName.contains("bed") || mName.contains("cama") || mName.contains("furniture_village_bed")) {
                                            TransformComponent tc = store.getComponent(er, TransformComponent.getComponentType());
                                            if (tc != null) {
                                                org.joml.Vector3d bedPos = tc.getPosition();
                                                double dx = bedPos.x - myPos.x;
                                                double dy = bedPos.y - myPos.y;
                                                double dz = bedPos.z - myPos.z;
                                                double distSq = dx*dx + dy*dy + dz*dz;
                                                
                                                if (distSq < 16.0 * 16.0) { // Raio de 16 blocos
                                                    BedPos bp = new BedPos((int)Math.floor(bedPos.x), (int)Math.floor(bedPos.y), (int)Math.floor(bedPos.z));
                                                    
                                                    // Checar se está ocupada
                                                    boolean isClaimed = false;
                                                    for (BedPos cb : claimedBeds) {
                                                        if (cb.x == bp.x && cb.y == bp.y && cb.z == bp.z) {
                                                            isClaimed = true;
                                                            break;
                                                        }
                                                    }
                                                    
                                                    if (!isClaimed && distSq < closestDistSq) {
                                                        closestDistSq = distSq;
                                                        bestBed = bp;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (bestBed != null) {
                    System.out.println("[DEBUG SIMTALE] Bed found at: " + bestBed.x + ", " + bestBed.y + ", " + bestBed.z);
                    npc.bedLocation = bestBed;
                    ai.targetBlockPosition = new Vector3i(bestBed.x, bestBed.y, bestBed.z);
                    ai.currentTask = TaskType.MOVING_TO_BED;
                    playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                } else {
                    System.out.println("[DEBUG SIMTALE] No bed found nearby in Entities");
                    ai.currentTask = TaskType.IDLE;
                }
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
                clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.SLEEPING;
                
                // Play sleep animation
                playAnim(ref, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
                AnimationUtils.playAnimation(ref, slotToUse, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
            } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(targetX, pos.y, targetZ), commandBuffer);
            }
        }

        if (ai.currentTask == TaskType.SLEEPING) {
            npc.needs.healEnergy(0.5f); // Heal fast!
            
            if (npc.needs.energy >= 100) {
                npc.needs.energy = 100;
                ai.currentTask = TaskType.IDLE;
                
                // Wake up (play Idle)
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

        if (ai.currentTask == TaskType.MOVING_TO_CONSTRUCTION) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Vector3d pos = transform.getPosition();
            double dx = ai.targetBlockPosition.x - pos.x;
            double dz = ai.targetBlockPosition.z - pos.z;
            double distanceSq = dx*dx + dz*dz;

            if (distanceSq < 15.0 * 15.0) {
                clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.BUILDING;
                playAnim(ref, "Characters/Animations/Actions/Mining.blockyanim", "Mining", store);
            } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5), commandBuffer);
            }
        }

        if (ai.currentTask == TaskType.BUILDING) {
            boolean siteActive = false;
            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                if (site.isBuilding && site.anchor.distance(ai.targetBlockPosition) < 2) {
                    siteActive = true;
                    break;
                }
            }

            if (!siteActive) {
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            } else {
                if (Math.random() < 0.05) {
                    playAnim(ref, "Characters/Animations/Actions/Mining.blockyanim", "Mining", store);
                }
            }
        }

        if (ai.currentTask == TaskType.WANDERING) {
            Vector3d pos = transform.getPosition();
            double targetX = ai.targetBlockPosition.x + 0.5;
            double targetZ = ai.targetBlockPosition.z + 0.5;
            double dx = targetX - pos.x;
            double dz = targetZ - pos.z;
            double distanceSq = dx*dx + dz*dz;

            if (distanceSq < 1.0) {
                clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(targetX, pos.y, targetZ), commandBuffer);
            }
        }
        
        if (ai.currentTask == TaskType.MOVING_TO_SOCIALIZE) {
            if (ai.socializeTargetId == null) {
                ai.currentTask = TaskType.IDLE;
            } else {
                Ref<EntityStore> otherRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
                if (otherRef == null) {
                    ai.currentTask = TaskType.IDLE;
                } else {
                    TransformComponent otherTransform = store.getComponent(otherRef, TransformComponent.getComponentType());
                    if (otherTransform != null) {
                        Vector3d pos = transform.getPosition();
                        double dx = otherTransform.getPosition().x - pos.x;
                        double dz = otherTransform.getPosition().z - pos.z;
                        double distanceSq = dx*dx + dz*dz;
                        
                        if (distanceSq < 4.0) {
                            clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.SOCIALIZING;
                            ai.taskStartTime = world.getTick();
                            playAnim(ref, "Characters/Animations/Actions/Talk.blockyanim", "Talk", store);
                        } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(otherTransform.getPosition().x, pos.y, otherTransform.getPosition().z), commandBuffer);
            }
                    } else { ai.currentTask = TaskType.IDLE; }
                }
            }
        }
        
        if (ai.currentTask == TaskType.SOCIALIZING) {
            if (world.getTick() - ai.taskStartTime > 100) {
                npc.needs.social = java.lang.Math.min(100f, npc.needs.social + 30f);
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }
        
      
        if (ai.currentTask == TaskType.FINDING_FOOD
                && world.getTick() - ai.taskStartTime >= FOOD_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick(); 
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x;
            int sy = (int) pos.y;
            int sz = (int) pos.z;
            boolean found = false;

            foodSearch:
            for (int x = sx - 10; x <= sx + 10; x++) {

        // --- MOVING_TO_FOOD ---
        if (ai.currentTask == TaskType.MOVING_TO_FOOD) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Vector3d pos = transform.getPosition();
            double targetX = ai.targetBlockPosition.x + 0.5;
            double targetZ = ai.targetBlockPosition.z + 0.5;
            double dx = targetX - pos.x;
            double dz = targetZ - pos.z;
            double distanceSq = dx*dx + dz*dz;

            if (distanceSq < 2.0 * 2.0) {
                clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.EATING;
                ai.taskStartTime = world.getTick();
            } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(targetX, pos.y, targetZ), commandBuffer);
            }
        }

        // --- EATING ---
        if (ai.currentTask == TaskType.EATING) {
            if (world.getTick() - ai.taskStartTime == 0) {
                playAnim(ref, "Characters/Animations/Actions/Eat.blockyanim", "Eat", store);
            }
            if (world.getTick() - ai.taskStartTime > 60) {
                npc.needs.hunger = java.lang.Math.min(100f, npc.needs.hunger + 40f);
                ai.currentTask = TaskType.IDLE;
                ai.forcedByDebug = false;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

      // --- FINDING_BATH: Scan for water blocks ---
            if (ai.currentTask == TaskType.FINDING_BATH
                && world.getTick() - ai.taskStartTime >= BATH_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick(); 

            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x;
            int sy = (int) pos.y;
            int sz = (int) pos.z;
            boolean found = false;

            bathSearch:
            for (int x = sx - 15; x <= sx + 15; x++) {
                for (int z = sz - 15; z <= sz + 15; z++) {
                    WorldChunk chunkAt = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunkAt == null) continue;

                    for (int y = sy - 5; y <= sy + 5; y++) {
                        BlockType bType = chunkAt.getBlockType(new Vector3i(x, y, z));
                        if (bType != null && bType.getId() != null) {
                            String name = bType.getId().toLowerCase();
                            if (name.contains("water")) {
                                ai.targetBlockPosition = new Vector3i(x, y, z);
                                ai.currentTask = TaskType.MOVING_TO_BATH;
                                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                                found = true;
                                break bathSearch;
                            }
                        }
                    }
                }
            }
            if (!found) {
                ai.currentTask = TaskType.IDLE;
            }
        }

        // --- MOVING_TO_BATH ---
        if (ai.currentTask == TaskType.MOVING_TO_BATH) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Vector3d pos = transform.getPosition();
            double targetX = ai.targetBlockPosition.x + 0.5;
            double targetZ = ai.targetBlockPosition.z + 0.5;
            double dx = targetX - pos.x;
            double dz = targetZ - pos.z;
            double distanceSq = dx*dx + dz*dz;

            if (distanceSq < 1.5 * 1.5) {
                clearMoveTarget(ai, world, commandBuffer);
                ai.currentTask = TaskType.BATHING;
                ai.taskStartTime = world.getTick();
                playAnim(ref, "Characters/Animations/Actions/Swim.blockyanim", "Swim", store);
            } else {
                ensureMoveTarget(ref, ai, world, new Vector3d(targetX, pos.y, targetZ), commandBuffer);
            }
        }

        // --- BATHING ---
        if (ai.currentTask == TaskType.BATHING) {
            npc.needs.hygiene = java.lang.Math.min(100f, npc.needs.hygiene + 1.0f);
            if (npc.needs.hygiene >= 100f) {
                ai.currentTask = TaskType.IDLE;
                ai.forcedByDebug = false;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
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
                transform.setPosition(new Vector3d(nx, transform.getPosition().y, nz));
                transform.getRotation().y = (float) Math.atan2(-dx, -dz);
                commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
            } else {
                ai.reapTimer--;
                if (ai.reapTimer <= 0) {
                    SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    String deceasedName = dyingNpc != null ? dyingNpc.name : "Alguém";
                    
                    for (PlayerRef p : Universe.get().getPlayers()) {
                        p.sendMessage(Message.translation("simtale.reaper.soul_taken").param("name", deceasedName));
                        try {
                            CommandManager.get().handleCommand(p, "give " + p.getUsername() + " Rock_Stone_Cobble --quantity=1");
                            p.sendMessage(Message.translation("simtale.reaper.tombstone_given").param("name", deceasedName));
                        } catch (Exception cmdEx) {
                            // ignore command failure
                        }
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

    private void ensureMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai, World world, Vector3d position, CommandBuffer<EntityStore> commandBuffer) {
        try {
            if (ai.currentMoveTarget != null && ai.currentMoveTarget.isValid()) {
                TransformComponent targetTransform = ai.currentMoveTarget.getStore().getComponent(ai.currentMoveTarget, TransformComponent.getComponentType());
                if (targetTransform != null) {
                    targetTransform.setPosition(new Vector3d(position.x, position.y, position.z));
                    NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
                    if (npcEntity != null && npcEntity.getRole() != null) {
                        Role role = npcEntity.getRole();
                        String slot = role.getStateSupport() != null ? "MoveTarget" : "LockedTarget";
                        role.getMarkedEntitySupport().setMarkedEntity(slot, ai.currentMoveTarget);
                        npcEntity.setLeashPoint(new Vector3d(position.x, position.y, position.z));
                    }
                    return;
                }
            }
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            ProjectileComponent projectile = new ProjectileComponent("Projectile");
            holder.putComponent(ProjectileComponent.getComponentType(), projectile);
            holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(position.x, position.y, position.z), new Rotation3f()));
            holder.ensureComponent(UUIDComponent.getComponentType());
            holder.ensureComponent(Intangible.getComponentType());
            holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore) world.getEntityStore().getStore().getExternalData()).takeNextNetworkId()));
            projectile.initialize();
            
            Ref<EntityStore> targetRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
            if (targetRef == null || !targetRef.isValid()) return;
            ai.currentMoveTarget = targetRef;
            
            NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity != null && npcEntity.getRole() != null) {
                Role role = npcEntity.getRole();
                String slot = role.getStateSupport() != null ? "MoveTarget" : "LockedTarget";
                role.getMarkedEntitySupport().setMarkedEntity(slot, targetRef);
                npcEntity.setLeashPoint(new Vector3d(position.x, position.y, position.z));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearMoveTarget(RoutineAIComponent ai, World world, CommandBuffer<EntityStore> commandBuffer) {
        if (ai.currentMoveTarget != null) {
            try {
                if (ai.currentMoveTarget.isValid()) {
                    commandBuffer.removeEntity(ai.currentMoveTarget, RemoveReason.REMOVE);
                }
            } catch (Exception e) {}
            ai.currentMoveTarget = null;
        }
    }

    private void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        AnimationUtils.playAnimation(ref, AnimationSlot.Base, anim, name, store);
    }
}
