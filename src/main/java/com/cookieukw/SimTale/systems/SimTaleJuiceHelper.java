package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Handles tactile and expressive visual/physical feedback for NPC interactions:
 * blushing and hearts when flirt succeeds, angry shoves with knockback physics,
 * and proximity greetings.
 */
public final class SimTaleJuiceHelper {

    private static final SimLog LOGGER = SimLog.forClass(SimTaleJuiceHelper.class);

    private static final String ANIM_BLOW_KISS = "Characters/Animations/Emote/Blow_Kiss.blockyanim";
    private static final String ANIM_WAVE = "Characters/Animations/Emote/Wave.blockyanim";
    private static final String ANIM_PUNCH_SHOVE = "Characters/Animations/Taunt/Punch.blockyanim";
    private static final String ANIM_TALK = "Characters/Animations/Expressions/Talk/Talk.blockyanim";

    private static final String FACE_CHEERFUL = "Characters/Animations/Expressions/Cheerful.blockyanim";
    private static final String FACE_SMILE = "Characters/Animations/Expressions/Smile.blockyanim";
    private static final String FACE_ANGRY = "Characters/Animations/Expressions/Angry.blockyanim";
    private static final String FACE_RAGE = "Characters/Animations/Expressions/Rage.blockyanim";
    private static final String FACE_FROWN = "Characters/Animations/Expressions/Frown.blockyanim";

    private SimTaleJuiceHelper() {
    }

