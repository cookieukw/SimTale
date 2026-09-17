package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tag ("pega-pega") and hide-and-seek ("esconde-esconde") between two nearby children.
 * <p>
 * Pairing happens in {@link RoutineSleepHelpers}'s own IDLE ladder, the same place
 * {@link NPCSocialHelper} picks a chat partner -- see {@link #startGame} for the entry point
 * both that organic trigger and {@code /simtale forceplay} share. Everything after pairing lives
 * here, dispatched from {@link RoutineAISystem} right next to
 * {@code NPCSocialHelper.handleSocialLogic} and {@code NPCGuardHelper.handleGuardLogic}.
 * <p>
 * No reservation flag is needed for the pairing itself the way {@code reservedForSocialUuid}
 * is needed for a chat: {@link #startGame} moves both children straight into an active state
 * (TAG_CHASING/TAG_FLEEING or SEEKING/MOVING_TO_HIDE) in the same synchronous call, with no
 * "walk over first" phase where a third child could see one of them as still idle and grab
 * them too -- by the time anyone else's tick looks, {@code currentTask} already says otherwise.
 * <p>
 * Same "not real engine simulation" philosophy {@code NPCGuardHelper} already states for its own
 * combat: tag's chase is a plain re-targeted {@code moveTo} with a catch radius (exactly how
 * {@code NPCGuardHelper} closes distance on a hostile), and hide-and-seek's "seeking" is the
 * seeker simply walking straight to the hider's known spot once its count finishes -- no actual
 * vision or line-of-sight check. Good enough to read as the game it is named after; anything
 * more is more risk and complexity than a background villager's game of tag needs.
 */
public class ChildPlayHelper {

    private static final SimLog LOGGER = SimLog.forClass(ChildPlayHelper.class);

    private ChildPlayHelper() {
    }

    /** How far apart two idle children can be and still be paired up to play. Tighter than
     *  {@link RoutineAISystem#SOCIALIZE_SEARCH_RANGE_SQ} (20 blocks) -- a chat can start with a
     *  walk over, but two kids need to already be close enough to notice each other to start a
     *  game on their own. */
    static final double PLAY_SEARCH_RANGE_SQ = 12.0 * 12.0;

    // --- Tag ---
    private static final double TAG_CATCH_DISTANCE_SQ = 1.3 * 1.3;
    private static final double TAG_FLEE_TRIGGER_SQ = 5.0 * 5.0;
    private static final double TAG_FLEE_STEP = 5.0;
    /** 30s, same ceiling {@code NPCGuardHelper} uses for its own chase. */
    private static final int TAG_CHASE_TIMEOUT_TICKS = 600;
    private static final int TAG_ROUNDS = 3;

    // --- Hide and seek ---
    private static final double HIDE_SEEK_RADIUS = 6.0;
    private static final double HIDE_REACH_DISTANCE_SQ = 1.0 * 1.0;
    /** 5s "counting" (eyes closed) before the seeker starts walking. */
    private static final int HIDE_COUNT_TICKS = 100;
    private static final int SEEK_TIMEOUT_TICKS = 500;
    private static final double SEEK_FOUND_DISTANCE_SQ = 1.5 * 1.5;
    private static final int HIDESEEK_ROUNDS = 2;

    /** Fun restored to both participants when a game ends on its own instead of being abandoned
     *  -- same idea as {@code NPCSocialHelper}'s own SOCIAL_RESTORE, just against the need this
     *  activity is actually about. */
    private static final float PLAY_FUN_RESTORE = 30f;

    /** Nearby players who overhear a tag/found line -- smaller than {@code NPCGuardHelper}'s
     *  25-block village-news radius, since this is two kids talking to each other, not an
     *  announcement. */
    private static final double PLAY_EVENT_HEARING_RANGE_SQ = 15.0 * 15.0;

    /** Same shape as {@code NPCSocialHelper.isAvailableToTalk}: free to be pulled into a new
     *  activity, and not already spoken for by a chat. A child already mid-game is naturally
     *  excluded too, since none of the five play TaskTypes are IDLE or WANDERING. */
    public static boolean isAvailableToPlay(RoutineAIComponent ai) {
        return (ai.currentTask == TaskType.IDLE || ai.currentTask == TaskType.WANDERING)
                && ai.reservedForSocialUuid == null;
    }

    /**
     * Top-level dispatch, called once per NPC per tick from {@code RoutineAISystem} exactly like
     * its social/guard siblings. Each phase below self-guards on {@code ai.currentTask}, so this
     * is a no-op for the vast majority of NPCs that are never in one of these five states.
     */
    public static void handleChildPlayLogic(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store) {
        switch (ai.currentTask) {
            case TAG_CHASING -> handleTagChasing(ref, npc, ai, transform, world, store);
            case TAG_FLEEING -> handleTagFleeing(ref, npc, ai, transform, world, store);
            case MOVING_TO_HIDE -> handleMovingToHide(ref, npc, ai, transform, world, store);
            case HIDING -> handleHiding(ref, ai, world);
            case SEEKING -> handleSeeking(ref, npc, ai, transform, world, store);
            default -> {
            }
        }
    }

    /**
     * Pairs {@code npc} with {@code partner} and starts a game -- the one entry point shared by
     * the organic IDLE-ladder trigger ({@code RoutineSleepHelpers.handleIdle}) and
     * {@code /simtale forceplay}, so both set up identical, correctly-mirrored state on both
     * sides. {@code forcedTag}: {@code null} picks randomly (what the organic trigger uses),
     * {@code TRUE}/{@code FALSE} forces tag or hide-and-seek (what the debug command uses).
     */
    public static void startGame(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            Ref<EntityStore> partnerRef, SimNPCComponent partner, RoutineAIComponent partnerAi,
            World world, Store<EntityStore> store, Boolean forcedTag) {

        boolean tag = forcedTag != null ? forcedTag : ThreadLocalRandom.current().nextBoolean();
        long tick = world.getTick();

        NPCMovementHelper.clearMoveTarget(ref, ai);
        NPCMovementHelper.clearMoveTarget(partnerRef, partnerAi);

        ai.playPartnerId = partner.entityId;
        partnerAi.playPartnerId = npc.entityId;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        TransformComponent partnerTransform = store.getComponent(partnerRef, TransformComponent.getComponentType());

        if (tag) {
            ai.currentTask = TaskType.TAG_CHASING;
            ai.playRoundsLeft = TAG_ROUNDS;
            partnerAi.currentTask = TaskType.TAG_FLEEING;
            partnerAi.playRoundsLeft = TAG_ROUNDS;
            NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
            announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.tag_start", 3);
        } else {
            ai.currentTask = TaskType.SEEKING;
            ai.playRoundsLeft = HIDESEEK_ROUNDS;
            partnerAi.currentTask = TaskType.MOVING_TO_HIDE;
            partnerAi.playRoundsLeft = HIDESEEK_ROUNDS;
            if (partnerTransform != null) {
                partnerAi.targetBlockPosition = pickHideSpot(partnerTransform);
                NPCMovementHelper.playAnim(partnerRef, NPCSocialHelper.walkAnimation(), "Walk", store);
            }
            announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.hideseek_start", 2);
        }

        ai.taskStartTime = tick;
        partnerAi.taskStartTime = tick;
    }

    /* ------------------------------------------------------------------
    TAG_CHASING / TAG_FLEEING
    ------------------------------------------------------------------
    */

    private static void handleTagChasing(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store) {
        if (ai.playPartnerId == null) {
            endGame(ref, npc, ai, world, false);
            return;
        }
        if (world.getTick() - ai.taskStartTime > TAG_CHASE_TIMEOUT_TICKS) {
            LOGGER.debug("[SimTale] {} desistiu de pegar (fora de alcance por tempo demais)", npc.name);
            endGame(ref, npc, ai, world, false);
            return;
        }

        Ref<EntityStore> partnerRef = world.getEntityStore().getRefFromUUID(ai.playPartnerId);
        TransformComponent partnerT = partnerRef != null ? store.getComponent(partnerRef, TransformComponent.getComponentType()) : null;
        RoutineAIComponent partnerAi = partnerRef != null ? store.getComponent(partnerRef, SimTale.ROUTINE_AI_COMPONENT_TYPE) : null;
        if (partnerT == null || partnerAi == null || partnerAi.currentTask != TaskType.TAG_FLEEING) {
            endGame(ref, npc, ai, world, false);
            return;
        }

        double distSq = transform.getPosition().distanceSquared(partnerT.getPosition());
        if (distSq <= TAG_CATCH_DISTANCE_SQ) {
            SimNPCComponent partnerNpc = resolveNpc(ai.playPartnerId);
            NPCMovementHelper.clearMoveTarget(ref, ai);
            NPCMovementHelper.clearMoveTarget(partnerRef, partnerAi);

            int roundsLeft = ai.playRoundsLeft - 1;
            if (roundsLeft <= 0) {
                announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.tag_end", 2);
                endGame(ref, npc, ai, world, true);
                endGame(partnerRef, partnerNpc, partnerAi, world, true);
            } else {
                ai.currentTask = TaskType.TAG_FLEEING;
                partnerAi.currentTask = TaskType.TAG_CHASING;
                ai.playRoundsLeft = roundsLeft;
                partnerAi.playRoundsLeft = roundsLeft;
                ai.taskStartTime = world.getTick();
                partnerAi.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(partnerRef, NPCSocialHelper.walkAnimation(), "Walk", store);
                announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.tag_tagged", 2);
            }
            return;
        }

        NPCMovementHelper.moveTo(ref, ai, world, partnerT.getPosition());
    }

    private static void handleTagFleeing(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store) {
        if (ai.playPartnerId == null) {
            endGame(ref, npc, ai, world, false);
            return;
        }

        Ref<EntityStore> chaserRef = world.getEntityStore().getRefFromUUID(ai.playPartnerId);
        TransformComponent chaserT = chaserRef != null ? store.getComponent(chaserRef, TransformComponent.getComponentType()) : null;
        RoutineAIComponent chaserAi = chaserRef != null ? store.getComponent(chaserRef, SimTale.ROUTINE_AI_COMPONENT_TYPE) : null;
        if (chaserT == null || chaserAi == null || chaserAi.currentTask != TaskType.TAG_CHASING) {
            endGame(ref, npc, ai, world, false);
            return;
        }

        /* Catches are detected on the chaser's own tick (handleTagChasing) -- this side only
        needs to run away once the chaser is actually closing in, and stand still otherwise
        rather than fidgeting every tick with nowhere in particular to go.
        */
        Vector3d myPos = transform.getPosition();
        double distSq = myPos.distanceSquared(chaserT.getPosition());
        if (distSq < TAG_FLEE_TRIGGER_SQ) {
            double dx = myPos.x - chaserT.getPosition().x;
            double dz = myPos.z - chaserT.getPosition().z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) {
                double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2.0);
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                len = 1.0;
            }
            Vector3d fleeTarget = new Vector3d(
                    myPos.x + (dx / len) * TAG_FLEE_STEP,
                    myPos.y,
                    myPos.z + (dz / len) * TAG_FLEE_STEP);
            NPCMovementHelper.moveTo(ref, ai, world, fleeTarget);
        }
    }

    /* ------------------------------------------------------------------
    MOVING_TO_HIDE / HIDING / SEEKING
    ------------------------------------------------------------------
    */

    private static void handleMovingToHide(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store) {
        if (ai.playPartnerId == null) {
            endGame(ref, npc, ai, world, false);
            return;
        }
        if (world.getTick() - ai.taskStartTime > SEEK_TIMEOUT_TICKS) {
            endGame(ref, npc, ai, world, false);
            return;
        }
        if (ai.targetBlockPosition == null) {
            ai.targetBlockPosition = pickHideSpot(transform);
        }

        Vector3d target = new Vector3d(ai.targetBlockPosition.x + 0.5, transform.getPosition().y, ai.targetBlockPosition.z + 0.5);
        double distSq = transform.getPosition().distanceSquared(target);
        if (distSq <= HIDE_REACH_DISTANCE_SQ) {
            NPCMovementHelper.clearMoveTarget(ref, ai);
            ai.currentTask = TaskType.HIDING;
            ai.taskStartTime = world.getTick();
            return;
        }
        NPCMovementHelper.moveTo(ref, ai, world, target);
    }

    /** Almost entirely passive -- the seeker's own tick ({@link #handleSeeking}) is what detects
     *  being found. This just guards against the seeker never showing up at all. */
    private static void handleHiding(Ref<EntityStore> ref, RoutineAIComponent ai, World world) {
        if (ai.playPartnerId == null || world.getTick() - ai.taskStartTime > SEEK_TIMEOUT_TICKS + HIDE_COUNT_TICKS) {
            endGame(ref, null, ai, world, false);
        }
    }

    private static void handleSeeking(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store) {
        if (ai.playPartnerId == null) {
            endGame(ref, npc, ai, world, false);
            return;
        }

        long elapsed = world.getTick() - ai.taskStartTime;
        if (elapsed > SEEK_TIMEOUT_TICKS) {
            endGame(ref, npc, ai, world, false);
            return;
        }
        if (elapsed == HIDE_COUNT_TICKS) {
            /* Counting just ended -- kick the walk animation off exactly once instead of every
            tick moveTo() re-targets.
            */
            NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
        }
        if (elapsed < HIDE_COUNT_TICKS) {
            return; // still "counting" with eyes closed -- stand still
        }

        Ref<EntityStore> hiderRef = world.getEntityStore().getRefFromUUID(ai.playPartnerId);
        RoutineAIComponent hiderAi = hiderRef != null ? store.getComponent(hiderRef, SimTale.ROUTINE_AI_COMPONENT_TYPE) : null;
        TransformComponent hiderT = hiderRef != null ? store.getComponent(hiderRef, TransformComponent.getComponentType()) : null;
        if (hiderAi == null || hiderT == null) {
            endGame(ref, npc, ai, world, false);
            return;
        }
        if (hiderAi.currentTask == TaskType.MOVING_TO_HIDE) {
            return; // still walking to her spot -- wait for her rather than seeking a moving target
        }
        if (hiderAi.currentTask != TaskType.HIDING) {
            endGame(ref, npc, ai, world, false);
            return;
        }

        double distSq = transform.getPosition().distanceSquared(hiderT.getPosition());
        if (distSq <= SEEK_FOUND_DISTANCE_SQ) {
            SimNPCComponent hiderNpc = resolveNpc(ai.playPartnerId);
            NPCMovementHelper.clearMoveTarget(ref, ai);

            int roundsLeft = ai.playRoundsLeft - 1;
            if (roundsLeft <= 0) {
                announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.hideseek_end", 2);
                endGame(ref, npc, ai, world, true);
                endGame(hiderRef, hiderNpc, hiderAi, world, true);
            } else {
                announcePlayEvent(npc, transform, world, store, "npc-dialogues.playing.hideseek_found", 2);
                // Roles rotate: whoever was just found now seeks, whoever found them hides.
                ai.currentTask = TaskType.MOVING_TO_HIDE;
                ai.targetBlockPosition = pickHideSpot(transform);
                hiderAi.currentTask = TaskType.SEEKING;
                ai.playRoundsLeft = roundsLeft;
                hiderAi.playRoundsLeft = roundsLeft;
                ai.taskStartTime = world.getTick();
                hiderAi.taskStartTime = world.getTick();
                NPCMovementHelper.playAnim(ref, NPCSocialHelper.walkAnimation(), "Walk", store);
            }
            return;
        }

        NPCMovementHelper.moveTo(ref, ai, world, hiderT.getPosition());
    }

    /** Random point within {@link #HIDE_SEEK_RADIUS} of where the hider stands right now --
     *  picked once at the start of the walk, not re-rolled, so she commits to one spot instead
     *  of wandering. Same polar-offset shape {@code RoutineSleepHelpers}'s own stroll fallback
     *  uses for picking a wander destination. */
    private static Vector3i pickHideSpot(TransformComponent transform) {
        Vector3d pos = transform.getPosition();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2.0);
        double radius = 2.0 + ThreadLocalRandom.current().nextDouble(0, HIDE_SEEK_RADIUS - 2.0);
        return new Vector3i(
                (int) (pos.x + Math.cos(angle) * radius),
                (int) pos.y,
                (int) (pos.z + Math.sin(angle) * radius));
    }

    /**
     * Resets {@code ai} back to IDLE and clears every play field, on both a normal finish and a
     * quiet give-up (partner gone, unreachable, timed out) -- {@code restoreFun} is what tells
     * the two apart: only a game that actually resolved (a tag landed, a hider was found, or the
     * round budget ran out) is worth crediting toward the need it exists to satisfy. {@code npc}
     * may be null when {@code restoreFun} is false -- it is only read inside that branch.
     */
    private static void endGame(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            World world, boolean restoreFun) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.currentTask = TaskType.IDLE;
        ai.targetBlockPosition = null;
        ai.playPartnerId = null;
        ai.playRoundsLeft = 0;
        ai.taskStartTime = world.getTick();

        if (restoreFun && npc != null && npc.entityRef != null) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID,
                    Math.min(100f, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) + PLAY_FUN_RESTORE));
            npc.setEmotion(Mood.HAPPY, 0.6f, "playing", world.getTick());
        }
    }

    private static SimNPCComponent resolveNpc(UUID id) {
        if (id == null) return null;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (id.equals(npc.entityId)) return npc;
        }
        return null;
    }

    /** Local copy of the pick-a-numbered-variant idiom every helper in this package keeps
     *  privately rather than sharing across classes -- see {@code NPCGuardHelper}'s own copy for
     *  the reasoning. */
    private static Message pickRandomTranslation(String baseKey, int optionsCount) {
        int index = ThreadLocalRandom.current().nextInt(1, optionsCount + 1);
        return Message.translation(baseKey + "." + index);
    }

    /**
     * Broadcasts a short in-character line to any player within earshot -- same "an NPC said
     * something nearby" shape {@code MotherAIManager.broadcastLocalMessage} and
     * {@code NPCGuardHelper.announceVictory} already use, just at their own radius.
     */
    private static void announcePlayEvent(SimNPCComponent speaker, TransformComponent speakerTransform,
            World world, Store<EntityStore> store, String key, int variants) {
        if (speaker == null || speakerTransform == null) return;
        Vector3d pos = speakerTransform.getPosition();
        Message line = Message.raw(speaker.name + ": ").insert(pickRandomTranslation(key, variants));

        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef == null || !pRef.isValid()) continue;
            TransformComponent pt = store.getComponent(pRef, TransformComponent.getComponentType());
            if (pt == null) continue;
            if (pt.getPosition().distanceSquared(pos) <= PLAY_EVENT_HEARING_RANGE_SQ) {
                pr.sendMessage(line);
            }
        }
    }
}
