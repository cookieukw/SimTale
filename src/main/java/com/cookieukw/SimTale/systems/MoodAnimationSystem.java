package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Mood;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;

import javax.annotation.Nonnull;

public class MoodAnimationSystem extends EntityTickingSystem<EntityStore> {

    /**
     * Expression variation pools per mood.
     */
    private static final String[] HAPPY_EXPRESSIONS = {"Smile", "Cheerful", "Smirk"};
    private static final String[] EXCITED_EXPRESSIONS = {"Grin", "Cheerful", "Smile"};
    private static final String[] ANGRY_EXPRESSIONS = {"Angry", "Frown"};
    private static final String[] BORED_EXPRESSIONS = {"Smirk", null};

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    private static String selectExpression(Mood mood, float intensity, int seed) {
        if (mood == null) return null;
        return switch (mood) {
            case HAPPY -> HAPPY_EXPRESSIONS[Math.abs(seed) % HAPPY_EXPRESSIONS.length];
            case EXCITED -> EXCITED_EXPRESSIONS[Math.abs(seed) % EXCITED_EXPRESSIONS.length];
            case ANGRY -> {
                if (intensity >= 0.7f) yield "Rage";
                yield ANGRY_EXPRESSIONS[Math.abs(seed) % ANGRY_EXPRESSIONS.length];
            }
            case SAD, SLEEPY -> "Frown";
            case SCARED -> "Surprised";
            case BORED -> BORED_EXPRESSIONS[Math.abs(seed) % BORED_EXPRESSIONS.length];
            default -> null;
        };
    }

    @Override
    @SuppressWarnings({ "null" })
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;

        RoutineAIComponent ai = chunk.getComponent(index, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai != null) {
            if (ai.currentTask == TaskType.SLEEPING || ai.currentTask == TaskType.ENTERING_BED
                    || ai.currentTask == TaskType.DYING || ai.currentTask == TaskType.DEAD
                    || ai.currentTask == TaskType.SOCIALIZING) {
                return;
            }
        }
        
        Mood currentMood = npc.getMood();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        
        ActiveAnimationComponent animComp = chunk.getComponent(index, ActiveAnimationComponent.getComponentType());
        if (animComp == null) return;

        int npcHash = (npc.entityId != null) ? npc.entityId.hashCode() : index;
        int seed = npcHash + (npc.expressionAge / 100);
        String animName = selectExpression(currentMood, npc.emotionIntensity, seed);
        
        AnimationSlot slotToUse = AnimationSlot.Face;

        // Determine if the visual face expression needs to be sent to the client
        boolean expressionChanged = (npc.lastPlayedEmotion != currentMood);

        // Special transition handling for ANGRY intensity changes (Angry <=> Rage)
        if (currentMood == Mood.ANGRY) {
            String currentPlaying = animComp.getActiveAnimations()[slotToUse.ordinal()];
            if (currentPlaying == null || !currentPlaying.equals(animName)) {
                expressionChanged = true;
            }
        }

        /* Replay interval between 18s and 28s (360-560 ticks), organically staggered per NPC.
        Avoids rapid 8s mouth open-and-close spam so facial reactions feel natural and varied.
        */
        int replayInterval = 360 + (Math.abs(npcHash) % 200);
        npc.expressionAge++;
        if (animName != null && npc.expressionAge >= replayInterval) {
            expressionChanged = true;
        }

        if (expressionChanged) {
            npc.lastPlayedEmotion = currentMood;
            npc.expressionAge = 0;

            if (animName != null) {
                animComp.getActiveAnimations()[slotToUse.ordinal()] = animName;
                animComp.setPlayingAnimation(slotToUse, animName);
                AnimationUtils.playAnimation(ref, slotToUse, animName, store);
                commandBuffer.replaceComponent(ref, ActiveAnimationComponent.getComponentType(), animComp);
            } else {
                // Clear the Face slot animation
                animComp.getActiveAnimations()[slotToUse.ordinal()] = null;
                AnimationUtils.playAnimation(ref, slotToUse, null, store);
                commandBuffer.replaceComponent(ref, ActiveAnimationComponent.getComponentType(), animComp);
            }
        }
    }
}
