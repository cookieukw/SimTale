package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.lifecycle.FamilyBonds;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.*;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import com.hypixel.hytale.server.npc.role.support.StateSupport;

/**
 * Split out of {@link RoutineAISystem}#tick(), which had grown to 1478 lines handling every
 * NPC TaskType inline in one method (same class of size problem, and the same fix, as
 * {@code DebugCommands}'s own split off {@code SimTaleCommand} — see that file's javadoc).
 * <p>
 * Each method here mirrors exactly one {@code if (ai.currentTask == TaskType.X) { ... } }
 * block from the original {@code tick()}, byte-for-byte, with one mechanical transformation:
 * every bare {@code return;} inside the block (which used to exit the whole {@code tick()}
 * method, skipping every TaskType check still to come this tick) now means "stop the dispatch
 * chain", signalled by returning {@code true}; falling through to the end of the block without
 * a {@code return} (which used to let the very next {@code if (ai.currentTask == ...)} in
 * {@code tick()} evaluate the NEW value of {@code ai.currentTask} in the SAME tick — the
 * mechanism that, e.g., lets an NPC set IDLE -> MOVING_TO_CONSTRUCTION and have
 * MOVING_TO_CONSTRUCTION's own handler run immediately rather than next tick) now means "keep
 * going", signalled by {@code false}. {@code RoutineAISystem.tick()} calls every method here
 * in the exact original order via {@code if (Helper.handleX(...)) return;}, which reproduces
 * both cases identically: {@code true} propagates the stop, {@code false} lets the very next
 * call in the chain run and see the updated {@code ai.currentTask}.
 * <p>
 * Constants and the handful of small private helpers this logic depends on
 * ({@code getBedPos}, {@code startWanderingFallback}, {@code dismissReaper}, the
 * {@code BED_*}/{@code BATH_*} thresholds, {@code LOGGER}) stayed on {@code
 * RoutineAISystem} and were widened from {@code private} to package-private so this class can
 * reach them, exactly like {@code DebugCommands} reaches things still living on
 * {@code SimTaleCommand}. {@code moveTo}/{@code clearMoveTarget}/{@code playAnim}/
 * {@code setSleepingState}/{@code getBedApproachPosition} were one-line delegates to
 * {@code NPCMovementHelper}'s own public static methods, so calls here go straight to
 * {@code NPCMovementHelper} instead of through those wrappers; the same is true of
 * {@code validateAndClaimBed}, a pure delegate to {@code HouseManager}.
 */
final class RoutineSleepHelpers {
    private RoutineSleepHelpers() {}

