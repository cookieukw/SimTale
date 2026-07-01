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

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return (Query<EntityStore>) SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    @SuppressWarnings({ "null" })
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null || npc.getMood() == null) return;
        
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        
        ActiveAnimationComponent animComp = chunk.getComponent(index, ActiveAnimationComponent.getComponentType());
        if (animComp == null) return;
        
        String animPath = null;
        String animName = null;
        
        switch (npc.getMood()) {
            case HAPPY:
                animPath = "Characters/Animations/Expressions/Smile.blockyanim";
                animName = "Smile";
                break;
            case ANGRY:
                animPath = "Characters/Animations/Expressions/Angry.blockyanim";
                animName = "Angry";
                break;
            case SAD:
            case SLEEPY:
                animPath = "Characters/Animations/Expressions/Frown.blockyanim";
                animName = "Frown";
                break;
            case SCARED:
                animPath = "Characters/Animations/Expressions/Surprised.blockyanim"; // Typo in CharacterCreator: Suprised.blockyanim
                animPath = "Characters/Animations/Expressions/Suprised.blockyanim";
                animName = "Surprised";
                break;
            case EXCITED:
                animPath = "Characters/Animations/Expressions/Cheerful.blockyanim";
                animName = "Cheerful";
                break;
            case NEUTRAL:
            default:
                break;
        }
        
        // Let's use AnimationSlot.valueOf("Expression") or fallback to Action
        AnimationSlot slotToUse = AnimationSlot.Action;
        try {
            slotToUse = AnimationSlot.valueOf("Expression");
        } catch (IllegalArgumentException e) {
            try {
                slotToUse = AnimationSlot.valueOf("Face");
            } catch (IllegalArgumentException e2) {
                // Ignore
            }
        }
        
        String currentAnim = animComp.getActiveAnimations()[slotToUse.ordinal()];
        
        if (animName != null) {
            if (currentAnim == null || !currentAnim.equals(animName)) {
                animComp.getActiveAnimations()[slotToUse.ordinal()] = animName;
                animComp.setPlayingAnimation(slotToUse, animName);
                AnimationUtils.playAnimation(ref, slotToUse, animPath, animName, store);
                commandBuffer.replaceComponent(ref, ActiveAnimationComponent.getComponentType(), animComp);
            }
        } else {
            // Stop animation if it's playing a mood animation
            if (currentAnim != null && (currentAnim.equals("Smile") || currentAnim.equals("Angry") || currentAnim.equals("Frown") || currentAnim.equals("Surprised") || currentAnim.equals("Cheerful"))) {
                animComp.getActiveAnimations()[slotToUse.ordinal()] = null;
                // We'd stop the animation here, but Hytale API for stopAnimation is not immediately clear.
                // Just clear it from the slot
                commandBuffer.replaceComponent(ref, ActiveAnimationComponent.getComponentType(), animComp);
            }
        }
    }
}
