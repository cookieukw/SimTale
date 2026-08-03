package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.cookieukw.SimTale.db.SimNPCData;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
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
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import com.hypixel.hytale.server.npc.role.support.StateSupport;
import javax.annotation.Nonnull;

public class RoutineAISystem extends EntityTickingSystem<EntityStore> {

    private static final int BATH_SEARCH_COOLDOWN_TICKS = 40;
    /**
     * Deadlines for the "walk somewhere" states. Without them an unreachable target (walled
     * off, across a ravine, chunk unloaded) parked the NPC in that state indefinitely — only
     * the low-energy interrupt could ever drag it out.
     */
    private static final int MOVE_TIMEOUT_TICKS = 600;
    /** Bathing restores 1 hygiene per tick, so 100 ticks is already the worst case. */
    private static final int BATH_DURATION_LIMIT_TICKS = 200;
    /** Horizontal/vertical half-extent of the water scan. 15x15x5 ≈ 10.500 blocos por varredura. */
    private static final int BATH_SEARCH_RADIUS = 15;
    private static final int BATH_SEARCH_HEIGHT = 5;
    private static final int BED_SEARCH_RETRY_COOLDOWN_TICKS = 60;
    /** Hunger low enough to drop whatever the NPC is doing. Well under the idle-time threshold of 50. */
    private static final float HUNGER_INTERRUPT_THRESHOLD = 25f;
    private static final int SLEEP_DURATION_TICKS = 20 * 120;
    private static final int WAKE_ANIM_TICKS = 20;
    private static final double BED_REACH_DISTANCE_SQ = 2.5 * 2.5; // Increased to prevent getting stuck on bed collision
    /** Look for a chat partner within 20 blocks. */
    private static final double SOCIALIZE_SEARCH_RANGE_SQ = 20.0 * 20.0;
    /** Max distance from home an idle stroll may take the NPC. */
    private static final double WANDER_RADIUS = 8.0;
    private static final SimLog LOGGER = SimLog.forClass(RoutineAISystem.class);
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

