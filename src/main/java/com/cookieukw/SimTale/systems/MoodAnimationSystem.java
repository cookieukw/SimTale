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
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.Universe;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Mood;

import javax.annotation.Nonnull;

public class MoodAnimationSystem extends EntityTickingSystem<EntityStore> {

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
                animPath = "Characters/Animations/Expressions/Smirk.blockyanim";
                animName = "Smirk";
                break;
            case NEUTRAL:
            default:
                break;
        }
        
        AnimationSlot slotToUse = AnimationSlot.Face;
        
        // Determine if the visual face expression needs to be sent to the client
        boolean expressionChanged = (npc.lastPlayedEmotion != currentMood);
        
        // Special transition handling for ANGRY intensity changes (Angry <=> Rage)
        if (currentMood == Mood.ANGRY && animName != null) {
            String currentPlaying = animComp.getActiveAnimations()[slotToUse.ordinal()];
            if (currentPlaying == null || !currentPlaying.equals(animName)) {
                expressionChanged = true;
            }
        }
        
        if (expressionChanged) {
            npc.lastPlayedEmotion = currentMood;
            
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

        // Apply HeadRotation effects based on active emotion (runs dynamically per tick for movement/shudders)
        HeadRotation headRot = chunk.getComponent(index, HeadRotation.getComponentType());
        if (headRot != null) {
            float targetPitch = 0f;
            float targetYaw = headRot.getRotation().yaw(); // keep current head yaw
            float targetRoll = 0f;

            World world = Universe.get().getWorlds().values().stream().findFirst().orElse(null);
            long tick = (world != null) ? world.getTick() : 0;

            switch (npc.activeEmotion) {
                case SAD:
                case SLEEPY:
                    // Look down sadly or sleepily
                    targetPitch = -0.3f;
                    break;
                case BORED:
                    // Look down slightly and drift gaze slowly left/right
                    targetPitch = -0.1f;
                    targetYaw += (float) Math.sin(tick * 0.05f) * 0.3f;
                    break;
                case HAPPY:
                case EXCITED:
                    // Gaze up slightly with positive energy
                    targetPitch = 0.1f + (float) Math.sin(tick * 0.08f) * 0.05f;
                    break;
                case ANGRY:
                    // Stare rigidly straight forward
                    targetPitch = 0.0f;
                    break;
                case SCARED:
                    // Fast nervous shudder/twitch
                    targetYaw += (float) Math.sin(tick * 0.6f) * 0.15f;
                    targetPitch = (float) Math.cos(tick * 0.6f) * 0.1f;
                    break;
                default:
                    break;
            }

            Rotation3f newRot = new Rotation3f(targetPitch, targetYaw, targetRoll);
            headRot.setRotation(newRot);
            commandBuffer.replaceComponent(ref, HeadRotation.getComponentType(), headRot);
        }
    }
}