    static boolean handleIdle(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, World world, TransformComponent transform) {
        /* Reserved (waiting for a conversation partner to arrive) must mean actually standing
        still: reservedForSocialUuid previously only stopped OTHER NPCs' searches from picking
        this one (NPCSocialHelper.isAvailableToTalk) -- nothing stopped THIS ladder from handing
        her a brand new errand, or even a second conversation, on a later tick while she waited.
        When her real suitor then arrived, isReservedForMe let it force her into SOCIALIZING out
        from under whatever she'd started, which is what showed up in-game as an NPC snapping
        out of one task mid-stride ("giro e volta").
        */
        if (ai.currentTask == TaskType.IDLE && NPCSocialHelper.isReservedAndActive(ai, world.getTick())) {
            if (npc.bedLocation == null && world.getTick() % 60 == 0) {
                BedPos bestBed = RoutineAISystem.getBedPos(transform);
                if (bestBed != null) {
                    HouseManager.validateAndClaimBed(world, bestBed, npc);
                }
            }

            if (npc.profession == Profession.BUILDER || npc.profession == Profession.UNEMPLOYED) {
                for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                    if (site.isBuilding) {
                        ai.currentTask = TaskType.MOVING_TO_CONSTRUCTION;
                        ai.taskStartTime = world.getTick();
                        NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                        ai.targetBlockPosition = new Vector3i(site.anchor);
                        break;
                    }
                }
            }

            /* The nextXSearchTick guards are what keep an unsatisfiable need from eating the whole
            chain. These checks are one else-if ladder, so a branch that fires and then fails
            silently costs the NPC every behaviour below it: an NPC that is dirty with no water
            in range, or hungry with no reachable food, re-entered its search every single tick
            and therefore never socialised and never wandered. From the outside that is an NPC
            standing perfectly still for hours with nothing at all in the logs.
            Backdating taskStartTime here is deliberate — it skips the handler's own cooldown so
            the search runs this tick — which is exactly why the cooldown has to be enforced up
            front instead. On failure each handler stamps its nextXSearchTick, and during that
            window the ladder falls through to strolling like normal.*/
            if (ai.currentTask == TaskType.IDLE
                    && NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HUNGER_ID) < 70
                    && world.getTick() >= ai.nextFoodSearchTick) {
                ai.currentTask = TaskType.FINDING_FOOD;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - NPCHungerHelper.FOOD_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE
                    && NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HYGIENE_ID) < 40
                    && world.getTick() >= ai.nextBathSearchTick) {
                ai.currentTask = TaskType.FINDING_BATH;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - RoutineAISystem.BATH_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE && NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.FUN_ID) < NPCLeisureHelper.FUN_THRESHOLD) {
                ai.currentTask = TaskType.FINDING_LEISURE;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick() - NPCLeisureHelper.LEISURE_SEARCH_COOLDOWN_TICKS;
            } else if (ai.currentTask == TaskType.IDLE
                    && (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) < 75f || Math.random() < 0.20)
                    && world.getTick() >= ai.nextChairSearchTick) {
                ai.currentTask = TaskType.FINDING_CHAIR;
                ai.taskStartTime = world.getTick();
            } else if (ai.currentTask == TaskType.IDLE
                    && world.getTick() >= ai.nextSocialSearchTick
                    && (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.SOCIAL_ID) < 45 || Math.random() < 0.04)) {
                SimNPCComponent bestTarget = null;
                double bestDist = RoutineAISystem.SOCIALIZE_SEARCH_RANGE_SQ;
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other == npc || other.entityRef == null || !other.entityRef.isValid() || other.entityId == null) continue;

                    // Do not drag someone out of bed or off the job for a chat.
                    RoutineAIComponent otherAi = store.getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (otherAi == null || !NPCSocialHelper.isAvailableToTalk(otherAi, world.getTick())) continue;

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

                    // Reserve partner so they pause and wait instead of wandering off
                    RoutineAIComponent otherAi = store.getComponent(bestTarget.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (otherAi != null) {
                        otherAi.reservedForSocialUuid = npc.entityId;
                        otherAi.currentTask = TaskType.IDLE;
                        otherAi.wanderTimer = 0;
                        otherAi.targetBlockPosition = null;
                        otherAi.taskStartTime = world.getTick();
                        NPCMovementHelper.clearMoveTarget(bestTarget.entityRef, otherAi);
                    }

                    NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
                } else {
                    // Stagger search retry so an NPC doesn't search every tick when no one is available
                    ai.nextSocialSearchTick = world.getTick() + 300;
                }
            }

            /* Child play: two nearby children start a real game of tag or hide-and-seek instead
            of each separately drifting into solo leisure or a normal chat. Deliberately its own
            statement after every real need above, same reasoning the stroll fallback below
            already documents -- a hungry or exhausted child still eats or sits down first, and
            this only ever claims a tick that would otherwise fall through to the aimless stroll,
            so it costs nothing on the many ticks nobody is around to play with. */
            if (ai.currentTask == TaskType.IDLE
                    && InteractionManager.isNpcAChild(npc)
                    && (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.FUN_ID) < NPCLeisureHelper.FUN_THRESHOLD || Math.random() < 0.15)
                    && world.getTick() >= ai.nextPlaySearchTick) {
                SimNPCComponent playmate = null;
                double bestPlayDist = ChildPlayHelper.PLAY_SEARCH_RANGE_SQ;
                for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
                    if (other == npc || other.entityRef == null || !other.entityRef.isValid() || other.entityId == null) continue;
                    if (!InteractionManager.isNpcAChild(other)) continue;

                    RoutineAIComponent otherAi = store.getComponent(other.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (otherAi == null || !ChildPlayHelper.isAvailableToPlay(otherAi)) continue;

                    TransformComponent ot = store.getComponent(other.entityRef, TransformComponent.getComponentType());
                    if (ot == null) continue;

                    double d2 = transform.getPosition().distanceSquared(ot.getPosition());
                    if (d2 < bestPlayDist) {
                        bestPlayDist = d2;
                        playmate = other;
                    }
                }
                if (playmate != null) {
                    RoutineAIComponent playmateAi = store.getComponent(playmate.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (playmateAi != null) {
                        ChildPlayHelper.startGame(ref, npc, ai, playmate.entityRef, playmate, playmateAi, world, store, null);
                    }
                } else {
                    ai.nextPlaySearchTick = world.getTick() + 200;
                }
            }

            /* Deliberately its own statement rather than the tail of the ladder above.
            Every branch up there can claim the tick and then not set a task: the searches fail
            silently, and the socialise roll can win with nobody available to talk to. As the
            last `else if` the stroll was only ever reached when none of them fired, so a need
            the NPC could not satisfy took its wandering away too. Guarding on "still IDLE"
            instead means the fallback is reached whenever nothing above it actually committed,
            and any branch added later inherits that safety net for free.*/
            if (ai.currentTask == TaskType.IDLE && (Math.random() < 0.08 || (ai.taskStartTime > 0 && world.getTick() - ai.taskStartTime > 30))) {
                /* Anchor the stroll: village territory if one exists, then own bed, then current pos.
                Expands the stroll so NPCs actually walk around town instead of clustering in an 8-block box.
                */
                double centerX = transform.getPosition().x;
                double centerZ = transform.getPosition().z;
                double wanderRadius = 18.0;

                VillageManager.Village village = VillageManager.nearest(centerX, centerZ);
                if (village != null) {
                    centerX = village.centerX();
                    centerZ = village.centerZ();
                    wanderRadius = Math.clamp(village.radius(), 18.0, 48.0);
                } else if (npc.bedLocation != null) {
                    centerX = npc.bedLocation.x;
                    centerZ = npc.bedLocation.z;
                    wanderRadius = 20.0;
                }

                double angle = Math.random() * Math.PI * 2.0;
                double radius = 3.0 + Math.random() * (wanderRadius - 3.0);

                ai.currentTask = TaskType.WANDERING;
                ai.wanderTimer = 0; // handler stamps the deadline on first tick
                ai.targetBlockPosition = new Vector3i(
                        (int) (centerX + Math.cos(angle) * radius),
                        (int) transform.getPosition().y,
                        (int) (centerZ + Math.sin(angle) * radius)
                );
                NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
            }
        }
        return false;
    }

    static boolean handleFindingBed(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.FINDING_BED) {
            if (npc.bedLocation == null && InteractionManager.isNpcAChild(npc)) {
                BedPos parentBed = FamilyBonds.findParentBed(npc);
                if (parentBed != null) {
                    npc.bedLocation = parentBed;
                    npc.family.homeX = parentBed.x;
                    npc.family.homeY = parentBed.y;
                    npc.family.homeZ = parentBed.z;
                    npc.family.hasSharedHome = true;
                    SimNPCPersistence.saveNPC(npc);
                    RoutineAISystem.LOGGER.info("[SimTale] Child NPC '{}' will use parents' bed at ({},{},{})",
                            npc.name, parentBed.x, parentBed.y, parentBed.z);
                }
            }

            if (npc.bedLocation != null) {
                RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' has bed at ({},{},{}), transitioning to MOVING_TO_BED",
                        npc.name, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.targetBlockPosition = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                ai.currentTask = TaskType.MOVING_TO_BED;
                /* MOVING_TO_BED's own timeout check runs later in this same tick (no return
                 between the blocks) and measures from taskStartTime. Whoever routed the NPC
                 into FINDING_BED zeroed it out (both the nightly trigger and /simtale
                 forcesleep do, to bypass FINDING_BED's own retry cooldown) — without restamping
                 it here, "now - 0" is always past the timeout, so an NPC that already owns a
                 bed gave up walking to it before taking a single step, every time.*/
                ai.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else if (ai.taskStartTime == 0 || world.getTick() - ai.taskStartTime >= RoutineAISystem.BED_SEARCH_RETRY_COOLDOWN_TICKS) {
                ai.taskStartTime = world.getTick();
                
                BedPos bestBed = RoutineAISystem.getBedPos(transform);

                if (bestBed != null) {
                    RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' found unclaimed bed at ({},{},{})",
                            npc.name, bestBed.x, bestBed.y, bestBed.z);
                    if (HouseManager.validateAndClaimBed(world, bestBed, npc)) {
                        ai.targetBlockPosition = new Vector3i(bestBed.x, bestBed.y, bestBed.z);
                        ai.currentTask = TaskType.MOVING_TO_BED;
                        NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                    } else {
                        // Schedules next search and goes to wander.
                        ai.nextBedSearchTick = world.getTick() + RoutineAISystem.BED_SEARCH_RETRY_COOLDOWN_TICKS;
                        ai.taskStartTime = world.getTick();
                        RoutineAISystem.startWanderingFallback(ref, ai, npc, transform, store, world);
                    }
                } else {
                    RoutineAISystem.LOGGER.warn("[SimTale] NPC '{}' could not find any bed! BedRegistry.BEDS.size={}",
                            npc.name, BedRegistry.BEDS.size());
                    ai.nextBedSearchTick = world.getTick() + RoutineAISystem.BED_SEARCH_RETRY_COOLDOWN_TICKS;
                    ai.taskStartTime = world.getTick();
                    RoutineAISystem.startWanderingFallback(ref, ai, npc, transform, store, world);
                }
            }
        }
        return false;
    }

    static boolean handleMovingToBed(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.MOVING_TO_BED) {
            if (npc.bedLocation == null) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.taskStartTime = world.getTick();
                return true;
            }

            Vector3i bedPos = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);

            // Validate bed still exists by checking the actual world block, and self-heal BedRegistry if missing
            WorldChunk bedChunk = world.getChunkStore().getChunkComponent(ChunkUtil.indexChunk(bedPos.x >> 4, bedPos.z >> 4), WorldChunk.getComponentType());
            if (bedChunk != null) {
                BlockType type = bedChunk.getBlockType(bedPos.x, bedPos.y, bedPos.z);
                if (type == null || type.getId() == null || !BedRegistry.isBedId(type.getId())) {
                    // Chunk loaded but bed block is gone — destroyed
                    RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' bed at ({},{},{}) was destroyed. Releasing.", npc.name, bedPos.x, bedPos.y, bedPos.z);
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    SimNPCPersistence.saveNPC(npc);
                    ai.currentTask = TaskType.IDLE;
                    return true;
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
                            RoutineAISystem.LOGGER.debug("[SimTale] Re-registering loaded bed at (" + bedPos.x + "," + bedPos.y + "," + bedPos.z + ") from NPC's memory");
                            BedRegistry.addOrReplace(bedPos.x, bedPos.y, bedPos.z, 0f);
                        }
                    }
                }
            }

            Vector3i approachPos = NPCMovementHelper.getBedApproachPosition(bedPos, transform, world);
            ai.targetBlockPosition = approachPos;

            // Give up on a bed that cannot be reached, instead of walking at a wall forever.
            if (world.getTick() - ai.taskStartTime > RoutineAISystem.BED_MOVE_TIMEOUT_TICKS) {
                RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' gave up walking to its bed at ({},{},{})", npc.name, bedPos.x, bedPos.y, bedPos.z);
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
                ai.nextBedSearchTick = world.getTick() + RoutineAISystem.BED_MOVE_TIMEOUT_TICKS;
                ai.currentTask = TaskType.IDLE;
                return true;
            }

            Vector3d pos = transform.getPosition();
            double dx = (approachPos.x + 0.5) - pos.x;
            double dy = (approachPos.y + 0.5) - pos.y;
            double dz = (approachPos.z + 0.5) - pos.z;

            /*Proximity alone is not enough to get into bed.
            This test used to be flat XZ distance, which ignored both height and walls: an NPC
            standing outside the house, one wall away from the bed, satisfied it and mounted
            straight through the wall. From the outside it looked like the NPC vanished. */
            boolean closeEnough = dx * dx + dz * dz < RoutineAISystem.BED_REACH_DISTANCE_SQ && Math.abs(dy) <= 2.0;
            boolean reachable = closeEnough
                    && NPCMovementHelper.hasClearPath(world, pos, approachPos);

            if (reachable) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.ENTERING_BED;
                ai.taskStartTime = world.getTick();
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(approachPos.x + 0.5, approachPos.y, approachPos.z + 0.5));
            }
        }
        return false;
    }

    static boolean handleEnteringBed(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.ENTERING_BED) {
            if (npc.bedLocation == null) {
                ai.currentTask = TaskType.FINDING_BED;
                ai.taskStartTime = world.getTick();
                return true;
            }

            /* Normalize to the furniture's anchor before mounting.
             A bed occupies six blocks, and mountOnBlock calculates where the body lies starting from
             the asset's assembly point — which is measured FROM THE ANCHOR. Passing a filler block
             displaces the NPC exactly by the distance from that block to the anchor, and since the
             chosen block varied, the error varied along with it. That was the origin of the misalignment that
             resisted all attempts to compensate by position. */
            Vector3i bedPos = FurnitureAnchorHelper.anchorOf(
                    world, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
            if (ai.targetBlockPosition == null) {
                ai.targetBlockPosition = NPCMovementHelper.getBedApproachPosition(bedPos, transform, world);
            }

            Vector3d interactPos = new Vector3d(
                bedPos.x + 0.5,
                bedPos.y + 0.2,
                bedPos.z + 0.5
            );

            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(ref, commandBuffer, bedPos, interactPos);

            if (result instanceof BlockMountAPI.Mounted) {
                RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' successfully mounted bed at ({},{},{})", npc.name, bedPos.x, bedPos.y, bedPos.z);
                
                /* do NOT position or rotate the NPC here. BlockMountAPI already did it.
                 Confirmed in the bytecode of BlockMountAPI.mountOnBlock, which executes in this order:
                 BlockType.getBeds() -> RotatedMountPointsArray.getRotated(rotationIndex)
                 BlockMountComponent.findAvailableSeat(...)   // chooses the mount point
                 BlockMountPoint.computeWorldSpacePosition(blockPose)
                 BlockMountPoint.computeRotationEuler(rotationIndex)
                 TransformComponent.setPosition(...)          // applies it directly, synchronously
                 TransformComponent.setRotation(...)

                In other words, the engine knows the exact spot where the body lies on that bed model and
                 applies it. The old code queued, immediately after, a Teleport to bedPos +
                 (0.5, 2.0, 0.5) with a yaw coming from the bed ENTITY's TransformComponent —
                 overwriting the two correct values with two wrong ones.

                 That explains three symptoms at once: the NPC lying across the bed (the
                 furniture's yaw points to where you enter, perpendicular to the person lying
                 down), the ~1.4 block drop to the mattress, and the lateral physics offset —
                 which was the reason the height had been raised to 2.0 as a temporary fix.
                 None of these problems exist when you let the assembly system work.
                 The leash still needs to be pinned: it's what the role AI chases, and the
                 clearMoveTarget in MOVING_TO_BED left it on the block NEXT TO the bed. Without
                 this, the NPC walks off the bed and sleeps on the floor nearby. Since the mount
                 already updated the TransformComponent synchronously, the position read now is
                 already the mattress position.

                 The POSE comes from here, not from the mount system.
                
                 A test with these three calls turned off left the NPC STANDING on the bed, which
                 settled the question: the mount handles position and rotation, but the one that
                 lays the body down is MovementStates.sleeping plus the animation. Do not remove
                 without repeating that test.
                NPCMovementHelper.setSleepingState(ref, store, commandBuffer, true);*/

                /* There is deliberately no setState("Sleep") here.
                 A call used to sit at this spot and it never did anything: our roles declare only
                 Idle and ReturnHome, so the engine refused it every time with "State 'Sleep.null'
                 does not exist and was set by an external call" — one log line per NPC per night,
                 for no effect. Sleeping works because of the two calls around this comment:
                 MovementStates.sleeping lays the body down and the animation holds the pose, while
                 pinLeashAt above parks the leash on the NPC's own position so the role's Leash
                 sensor stops firing and it settles back into Idle on its own.
                 Giving the roles a real Sleep state is possible (vanilla does it with
                 StateTransitions -> Laydown/Wake) and would let the role own the pose instead. It
                 needs every state to be both sensed and set or the role fails to validate and
                 spawning breaks server-wide — see scripts/add_returnhome_state.py for the time
                 that already cost us. Not worth it while the mod drives sleep entirely from Java.*/
                NPCMovementHelper.playAnim(ref, AnimationSlot.Status, "Characters/Animations/Flavor/Sleep.blockyanim", "Sleep", store);

                ai.currentTask = TaskType.SLEEPING;
            } else {
                RoutineAISystem.LOGGER.warn("[SimTale] Bed mount failed for NPC '{}': {}", npc.name, result);

                /* Any failure does not mean the bed is gone.
                ALREADY_MOUNTED only says that the NPC is stuck to a previous mount — the
                bed is intact. The old code treated any failure the same way: it erased
                npc.bedLocation and saved it to the database. In other words, a transient
                stumble cost the NPC her bed permanently, and she would go look for another
                one from scratch.
                */
                /* Here the old mount is removed and the next attempt happens on the next
                tick, with the bed preserved.
                */
                if (result == BlockMountAPI.DidNotMount.ALREADY_MOUNTED) {
                    commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                    NPCMovementHelper.setSleepingState(ref, store, commandBuffer, false);
                    ai.currentTask = TaskType.ENTERING_BED;
                } else if (InteractionManager.isNpcAChild(npc)) {
                    // Child sharing parents' bed: if mount point is occupied, sleep alongside/on bed
                    transform.setPosition(new Vector3d(bedPos.x + 0.5, bedPos.y + 0.6, bedPos.z + 0.5));
                    NPCMovementHelper.pinLeashAt(ref, ai, transform.getPosition());
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Status, "Characters/Animations/Flavor/Sleep.blockyanim", "Sleep", store);
                    ai.currentTask = TaskType.SLEEPING;
                    RoutineAISystem.LOGGER.info("[SimTale] Child NPC '{}' sharing parents' bed at ({},{},{})",
                            npc.name, bedPos.x, bedPos.y, bedPos.z);
                } else {
                    npc.bedLocation = null;
                    SimNPCPersistence.saveNPC(npc);
                    ai.currentTask = TaskType.FINDING_BED;
                }
            }
            ai.taskStartTime = world.getTick();
        }
        return false;
    }

    static boolean handleSleeping(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world) {
        if (ai.currentTask == TaskType.SLEEPING) {
            if (npc.bedLocation == null) {
                // Bed was released elsewhere (e.g. destroyed by another system) — wake up cleanly
                NPCMovementHelper.setSleepingState(ref, store, commandBuffer, false);
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
                return true;
            }

            NeedsHelper.setNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID, Math.min(100f, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) + 0.045f));

            // Verify bed still exists periodically
            if ((world.getTick() - ai.taskStartTime) % 20 == 0) {
                Vector3i bedPos = new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                BlockType type = NPCMovementHelper.getBlockTypeSafe(world, bedPos);
                if (type != null && !BedRegistry.isBedId(type.getId())) {
                    RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' bed at ({},{},{}) was destroyed while sleeping. Waking up.", npc.name, bedPos.x, bedPos.y, bedPos.z);
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    SimNPCPersistence.saveNPC(npc);
                    
                    NPCMovementHelper.setSleepingState(ref, store, commandBuffer, false);
                    NPCMovementHelper.playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                    ai.currentTask = TaskType.IDLE;
                    ai.taskStartTime = world.getTick();
                    return true;
                }
            }

            /* A scheduled sleeper stays down until its window closes, however rested it is;
            otherwise it would pop out of bed in the middle of the night as soon as energy
            filled up. An exhaustion nap still ends on the old rule.
            */
            boolean sleepPeriodClosed = !NPCSleepHelper.isSleepPeriod(npc, world);
            boolean doneSleeping;
            if (ai.sleepingOnSchedule) {
                doneSleeping = sleepPeriodClosed;
            } else {
                doneSleeping = sleepPeriodClosed
                        || NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) >= 100
                        || world.getTick() - ai.taskStartTime >= RoutineAISystem.SLEEP_DURATION_TICKS;
            }

            if ((world.getTick() - ai.taskStartTime) % 20 == 0) {
                Float currentHour = NPCSleepHelper.currentHour(world);
                RoutineAISystem.LOGGER.info("[SimTale-SleepDebug] NPC '{}' tick in SLEEPING | world='{}' | hour={} | sleepPeriodClosed={} | sleepingOnSchedule={} | doneSleeping={}",
                        npc.name, world.getName(), currentHour, sleepPeriodClosed, ai.sleepingOnSchedule, doneSleeping);
            }

            if (doneSleeping) {
                RoutineAISystem.LOGGER.info("[SimTale-SleepDebug] NPC '{}' finished sleeping! Transitioning to WAKING | world='{}' | hour={}",
                        npc.name, world.getName(), NPCSleepHelper.currentHour(world));
                NeedsHelper.setNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID, Math.min(100f, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID)));
                ai.sleepingOnSchedule = false;
                ai.currentTask = TaskType.WAKING;
                ai.taskStartTime = world.getTick();
                ai.lastWakeTick = world.getTick();
            }
        }
        return false;
    }

    static boolean handleWaking(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.WAKING) {
            if (world.getTick() - ai.taskStartTime >= RoutineAISystem.WAKE_ANIM_TICKS) {
                RoutineAISystem.LOGGER.info("[SimTale] NPC '{}' has woken up and is leaving bed.", npc.name);
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                NPCMovementHelper.setSleepingState(ref, store, commandBuffer, false);


                
                NPCEntity npcEntityComponent = store.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
                if (npcEntityComponent != null) {
                    StateSupport stateSupport = StateSupport.get(ref, store);
                    stateSupport.setState(ref, "Idle", null, store);
                }
                
                AnimationUtils.stopAnimation(ref, AnimationSlot.Status, true, store);
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", store);
                
                if (npc.bedLocation != null) {
                    /* Must be the nullable lookup, not getBedApproachPosition: that one falls back
                    to the bed itself when nothing beside it is standable, and teleporting there
                    buries the NPC inside the bed. A bed pushed against a wall hits that case.
                    
                    Normalise to the anchor first — bedLocation may be any of the six blocks, and
                    the candidates are computed relative to whatever is passed in.
                    */
                    Vector3i bedAnchor = FurnitureAnchorHelper.anchorOf(
                            world, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                    Vector3i exitPos = NPCMovementHelper.findStandableBeside(bedAnchor, transform, world);

                    if (exitPos != null) {
                        double offX = 0;
                        double offZ = 0;
                        if (InteractionManager.isNpcAChild(npc) && npc.entityId != null) {
                            int h = npc.entityId.hashCode();
                            offX = ((h & 1) == 0 ? 0.35 : -0.35);
                            offZ = (((h >> 1) & 1) == 0 ? 0.35 : -0.35);
                        }
                        transform.teleportPosition(new Vector3d(exitPos.x + 0.5 + offX, exitPos.y, exitPos.z + 0.5 + offZ));
                        commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
                    } else {
                        /* Nowhere to step out to. Staying put is wrong-looking but recoverable;
                        teleporting into the bed is not.
                        */
                        RoutineAISystem.LOGGER.warn("[SimTale] NPC '{}' has no standable spot beside its bed at ({},{},{}); skipping wake-up teleport",
                                npc.name, bedAnchor.x, bedAnchor.y, bedAnchor.z);
                    }
                }

                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
            } else if (world.getTick() - ai.taskStartTime == 1) {
                NPCMovementHelper.playAnim(ref, AnimationSlot.Status, "Characters/Animations/Default/Wake.blockyanim", "Wake", store);
            }
        }
        return false;
    }

}