        // Skip routine AI for babies and toddlers (cared for by parents).
        // The isEmpty() guard matters: without any children in the world this loop still ran
        // once per NPC per tick for nothing.
        if (npc.entityId != null && !LifecycleManager.ACTIVE_CHILDREN.isEmpty()) {
            for (GrowthComponent gc : LifecycleManager.ACTIVE_CHILDREN) {
                if (!npc.entityId.equals(gc.childId)) continue;

                if (gc.stage == GrowthStage.BABY || gc.stage == GrowthStage.TODDLER) {
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

        // Was `getWorlds().values().stream().findFirst()`, which allocated a stream per NPC
        // per tick.
        World world = WorldUtil.first();
        if (world == null) return;

        NPCDoorHelper.handleNpcDoors(world, npc, transform);

        // --- Dialogue lock ---
        // While an interaction page is open the mod's AI stands down entirely and the NPC is
        // pinned facing the player, re-applied every tick.
        //
        // A single teleportRotation when the page opens is not enough: the Hytale role keeps
        // running its own Idle instructions underneath (WanderInCircle, and now the Seek that
        // walks to the leash point), and those steer the body continuously. The NPC therefore
        // drifted to face wherever the role was taking it — which is why it ended up looking
        // off to the side and why the camera framed something different every time.
        if (npc.isInteractingViaUI) {
            faceConversationPartner(ref, npc, transform, world, store);
            return;
        }

        // Ensure Frozen component is cleared if task changes externally and dialogue is inactive
        boolean hasFrozen = store.getComponent(ref, Frozen.getComponentType()) != null;
        if (ai.currentTask != TaskType.SLEEPING && ai.currentTask != TaskType.ENTERING_BED
                && ai.currentTask != TaskType.WAKING
                && hasFrozen && npc.currentConversationPartner == null && !npc.isInteractingViaUI) {
            commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
        }

        // --- 1. Evaluation Phase ---
        if (ai.currentTask != TaskType.DYING && ai.currentTask != TaskType.DEAD && ai.currentTask != TaskType.REAPING) {
            // Death comes from the health starvation has drained, not from the hunger bar itself.
            // Hunger hitting zero used to kill instantly, which made the whole starvation system
            // decorative: an NPC died the moment its belly emptied, long before the damage
            // mattered, and healing from food changed nothing.
            if (npc.needs.starvationDamage >= NPCHungerHelper.LETHAL_STARVATION_DAMAGE) {
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
                    if (otherAi != null && otherAi.currentTask == TaskType.IDLE && other.isReaper) {
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

        // --- Check low energy to go to bed immediately (interrupts current task) ---
        float sleepThreshold = npc.personality.traits.contains(Trait.LAZY) ? 60f : 30f;
        // O `world.getTick() >= ai.nextBedSearchTick` impede a tempestade de buscas.
        //
        // Sem ele: NPC exausta -> FINDING_BED -> a cama achada nao pode ser reivindicada ->
        // IDLE -> no proximo tick a interrupcao dispara outra vez. Como ela zera taskStartTime
        // para furar o cooldown, isso rodava a cada tick. Um log real acumulou 3447 rejeicoes da
        // MESMA cama em poucos segundos, com a NPC parada de exaustao o tempo todo.
        // The clock, not just exhaustion, sends an NPC to bed. Before this a villager with full
        // energy simply never slept, and the village stayed busy all night. Guards run the
        // opposite shift, so for them this window is the daytime.
        boolean sleepWindowOpen = NPCSleepHelper.isSleepPeriod(npc, world);
        boolean exhausted = npc.needs.energy < sleepThreshold;

        boolean alreadyHeadedToBed = ai.currentTask == TaskType.FINDING_BED
                || ai.currentTask == TaskType.MOVING_TO_BED || ai.currentTask == TaskType.ENTERING_BED
                || ai.currentTask == TaskType.SLEEPING || ai.currentTask == TaskType.WAKING;

        // Dying is not a task to be interrupted. Neither interrupt excluded it, so a starving NPC
        // was pulled straight back out of DYING, the death check re-fired on the next tick, and the
        // "is dying" broadcast repeated forever without the NPC ever actually dying.
        boolean inDeathFlow = ai.currentTask == TaskType.DYING || ai.currentTask == TaskType.DEAD
                || ai.currentTask == TaskType.REAPING;

        if ((sleepWindowOpen || exhausted) && world.getTick() >= ai.nextBedSearchTick
                && !alreadyHeadedToBed && !inDeathFlow) {

            ai.currentTask = TaskType.FINDING_BED;
            ai.targetBlockPosition = null;
            ai.taskStartTime = 0; // bypass cooldown
            ai.sleepingOnSchedule = sleepWindowOpen;
            clearAutonomyState(ai);
            if (sleepWindowOpen) {
                LOGGER.info("[SimTale] NPC '{}' sleep window opened, heading to bed", npc.name);
            } else {
                LOGGER.info("[SimTale] NPC '{}' is tired (energy={}), interrupting task to find bed immediately", npc.name, npc.needs.energy);
            }
        }

        // --- Very low hunger interrupts the current task, mirroring the sleep interrupt above ---
        //
        // Without this, hunger was only ever checked inside the IDLE branch, so a busy NPC could
        // starve with a full larder simply by never running out of things to do. Sleep already
        // worked this way; hunger did not, and that asymmetry had no reason behind it.
        //
        // The threshold sits well below the IDLE one (50): this is the emergency path, not the
        // normal one. Eating takes about three seconds, so interrupting costs little.
        if (npc.needs.hunger < HUNGER_INTERRUPT_THRESHOLD
                && world.getTick() >= ai.nextFoodSearchTick
                && ai.currentTask != TaskType.FINDING_FOOD && ai.currentTask != TaskType.MOVING_TO_FOOD
                && ai.currentTask != TaskType.EATING
                && ai.currentTask != TaskType.FINDING_BED && ai.currentTask != TaskType.MOVING_TO_BED
                && ai.currentTask != TaskType.ENTERING_BED && ai.currentTask != TaskType.SLEEPING
                && ai.currentTask != TaskType.WAKING
                && !inDeathFlow) {

            ai.currentTask = TaskType.FINDING_FOOD;
            ai.targetBlockPosition = null;
            ai.taskStartTime = world.getTick() - NPCHungerHelper.FOOD_SEARCH_COOLDOWN_TICKS;
            clearAutonomyState(ai);
            LOGGER.info("[SimTale] NPC '{}' is starving (hunger={}), interrupting task to find food", npc.name, npc.needs.hunger);
        }

        // --- Force sleep from command (uses SimNPCComponent flag to survive tick overwrite) ---
        if (npc.forceSleep) {
            npc.forceSleep = false;
            ai.currentTask = TaskType.FINDING_BED;
            ai.targetBlockPosition = null;
            ai.taskStartTime = 0; // bypass cooldown
            clearAutonomyState(ai);
            LOGGER.info("[SimTale] Force sleep triggered for NPC '{}', entering FINDING_BED", npc.name);
        }

        if (ai.currentTask == TaskType.IDLE) {
            if (npc.bedLocation == null && world.getTick() % 60 == 0) {
                BedPos bestBed = getBedPos(transform);
                if (bestBed != null) {
                    validateAndClaimBed(world, bestBed, npc);
                }
            }

            if (npc.profession == Profession.BUILDER || npc.profession == Profession.UNEMPLOYED) {
                for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                    if (site.isBuilding) {
                        ai.currentTask = TaskType.MOVING_TO_CONSTRUCTION;
                        ai.taskStartTime = world.getTick();
                        playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                        ai.targetBlockPosition = new Vector3i(site.anchor);
                        break;
                    }
                }
            }

            if (ai.currentTask == TaskType.IDLE && npc.needs.hunger < 50) {
                ai.currentTask = TaskType.FINDING_FOOD;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - NPCHungerHelper.FOOD_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.hygiene < 40) {
                ai.currentTask = TaskType.FINDING_BATH;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - BATH_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.fun < NPCLeisureHelper.FUN_THRESHOLD) {
                ai.currentTask = TaskType.FINDING_LEISURE;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - NPCLeisureHelper.LEISURE_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && npc.needs.social < 50 && Math.random() < 0.05) {
                SimNPCComponent bestTarget = null;
                double bestDist = SOCIALIZE_SEARCH_RANGE_SQ;
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other == npc || other.entityRef == null || other.entityId == null) continue;

                    // Do not drag someone out of bed or off the job for a chat.
                    RoutineAIComponent otherAi = store.getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (otherAi == null || !NPCSocialHelper.isAvailableToTalk(otherAi)) continue;

                    TransformComponent ot = store.getComponent(other.entityRef, TransformComponent.getComponentType());
                    if (ot == null) continue;

                    double d2 = transform.getPosition().distanceSquared(ot.getPosition());
                    if (d2 < bestDist) {
                        bestDist = d2;
                        bestTarget = other;
                    }
                }
                if (bestTarget != null) {
                    ai.currentTask = TaskType.MOVING_TO_SOCIALIZE;
                    ai.socializeTargetId = bestTarget.entityId;
                    ai.socializeHost = true;
                    ai.taskStartTime = world.getTick();
                    playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
                }
            } else if (ai.currentTask == TaskType.IDLE && Math.random() < 0.02) {
                // Anchor the stroll on the NPC's home so the village stays together;
                // NPCs without a bed wander around wherever they happen to be.
                double centerX = transform.getPosition().x;
                double centerZ = transform.getPosition().z;
                if (npc.bedLocation != null) {
                    centerX = npc.bedLocation.x;
                    centerZ = npc.bedLocation.z;
                }

                double angle = Math.random() * Math.PI * 2.0;
                double radius = 2.0 + Math.random() * (WANDER_RADIUS - 2.0);

                ai.currentTask = TaskType.WANDERING;
                ai.wanderTimer = 0; // handler stamps the deadline on first tick
                ai.targetBlockPosition = new Vector3i(
                        (int) (centerX + Math.cos(angle) * radius),
                        (int) transform.getPosition().y,
                        (int) (centerZ + Math.sin(angle) * radius)
                );
                playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
            }
        }

        // --- FINDING_BED ---
        if (ai.currentTask == TaskType.FINDING_BED) {
            if (npc.bedLocation != null) {
                LOGGER.info("[SimTale] NPC '{}' has bed at ({},{},{}), transitioning to MOVING_TO_BED",
                        npc.name, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.targetBlockPosition = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.currentTask = TaskType.MOVING_TO_BED;
                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else if (ai.taskStartTime == 0 || world.getTick() - ai.taskStartTime >= BED_SEARCH_RETRY_COOLDOWN_TICKS) {
                ai.taskStartTime = world.getTick();
                
                BedPos bestBed = getBedPos(transform);

                if (bestBed != null) {
                    LOGGER.info("[SimTale] NPC '{}' found unclaimed bed at ({},{},{})",
                            npc.name, bestBed.x, bestBed.y, bestBed.z);
                    if (validateAndClaimBed(world, bestBed, npc)) {
                        ai.targetBlockPosition = new Vector3i(bestBed.x, bestBed.y, bestBed.z);
                        ai.currentTask = TaskType.MOVING_TO_BED;
                        playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                    } else {
                        // Segura a proxima busca. Sem isto a interrupcao de cansaco reabria
                        // FINDING_BED no tick seguinte, com a mesma cama e o mesmo resultado.
                        ai.nextBedSearchTick = world.getTick() + BED_SEARCH_RETRY_COOLDOWN_TICKS;
                        ai.currentTask = TaskType.IDLE;
                    }
                } else {
                    LOGGER.warn("[SimTale] NPC '{}' could not find any bed! BedRegistry.BEDS.size={}",
                            npc.name, BedRegistry.BEDS.size());
                    ai.nextBedSearchTick = world.getTick() + BED_SEARCH_RETRY_COOLDOWN_TICKS;
                    ai.currentTask = TaskType.IDLE;
                }
            }
        }

        // --- MOVING_TO_BED: navigate to approach position adjacent to bed ---
        if (ai.currentTask == TaskType.MOVING_TO_BED) {
            if (npc.bedLocation == null) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.taskStartTime = world.getTick();
                return;
            }

            Vector3i bedPos = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);

            // Validate bed still exists by checking the actual world block, and self-heal BedRegistry if missing
            WorldChunk bedChunk = world.getChunkIfInMemory(ChunkUtil.indexChunk(bedPos.x >> 4, bedPos.z >> 4));
            if (bedChunk != null) {
                BlockType type = world.getBlockType(bedPos.x, bedPos.y, bedPos.z);
                if (type == null || type.getId() == null || !BedRegistry.isBedId(type.getId())) {
                    // Chunk loaded but bed block is gone — destroyed
                    LOGGER.info("[SimTale] NPC '{}' bed at ({},{},{}) was destroyed. Releasing.", npc.name, bedPos.x, bedPos.y, bedPos.z);
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
                    ai.currentTask = TaskType.IDLE;
                    return;
                } else {
                    // Self-healing: if the bed block is present in the world, make sure it is in BedRegistry (e.g. after server restart)
                    synchronized (BedRegistry.BEDS) {
                        boolean existsInRegistry = false;
                        for (BedPos b : BedRegistry.BEDS) {
                            if (b.x == bedPos.x && b.y == bedPos.y && b.z == bedPos.z) {
                                existsInRegistry = true;
                                break;
                            }
                        }
                        if (!existsInRegistry) {
                            LOGGER.debug("[SimTale] Re-registering loaded bed at (" + bedPos.x + "," + bedPos.y + "," + bedPos.z + ") from NPC's memory");
                            BedRegistry.addOrReplace(bedPos.x, bedPos.y, bedPos.z, 0f);
                        }
                    }
                }
            }

            Vector3i approachPos = getBedApproachPosition(bedPos, transform, world);
            ai.targetBlockPosition = approachPos;

            Vector3d pos = transform.getPosition();
            double dx = (approachPos.x + 0.5) - pos.x;
            double dz = (approachPos.z + 0.5) - pos.z;

            if (dx * dx + dz * dz < BED_REACH_DISTANCE_SQ) {
                clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.ENTERING_BED;
                ai.taskStartTime = world.getTick();
            } else {
                moveTo(ref, ai, world, new Vector3d(approachPos.x + 0.5, approachPos.y, approachPos.z + 0.5));
            }
        }

        // --- ENTERING_BED: teleport onto the bed block ---
        if (ai.currentTask == TaskType.ENTERING_BED) {
            if (npc.bedLocation == null) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.taskStartTime = world.getTick();
                return;
            }

            // Normaliza para a ancora do movel antes de montar.
            //
            // Uma cama ocupa seis blocos, e o mountOnBlock calcula onde o corpo deita a partir do
            // ponto de montagem do asset — que e medido DA ANCORA. Passar um bloco de filler
            // desloca a NPC exatamente pela distancia daquele bloco ate a ancora, e como o bloco
            // sorteado variava, o erro variava junto. Era essa a origem do desalinhamento que
            // resistiu a todas as tentativas de compensar por posicao.
            Vector3i bedPos = FurnitureAnchorHelper.anchorOf(
                    world, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
            if (ai.targetBlockPosition == null) {
                ai.targetBlockPosition = getBedApproachPosition(bedPos, transform, world);
            }

            Vector3d interactPos = new Vector3d(
                bedPos.x + 0.5,
                bedPos.y + 0.2,
                bedPos.z + 0.5
            );

            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(ref, commandBuffer, bedPos, interactPos);

            if (result instanceof BlockMountAPI.Mounted) {
                LOGGER.info("[SimTale] NPC '{}' successfully mounted bed at ({},{},{})", npc.name, bedPos.x, bedPos.y, bedPos.z);
                
                // NAO posiciona nem gira a NPC aqui. O mountOnBlock acima ja fez isso.
                //
                // Confirmado no bytecode de BlockMountAPI.mountOnBlock, que executa, nesta ordem:
                //
                //   BlockType.getBeds() -> RotatedMountPointsArray.getRotated(rotationIndex)
                //   BlockMountComponent.findAvailableSeat(...)   // escolhe o ponto de montagem
                //   BlockMountPoint.computeWorldSpacePosition(blockPos)
                //   BlockMountPoint.computeRotationEuler(rotationIndex)
                //   TransformComponent.setPosition(...)          // aplica direto, sincrono
                //   TransformComponent.setRotation(...)
                //
                // In other words, the engine knows the exact spot where the body lies on that bed model and
                // applies it. The old code queued, immediately after, a Teleport to bedPos +
                // (0.5, 2.0, 0.5) with a yaw coming from the bed ENTITY's TransformComponent —
                // overwriting the two correct values with two wrong ones.
                //
                //  explicava tres sintomas de uma vez: a NPC deitada atravessada (o yaw da
                // mobilia aponta para o lado por onde se entra, perpendicular a quem deita), a
                // queda de ~1,4 bloco ate o colchao, e a ejecao lateral da fisica — que foi o
                // motivo de a altura ter sido subida para 2.0 como paliativo. Nenhum desses
                // problemas existe quando se deixa o sistema de montagem trabalhar.
                //
                // O leash ainda precisa ser preso: ele e o que a IA do role persegue, e o
                // clearMoveTarget do MOVING_TO_BED o deixou no bloco AO LADO da cama. Sem isto,
                // a NPC sai da cama e vai dormir no chao, ao lado. Como o mount ja atualizou o
                // TransformComponent de forma sincrona, a posicao lida agora ja e a do colchao.
                NPCMovementHelper.pinLeashAt(ref, ai, new Vector3d(transform.getPosition()));

                // The POSE comes from here, not from the mount system.
                //
                // A test with these three calls turned off left the NPC STANDING on the bed, which
                // settled the question: the mount handles position and rotation, but the one that
                // lays the body down is MovementStates.sleeping plus the animation. Do not remove
                // without repeating that test.
                setSleepingState(ref, store, commandBuffer, true);

                // The role does not declare the "Sleep" state, so this call generates
                // "State 'Sleep.null' ... does not exist" in the log. It stays because it is harmless
                // and because declaring the state requires a BlockSet asset that we still haven't
                // figured out how to register by mod — see docs/sistemas/npc-comportamento.md.
                NPCEntity npcEntityComponent = store.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
                if (npcEntityComponent != null) {
                    StateSupport stateSupport = StateSupport.get(ref, store);
                    if (stateSupport != null) {
                        stateSupport.setState(ref, "Sleep", null, store);
                    }
                }

                playAnim(ref, AnimationSlot.Status, "Characters/Animations/Flavor/Sleep.blockyanim", "Sleep", store);

                ai.currentTask = TaskType.SLEEPING;
            } else {
                LOGGER.warn("[SimTale] Bed mount failed for NPC '{}': {}", npc.name, result);

                // Any failure does not mean the bed is gone.
                //
                // ALREADY_MOUNTED only says that the NPC is stuck to a previous mount — the
                // bed is intact. The old code treated any failure the same way: it erased
                // npc.bedLocation and saved it to the database. In other words, a transient
                // stumble cost the NPC her bed permanently, and she would go look for another
                // one from scratch.
                //
                // Aqui a montagem velha e removida e a proxima tentativa acontece no proximo
                // tick, com a cama preservada.
                if (result == BlockMountAPI.DidNotMount.ALREADY_MOUNTED) {
                    commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                    setSleepingState(ref, store, commandBuffer, false);
                    ai.currentTask = TaskType.ENTERING_BED;
                } else {
                    npc.bedLocation = null;
                    SimNPCPersistence.saveNPC(npc);
                    ai.currentTask = TaskType.FINDING_BED;
                }
            }
            ai.taskStartTime = world.getTick();
        }

        // --- SLEEPING: maintain sleep state and recover energy ---
        if (ai.currentTask == TaskType.SLEEPING) {
            if (npc.bedLocation == null) {
                // Bed was released elsewhere (e.g. destroyed by another system) — wake up cleanly
                setSleepingState(ref, store, commandBuffer, false);
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
                return;
            }





            npc.needs.healEnergy(0.045f);

            // Verify bed still exists periodically
            if ((world.getTick() - ai.taskStartTime) % 20 == 0) {
                Vector3i bedPos = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                BlockType type = world.getBlockType(bedPos.x, bedPos.y, bedPos.z);
                if (type == null || type.getId() == null || !BedRegistry.isBedId(type.getId())) {
                    LOGGER.info("[SimTale] NPC '{}' bed at ({},{},{}) was destroyed while sleeping. Waking up.", npc.name, bedPos.x, bedPos.y, bedPos.z);
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
                    
                    setSleepingState(ref, store, commandBuffer, false);
                    playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                    ai.currentTask = TaskType.IDLE;
                    ai.taskStartTime = world.getTick();
                    return;
                }
            }

            // A scheduled sleeper stays down until its window closes, however rested it is;
            // otherwise it would pop out of bed in the middle of the night as soon as energy
            // filled up. An exhaustion nap still ends on the old rule.
            boolean doneSleeping;
            if (ai.sleepingOnSchedule) {
                doneSleeping = !NPCSleepHelper.isSleepPeriod(npc, world);
            } else {
                doneSleeping = npc.needs.energy >= 100
                        || world.getTick() - ai.taskStartTime >= SLEEP_DURATION_TICKS;
            }

            if (doneSleeping) {
                npc.needs.energy = Math.min(100f, npc.needs.energy);
                ai.sleepingOnSchedule = false;
                ai.currentTask = TaskType.WAKING;
                ai.taskStartTime = world.getTick();
            }
        }

        if (ai.currentTask == TaskType.WAKING) {
            if (world.getTick() - ai.taskStartTime >= WAKE_ANIM_TICKS) {
                LOGGER.info("[SimTale] NPC '{}' has woken up and is leaving bed.", npc.name);
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                setSleepingState(ref, store, commandBuffer, false);
                
                NPCEntity npcEntityComponent = store.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
                if (npcEntityComponent != null) {
                    StateSupport stateSupport = StateSupport.get(ref, store);
                    if (stateSupport != null) {
                        stateSupport.setState(ref, "Idle", null, store);
                    }
                }
                
                AnimationUtils.stopAnimation(ref, AnimationSlot.Status, true, store);
                playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                
                if (npc.bedLocation != null) {
                    Vector3i approachPos = getBedApproachPosition(new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z), transform, world);
                    transform.teleportPosition(new Vector3d(approachPos.x + 0.5, approachPos.y, approachPos.z + 0.5));
                    commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
                }

                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
            } else if (world.getTick() - ai.taskStartTime == 1) {
                playAnim(ref, AnimationSlot.Status, "Characters/Animations/Default/Wake.blockyanim", "Wake", store);
            }
        }

        // --- Chest Interaction & Feeding Logic (Delegado ao NPCHungerHelper) ---
        NPCHungerHelper.tickStarvation(ref, npc, world);
        NPCHungerHelper.handleHungerLogic(ref, npc, ai, transform, world, store);

        // --- Crop Harvesting & Hunting Logic (Delegado ao NPCWorkHelper) ---
        NPCWorkHelper.handleWorkLogic(ref, npc, ai, transform, world, store);

        // --- Socializing & Wandering (Delegado ao NPCSocialHelper) ---
        NPCSocialHelper.handleSocialLogic(ref, npc, ai, transform, world, store);

        // --- Leisure / Hobby (Delegado ao NPCLeisureHelper) ---
        NPCLeisureHelper.handleLeisureLogic(ref, npc, ai, transform, world, store);

        // --- FINDING_BATH (OPTIMIZATION) ---
        if (ai.currentTask == TaskType.FINDING_BATH && world.getTick() - ai.taskStartTime >= BATH_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x; int sy = (int) pos.y; int sz = (int) pos.z;
            boolean found = false;

            // This scan touches ~31x31x11 ≈ 10.500 blocos por NPC. It used to allocate a
            // Vector3i *and* a lowercased String per block (≈21.000 objetos descartáveis por
            // varredura, por NPC). The cursor below is reused and the id match is
            // allocation-free. getChunkIfInMemory replaces getChunk so the scan never forces
            // a chunk load from inside the tick loop.
            Vector3i cursor = new Vector3i();

            bathSearch:
            for (int cx = (sx - BATH_SEARCH_RADIUS) >> 4; cx <= (sx + BATH_SEARCH_RADIUS) >> 4; cx++) {
                for (int cz = (sz - BATH_SEARCH_RADIUS) >> 4; cz <= (sz + BATH_SEARCH_RADIUS) >> 4; cz++) {
                    WorldChunk chunkAt = world.getChunkIfInMemory(ChunkUtil.indexChunk(cx, cz));
                    if (chunkAt == null) continue;

                    int minX = Math.max(sx - BATH_SEARCH_RADIUS, cx << 4);
                    int maxX = Math.min(sx + BATH_SEARCH_RADIUS, (cx << 4) + 15);
                    int minZ = Math.max(sz - BATH_SEARCH_RADIUS, cz << 4);
                    int maxZ = Math.min(sz + BATH_SEARCH_RADIUS, (cz << 4) + 15);

                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int y = sy - BATH_SEARCH_HEIGHT; y <= sy + BATH_SEARCH_HEIGHT; y++) {
                                BlockType bType = chunkAt.getBlockType(cursor.set(x, y, z));
                                if (bType == null) continue;
                                if (!containsIgnoreCase(bType.getId(), "water")) continue;

                                ai.targetBlockPosition = new Vector3i(x, y, z);
                                ai.currentTask = TaskType.MOVING_TO_BATH;
                                ai.taskStartTime = world.getTick();
                                playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                                found = true;
                                break bathSearch;
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
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] NPC '{}' desistiu de chegar na agua", npc.name);
                clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
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
            // The hygiene check alone was the only exit; if anything else clamped hygiene the
            // NPC would swim forever.
            if (npc.needs.hygiene >= 100f
                    || world.getTick() - ai.taskStartTime > BATH_DURATION_LIMIT_TICKS) {
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
                        // Caskara.delete() targets the "default" shell, so this never removed
                        // anything: every NPC that ever died stayed in the database forever.
                        SimNPCPersistence.deleteNPC(dyingNpc.entityId);
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
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] NPC '{}' desistiu de chegar ao canteiro de obras", npc.name);
                clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
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

        // No replaceComponent here on purpose.
        //
        // ArchetypeChunk.getComponent() hands back the instance stored in the chunk itself —
        // it does not clone — so every `ai.currentTask = ...` above is already visible to
        // every other reader. Re-submitting the same instance only mattered if
        // Store.replaceComponent had side effects, and its only one is notifying a
        // RefChangeSystem registered for the component type; the mod's single RefChangeSystem
        // (BedEntityRegistrySystem) is bound to PersistentModel, not to RoutineAIComponent.
        //
        // So the call was a per-NPC, per-tick no-op that still allocated a lambda and queued
        // an entry on the command buffer. Dropping it also settles the question of the ~10
        // early `return`s in this method: they never lost state to begin with.
    }

    @NullableDecl
    private static BedPos getBedPos(TransformComponent transform) {
        BedPos bestBed = null;
        double closestDistSq = Double.MAX_VALUE;
        Vector3d myPos = transform.getPosition();

        // BedPos already implements equals/hashCode over x/y/z, so the set can hold the
        // positions directly. Building "x,y,z" strings meant two throwaway allocations per
        // bed per lookup, on a path that runs whenever an NPC goes looking for a bed.
        Set<BedPos> claimedBeds = new HashSet<>();
        for (SimNPCComponent otherNpc : SimTale.ACTIVE_NPCS) {
            if (otherNpc.bedLocation != null) {
                claimedBeds.add(otherNpc.bedLocation);
            }
        }

        synchronized (BedRegistry.BEDS) {
            for (BedPos bp : BedRegistry.BEDS) {
                if (claimedBeds.contains(bp)) continue;

                double dx = bp.x - myPos.x;
                double dy = bp.y - myPos.y;
                double dz = bp.z - myPos.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < closestDistSq) {
                    closestDistSq = d2;
                    bestBed = bp;
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[SimTale] getBedPos: registry={}, claimed={}, chosen={}",
                    BedRegistry.BEDS.size(), claimedBeds.size(),
                    bestBed != null ? "(" + bestBed.x + "," + bestBed.y + "," + bestBed.z + ")" : "null");
        }
        return bestBed;
    }


    private void moveTo(Ref<EntityStore> ref, RoutineAIComponent ai, World world, Vector3d targetPos) {
        NPCMovementHelper.moveTo(ref, ai, world, targetPos);
    }

    private void clearMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai) {
        NPCMovementHelper.clearMoveTarget(npcRef, ai);
    }

    void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, anim, name, store);
    }

