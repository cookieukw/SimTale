package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.lifecycle.FamilyBonds;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.cookieukw.SimTale.core.Relationship;
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
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.cookie.runecore.api.EffectHelper;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.Message;

import java.util.concurrent.ThreadLocalRandom;
import com.hypixel.hytale.server.core.command.system.CommandManager;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import com.hypixel.hytale.server.npc.role.support.StateSupport;
import javax.annotation.Nonnull;

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
final class RoutineTaskHelpers {
    private RoutineTaskHelpers() {}

    static boolean handleFindingBath(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.FINDING_BATH && world.getTick() - ai.taskStartTime >= RoutineAISystem.BATH_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            Vector3d pos = transform.getPosition();
            int sx = (int) pos.x; int sy = (int) pos.y; int sz = (int) pos.z;
            boolean found = false;

            Vector3i nearestBath = BathRegistry.nearestTo(pos.x, pos.y, pos.z, npc.entityId);
            if (nearestBath != null) {
                double dx = nearestBath.x - sx;
                double dz = nearestBath.z - sz;
                if (dx * dx + dz * dz <= RoutineAISystem.BATH_SEARCH_RADIUS * RoutineAISystem.BATH_SEARCH_RADIUS) {
                    ai.targetBlockPosition = nearestBath;
                    ai.currentTask = TaskType.MOVING_TO_BATH;
                    ai.taskStartTime = world.getTick();
                    NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                    found = true;
                }
            }
            if (!found) {
                /* Back off before returning to IDLE. Without this the IDLE branch re-enters the
                 * search on the very next tick and this ~10.500-block sweep runs at 20 Hz per
                 * dirty NPC, with the NPC frozen in place the whole time.
                 */
                ai.nextBathSearchTick = world.getTick() + RoutineAISystem.BATH_SEARCH_RETRY_COOLDOWN_TICKS;
                ai.currentTask = TaskType.IDLE;
            }
        }
        return false;
    }

    static boolean handleMovingToBath(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.MOVING_TO_BATH) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE; 
                return true;
            }
            if (world.getTick() - ai.taskStartTime > RoutineAISystem.MOVE_TIMEOUT_TICKS) {
                RoutineAISystem.LOGGER.debug("[SimTale] NPC '{}' gave up reaching the water", npc.name);
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
                /* Unreachable water still scores as the best option, so without the backoff the
                 * NPC is sent straight back to it on the next tick, forever.
                 */
                ai.nextBathSearchTick = world.getTick() + RoutineAISystem.BATH_SEARCH_RETRY_COOLDOWN_TICKS;
                ai.currentTask = TaskType.IDLE;
                return true;
            }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 1.5 * 1.5) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.BATHING;
                ai.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Swim.blockyanim", "Swim", store);
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }
        return false;
    }

    static boolean handleBathing(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.BATHING) {
            NeedsHelper.setNeed(store, npc.entityRef, NeedsHelper.HYGIENE_ID, Math.min(100f, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HYGIENE_ID) + 1.0f));
            /* The hygiene check alone was the only exit; if anything else clamped hygiene the
             * NPC would swim forever.
             */
            if (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HYGIENE_ID) >= 100f
                    || world.getTick() - ai.taskStartTime > RoutineAISystem.BATH_DURATION_LIMIT_TICKS) {
                ai.currentTask = TaskType.IDLE;
                ai.targetBlockPosition = null;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }
        return false;
    }

    static boolean handleReaping(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.REAPING && ai.dyingEntityId != null) {
            /* Self-heal against whatever it is (role's own appearance system, most likely —
             * REAPER spawns on the "SimTale_Human_Male" role for its behavior, and that role's
             * own "Appearance" is a normal human) keeps putting the human model back after
             * SimNPCFactory's initial override. Checked every tick instead of once so it doesn't
             * matter when the conflicting system runs relative to spawn.
             */
            PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
            if (pm != null && !SimNPCFactory.REAPER_MODEL_ASSET_ID.equals(pm.getModelReference().getModelAssetId())) {
                /* Through applyModel, which writes ModelComponent as well as PersistentModel.
                 *
                 * This self-heal ran every tick and kept "correcting" a model that visually never
                 * changed, because only the persisted component was being rewritten — the drawn
                 * one was never touched and never marked for resend. That is almost certainly the
                 * whole of the "Reaper still uses the player model" report: the id stored was
                 * right the entire time.
                 */
                SimNPCFactory.applyModel(store, ref, SimNPCFactory.REAPER_MODEL_ASSET_ID, 1.0f, new HashMap<>());
            }

            Ref<EntityStore> dyingRef = world.getEntityStore().getRefFromUUID(ai.dyingEntityId);
            TransformComponent dyingTransform = (dyingRef != null) ? store.getComponent(dyingRef, TransformComponent.getComponentType()) : null;
            if (dyingTransform == null) {
                /* The corpse is gone (already collected, chunk unloaded, removed by a command).
                 * This used to drop the Reaper to IDLE, which quietly turned Death into a
                 * permanent villager: she is spawned per-death and has no other exit, so nothing
                 * was ever going to despawn her again. She then wandered and socialised like
                 * anyone else — and, because the model self-heal above only runs while REAPING,
                 * the role's own Appearance system put the human model back on her within a few
                 * ticks. That is the "Reaper still in the world" and almost certainly the "Reaper
                 * is still using the player model" report too. Her target is gone, so her reason
                 * to exist is gone: she leaves.
                 */
                RoutineAISystem.LOGGER.info("[SimTale] Reaper's target is gone — despawning her instead of leaving her in the world");
                RoutineAISystem.dismissReaper(npc, ref, commandBuffer);
                return true;
            }

            double dx = dyingTransform.getPosition().x - transform.getPosition().x;
            double dz = dyingTransform.getPosition().z - transform.getPosition().z;
            double d2 = dx*dx + dz*dz;

            if (d2 > 2.0 * 2.0) {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(dyingTransform.getPosition().x, dyingTransform.getPosition().y, dyingTransform.getPosition().z));
            } else {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.reapTimer--;
                if (ai.reapTimer <= 0) {
                    SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    String deceasedName = dyingNpc != null ? dyingNpc.name : "Someone";
                    Universe.get().getPlayers().forEach(p -> {
                        p.sendMessage(Message.translation("general.reaper.soul_taken").param("name", deceasedName));
                        try {
                            /* A raw stone stood in only because there was nothing better on hand.
                             * Life_Essence actually reads as a collected soul.
                             */
                            CommandManager.get().handleCommand(p, "give " + p.getUsername() + " Ingredient_Life_Essence --quantity=1");
                        } catch (Exception e) {
                            RoutineAISystem.LOGGER.error("Error giving soul to player", e);
                        }
                    });
                    if (dyingNpc != null && dyingNpc.entityId != null) {
                        PlumbobSystem.removePlumbob(dyingNpc.entityId);
                        /* Record survives now instead of being deleted outright — foundation for
                         * a future revive/cemetery feature (SimNPCPersistence.archiveToGraveyard).
                         */
                        SimNPCPersistence.archiveToGraveyard(dyingNpc.entityId);
                    }
                    /* Same class of leak as the DB one above, just in memory: the corpse entity
                     * was removed from the world here, but its SimNPCComponent stayed in
                     * ACTIVE_NPCS/NPCS_BY_ID forever with a now-invalid entityRef — a permanent
                     * ghost entry for every NPC that ever died, for the life of the server
                     * process. Every list scan and lookup elsewhere had to keep guarding against
                     * it via isValid() checks instead of it simply not being there.
                     */
                    if (dyingNpc != null) {
                        SimTale.untrackNpc(dyingNpc);
                    }
                    commandBuffer.removeEntity(dyingRef, RemoveReason.REMOVE);

                    RoutineAISystem.dismissReaper(npc, ref, commandBuffer);
                }
            }
        }
        return false;
    }

    static boolean handleMovingToConstruction(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
        if (ai.currentTask == TaskType.MOVING_TO_CONSTRUCTION) {
            if (ai.targetBlockPosition == null) {
                ai.currentTask = TaskType.IDLE;
                return true;
            }
            if (world.getTick() - ai.taskStartTime > RoutineAISystem.MOVE_TIMEOUT_TICKS) {
                RoutineAISystem.LOGGER.debug("[SimTale] NPC '{}' desistiu de chegar ao canteiro de obras", npc.name);
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
                ai.currentTask = TaskType.IDLE;
                return true;
            }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 3.0 * 3.0) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.BUILDING;
                ai.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }
        return false;
    }

    static boolean handleBuilding(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, World world, TransformComponent transform) {
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
                ai.targetBlockPosition = null;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            } else {
                if ((world.getTick() - ai.taskStartTime) % 40 == 0) {
                    NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
                }
                NeedsHelper.setNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID, Math.max(0f, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) - 0.05f));
                if (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) <= 10f) {
                    ai.currentTask = TaskType.IDLE;
                    ai.targetBlockPosition = null;
                    NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
                }
            }
        }
        return false;
    }

}
