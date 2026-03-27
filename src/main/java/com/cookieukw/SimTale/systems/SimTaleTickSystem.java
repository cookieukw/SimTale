package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookieukw.SimTale.logic.InteractionType;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;

/**
 * Core ticking system for SimTale.
 * Handles needs decay and autonomous NPC logic.
 */
public class SimTaleTickSystem extends TickingSystem<EntityStore> {

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void tick(float deltaTime, int currentTick, Store<EntityStore> store) {
        // Run every 20 ticks (1 second) to save performance
        if (currentTick % 20 != 0)
            return;

        // Use raw types and reflection-like pattern to bypass compiler issues
        try {
            ComponentAccessor accessor = (ComponentAccessor) store;
            // Explicitly cast to the generic method signature expected by the compiler
            Collection npcs = (Collection) accessor.getClass().getMethod("getComponents", ComponentType.class)
                    .invoke(accessor, SimTale.SIM_NPC_COMPONENT_TYPE);

            if (npcs != null) {
                for (Object obj : npcs) {
                    if (obj instanceof SimNPCComponent) {
                        SimNPCComponent npc = (SimNPCComponent) obj;
                        npc.needs.tickDecay();

                        // Simple autonomous interaction: 5% chance to socialize with themselves (for
                        // stat gain)
                        if (Math.random() < 0.05) {
                            InteractionManager.performInteraction(npc, npc, InteractionType.RANDOM);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log or ignore if the accessor doesn't support getComponents or reflection
            // fails
        }
    }
}