    void playAnim(Ref<EntityStore> ref, AnimationSlot slot, String anim, String name, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, slot, anim, name, store);
    }

    private void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, boolean sleeping) {
        NPCMovementHelper.setSleepingState(ref, store, commandBuffer, sleeping);
    }

    private Vector3i getBedApproachPosition(Vector3i bedPos, TransformComponent transform, World world) {
        return NPCMovementHelper.getBedApproachPosition(bedPos, transform, world);
    }

    /**
     * Allocation-free {@code id.toLowerCase().contains(needle)}. The bath scan ran this on
     * thousands of block ids per NPC; the lowercase copy alone was the bulk of the garbage.
     *
     * @param needle must already be lowercase.
     */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null) return false;
        int limit = haystack.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the NPC turned toward whoever opened its dialogue, and keeps its leash pinned to
     * where it stands so the role's own Seek does not try to walk it away mid-conversation.
     * <p>
     * The yaw formula is the engine's own: {@code PhysicsMath.headingFromDirection} computes
     * {@code atan2(-dx, -dz)}, and {@code Rotation3f(x, y, z)} maps to {@code (pitch, yaw,
     * roll)} — so the heading goes in the second slot.
     */
    private static void faceConversationPartner(Ref<EntityStore> ref, SimNPCComponent npc,
                                                TransformComponent transform, World world,
                                                Store<EntityStore> store) {
        Vector3d myPos = transform.getPosition();

        // Pin the leash to where the NPC stands, so the injected Idle -> ReturnHome transition
        // cannot fire and try to walk it off mid-conversation.
        NPCEntity npcEntity = store.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            npcEntity.setLeashPoint(new Vector3d(myPos.x, myPos.y, myPos.z));
        }

        UUID partnerId = npc.uiInteractionPlayer;
        if (partnerId == null) return;

        Ref<EntityStore> partnerRef = world.getEntityStore().getRefFromUUID(partnerId);
        if (partnerRef == null || !partnerRef.isValid()) return;

        TransformComponent partnerTransform = store.getComponent(partnerRef, TransformComponent.getComponentType());
        if (partnerTransform == null) return;

        Vector3d target = partnerTransform.getPosition();
        double dx = target.x - myPos.x;
        double dz = target.z - myPos.z;
        if (dx * dx + dz * dz < 1e-6) return;

        transform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
    }

    /**
     * Drops any pending socialize/wander bookkeeping so an interrupted task cannot leave
     * stale target ids behind. The chat partner, if any, times out on its own side.
     */
    private static void clearAutonomyState(RoutineAIComponent ai) {
        ai.socializeTargetId = null;
        ai.socializeHost = false;
        ai.wanderTimer = 0;
    }

    private static boolean validateAndClaimBed(World world, BedPos bestBed, SimNPCComponent npc) {
        return HouseManager.validateAndClaimBed(world, bestBed, npc);
    }

}