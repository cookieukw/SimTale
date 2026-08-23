package com.cookieukw.SimTale.systems;


import java.util.UUID;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

/**
 * Handles the two "autonomy" branches of the routine AI: walking over to another NPC for a
 * chat, and idle wandering around home.
 * <p>
 * Both states used to be assigned by {@link RoutineAISystem} without any handler, which left
 * NPCs frozen in place until the low-energy interrupt dragged them to bed.
 */
public class NPCSocialHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCSocialHelper.class);

    private static final String ANIM_WALK = "Characters/Animations/Actions/Walk.blockyanim";
    private static final String ANIM_IDLE = "Characters/Animations/Actions/Idle.blockyanim";

    /** How close two NPCs must be to start talking. */
    private static final double SOCIALIZE_REACH_DISTANCE_SQ = 2.5 * 2.5;
    /** Length of the conversation itself. */
    private static final int SOCIALIZE_DURATION_TICKS = 100;
    /** Give up walking to the partner after this long (unreachable, wandered off, ...). */
    private static final int SOCIALIZE_TIMEOUT_TICKS = 400;
    /** Social need restored to both participants by a successful chat. */
    private static final float SOCIAL_RESTORE = 35f;

    private static final double WANDER_REACH_DISTANCE_SQ = 2.0 * 2.0;
    /** Give up on an unreachable wander destination instead of standing there forever. */
    private static final int WANDER_TIMEOUT_TICKS = 300;

    private NPCSocialHelper() {
    }

    public static void handleSocialLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        handleMovingToSocialize(ref, npc, ai, transform, world, store);
        handleSocializing(ref, npc, ai, world, store);
        handleWandering(ref, ai, transform, world, store);
    }

    // ------------------------------------------------------------------
    // MOVING_TO_SOCIALIZE
    // ------------------------------------------------------------------

    private static void handleMovingToSocialize(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.MOVING_TO_SOCIALIZE) {
            return;
        }

        if (ai.socializeTargetId == null) {
            abortSocial(ref, ai, store);
            return;
        }

        // Bail out if the walk is taking too long — the partner may be unreachable.
        if (ai.taskStartTime > 0 && world.getTick() - ai.taskStartTime > SOCIALIZE_TIMEOUT_TICKS) {
            LOGGER.debug("[SimTale] NPC '{}' gave up walking to socialize", npc.name);
            abortSocial(ref, ai, store);
            return;
        }

        Ref<EntityStore> targetRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
        if (targetRef == null || !targetRef.isValid()) {
            abortSocial(ref, ai, store);
            return;
        }

        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        RoutineAIComponent targetAi = store.getComponent(targetRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (targetTransform == null || targetAi == null || !isAvailableToTalk(targetAi)) {
            abortSocial(ref, ai, store);
            return;
        }

        Vector3d myPos = transform.getPosition();
        Vector3d targetPos = targetTransform.getPosition();
        double dx = targetPos.x - myPos.x;
        double dz = targetPos.z - myPos.z;

        if (dx * dx + dz * dz < SOCIALIZE_REACH_DISTANCE_SQ) {
            NPCMovementHelper.clearMoveTarget(ref, ai);
            ai.currentTask = TaskType.SOCIALIZING;
            ai.socializeHost = true;
            ai.taskStartTime = world.getTick();
            NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);

            // Pull the partner into the conversation as the guest side, so they stop and
            // "listen" instead of walking away mid-chat. Only the host applies the rewards.
            targetAi.currentTask = TaskType.SOCIALIZING;
            targetAi.socializeHost = false;
            targetAi.socializeTargetId = npc.entityId;
            targetAi.taskStartTime = world.getTick();
            NPCMovementHelper.clearMoveTarget(targetRef, targetAi);
            NPCMovementHelper.playAnim(targetRef, ANIM_IDLE, "Idle", store);
        } else {
            NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(targetPos.x, myPos.y, targetPos.z));
        }
    }

    // ------------------------------------------------------------------
    // SOCIALIZING
    // ------------------------------------------------------------------

    private static void handleSocializing(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.SOCIALIZING) {
            return;
        }

        if (world.getTick() - ai.taskStartTime < SOCIALIZE_DURATION_TICKS) {
            return;
        }

        // The guest side just waits out the conversation; the host owns the outcome so the
        // rewards are not applied twice.
        if (!ai.socializeHost) {
            endSocial(ref, ai, store);
            return;
        }

        SimNPCComponent other = resolveNpc(ai.socializeTargetId, world, store);
        if (other != null) {
            applyChatOutcome(npc, other, world.getTick());
            LOGGER.debug("[SimTale] '{}' and '{}' finished chatting", npc.name, other.name);
        }

        endSocial(ref, ai, store);
    }

    /**
     * Applies the result of a finished conversation to both participants: social need,
     * mutual relationship and mood contagion.
     */
    private static void applyChatOutcome(SimNPCComponent host, SimNPCComponent guest, long tick) {
        NeedsHelper.setNeed(null, host.entityRef, NeedsHelper.SOCIAL_ID, Math.min(100f, NeedsHelper.getNeed(null, host.entityRef, NeedsHelper.SOCIAL_ID) + SOCIAL_RESTORE));
        NeedsHelper.setNeed(null, guest.entityRef, NeedsHelper.SOCIAL_ID, Math.min(100f, NeedsHelper.getNeed(null, guest.entityRef, NeedsHelper.SOCIAL_ID) + SOCIAL_RESTORE));

        boolean pleasant = bondNpcs(host, guest);
        exchangeMood(host, guest, pleasant, tick);
        exchangeMood(guest, host, pleasant, tick);
    }

    /**
     * Grows (or sours) the NPC-to-NPC relationship. Returns true when the chat went well.
     */
    private static boolean bondNpcs(SimNPCComponent host, SimNPCComponent guest) {
        if (host.entityId == null || guest.entityId == null) {
            return true;
        }

        Relationship hostView = host.getRelationship(guest.entityId);
        Relationship guestView = guest.getRelationship(host.entityId);

        // Two NPCs who dislike each other do not have a nice time.
        boolean hostile = hostView.status == RelationshipStatus.ENEMIES
                || guestView.status == RelationshipStatus.ENEMIES
                || host.personality.traits.contains(Trait.AGGRESSIVE)
                || guest.personality.traits.contains(Trait.AGGRESSIVE);

        int friendship = hostile ? -2 : 3;
        int affinity = hostile ? -3 : 4;
        int trust = hostile ? -1 : 1;

        // Shared interests give people more to talk about. Only applies to a friendly chat —
        // a common hobby does not make an argument go any better.
        if (!hostile && NPCLeisureHelper.hobbyOf(host) == NPCLeisureHelper.hobbyOf(guest)) {
            friendship += 2;
            affinity += 3;
        }

        hostView.addFriendship(friendship);
        hostView.addAffinity(affinity);
        hostView.addTrust(trust);

        guestView.addFriendship(friendship);
        guestView.addAffinity(affinity);
        guestView.addTrust(trust);

        return !hostile;
    }

    /**
     * Mood contagion: {@code listener} reacts to whatever {@code speaker} is feeling.
     */
    private static void exchangeMood(SimNPCComponent listener, SimNPCComponent speaker, boolean pleasant, long tick) {
        Mood speakerMood = speaker.getMood();

        if (!pleasant) {
            Mood reaction = listener.personality.traits.contains(Trait.AGGRESSIVE) ? Mood.ANGRY : Mood.SAD;
            listener.setEmotion(reaction, 0.5f, "argument", tick);
            return;
        }

        if (speakerMood == Mood.HAPPY || speakerMood == Mood.EXCITED) {
            // Good mood rubs off, more strongly on someone who was feeling down.
            Mood listenerMood = listener.getMood();
            boolean wasDown = listenerMood == Mood.SAD || listenerMood == Mood.BORED || listenerMood == Mood.ANGRY;
            listener.setEmotion(Mood.HAPPY, wasDown ? 0.7f : 0.4f, "good_company", tick);
        } else if (speakerMood == Mood.ANGRY) {
            listener.setEmotion(Mood.SAD, 0.4f, "bad_company", tick);
        } else {
            listener.setEmotion(Mood.HAPPY, 0.3f, "small_talk", tick);
        }
    }

    // ------------------------------------------------------------------
    // WANDERING
    // ------------------------------------------------------------------

    private static void handleWandering(
            Ref<EntityStore> ref,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.WANDERING) {
            return;
        }

        if (ai.targetBlockPosition == null) {
            stopWandering(ref, ai, store);
            return;
        }

        // wanderTimer holds the tick at which we give up on this destination.
        if (ai.wanderTimer == 0) {
            ai.wanderTimer = world.getTick() + WANDER_TIMEOUT_TICKS;
        }

        Vector3d pos = transform.getPosition();
        Vector3i target = ai.targetBlockPosition;
        double dx = (target.x + 0.5) - pos.x;
        double dz = (target.z + 0.5) - pos.z;

        if (dx * dx + dz * dz < WANDER_REACH_DISTANCE_SQ || world.getTick() >= ai.wanderTimer) {
            stopWandering(ref, ai, store);
            return;
        }

        NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(target.x + 0.5, pos.y, target.z + 0.5));
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** An NPC can only be pulled into a chat while it is not doing something important. */
    public static boolean isAvailableToTalk(RoutineAIComponent ai) {
        return ai.currentTask == TaskType.IDLE || ai.currentTask == TaskType.WANDERING;
    }

    private static SimNPCComponent resolveNpc(UUID id, World world, Store<EntityStore> store) {
        if (id == null) {
            return null;
        }
        Ref<EntityStore> otherRef = world.getEntityStore().getRefFromUUID(id);
        if (otherRef == null || !otherRef.isValid()) {
            return null;
        }
        return store.getComponent(otherRef, SimTale.SIM_NPC_COMPONENT_TYPE);
    }

    private static void abortSocial(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        endSocial(ref, ai, store);
    }

    private static void endSocial(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store) {
        ai.socializeTargetId = null;
        ai.socializeHost = false;
        ai.currentTask = TaskType.IDLE;
        ai.taskStartTime = 0;
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
    }

    private static void stopWandering(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.targetBlockPosition = null;
        ai.wanderTimer = 0;
        ai.currentTask = TaskType.IDLE;
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
    }

    /** Exposed so RoutineAISystem uses the same walk animation when it starts these tasks. */
    public static String walkAnimation() {
        return ANIM_WALK;
    }
}
