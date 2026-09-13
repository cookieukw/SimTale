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

public class RoutineAISystem extends EntityTickingSystem<EntityStore> {

    static final int BATH_SEARCH_COOLDOWN_TICKS = 40;
    /**
     * Deadlines for the "walk somewhere" states. Without them an unreachable target (walled
     * off, across a ravine, chunk unloaded) parked the NPC in that state indefinitely — only
     * the low-energy interrupt could ever drag it out.
     */
    static final int MOVE_TIMEOUT_TICKS = 600;
    /** Bathing restores 1 hygiene per tick, so 100 ticks is already the worst case. */
    static final int BATH_DURATION_LIMIT_TICKS = 200;
    /** Horizontal/vertical half-extent of the water scan. 15x15x5 ≈ 10.500 blocos por varredura. */
    static final int BATH_SEARCH_RADIUS = 15;
    private static final int BATH_SEARCH_HEIGHT = 5;
    /**
     * How long a failed bath search waits before trying again. Longer than the bed retry because
     * a world with no water nearby will keep failing, and the sweep is the most expensive one the
     * routine runs. Ten seconds of strolling between attempts costs nothing and the NPC stays
     * visibly alive.
     */
    static final int BATH_SEARCH_RETRY_COOLDOWN_TICKS = 200;
    static final int BED_SEARCH_RETRY_COOLDOWN_TICKS = 600;
    public static final int SLEEP_DURATION_TICKS = 20 * 120;
    static final int WAKE_ANIM_TICKS = 20;
    static final double BED_REACH_DISTANCE_SQ = 2.5 * 2.5; // Increased to prevent getting stuck on bed collision

    /** Give up walking to a bed after 30 s, so an unreachable one does not trap the NPC. */
    static final int BED_MOVE_TIMEOUT_TICKS = 600;

    /** How long the Reaper stands at the corpse before completing the collection — long enough
     *  for the player to notice, go find an Ingredient_Voidheart, and come plead for the NPC's
     *  life before it's too late. */
    private static final int REAP_PLEAD_WINDOW_TICKS = 20 * 20;

    /**
     * How long after getting up an NPC refuses to go back to bed on schedule. Two real minutes.
     *
     * <p>This is a sanity check, not a second tiredness threshold: it exists so that someone who
     * wakes up inside their own sleeping window — or who changes profession mid-nap and inherits a
     * different shift — does not turn around and walk straight back to bed. Anything longer than the
     * gap between waking and the next window would start suppressing real nights of sleep, so it is
     * deliberately short.
     */
    private static final int WAKE_GRACE_TICKS = 20 * 60 * 2;
    /** Look for a chat partner within 20 blocks. */
    static final double SOCIALIZE_SEARCH_RANGE_SQ = 20.0 * 20.0;
    /** Max distance from home an idle stroll may take the NPC. */
    static final double WANDER_RADIUS = 8.0;
    static final SimLog LOGGER = SimLog.forClass(RoutineAISystem.class);
    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (npc.entityRef == null || !npc.entityRef.isValid()) {
            npc.entityRef = ref;
        }

