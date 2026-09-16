package com.cookieukw.SimTale.systems;

import java.util.Objects;
import java.util.UUID;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.lifecycle.FamilyBonds;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.InteractionManager;
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
    /**
     * Safety net for the RESERVED side of a conversation, not the one walking over (that side
     * already has SOCIALIZE_TIMEOUT_TICKS). A reservation is supposed to end via the suitor
     * reaching her (-> SOCIALIZING) or giving up (-> abortSocial, which cross-clears her too) --
     * but if the suitor stops ticking before either happens (despawned, unloaded, or any other
     * case not accounted for), nothing was ever going to clear reservedForSocialUuid again, and
     * the reserved NPC would sit out every idle/work decision forever. A little longer than
     * SOCIALIZE_TIMEOUT_TICKS so the suitor's own cleanup gets first chance to run normally.
     */
    private static final int RESERVATION_STALE_TICKS = SOCIALIZE_TIMEOUT_TICKS + 100;

    /**
     * Whether {@code ai} is currently held by a live reservation (used to gate the IDLE
     * decision ladders in RoutineSleepHelpers and NPCWorkHelper). A reservation older than
     * {@link #RESERVATION_STALE_TICKS} -- or one whose timestamp doesn't make sense against the
     * current tick at all, which is what an old save loaded into a fresh session looks like --
     * is cleared right here instead of trusted, so a stuck flag self-heals the first time
     * anything checks it rather than freezing the NPC permanently.
     */
    public static boolean isReservedAndActive(RoutineAIComponent ai, long tick) {
        if (ai.reservedForSocialUuid == null) {
            return false;
        }
        if (ai.taskStartTime <= 0 || tick < ai.taskStartTime || tick - ai.taskStartTime > RESERVATION_STALE_TICKS) {
            ai.reservedForSocialUuid = null;
            return false;
        }
        return true;
    }
    /** Social need restored to both participants by a successful chat. */
    private static final float SOCIAL_RESTORE = 35f;

    /** Maximum hearing distance squared for player earshot (4 blocks = 16.0). */
    private static final double EARSHOT_DISTANCE_SQ = 16.0;

    private static final double WANDER_REACH_DISTANCE_SQ = 2.0 * 2.0;
    /** Give up on an unreachable wander destination instead of standing there forever. */
    private static final int WANDER_TIMEOUT_TICKS = 300;

    // Social topic IDs
    public static final int TOPIC_HOSTILE = 0;
    public static final int TOPIC_ROMANTIC = 1;
    public static final int TOPIC_HUNGER = 2;
    public static final int TOPIC_FATIGUE = 3;
    public static final int TOPIC_WORK_FARM = 4;
    public static final int TOPIC_WORK_WOOD = 5;
    public static final int TOPIC_WORK_GUARD = 6;
    public static final int TOPIC_WORK_FISH = 7;
    public static final int TOPIC_MOOD_HAPPY = 8;
    public static final int TOPIC_MOOD_SAD = 9;
    public static final int TOPIC_TIME_NIGHT = 10;
    public static final int TOPIC_WEATHER = 11;
    public static final int TOPIC_VILLAGE = 12;
    /** Two children talking to each other -- never reached for an adult/child mixed pair, see
     *  {@link #evaluateSocialTopic}. */
    public static final int TOPIC_CHILD_PLAY = 13;

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

    /* ------------------------------------------------------------------
    MOVING_TO_SOCIALIZE
    ------------------------------------------------------------------
    */

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
            ai.targetBlockPosition = null;
            ai.currentTask = TaskType.SOCIALIZING;
            ai.socializeHost = true;
            ai.taskStartTime = world.getTick();
            ai.socialTalkTimer = 0;

            // Context-based topic selection (topic * 10 + variant)
            SimNPCComponent targetNpc = store.getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (targetNpc != null) {
                ai.socialTopic = evaluateSocialTopic(npc, targetNpc, world);
            } else {
                ai.socialTopic = TOPIC_WEATHER * 10 + 1;
            }

            // Pull partner into conversation and face each other
            targetAi.currentTask = TaskType.SOCIALIZING;
            targetAi.targetBlockPosition = null;
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
            int topic = ai.socialTopic / 10;
            String face = getTopicExpression(topic);
            NPCMovementHelper.playAnim(ref, AnimationSlot.Face, face, "Face", store);
            NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, face, "Face", store);

            if (topic == TOPIC_ROMANTIC) {
                SimTaleJuiceHelper.spawnHeartParticles(myPos, store);
                SimTaleJuiceHelper.spawnHeartParticles(targetPos, store);
            }
        } else {
            double distSq = dx * dx + dz * dz;
            if (distSq > 1e-4) {
                double dist = Math.sqrt(distSq);
                double stopDist = 1.5;
                double approachX = targetPos.x - (dx / dist) * stopDist;
                double approachZ = targetPos.z - (dz / dist) * stopDist;
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(approachX, myPos.y, approachZ));
            }
        }
    }

    /* ------------------------------------------------------------------
    SOCIALIZING
    ------------------------------------------------------------------
    */

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
                    double d2 = dx * dx + dz * dz;
                    // Only re-align rotation on speech turns if NPCs are at a stable distance (> 0.25 blocks)
                    if (d2 > 0.25 && (elapsed == 15 || elapsed == 70)) {
                        transform.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
                        targetTrans.teleportRotation(new Rotation3f(0f, (float) Math.atan2(dx, dz), 0f));
                    }
                }

                int topic = ai.socialTopic / 10;
                int variant = Math.max(1, ai.socialTopic % 10);
                String topicKey = getTopicKey(topic);
                String face = getTopicExpression(topic);

                // TURN 1 (elapsed == 15): Host speaks line A, Guest listens
                if (elapsed == 15) {
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, SimTaleJuiceHelper.animTalk(), "Talk", store);
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, face, "Listen", store);

                    if (transform != null && isPlayerWithinEarshot(transform.getPosition())) {
                        Message msg = Message.translation("npc-dialogues." + topicKey + "." + variant + ".a")
                                .param("name", npc.name);
                        broadcastSingleLine(msg, ref, store);
                    }
                }
                // TURN 2 (elapsed == 70): Guest replies with line B, Host listens
                else if (elapsed == 70) {
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, SimTaleJuiceHelper.animTalk(), "Talk", store);
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, face, "Listen", store);

                    if (targetTrans != null && isPlayerWithinEarshot(targetTrans.getPosition())) {
                        Message msg = Message.translation("npc-dialogues." + topicKey + "." + variant + ".b")
                                .param("name", other.name);
                        broadcastSingleLine(msg, targetRef, store);
                    }
                }
                // Wrap up speech animation before parting
                else if (elapsed == 125) {
                    NPCMovementHelper.playAnim(ref, AnimationSlot.Face, face, "Face", store);
                    NPCMovementHelper.playAnim(targetRef, AnimationSlot.Face, face, "Face", store);
                }
            }
        }

        if (elapsed < SOCIALIZE_DURATION_TICKS) {
            return;
        }

        /* The guest side just waits out the conversation; the host owns the outcome so the
        rewards are not applied twice.
        */
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
     * Context-aware evaluation to select a meaningful dialogue topic based on relationship,
     * critical needs, mood, profession, and time of day.
     *
     * @return encoded int: {@code topic * 10 + variant}
     */
    public static int evaluateSocialTopic(SimNPCComponent host, SimNPCComponent guest, World world) {
        Relationship hostView = host.getRelationship(guest.entityId);
        Relationship guestView = guest.getRelationship(host.entityId);

        /* 0. Children get their own topic pool first. Without this, a child could be scored
        straight into TOPIC_ROMANTIC or TOPIC_HOSTILE below by whatever relationship/trait
        state the adults' scoring cares about -- neither belongs in a kid's conversation.
        Two children together get a dedicated play topic; a child paired with an adult (the
        mixed case) just has those two adult-only topics taken off the table and falls
        through to whichever age-neutral one below scores highest (weather, village, mood, a
        parent's own work) -- those already read fine from either side of the conversation.
        */
        boolean hostChild = InteractionManager.isNpcAChild(host);
        boolean guestChild = InteractionManager.isNpcAChild(guest);
        if (hostChild && guestChild) {
            int variant = (int) (Math.random() * 2) + 1;
            return TOPIC_CHILD_PLAY * 10 + variant;
        }
        boolean childInvolved = hostChild || guestChild;

        // 1. Hostile priority
        boolean hostile = !childInvolved && (
                (hostView != null && hostView.status == RelationshipStatus.ENEMIES)
                || (guestView != null && guestView.status == RelationshipStatus.ENEMIES)
                || host.personality.traits.contains(Trait.AGGRESSIVE)
                || guest.personality.traits.contains(Trait.AGGRESSIVE));
        if (hostile) {
            int variant = (int) (Math.random() * 2) + 1;
            return TOPIC_HOSTILE * 10 + variant;
        }

        // 2. Romantic priority (3 variants here, not the usual 2 -- couples asked for more).
        boolean romantic = !childInvolved && (isRomantic(hostView) || isRomantic(guestView));
        if (romantic) {
            int variant = (int) (Math.random() * 3) + 1;
            return TOPIC_ROMANTIC * 10 + variant;
        }

        // 3. Needs & Context scoring
        int bestTopic = TOPIC_WEATHER;
        int highestScore = 20;

        // Hunger (< 40)
        float hostHunger = NeedsHelper.getNeed(null, host.entityRef, NeedsHelper.HUNGER_ID);
        float guestHunger = NeedsHelper.getNeed(null, guest.entityRef, NeedsHelper.HUNGER_ID);
        if (hostHunger < 40f || guestHunger < 40f) {
            int score = (int) (100 - Math.min(hostHunger, guestHunger));
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_HUNGER;
            }
        }

        // Energy / Fatigue (< 40)
        float hostEnergy = NeedsHelper.getNeed(null, host.entityRef, NeedsHelper.ENERGY_ID);
        float guestEnergy = NeedsHelper.getNeed(null, guest.entityRef, NeedsHelper.ENERGY_ID);
        if (hostEnergy < 40f || guestEnergy < 40f) {
            int score = (int) (100 - Math.min(hostEnergy, guestEnergy));
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_FATIGUE;
            }
        }

        // Mood
        Mood hMood = host.getMood();
        Mood gMood = guest.getMood();
        if (hMood == Mood.SAD || gMood == Mood.SAD) {
            if (45 > highestScore) {
                highestScore = 45;
                bestTopic = TOPIC_MOOD_SAD;
            }
        } else if (hMood == Mood.HAPPY || hMood == Mood.EXCITED || gMood == Mood.HAPPY || gMood == Mood.EXCITED) {
            if (40 > highestScore) {
                highestScore = 40;
                bestTopic = TOPIC_MOOD_HAPPY;
            }
        }

        // Profession
        Profession hProf = host.profession;
        Profession gProf = guest.profession;
        if (hProf == Profession.FARMER || gProf == Profession.FARMER) {
            int score = (hProf == gProf) ? 55 : 35;
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_WORK_FARM;
            }
        }
        if (hProf == Profession.LUMBERJACK || gProf == Profession.LUMBERJACK) {
            int score = (hProf == gProf) ? 55 : 35;
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_WORK_WOOD;
            }
        }
        if (hProf == Profession.GUARD || gProf == Profession.GUARD) {
            int score = (hProf == gProf) ? 55 : 35;
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_WORK_GUARD;
            }
        }
        if (hProf == Profession.FISHERMAN || gProf == Profession.FISHERMAN) {
            int score = (hProf == gProf) ? 55 : 35;
            if (score > highestScore) {
                highestScore = score;
                bestTopic = TOPIC_WORK_FISH;
            }
        }

        // Time of day: Night
        if (world != null && NPCSleepHelper.isNight(world)) {
            if (50 > highestScore) {
                highestScore = 50;
                bestTopic = TOPIC_TIME_NIGHT;
            }
        }

        if (highestScore <= 30) {
            bestTopic = Math.random() < 0.5 ? TOPIC_WEATHER : TOPIC_VILLAGE;
        }

        int variant = (int) (Math.random() * 2) + 1;
        return bestTopic * 10 + variant;
    }

    private static String getTopicKey(int topic) {
        return switch (topic) {
            case TOPIC_HOSTILE -> "social.hostile";
            case TOPIC_ROMANTIC -> "social.romantic";
            case TOPIC_HUNGER -> "social.hunger";
            case TOPIC_FATIGUE -> "social.fatigue";
            case TOPIC_WORK_FARM -> "social.work.farm";
            case TOPIC_WORK_WOOD -> "social.work.wood";
            case TOPIC_WORK_GUARD -> "social.work.guard";
            case TOPIC_WORK_FISH -> "social.work.fish";
            case TOPIC_MOOD_HAPPY -> "social.mood.happy";
            case TOPIC_MOOD_SAD -> "social.mood.sad";
            case TOPIC_TIME_NIGHT -> "social.night";
            case TOPIC_VILLAGE -> "social.village";
            case TOPIC_CHILD_PLAY -> "social.child_play";
            default -> "social.weather";
        };
    }

    private static String getTopicExpression(int topic) {
        return switch (topic) {
            case TOPIC_HOSTILE -> SimTaleJuiceHelper.faceAngry();
            case TOPIC_ROMANTIC -> SimTaleJuiceHelper.faceCheerful();
            case TOPIC_MOOD_SAD, TOPIC_FATIGUE -> SimTaleJuiceHelper.faceFrown();
            default -> SimTaleJuiceHelper.faceSmile();
        };
    }

    private static boolean isRomantic(Relationship rel) {
        if (rel == null || rel.status == null) return false;
        return rel.status == RelationshipStatus.MARRIED
                || rel.status == RelationshipStatus.ENGAGED
                || rel.status == RelationshipStatus.PARTNER
                || rel.status == RelationshipStatus.DATING
                || rel.status == RelationshipStatus.CRUSH;
    }

    /**
     * Checks whether at least one player is within earshot (4 blocks) of the position.
     */
    private static boolean isPlayerWithinEarshot(Vector3d pos) {
        if (pos == null) return false;
        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef != null && pRef.isValid()) {
                TransformComponent pt = pRef.getStore().getComponent(pRef, TransformComponent.getComponentType());
                if (pt != null && pt.getPosition().distanceSquared(pos) <= EARSHOT_DISTANCE_SQ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Broadcasts a single spoken line from an NPC to all players within earshot (4 blocks).
     */
    private static void broadcastSingleLine(Message msg, Ref<EntityStore> speakerRef, Store<EntityStore> store) {
        TransformComponent trans = store.getComponent(speakerRef, TransformComponent.getComponentType());
        if (trans == null) return;
        Vector3d pos = trans.getPosition();

        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef != null && pRef.isValid()) {
                TransformComponent pt = pRef.getStore().getComponent(pRef, TransformComponent.getComponentType());
                if (pt != null && pt.getPosition().distanceSquared(pos) <= EARSHOT_DISTANCE_SQ) { // 4 blocks
                    pr.sendMessage(Message.raw("[Vila] ").insert(msg));
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
        if (pleasant) {
            tryCourtship(host, guest);
        }
        return pleasant;
    }

    /** Romance a pleasant chat can build between two eligible adult NPCs, and grow their own family. */
    private static final int NPC_ROMANCE_GAIN = 2;

    /** Same bar the player's own wedding-ring proposal already uses ({@code handleMarriageProposal}). */
    private static final int NPC_MARRIAGE_ROMANCE_THRESHOLD = 80;
    private static final int NPC_MARRIAGE_FRIENDSHIP_THRESHOLD = 70;

    /**
     * Lets two NPCs who just had a nice chat fall for each other, and — once the bond is strong
     * enough — marry each other, entirely on their own. No player involved anywhere in this path.
     * <p>
     * Deliberately narrow about who is eligible: no minors ({@link InteractionManager#isNpcAChild}
     * also covers teens, not just small children), no close family
     * ({@link FamilyBonds#areCloseFamily}, which the strong platonic bond {@link FamilyBonds}
     * itself gives parents/siblings would otherwise read as a great match), and nobody already
     * married to a third party — this does not model affairs.
     */
    private static void tryCourtship(SimNPCComponent host, SimNPCComponent guest) {
        if (host.entityId == null || guest.entityId == null) return;
        if (InteractionManager.isNpcAChild(host) || InteractionManager.isNpcAChild(guest)) return;
        if (FamilyBonds.areCloseFamily(host, guest)) return;

        boolean hostSpokenFor = host.family.isMarried && !guest.entityId.equals(host.family.spouseId);
        boolean guestSpokenFor = guest.family.isMarried && !host.entityId.equals(guest.family.spouseId);
        if (hostSpokenFor || guestSpokenFor) return;

        Relationship hostView = host.getRelationship(guest.entityId);
        Relationship guestView = guest.getRelationship(host.entityId);

        /* Already married to each other: nothing left to propose, but still worth reinforcing —
        the daily natural-pregnancy roll in SimTaleTickSystem reads this same romance value.
        */
        boolean alreadyToEachOther = host.family.isMarried && guest.entityId.equals(host.family.spouseId);

        hostView.addRomance(NPC_ROMANCE_GAIN);
        guestView.addRomance(NPC_ROMANCE_GAIN);

        if (alreadyToEachOther) return;
        if (host.family.isMarried || guest.family.isMarried) return;

        if (hostView.romance >= NPC_MARRIAGE_ROMANCE_THRESHOLD && guestView.romance >= NPC_MARRIAGE_ROMANCE_THRESHOLD
                && hostView.friendship >= NPC_MARRIAGE_FRIENDSHIP_THRESHOLD && guestView.friendship >= NPC_MARRIAGE_FRIENDSHIP_THRESHOLD) {
            host.family.marry(guest.entityId, null);
            guest.family.marry(host.entityId, null);
            hostView.status = RelationshipStatus.MARRIED;
            guestView.status = RelationshipStatus.MARRIED;
            SimNPCPersistence.saveNPC(host);
            SimNPCPersistence.saveNPC(guest);
            LOGGER.info("[SimTale] '{}' e '{}' se casaram por conta propria", host.name, guest.name);

            if (host.entityRef != null && host.entityRef.isValid()) {
                Message announce = Message.translation("npc-dialogues.npc_marriage.announce")
                        .param("name", host.name).param("partner", guest.name);
                broadcastSingleLine(announce, host.entityRef, host.entityRef.getStore());
            }
        }
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

        /* Shared interests give people more to talk about. Only applies to a friendly chat —
        a common hobby does not make an argument go any better.
        */
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

    /* ------------------------------------------------------------------
    WANDERING
    ------------------------------------------------------------------
    */

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

    /* ------------------------------------------------------------------
    Shared helpers
    ------------------------------------------------------------------
    */

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
        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.targetBlockPosition = null;
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
