package com.cookieukw.SimTale.systems;

import java.util.Objects;
import java.util.UUID;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import org.joml.Vector3i;

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
    /** Length of the conversation itself (7 seconds = 140 ticks). */
    private static final int SOCIALIZE_DURATION_TICKS = 140;
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
        handleSocializing(ref, npc, ai, transform, world, store);
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
            abortSocial(ref, ai, store, world);
            return;
        }

        // Bail out if the walk is taking too long — the partner may be unreachable.
        if (ai.taskStartTime > 0 && world.getTick() - ai.taskStartTime > SOCIALIZE_TIMEOUT_TICKS) {
            LOGGER.debug("[SimTale] NPC '{}' gave up walking to socialize", npc.name);
            abortSocial(ref, ai, store, world);
            return;
        }

        Ref<EntityStore> targetRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
        if (targetRef == null || !targetRef.isValid()) {
            abortSocial(ref, ai, store, world);
            return;
        }

        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        RoutineAIComponent targetAi = store.getComponent(targetRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        boolean isReservedForMe = targetAi != null && npc.entityId.equals(targetAi.reservedForSocialUuid);
        if (targetTransform == null || targetAi == null || (!isAvailableToTalk(targetAi) && !isReservedForMe)) {
            abortSocial(ref, ai, store, world);
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
            ai.socialTalkTimer = 0;

            // Determine topic: hostile (0) or banter (1..4)
            SimNPCComponent targetNpc = store.getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            boolean hostile = false;
            if (targetNpc != null) {
                Relationship rel = npc.getRelationship(targetNpc.entityId);
                hostile = (rel != null && rel.status == RelationshipStatus.ENEMIES)
                        || npc.personality.traits.contains(Trait.AGGRESSIVE)
                        || targetNpc.personality.traits.contains(Trait.AGGRESSIVE);
            }
            ai.socialTopic = hostile ? 0 : (int) (Math.random() * 4) + 1;

            // Pull partner into conversation and face each other
            targetAi.currentTask = TaskType.SOCIALIZING;
            targetAi.socializeHost = false;
            targetAi.socializeTargetId = npc.entityId;
            targetAi.taskStartTime = world.getTick();
            targetAi.socialTalkTimer = 0;
            targetAi.socialTopic = ai.socialTopic;
            NPCMovementHelper.clearMoveTarget(targetRef, targetAi);

            // Pin leash points so vanilla idle doesn't yank them away
            NPCEntity myNpcEntity = store.getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (myNpcEntity != null) {
                myNpcEntity.setLeashPoint(new Vector3d(myPos.x, myPos.y, myPos.z));
            }
            NPCEntity targetNpcEntity = store.getComponent(targetRef, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (targetNpcEntity != null) {
                targetNpcEntity.setLeashPoint(new Vector3d(targetPos.x, targetPos.y, targetPos.z));
            }

            // Rotate both NPCs to face each other directly
            transform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
            targetTransform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(dx, dz), 0f));

            // Initial attention expression
            String face = hostile ? SimTaleJuiceHelper.faceAngry() : SimTaleJuiceHelper.faceSmile();
            NPCMovementHelper.playAnim(ref, AnimationSlot.Face, face, "Face", store);
            NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, face, "Face", store);
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
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.SOCIALIZING) {
            return;
        }

        long elapsed = world.getTick() - ai.taskStartTime;

        // Host coordinates turn-based dialogue and animations
        if (ai.socializeHost && ai.socializeTargetId != null) {
            Ref<EntityStore> targetRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
            SimNPCComponent other = resolveNpc(ai.socializeTargetId, world, store);

            if (targetRef != null && targetRef.isValid() && other != null) {
                TransformComponent targetTrans = store.getComponent(targetRef, TransformComponent.getComponentType());
                if (transform != null && targetTrans != null) {
                    Vector3d myPos = transform.getPosition();
                    Vector3d targetPos = targetTrans.getPosition();
                    double dx = targetPos.x - myPos.x;
                    double dz = targetPos.z - myPos.z;
                    if (dx * dx + dz * dz > 1e-4) {
                        transform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
                        targetTrans.teleportRotation(new Rotation3f(0f, (float) Math.atan2(dx, dz), 0f));
                    }
                }

                boolean hostile = ai.socialTopic == 0;

                // TURN 1 (elapsed == 15): Host speaks line A, Guest listens
                if (elapsed == 15) {
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, SimTaleJuiceHelper.animTalk(), "Talk", store);
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, hostile ? SimTaleJuiceHelper.faceAngry() : SimTaleJuiceHelper.faceSmile(), "Listen", store);

                    Message msg;
                    if (hostile) {
                        msg = Message.translation("npc-dialogues.social.hostile.a").param("name", npc.name);
                    } else {
                        int topic = ai.socialTopic > 0 ? ai.socialTopic : 1;
                        msg = Message.translation("npc-dialogues.social.banter." + topic + ".a").param("name", npc.name);
                    }
                    broadcastSingleLine(msg, ref, store);
                }
                // TURN 2 (elapsed == 70): Guest replies with line B, Host listens
                else if (elapsed == 70) {
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, SimTaleJuiceHelper.animTalk(), "Talk", store);
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, hostile ? SimTaleJuiceHelper.faceAngry() : SimTaleJuiceHelper.faceSmile(), "Listen", store);

                    Message msg;
                    if (hostile) {
                        msg = Message.translation("npc-dialogues.social.hostile.b").param("name", other.name);
                    } else {
                        int topic = ai.socialTopic > 0 ? ai.socialTopic : 1;
                        msg = Message.translation("npc-dialogues.social.banter." + topic + ".b").param("name", other.name);
                    }
                    broadcastSingleLine(msg, targetRef, store);
                }
                // Wrap up speech animation before parting
                else if (elapsed == 125) {
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, hostile ? SimTaleJuiceHelper.faceAngry() : SimTaleJuiceHelper.faceSmile(), "Face", store);
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, hostile ? SimTaleJuiceHelper.faceAngry() : SimTaleJuiceHelper.faceSmile(), "Face", store);
                }
            }
        }

        if (elapsed < SOCIALIZE_DURATION_TICKS) {
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
            boolean pleasant = applyChatOutcome(npc, other, world.getTick());
            // If the chat soured into a fight, have the hostile NPC shove the other!
            if (!pleasant && other.entityRef != null && other.entityRef.isValid()) {
                SimTaleJuiceHelper.playShove(ref, npc, other.entityRef, null, store, 4.0f, world.getTick());
            }
            LOGGER.debug("[SimTale] '{}' and '{}' finished chatting (pleasant={})", npc.name, other.name, pleasant);
        }

        // Release partner NPC as well
        if (ai.socializeTargetId != null) {
            Ref<EntityStore> targetRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
            if (targetRef != null && targetRef.isValid()) {
                RoutineAIComponent targetAi = store.getComponent(targetRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                if (targetAi != null && targetAi.currentTask == TaskType.SOCIALIZING) {
                    endSocial(targetRef, targetAi, store);
                }
            }
        }

        endSocial(ref, ai, store);
    }

    /**
     * Broadcasts a single spoken line from an NPC to all players within earshot (15 blocks).
     */
    private static void broadcastSingleLine(Message msg, Ref<EntityStore> speakerRef, Store<EntityStore> store) {
        TransformComponent trans = store.getComponent(speakerRef, TransformComponent.getComponentType());
        if (trans == null) return;
        Vector3d pos = trans.getPosition();

        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef != null && pRef.isValid()) {
                TransformComponent pt = pRef.getStore().getComponent(pRef, TransformComponent.getComponentType());
                if (pt != null && pt.getPosition().distanceSquared(pos) <= 225.0) { // 15 blocks
                    pr.sendMessage(Message.raw("§e[Vila] ").insert(msg));
                }
            }
        }
    }

    /**
     * Applies the result of a finished conversation to both participants: social need,
     * mutual relationship and mood contagion.
     */
    private static boolean applyChatOutcome(SimNPCComponent host, SimNPCComponent guest, long tick) {
        NeedsHelper.setNeed(null, host.entityRef, NeedsHelper.SOCIAL_ID, Math.min(100f, NeedsHelper.getNeed(null, host.entityRef, NeedsHelper.SOCIAL_ID) + SOCIAL_RESTORE));
        NeedsHelper.setNeed(null, guest.entityRef, NeedsHelper.SOCIAL_ID, Math.min(100f, NeedsHelper.getNeed(null, guest.entityRef, NeedsHelper.SOCIAL_ID) + SOCIAL_RESTORE));

        boolean pleasant = bondNpcs(host, guest);
        exchangeMood(host, guest, pleasant, tick);
        exchangeMood(guest, host, pleasant, tick);
        return pleasant;
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
        return (ai.currentTask == TaskType.IDLE || ai.currentTask == TaskType.WANDERING)
                && ai.reservedForSocialUuid == null;
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

    private static void abortSocial(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store, World world) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        if (ai.socializeTargetId != null && world != null) {
            Ref<EntityStore> targetRef = world.getEntityStore().getRefFromUUID(ai.socializeTargetId);
            if (targetRef != null && targetRef.isValid()) {
                RoutineAIComponent targetAi = store.getComponent(targetRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                if (targetAi != null && (targetAi.currentTask == TaskType.SOCIALIZING || targetAi.reservedForSocialUuid != null)) {
                    endSocial(targetRef, targetAi, store);
                }
            }
        }
        endSocial(ref, ai, store);
    }

    private static void endSocial(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store) {
        ai.socializeTargetId = null;
        ai.reservedForSocialUuid = null;
        ai.socializeHost = false;
        ai.socialTalkTimer = 0;
        ai.currentTask = TaskType.IDLE;
        ai.taskStartTime = 0;
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
        NPCMovementHelper.playAnim(ref, AnimationSlot.Face, SimTaleJuiceHelper.faceSmile(), "Smile", store);
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