        /* Skip routine AI for babies and toddlers (cared for by parents).
The isEmpty() guard matters: without any children in the world this loop still ran
once per NPC per tick for nothing.
*/
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
            /* A brand-new component silently resets everything to IDLE/no-target — indistinguishable
            from a genuine state change unless logged here. If this fires for an NPC that
            already had one going (mid-work, mid-social, etc.), that's the actual bug: something
            made the existing RoutineAIComponent invisible to this tick's chunk view.
            */
            LOGGER.info("[SimTale] {} had no RoutineAIComponent this tick — creating a fresh one (state reset to IDLE)", npc.name);
            ai = new RoutineAIComponent();
            commandBuffer.addComponent(chunk.getReferenceTo(index), SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        /* Was `getWorlds().values().stream().findFirst()`, which allocated a stream per NPC
        per tick.
        */
        World world = WorldUtil.fromEntityRef(ref);
        if (world == null) return;

        /* Being carried by a player suspends the routine entirely.
        This has to come before the mount cleanup below, which exists for beds and would rip a
        carried child straight off the player's shoulders on the very next tick — the NPC is
        mounted and not in a sleep state, which is exactly the condition that block reacts to.
        Standing the AI down matters as much as keeping the mount: a routine that keeps setting
        leash points and walking states on a body pinned to someone's shoulders is how an NPC
        ends up sliding across the floor, which is the failure this project already spent a
        session diagnosing once.
        */
        if (ChildCarryHelper.isBeingCarried(store, npc)) {
            /*
            Topped up rather than set once at pickup: moods decay, and a single HAPPY at the
            moment she was lifted would have faded back to BORED while she was still up there.
            Cheap because it only runs for a carried child, and only every few seconds.
            */
            if (world.getTick() % 40 == 0) {
                npc.setEmotion(Mood.HAPPY, 0.7f, "carried", world.getTick());
            }
            /*
            Re-settled on the same cadence rather than only at pickup: anything that pushes the
            entity — a shove, a fluid, a knockback the carrier walked into — would put velocity
            back and the role would start the walk cycle again with nothing to stop it.
            */
            if (world.getTick() % 40 == 0 && npc.entityRef != null && npc.entityRef.isValid()) {
                ChildCarryHelper.settleMovementStates(store, npc.entityRef);
            }
            return;
        }

        // unmounts and clears MountedComponent if the distant NPC is no longer in active sleep or sit state
        // or if its bed chunk has been unloaded, avoiding crashes in Hytale's ChunkUnloadingSystem.
        if (ai.currentTask != TaskType.SLEEPING && ai.currentTask != TaskType.ENTERING_BED && ai.currentTask != TaskType.SITTING) {
            if (chunk.getComponent(index, MountedComponent.getComponentType()) != null) {
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
            }
        }

        // The leash point is where the NPC is actually walking to, which is a far better statement
        // of intent than which way its body happens to be turned.
        NPCEntity doorNpcEntity = chunk.getComponent(index, Objects.requireNonNull(NPCEntity.getComponentType()));
        Vector3d doorDestination = doorNpcEntity != null ? doorNpcEntity.getLeashPoint() : null;
        if (doorDestination == null && ai != null) {
            if (ai.lastLeashPos != null) {
                doorDestination = ai.lastLeashPos;
            } else if (ai.targetBlockPosition != null) {
                doorDestination = new Vector3d(ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.y, ai.targetBlockPosition.z + 0.5);
            }
        }
        NPCDoorHelper.handleNpcDoors(world, npc, transform, doorDestination);


        /* Dialogue lock
        While an interaction page is open the mod's AI stands down entirely and the NPC is
        pinned facing the player, re-applied every tick.
        A single teleportRotation when the page opens is not enough: the Hytale role keeps
        running its own Idle instructions underneath (WanderInCircle, and now the Seek that
        walks to the leash point), and those steer the body continuously. The NPC therefore
        drifted to face wherever the role was taking it — which is why it ended up looking
        off to the side and why the camera framed something different every time.
        
        The death flow is exempt. DYING -> DEAD -> REAPING is a ceremony on a timer that ends by
        despawning both the corpse and the Reaper, and this early return sits above it, so
        opening any page on either of them halted the ritual for as long as the page stayed open
        — and permanently if the page ever failed to fire onDismiss, which is a bug this project
        has already hit once. The result was a Reaper left standing in the world for good. No UI
        should be able to deadlock a state machine that owns entity cleanup.
        */

        boolean inDeathCeremony = ai.currentTask == TaskType.DYING || ai.currentTask == TaskType.DEAD
                || ai.currentTask == TaskType.REAPING;
        if (npc.isInteractingViaUI && !inDeathCeremony) {
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

        /* Same self-heal for the sleeping movement state.
           Frozen had a guard and the sleep flag did not, so any exit path that forgot to clear it
           left the NPC walking around playing the sleep animation. Rather than hunting every exit,
           the invariant is asserted here: not a sleep task means not sleeping.
        */
        boolean inSleepTask = ai.currentTask == TaskType.SLEEPING
                || ai.currentTask == TaskType.ENTERING_BED
                || ai.currentTask == TaskType.WAKING;
        if (!inSleepTask) {
            MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
            if (msc != null && msc.getMovementStates().sleeping) {
                LOGGER.info("[SimTale] NPC '{}' was flagged as sleeping while doing {}; clearing", npc.name, ai.currentTask);
                NPCMovementHelper.setSleepingState(ref, store, commandBuffer, false);
            }
        }

        /* Real combat (a player's weapon, anything dealing damage through the engine's own
        health stat) never routed through here at all — SimTale had no hook for it, only for
        /simtale forcekill setting DYING directly. An NPC actually killed in melee kept
        getting ticked by every system below as if nothing happened: RoutineAISystem kept
        walking her around (no death animation exists for that, hence "no walk animation"),
        and since the engine already considers her dead/at 0 HP, further hits on her did
        nothing. Checking health here, for any task that isn't already part of the death flow,
        means any way an NPC reaches 0 HP funnels into the same DYING -> DEAD -> REAPING
        pipeline instead of leaving a broken not-quite-dead entity behind.
        */
        if (ai.currentTask != TaskType.DYING && ai.currentTask != TaskType.DEAD && ai.currentTask != TaskType.REAPING
                && ai.currentTask != TaskType.EXPEDITION) {
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap != null) {
                EntityStatValue healthVal = statMap.get(DefaultEntityStatTypes.getHealth());
                if (healthVal != null && healthVal.get() <= 0f) {
                    LOGGER.info("[SimTale] NPC '{}' reached 0 HP outside of forcekill — routing into the death flow", npc.name);
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    ai.currentTask = TaskType.DYING;
                    ai.taskStartTime = world.getTick();
                    return;
                }
            }
        }

        /* 1. Evaluation Phase
        Hunger does not kill. An NPC at zero stops working, cries and stays miserable until
        someone feeds it; the DYING flow below is reached only by old age, disease or a command.
        */
        if (ai.currentTask == TaskType.DYING) {
            /* Visual cue that something is wrong, for the ~10s before the Reaper shows up —
            otherwise the NPC just stands there giving no indication anything is happening.
            */
            if (world.getTick() - ai.taskStartTime == 1) {
                // StatusEffectHelper.applyBleeding only toggles a HUD icon on a connected
                // player's own screen (see RuneCore AUDITORIA.md #4.2) — silently does nothing on
                // an NPC ref, which is why this warning never actually showed up. applyVisualEffect
                // is the generic, player-agnostic half of the same native effect: it puts the real
                // "Bleeding" particle effect on the NPC itself, visible to anyone standing nearby.
                // 10s matches this DYING window (200 ticks) exactly, so it also naturally clears
                // itself even if the removeVisualEffect call below is ever skipped.
                EffectHelper.applyVisualEffect(ref, "Bleeding", 10.0f);
            }
            if (world.getTick() - ai.taskStartTime > 200) {
                EffectHelper.removeVisualEffect(ref, "Bleeding");
                ai.currentTask = TaskType.DEAD;
                ai.taskStartTime = world.getTick();

                /* The Reaper is ephemeral — spawned fresh for this specific death and removed
                again once the ritual finishes (REAPING below), rather than needing to already
                exist in the world beforehand. Previously nothing spawned her at all: without
                running /simtale spawn reaper ahead of time (undocumented outside the
                forcekill warning), or if the one Reaper that did exist was already busy with
                a different corpse, the body was stuck in DEAD forever. Spawning is a
                structural write and this runs from inside the Store's own tick, so it has to
                be deferred the same way startExpedition/spawnNPC elsewhere are.
                Offset a few blocks away instead of spawning her exactly on top of the
                corpse — she was clipping straight into the dying NPC, making it awful to
                even see or interact with either of them. REAPING already walks her in from
                wherever she starts if she's more than 2 blocks out, so this also means she
                visibly approaches instead of just appearing glued to the body.
                Copy before offsetting. joml's add(x,y,z) mutates the receiver and returns it,
                and getPosition() hands back the component's live vector — so this was not
                "three blocks from the corpse", it was *moving the corpse three blocks* and
                spawning the Reaper on top of it. From the outside it read as the dying NPC
                teleporting onto Death the moment she appeared.*/
                Vector3d deathPos = new Vector3d(transform.getPosition()).add(3, 0, 3);
                UUID dyingId = npc.entityId;
                WorldUtil.execute(() -> {
                    Ref<EntityStore> reaperRef = SimNPCFactory.spawnNPC(store, deathPos, SimNPCFactory.NPCType.REAPER);
                    RoutineAIComponent reaperAi = store.getComponent(reaperRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (reaperAi == null) {
                        reaperAi = new RoutineAIComponent();
                        store.addComponent(reaperRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, reaperAi);
                    }
                    reaperAi.currentTask = TaskType.REAPING;
                    reaperAi.dyingEntityId = dyingId;
                    reaperAi.reapTimer = REAP_PLEAD_WINDOW_TICKS;
                });
            }
            return;
        }

        if (ai.currentTask == TaskType.DEAD) return;

        // Check low energy to go to bed immediately (interrupts current task)
        float sleepThreshold = npc.personality.traits.contains(Trait.LAZY) ? 60f : 30f;
        /* Without `world.getTick() >= ai.nextBedSearchTick` there is a storm of searches. NPC exhausted -> FINDING_BED -> claimed bed cannot be claimed -> IDLE -> next tick interruption fires again. Because she resets taskStartTime to break the cooldown, this ran every tick. A real log accumulated 3447 rejections of the SAME bed in a few seconds, with the NPC stopped from exhaustion the whole time.
        The clock, not just exhaustion, sends an NPC to bed. Before this a villager with full
        energy simply never slept, and the village stayed busy all night. Guards run the
        opposite shift, so for them this window is the daytime.
        The clock alone is not enough to send someone to bed: they also have to be at least a
        little tired. An NPC with full energy going to sleep looks broken no matter what the
        schedule says — and it produced a real dead end, where a guard switched to another
        // profession mid-nap stayed in bed with 100 energy on its first day in the new job.
        */
        boolean justWokeUp = ai.lastWakeTick != 0
                && world.getTick() - ai.lastWakeTick < WAKE_GRACE_TICKS;
        boolean sleepWindowOpen = NPCSleepHelper.isSleepPeriod(npc, world) && !justWokeUp;
        boolean exhausted = NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) < sleepThreshold;

        boolean alreadyHeadedToBed = ai.currentTask == TaskType.FINDING_BED
                || ai.currentTask == TaskType.MOVING_TO_BED || ai.currentTask == TaskType.ENTERING_BED
                || ai.currentTask == TaskType.SLEEPING || ai.currentTask == TaskType.WAKING;

        /* Dying is not a task to be interrupted. Neither interrupt excluded it, so a starving NPC
        was pulled straight back out of DYING, the death check re-fired on the next tick, and the
        "is dying" broadcast repeated forever without the NPC ever actually dying.
        EXPEDITION (Hunter/Miner "gone for a while") is protected for a different reason: it
        shrinks the NPC to near-zero scale for the duration, standing in as "not here" without
        an actual invisibility flag (the engine has none). An interrupt yanking the task away
        mid-expedition would leave that shrink permanent — the model never gets restored — so
        this state has to run to completion, same as death does.*/
        boolean inDeathFlow = ai.currentTask == TaskType.DYING || ai.currentTask == TaskType.DEAD
                || ai.currentTask == TaskType.REAPING || ai.currentTask == TaskType.EXPEDITION;

        /* A claimed work post (fishing, and future lumberjack/farmer posts) must not outlive the
        NPC that claimed it — otherwise a killed fisherman leaves its post permanently
        reserved, with no one left to release it. releaseWorkPost is a no-op once the claim is
        already gone, so calling it every tick a dying/dead/reaped NPC ticks is harmless.*/
        if (inDeathFlow && ai.claimedWorkPost != null) {
            NPCWorkHelper.releaseWorkPost(ai, npc);
        }

        /* A task set by a debug command outranks the interrupts.
        Without this, /simtale forcework looked broken: it set the task, and on the very next
        tick the sleep interrupt overwrote it with FINDING_BED. Anything that fires from any
        state will win against a one-shot command unless it is told not to — and a debug command
        that cannot override the routine is useless for diagnosing the routine.*/
        boolean forcedByCommand = ai.forcedByDebug;

        if ((sleepWindowOpen || exhausted) && world.getTick() >= ai.nextBedSearchTick
                && !alreadyHeadedToBed && !inDeathFlow && !forcedByCommand) {

            if (ai.currentTask == TaskType.SITTING || ai.currentTask == TaskType.MOVING_TO_CHAIR) {
                NPCSeatingHelper.exitSitting(ref, store, commandBuffer, npc, ai);
            }
            ai.currentTask = TaskType.FINDING_BED;
            ai.targetBlockPosition = null;
            ai.taskStartTime = 0; // bypass cooldown
            ai.sleepingOnSchedule = sleepWindowOpen;
            clearAutonomyState(ai);
            if (sleepWindowOpen) {
                LOGGER.info("[SimTale] NPC '{}' sleep window opened, heading to bed", npc.name);
            } else {
                LOGGER.info("[SimTale] NPC '{}' is tired (energy={}), interrupting task to find bed immediately", npc.name, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID));
            }
        }

