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

import javax.annotation.Nonnull;

public class MoodAnimationSystem extends EntityTickingSystem<EntityStore> {

    /**
     * How often a held mood shows its face again: 8 s.
     *
     * <p>Long enough that the NPC is not permanently mugging at the camera, short enough that
     * walking past a happy villager reliably catches a smile. Counted in ticks rather than from
     * {@code dt}, because the tick rate is a known 20/s and the unit of {@code dt} is not.
     */
    private static final int REPLAY_INTERVAL_TICKS = 160;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    @SuppressWarnings({ "null" })
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;
        
        Mood currentMood = npc.getMood();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        
        ActiveAnimationComponent animComp = chunk.getComponent(index, ActiveAnimationComponent.getComponentType());
        if (animComp == null) return;
        
        String animPath = null;
        String animName = null;
        
        switch (currentMood) {
            case HAPPY:
                animPath = "Characters/Animations/Expressions/Smile.blockyanim";
                animName = "Smile";
                break;
            case ANGRY:
                if (npc.emotionIntensity >= 0.7f) {
                    animPath = "Characters/Animations/Expressions/Rage.blockyanim";
                    animName = "Rage";
                } else {
                    animPath = "Characters/Animations/Expressions/Angry.blockyanim";
                    animName = "Angry";
                }
                break;
            case SAD:
            case SLEEPY:
                animPath = "Characters/Animations/Expressions/Frown.blockyanim";
                animName = "Frown";
                break;
            case SCARED:
                animPath = "Characters/Animations/Expressions/Suprised.blockyanim";
                animName = "Surprised";
                break;
            case EXCITED:
                animPath = "Characters/Animations/Expressions/Grin.blockyanim";
                animName = "Grin";
                break;
            case BORED:
                // Neutral face is best for boredom to avoid weird smiles
                break;
            case NEUTRAL:
            default:
                break;
        }
        
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

        /* Replaying on a timer is what keeps the face alive.

        These clips are one-shot: they play, they end, and the head goes back to neutral. Firing
        only on change meant a mood that stayed put showed its face once and never again — an NPC
        could be HAPPY for ten minutes wearing a blank stare. It used to hide behind the fast
        emotion decay, which dropped moods to NEUTRAL every few seconds and re-triggered the
        animation by accident; slowing the decay removed the accident and exposed this.
        */
        npc.expressionAge++;
        if (animName != null && npc.expressionAge >= REPLAY_INTERVAL_TICKS) {
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