    /**
     * Spawns heart particle effect above the given position.
     */
    public static void spawnHeartParticles(Vector3d pos, Store<EntityStore> store) {
        if (pos == null || store == null) return;
        Vector3d headPos = new Vector3d(pos.x, pos.y + 1.8, pos.z);
        try {
            ParticleUtil.spawnParticleEffect("NPC/Emotions/Hearts", headPos, store);
        } catch (Exception e) {
            try {
                ParticleUtil.spawnParticleEffect("Hearts", headPos, store);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Triggers the full flirt success reaction on the NPC:
     * cheerful blushing face, blow kiss emote, heart particles, emotion boost, and chat feedback.
     */
    public static void playFlirtSuccess(Ref<EntityStore> npcRef, SimNPCComponent npc, PlayerRef playerRef, Store<EntityStore> store, long tick) {
        if (npcRef == null || npc == null || store == null) return;

        // Face & Emote
        NPCMovementHelper.playAnim(npcRef, AnimationSlot.Face, FACE_CHEERFUL, "Cheerful", store);
        NPCMovementHelper.playAnim(npcRef, AnimationSlot.Action, ANIM_BLOW_KISS, "BlowKiss", store);

        // Hearts particle effect
        TransformComponent trans = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (trans != null) {
            spawnHeartParticles(trans.getPosition(), store);
        }

        // Mood sentiment
        npc.setEmotion(Mood.EXCITED, 0.85f, "flirt_blush", tick);

        // Feedback message to player
        if (playerRef != null) {
            playerRef.sendMessage(Message.translation("npc-dialogues.romantic.blush_feedback")
                    .param("name", npc.name));
        }
        LOGGER.debug("[SimTaleJuice] NPC '{}' blushed and blew kiss on flirt success", npc.name);
    }

    /**
     * Triggers the flirt rejection reaction on the NPC:
     * frown/angry face and mood drop.
     */
    public static void playFlirtReject(Ref<EntityStore> npcRef, SimNPCComponent npc, PlayerRef playerRef, Store<EntityStore> store, long tick) {
        if (npcRef == null || npc == null || store == null) return;

        NPCMovementHelper.playAnim(npcRef, AnimationSlot.Face, FACE_FROWN, "Frown", store);
        npc.setEmotion(Mood.ANGRY, 0.6f, "flirt_rejected", tick);
    }

    /**
     * Simulates an angry physical shove by the NPC against a victim (player or another NPC).
     * Plays punch/shove animation, rage face expression, and applies knockback velocity.
     */
    public static void playShove(Ref<EntityStore> attackerRef, SimNPCComponent attackerNpc,
                                 Ref<EntityStore> victimRef, PlayerRef victimPlayer,
                                 Store<EntityStore> store, float force, long tick) {
        if (attackerRef == null || store == null) return;

        // Attacker animations
        NPCMovementHelper.playAnim(attackerRef, AnimationSlot.Face, FACE_RAGE, "Rage", store);
        NPCMovementHelper.playAnim(attackerRef, AnimationSlot.Action, ANIM_PUNCH_SHOVE, "Punch", store);

        if (attackerNpc != null) {
            attackerNpc.setEmotion(Mood.ANGRY, 0.9f, "irritated", tick);
        }

        if (victimRef == null || !victimRef.isValid()) return;

        TransformComponent attackerTrans = store.getComponent(attackerRef, TransformComponent.getComponentType());
        TransformComponent victimTrans = store.getComponent(victimRef, TransformComponent.getComponentType());

        double nx = 0;
        double nz = 1;
        if (attackerTrans != null && victimTrans != null) {
            Vector3d aPos = attackerTrans.getPosition();
            Vector3d vPos = victimTrans.getPosition();
            double dx = vPos.x - aPos.x;
            double dz = vPos.z - aPos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq > 1e-4) {
                double len = Math.sqrt(distSq);
                nx = dx / len;
                nz = dz / len;
            } else {
                // If on the exact same tile, use attacker rotation forward
                float yaw = attackerTrans.getRotation().yaw();
                nx = -Math.sin(yaw);
                nz = -Math.cos(yaw);
            }
        }

        // Apply physical knockback.
        //
        // Deferred: store.ensureAndGetComponent attaches KnockbackComponent if the victim
        // doesn't already have one, which is a structural write (can move the entity between
        // archetypes). Calling that synchronously from here crashed the world thread with
        // "Store is currently processing!" — playShove runs inside RoutineAISystem's own tick,
        // the same class of bug as the entity-spawn crash elsewhere in this project, fixed the
        // same way: push it onto WorldUtil.execute so it runs after the current tick, not
        // during it.
        final Vector3d impulse = new Vector3d(nx * force, 1.4, nz * force);
        WorldUtil.execute(() -> {
            if (!victimRef.isValid()) return;
            KnockbackComponent kb = store.ensureAndGetComponent(victimRef, KnockbackComponent.getComponentType());
            if (kb != null) {
                kb.setVelocity(impulse);
                kb.setVelocityType(ChangeVelocityType.Set);
                kb.setDuration(0f);
                kb.setTimer(0f);
            }

            Velocity vel = store.getComponent(victimRef, Velocity.getComponentType());
            if (vel != null) {
                vel.addVelocity(impulse.x, impulse.y, impulse.z);
            }
        });

        // Message to victim if it is a player
        if (victimPlayer != null && attackerNpc != null) {
            victimPlayer.sendMessage(Message.translation("npc-dialogues.mean.shove_feedback")
                    .param("name", attackerNpc.name));
        }

        LOGGER.debug("[SimTaleJuice] NPC '{}' shoved entity with force {}", attackerNpc != null ? attackerNpc.name : "NPC", force);
    }

    /**
     * Plays a wave animation and smile expression on the NPC for player greeting.
     */
    public static void playGreeting(Ref<EntityStore> npcRef, Store<EntityStore> store) {
        if (npcRef == null || store == null) return;
        NPCMovementHelper.playAnim(npcRef, AnimationSlot.Face, FACE_SMILE, "Smile", store);
        NPCMovementHelper.playAnim(npcRef, AnimationSlot.Action, ANIM_WAVE, "Wave", store);
    }

    /**
     * Animation path for talk expressions during NPC-NPC conversations.
     */
    public static String animTalk() {
        return ANIM_TALK;
    }

    public static String faceSmile() {
        return FACE_SMILE;
    }

    public static String faceCheerful() {
        return FACE_CHEERFUL;
    }

    public static String faceAngry() {
        return FACE_ANGRY;
    }

    public static String faceRage() {
        return FACE_RAGE;
    }

    public static String faceFrown() {
        return FACE_FROWN;
    }
}