        /* Very low hunger interrupts the current task, mirroring the sleep interrupt above
        Without this, hunger was only ever checked inside the IDLE branch, so a busy NPC could
        starve with a full larder simply by never running out of things to do. Sleep already
        worked this way; hunger did not, and that asymmetry had no reason behind it.
        The threshold sits well below the IDLE one (50): this is the emergency path, not the
        normal one. Eating takes about three seconds, so interrupting costs little.*/
        if (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HUNGER_ID) < NeedsHelper.HUNGER_INTERRUPT_THRESHOLD
                && world.getTick() >= ai.nextFoodSearchTick
                && ai.currentTask != TaskType.FINDING_FOOD && ai.currentTask != TaskType.MOVING_TO_FOOD
                && ai.currentTask != TaskType.EATING
                && ai.currentTask != TaskType.FINDING_BED && ai.currentTask != TaskType.MOVING_TO_BED
                && ai.currentTask != TaskType.ENTERING_BED && ai.currentTask != TaskType.SLEEPING
                && ai.currentTask != TaskType.WAKING
                && !inDeathFlow && !forcedByCommand) {

            if (ai.currentTask == TaskType.SITTING || ai.currentTask == TaskType.MOVING_TO_CHAIR) {
                NPCSeatingHelper.exitSitting(ref, store, commandBuffer, npc, ai);
            }
            ai.currentTask = TaskType.FINDING_FOOD;
            ai.targetBlockPosition = null;
            ai.taskStartTime = world.getTick() - NPCHungerHelper.FOOD_SEARCH_COOLDOWN_TICKS;
            clearAutonomyState(ai);
            LOGGER.info("[SimTale] NPC '{}' is starving (hunger={}), interrupting task to find food", npc.name, NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HUNGER_ID));
        }

        /* Force sleep from command (uses SimNPCComponent flag to survive tick overwrite)*/
        if (npc.forceSleep) {
            npc.forceSleep = false;
            if (ai.currentTask == TaskType.SITTING || ai.currentTask == TaskType.MOVING_TO_CHAIR) {
                NPCSeatingHelper.exitSitting(ref, store, commandBuffer, npc, ai);
            }
            ai.currentTask = TaskType.FINDING_BED;
            ai.targetBlockPosition = null;
            ai.taskStartTime = 0; // bypass cooldown
            clearAutonomyState(ai);
            LOGGER.info("[SimTale] Force sleep triggered for NPC '{}', entering FINDING_BED", npc.name);
        }

        if (RoutineSleepHelpers.handleIdle(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* FINDING_BED */
        if (RoutineSleepHelpers.handleFindingBed(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* MOVING_TO_BED: navigate to approach position adjacent to bed */
        if (RoutineSleepHelpers.handleMovingToBed(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* ENTERING_BED: teleport onto the bed block */
        if (RoutineSleepHelpers.handleEnteringBed(ref, npc, ai, store, commandBuffer, world, transform)) return;

        //SLEEPING: maintain sleep state and recover energy 
        if (RoutineSleepHelpers.handleSleeping(ref, npc, ai, store, commandBuffer, world, transform)) return;

        if (RoutineSleepHelpers.handleWaking(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* Chest Interaction & Feeding Logic (Delegado ao NPCHungerHelper) 
        */
        NPCHungerHelper.tickStarvation(ref, npc, world, store);
        NPCHungerHelper.handleHungerLogic(ref, npc, ai, transform, world, store);

        /* Crop Harvesting & Hunting Logic (Delegado ao NPCWorkHelper) 
        */
        NPCWorkHelper.handleWorkLogic(ref, npc, ai, transform, world, store);

        /* `forcedByDebug` only means "outrank the sleep/hunger interrupts for the tick a debug
         command just set the task on" — every read site above (the interrupt checks, and
         NPCWorkHelper's own stagger bypass) has already had its chance to see it true this
         tick. Clearing it here, once, unconditionally, replaces a single reset buried inside
         NPCWorkHelper's IDLE+Farmer/Hunter branch, which every OTHER debug command that sets
         the flag (forcekill, forceplant setting MOVING_TO_WORK directly, the SimDebug UI's
         force buttons) never passed through — leaving the flag stuck true forever on any NPC
         those touched, which silently and permanently disabled its sleep and hunger interrupts.
        */
        ai.forcedByDebug = false;

        /* Socializing & Wandering (Delegated to NPCSocialHelper) 
        */
        NPCSocialHelper.handleSocialLogic(ref, npc, ai, transform, world, store);

        /* Guard combat: detect a hostile mob nearby and deal with it (Delegated to NPCGuardHelper).
         * Written the same session Profession.GUARD started meaning anything, but never actually
         * wired in here -- the method self-guards on `npc.profession != Profession.GUARD`, so it
         * was silently dead for every NPC the whole time. Same call shape as its siblings above.
         */
        NPCGuardHelper.handleGuardLogic(ref, npc, ai, transform, world, store, commandBuffer);

        /* Leisure / Hobby (Delegated to NPCLeisureHelper) 
        */
        NPCLeisureHelper.handleLeisureLogic(ref, npc, ai, transform, world, store);

        /* Seating / Resting on chairs (Delegated to NPCSeatingHelper) 
        */
        NPCSeatingHelper.handleSeatingLogic(ref, npc, ai, transform, world, store, commandBuffer);

        /* Player Proximity Greeting */
        checkPlayerProximityGreeting(ref, npc, ai, transform, world, store);

        /* Finding Bath (Optimization)
        */
        if (RoutineTaskHelpers.handleFindingBath(ref, npc, ai, store, commandBuffer, world, transform)) return;

        if (RoutineTaskHelpers.handleMovingToBath(ref, npc, ai, store, commandBuffer, world, transform)) return;

        if (RoutineTaskHelpers.handleBathing(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* REAPING 
        */
        if (RoutineTaskHelpers.handleReaping(ref, npc, ai, store, commandBuffer, world, transform)) return;

        if (RoutineTaskHelpers.handleMovingToConstruction(ref, npc, ai, store, commandBuffer, world, transform)) return;

        if (RoutineTaskHelpers.handleBuilding(ref, npc, ai, store, commandBuffer, world, transform)) return;

        /* No replaceComponent here on purpose.
         *
         * ArchetypeChunk.getComponent() hands back the instance stored in the chunk itself —
         * it does not clone — so every `ai.currentTask = ...` above is already visible to
         * every other reader. Re-submitting the same instance only mattered if
         * Store.replaceComponent had side effects, and its only one is notifying a
         * RefChangeSystem registered for the component type; the mod's single RefChangeSystem
         * (BedEntityRegistrySystem) is bound to PersistentModel, not to RoutineAIComponent.
         *
         * So the call was a per-NPC, per-tick no-op that still allocated a lambda and queued
         * an entry on the command buffer. Dropping it also settles the question of the ~10
         * early `return`s in this method: they never lost state to begin with.
         */
    }

    /**
     * Sends the Reaper away for good: untracked, plumbob released, entity removed.
     * <p>
     * She is spawned per-death and has no other way out, so every path that ends her involvement
     * has to go through here — the ritual completing, and her target disappearing before it could.
     * Missing either one leaves Death standing in the world permanently.
     * <p>
     * The plumbob matters as much as the entity: untracking only the deceased's left hers
     * registered under a UUID whose entity no longer exists, and the orphan sweep deliberately
     * skips anything still tracked, so it hung in the air at the exact spot of every death.
     */
    static void dismissReaper(SimNPCComponent npc, Ref<EntityStore> ref,
                                      CommandBuffer<EntityStore> commandBuffer) {
        SimTale.untrackNpc(npc);
        if (npc.entityId != null) {
            PlumbobSystem.removePlumbob(npc.entityId);
        }
        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
    }

    @NullableDecl
    static BedPos getBedPos(TransformComponent transform) {
        BedPos bestBed = null;
        double closestDistSq = Double.MAX_VALUE;
        Vector3d myPos = transform.getPosition();

        /* BedPos already implements equals/hashCode over x/y/z, so the set can hold the
         * positions directly. Building "x,y,z" strings meant two throwaway allocations per
         * bed per lookup, on a path that runs whenever an NPC goes looking for a bed.
         */
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

    /** How many variants each proximity-greeting line set ships with. */
    private static final int PROXIMITY_LINE_VARIANTS = 3;

    /**
     * Same random-pick pattern {@code InteractionManager} uses for its own dialogue lines — kept
     * as a small local copy rather than shared, since that one is private to its own package.
     */
    private static Message pickRandomTranslation(String baseKey, int optionsCount) {
        int index = ThreadLocalRandom.current().nextInt(1, optionsCount + 1);
        return Message.translation(baseKey + "." + index);
    }

    /**
     * Checks if a player has walked close to this NPC and performs an ambient greeting (wave + message).
     */
    private void checkPlayerProximityGreeting(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
                                              TransformComponent transform, World world, Store<EntityStore> store) {
        if (ai.currentTask == TaskType.SLEEPING || ai.currentTask == TaskType.DYING || ai.currentTask == TaskType.REAPING
                || ai.currentTask == TaskType.SOCIALIZING || npc.isInteractingViaUI) {
            return;
        }

        // Cooldown: 45 seconds (900 ticks)
        if (world.getTick() - ai.lastPlayerGreetingTick < 900) {
            return;
        }

        Vector3d npcPos = transform.getPosition();
        double greetRadiusSq = 4.5 * 4.5;

        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef == null || !pRef.isValid()) continue;

            TransformComponent pt = store.getComponent(pRef, TransformComponent.getComponentType());
            if (pt == null) continue;

            double d2 = pt.getPosition().distanceSquared(npcPos);
            if (d2 <= greetRadiusSq) {
                ai.lastPlayerGreetingTick = world.getTick();

                // Turn briefly towards player
                double dx = pt.getPosition().x - npcPos.x;
                double dz = pt.getPosition().z - npcPos.z;
                if (dx * dx + dz * dz > 1e-4) {
                    transform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
                }

                // Play wave and smile
                SimTaleJuiceHelper.playGreeting(ref, store);

                // Send contextual greeting message
                Relationship rel = npc.getRelationship(pr.getUuid());
                Message greetingMsg = switch (rel.status) {
                    case MARRIED, PARTNER, ENGAGED, DATING, CRUSH -> pickRandomTranslation("npc-dialogues.proximity.partner", PROXIMITY_LINE_VARIANTS).param("player", pr.getUsername());
                    case BEST_FRIEND, GOOD_FRIEND, FRIEND -> pickRandomTranslation("npc-dialogues.proximity.friend", PROXIMITY_LINE_VARIANTS).param("player", pr.getUsername());
                    case ENEMIES -> pickRandomTranslation("npc-dialogues.proximity.enemy", PROXIMITY_LINE_VARIANTS).param("player", pr.getUsername());
                    default -> pickRandomTranslation("npc-dialogues.proximity.stranger", PROXIMITY_LINE_VARIANTS).param("player", pr.getUsername());
                };
                pr.sendMessage(Message.raw(npc.name + ": ").insert(greetingMsg));
                LOGGER.debug("[SimTale] NPC '{}' greeted player '{}'", npc.name, pr.getUsername());
                break;
            }
        }
    }

    /**
     * Drops any pending socialize/wander bookkeeping so an interrupted task cannot leave
     * stale target ids behind. The chat partner, if any, times out on its own side.
     */
    private static void clearAutonomyState(RoutineAIComponent ai) {
        ai.socializeTargetId = null;
        ai.reservedForSocialUuid = null;
        ai.socializeHost = false;
        ai.socialTalkTimer = 0;
        ai.wanderTimer = 0;
        if (ai.targetChairPos != null) {
            ChairRegistry.releaseChair(ai.targetChairPos);
            ai.targetChairPos = null;
        }
    }

    static void startWanderingFallback(Ref<EntityStore> ref, RoutineAIComponent ai, SimNPCComponent npc, TransformComponent transform, Store<EntityStore> store, World world) {
        double centerX = transform.getPosition().x;
        double centerZ = transform.getPosition().z;
        double wanderRadius = WANDER_RADIUS;

        if (npc.bedLocation != null) {
            centerX = npc.bedLocation.x;
            centerZ = npc.bedLocation.z;
        } else {
            VillageManager.Village village = VillageManager.nearest(centerX, centerZ);
            if (village != null) {
                centerX = village.centerX();
                centerZ = village.centerZ();
                wanderRadius = village.radius();
            }
        }

        double angle = Math.random() * Math.PI * 2.0;
        double radius = 2.0 + Math.random() * (wanderRadius - 2.0);

        ai.currentTask = TaskType.WANDERING;
        ai.wanderTimer = 0;
        ai.targetBlockPosition = new Vector3i(
                (int) (centerX + Math.cos(angle) * radius),
                (int) transform.getPosition().y,
                (int) (centerZ + Math.sin(angle) * radius)
        );
        NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
    }

}