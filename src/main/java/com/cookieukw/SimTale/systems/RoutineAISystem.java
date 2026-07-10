package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
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

import java.util.*;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import javax.annotation.Nonnull;

public class RoutineAISystem extends EntityTickingSystem<EntityStore> {

    private static final int FOOD_SEARCH_COOLDOWN_TICKS = 40;
    private static final int BATH_SEARCH_COOLDOWN_TICKS = 40;
    private static final int BED_SEARCH_RETRY_COOLDOWN_TICKS = 60;
    private static final double LEASH_UPDATE_THRESHOLD_SQ = 0.25; 
    private static final int LEASH_FORCE_UPDATE_TICKS = 20; 
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutineAISystem.class);
    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null || npc.needs == null) return;

        // Skip routine AI for babies and toddlers (cared for by parents)
        for (GrowthComponent gc : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(gc.childId)) {
                if (gc.stage == GrowthStage.BABY || gc.stage == com.cookieukw.SimTale.core.lifecycle.GrowthStage.TODDLER) {
                    return; 
                }
                // If they are CHILD or TEEN, inherit parent's bed
                if (npc.bedLocation == null) {
                    SimNPCComponent mother = LifecycleUtils.findNPCById(gc.motherId);
                    if (mother != null && mother.bedLocation != null) {
                        npc.bedLocation = mother.bedLocation;
                    } else {
                        SimNPCComponent father = LifecycleUtils.findNPCById(gc.fatherId);
                        if (father != null && father.bedLocation != null) {
                            npc.bedLocation = father.bedLocation;
                        }
                    }
                }
                break;
            }
        }

        RoutineAIComponent ai = chunk.getComponent(index, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai == null) {
            ai = new RoutineAIComponent();
            commandBuffer.addComponent(chunk.getReferenceTo(index), SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        World world = Universe.get().getWorlds().values().stream().findFirst().orElse(null);
        if (world == null) return;

        // Ensure Frozen component is cleared if task changes externally and dialogue is inactive
        boolean hasFrozen = store.getComponent(ref, Frozen.getComponentType()) != null;
        if (ai.currentTask != TaskType.SLEEPING && hasFrozen && npc.currentConversationPartner == null) {
            commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
        }

        // --- 1. Evaluation Phase ---
        if (ai.currentTask != TaskType.DYING && ai.currentTask != TaskType.DEAD && ai.currentTask != TaskType.REAPING) {
            if (npc.needs.hunger <= 0) {
                ai.currentTask = TaskType.DYING;
                ai.taskStartTime = world.getTick();
                playAnim(ref, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);

                Universe.get().getPlayers().forEach(p ->
                        p.sendMessage(Message.translation("general.npc.dying").param("name", npc.name))
                );
            }
        }

        if (ai.currentTask == TaskType.DYING) {
            if (world.getTick() - ai.taskStartTime > 200) {
                ai.currentTask = TaskType.DEAD;
                ai.taskStartTime = world.getTick();
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other.entityRef == null) continue;
                    RoutineAIComponent otherAi = store.getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (otherAi != null && otherAi.currentTask == TaskType.IDLE && other.name.contains("Reaper")) {
                        otherAi.currentTask = TaskType.REAPING;
                        otherAi.dyingEntityId = npc.entityId;
                        otherAi.reapTimer = 100;
                        break;
                    }
                }
            }
            return;
        }

        if (ai.currentTask == TaskType.DEAD) return;

        if (ai.currentTask == TaskType.IDLE) {
            if (npc.bedLocation == null && world.getTick() % 60 == 0) {
                BedPos bestBed = getBedPos(transform);
                if (bestBed != null) {
                    npc.bedLocation = bestBed;
                    npc.family.homeX = bestBed.x;
                    npc.family.homeY = bestBed.y;
                    npc.family.homeZ = bestBed.z;
                    npc.family.hasSharedHome = true;
                    SimNPCPersistence.saveNPC(npc);
                }
            }

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
                ai.taskStartTime = world.getTick() - FOOD_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.hygiene < 40) {
                ai.currentTask = TaskType.FINDING_BATH;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - BATH_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.social < 50 && Math.random() < 0.05) {
                SimNPCComponent bestTarget = null;
                double bestDist = 400.0;
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other != npc && other.entityRef != null) {
                        TransformComponent ot = store.getComponent(other.entityRef, TransformComponent.getComponentType());
                        if (ot != null) {
                            double d2 = transform.getPosition().distanceSquared(ot.getPosition());
                            if (d2 < bestDist) { bestDist = d2; bestTarget = other; }
                        }
                    }
                }
                if (bestTarget != null) {
                    ai.currentTask = TaskType.MOVING_TO_SOCIALIZE;
                    playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                    ai.socializeTargetId = bestTarget.entityId;
                }
            } else if (ai.currentTask == TaskType.IDLE && Math.random() < 0.02) {
                ai.currentTask = TaskType.WANDERING;
                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                double centerX = transform.getPosition().x;
                double centerZ = transform.getPosition().z;
                if (npc.bedLocation != null) {
                    centerX = npc.bedLocation.x;
                    centerZ = npc.bedLocation.z;
                }
                ai.targetBlockPosition = new Vector3i(
                        (int)(centerX + (Math.random() - 0.5) * 16),
                        (int)transform.getPosition().y,
                        (int)(centerZ + (Math.random() - 0.5) * 16)
                );
            }
        }

        // --- FINDING_BED ---
        if (ai.currentTask == TaskType.FINDING_BED) {
            if (npc.bedLocation != null) {
                ai.targetBlockPosition = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.currentTask = TaskType.MOVING_TO_BED;
                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else if (world.getTick() - ai.taskStartTime >= BED_SEARCH_RETRY_COOLDOWN_TICKS) {
                ai.taskStartTime = world.getTick();
                BedPos bestBed = getBedPos(transform);

                if (bestBed != null) {
                    npc.bedLocation = bestBed;
                    npc.family.homeX = bestBed.x;
                    npc.family.homeY = bestBed.y;
                    npc.family.homeZ = bestBed.z;
                    npc.family.hasSharedHome = true;
                    ai.targetBlockPosition = new Vector3i(bestBed.x, bestBed.y, bestBed.z);
                    ai.currentTask = TaskType.MOVING_TO_BED;
                    playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                } else {
                    ai.currentTask = TaskType.IDLE;
                }
            }
        }

        if (ai.currentTask == TaskType.MOVING_TO_BED) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 1.5 * 1.5) {
                clearMoveTarget(ref, ai);
                
                // If we arrived and the bed is not in BEDS, it was destroyed (since chunk is loaded)
                if (npc.bedLocation != null && !BedRegistry.BEDS.contains(npc.bedLocation)) {
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    ai.currentTask = TaskType.IDLE;
                    return;
                }
                
                // Snap position exactly to bed surface with a small Y offset
                Vector3d snapPos = new Vector3d(ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.y + 0.35, ai.targetBlockPosition.z + 0.5);
                transform.teleportPosition(snapPos);
                
                // Align rotation to bed's yaw
                float bedYaw = npc.bedLocation != null ? npc.bedLocation.yaw : 0f;
                transform.teleportRotation(new Rotation3f(0f, bedYaw, 0f));
                commandBuffer.putComponent(ref, TransformComponent.getComponentType(), transform);

                ai.currentTask = TaskType.SLEEPING;
                playAnim(ref, "Characters/Animations/Actions/Sleep.blockyanim", "Sleep", store);
                commandBuffer.ensureComponent(ref, Frozen.getComponentType());
            } else {
                moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        if (ai.currentTask == TaskType.SLEEPING) {
            npc.needs.healEnergy(0.5f);
            if (npc.needs.energy >= 100) {
                npc.needs.energy = 100;
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
                commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
            }
        }

        // --- FINDING_FOOD (OPTIMIZATION) ---
        if (ai.currentTask == TaskType.FINDING_FOOD && world.getTick() - ai.taskStartTime >= FOOD_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x; int sy = (int) pos.y; int sz = (int) pos.z;
            boolean found = false;

            foodSearch:
            for (int cx = (sx - 10) >> 4; cx <= (sx + 10) >> 4; cx++) {
                for (int cz = (sz - 10) >> 4; cz <= (sz + 10) >> 4; cz++) {
                    WorldChunk chunkAt = world.getChunk(ChunkUtil.indexChunk(cx, cz));
                    if (chunkAt == null) continue;

                    int minX = Math.max(sx - 10, cx << 4);
                    int maxX = Math.min(sx + 10, (cx << 4) + 15);
                    int minZ = Math.max(sz - 10, cz << 4);
                    int maxZ = Math.min(sz + 10, (cz << 4) + 15);

                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int y = sy - 2; y <= sy + 2; y++) {
                                BlockType bType = chunkAt.getBlockType(new Vector3i(x, y, z));
                                if (bType != null && bType.getId() != null) {
                                    String name = bType.getId().toLowerCase();
                                    if (name.contains("barrel") || name.contains("chest") || name.contains("food") || name.contains("cupboard")) {
                                        ai.targetBlockPosition = new Vector3i(x, y, z);
                                        ai.currentTask = TaskType.MOVING_TO_FOOD;
                                        playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                                        found = true; break foodSearch;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!found) ai.currentTask = TaskType.IDLE;
        }

        if (ai.currentTask == TaskType.MOVING_TO_FOOD) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 2.0 * 2.0) {
                clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.EATING;
                ai.taskStartTime = world.getTick();
            } else {
                moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        if (ai.currentTask == TaskType.EATING) {
            if (world.getTick() - ai.taskStartTime == 1) playAnim(ref, "Characters/Animations/Actions/Eat.blockyanim", "Eat", store);
            if (world.getTick() - ai.taskStartTime > 60) {
                npc.needs.hunger = Math.min(100f, npc.needs.hunger + 40f);
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

        // --- FINDING_BATH (OPTIMIZATION) ---
        if (ai.currentTask == TaskType.FINDING_BATH && world.getTick() - ai.taskStartTime >= BATH_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x; int sy = (int) pos.y; int sz = (int) pos.z;
            boolean found = false;

            bathSearch:
            for (int cx = (sx - 15) >> 4; cx <= (sx + 15) >> 4; cx++) {
                for (int cz = (sz - 15) >> 4; cz <= (sz + 15) >> 4; cz++) {
                    WorldChunk chunkAt = world.getChunk(ChunkUtil.indexChunk(cx, cz));
                    if (chunkAt == null) continue;

                    int minX = Math.max(sx - 15, cx << 4);
                    int maxX = Math.min(sx + 15, (cx << 4) + 15);
                    int minZ = Math.max(sz - 15, cz << 4);
                    int maxZ = Math.min(sz + 15, (cz << 4) + 15);

                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int y = sy - 5; y <= sy + 5; y++) {
                                BlockType bType = chunkAt.getBlockType(new Vector3i(x, y, z));
                                if (bType != null && bType.getId() != null && bType.getId().toLowerCase().contains("water")) {
                                    ai.targetBlockPosition = new Vector3i(x, y, z);
                                    ai.currentTask = TaskType.MOVING_TO_BATH;
                                    playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                                    found = true; break bathSearch;
                                }
                            }
                        }
                    }
                }
            }
            if (!found) ai.currentTask = TaskType.IDLE;
        }

        if (ai.currentTask == TaskType.MOVING_TO_BATH) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE; 
                return;
            }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 1.5 * 1.5) {
                clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.BATHING;
                ai.taskStartTime = world.getTick();
                playAnim(ref, "Characters/Animations/Actions/Swim.blockyanim", "Swim", store);
            } else {
                moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        if (ai.currentTask == TaskType.BATHING) {
            npc.needs.hygiene = Math.min(100f, npc.needs.hygiene + 1.0f);
            if (npc.needs.hygiene >= 100f) {
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

        // --- REAPING ---
        if (ai.currentTask == TaskType.REAPING && ai.dyingEntityId != null) {
            Ref<EntityStore> dyingRef = world.getEntityStore().getRefFromUUID(ai.dyingEntityId);
            TransformComponent dyingTransform = (dyingRef != null) ? store.getComponent(dyingRef, TransformComponent.getComponentType()) : null;
            if (dyingTransform == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }

            double dx = dyingTransform.getPosition().x - transform.getPosition().x;
            double dz = dyingTransform.getPosition().z - transform.getPosition().z;
            double d2 = dx*dx + dz*dz;

            if (d2 > 2.0 * 2.0) {
                moveTo(ref, ai, world, new Vector3d(dyingTransform.getPosition().x, dyingTransform.getPosition().y, dyingTransform.getPosition().z));
            } else {
                clearMoveTarget(ref, ai);
                ai.reapTimer--;
                if (ai.reapTimer <= 0) {
                    SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    String deceasedName = dyingNpc != null ? dyingNpc.name : "Someone";
                    Universe.get().getPlayers().forEach(p -> {
                        p.sendMessage(Message.translation("general.reaper.soul_taken").param("name", deceasedName));
                        try {
                            CommandManager.get().handleCommand(p, "give " + p.getUsername() + " Rock_Stone_Cobble --quantity=1");
                        } catch (Exception e) {
                            LOGGER.error("Error giving soul to player", e);
                        }
                    });
                    if (dyingNpc != null && dyingNpc.entityId != null) {
                        PlumbobSystem.removePlumbob(dyingNpc.entityId);
                    }
                    commandBuffer.removeEntity(dyingRef, RemoveReason.REMOVE);
                    ai.currentTask = TaskType.IDLE;
                }
            }
        }

        if (ai.currentTask == TaskType.MOVING_TO_CONSTRUCTION) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 3.0 * 3.0) {
                clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.BUILDING;
                ai.taskStartTime = world.getTick();
                playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
            } else {
                moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        if (ai.currentTask == TaskType.BUILDING) {
            ConstructionSiteComponent activeSite = null;
            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                if (site.isBuilding && site.anchor.equals(ai.targetBlockPosition)) {
                    activeSite = site;
                    break;
                }
            }
            if (activeSite == null) {
                ai.currentTask = TaskType.IDLE;
                playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            } else {
                if ((world.getTick() - ai.taskStartTime) % 40 == 0) {
                    playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
                }
                npc.needs.energy = Math.max(0f, npc.needs.energy - 0.05f);
                if (npc.needs.energy <= 10f) {
                    ai.currentTask = TaskType.IDLE;
                    playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
                }
            }
        }

        commandBuffer.replaceComponent(ref, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
    }

    @NullableDecl
    private static BedPos getBedPos(TransformComponent transform) {
        BedPos bestBed = null;
        double closestDistSq = Double.MAX_VALUE;
        Vector3d myPos = transform.getPosition();

        Set<String> claimedBedKeys = new HashSet<>();
        for (SimNPCComponent otherNpc : SimTale.ACTIVE_NPCS) {
            if (otherNpc.bedLocation != null) {
                BedPos ob = otherNpc.bedLocation;
                claimedBedKeys.add(ob.x + "," + ob.y + "," + ob.z);
            }
        }

        synchronized (BedRegistry.BEDS) {
            for (BedPos bp : BedRegistry.BEDS) {
                double dx = bp.x - myPos.x;
                double dy = bp.y - myPos.y;
                double dz = bp.z - myPos.z;
                double d2 = dx*dx + dy*dy + dz*dz;
                if (d2 < 96.0 * 96.0) { // Limit search radius to 96 blocks
                    String key = bp.x + "," + bp.y + "," + bp.z;
                    if (!claimedBedKeys.contains(key) && d2 < closestDistSq) {
                        closestDistSq = d2;
                        bestBed = bp;
                    }
                }
            }
        }
        return bestBed;
    }


    private void moveTo(Ref<EntityStore> ref, RoutineAIComponent ai, World world, Vector3d targetPos) {
        boolean needsUpdate;

        if (ai.lastLeashPos == null) {
            needsUpdate = true;
        } else {
            double d2 = ai.lastLeashPos.distanceSquared(targetPos);
            needsUpdate = d2 > LEASH_UPDATE_THRESHOLD_SQ
                    || (world.getTick() - ai.lastLeashTick) >= LEASH_FORCE_UPDATE_TICKS;
        }

        if (needsUpdate) {
            NPCEntity npcEntity = ref.getStore().getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null) {
                npcEntity.setLeashPoint(new Vector3d(targetPos.x, targetPos.y, targetPos.z));
                if (npcEntity.getRole() != null) {
                    npcEntity.getRole().getStateSupport().setState(ref, "Moving", null, ref.getStore());
                }
            }
            ai.lastLeashPos = new Vector3d(targetPos);
            ai.lastLeashTick = world.getTick();
        }
    }

    private void clearMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai) {
        ai.lastLeashPos = null;
        ai.lastLeashTick = 0;

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            TransformComponent transform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null) {
                npcEntity.setLeashPoint(new Vector3d(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z));
            }
            if (npcEntity.getRole() != null) {
                npcEntity.getRole().getStateSupport().setState(npcRef, "Idle", null, npcRef.getStore());
            }
        }
    }

    void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        AnimationUtils.playAnimation(ref, AnimationSlot.Action, anim, name, store);
    }
}