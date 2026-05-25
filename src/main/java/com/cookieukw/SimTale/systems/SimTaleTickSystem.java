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
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.logic.JobLootTable;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;

import java.util.Collection;
import java.util.List;

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

                        // Conversation Timeout Handling
                        if (npc.currentConversationPartner != null) {
                            World world = null;
                            for (World w : Universe.get().getWorlds().values()) {
                                world = w;
                                break;
                            }
                            if (world != null && currentTick > npc.conversationTimeoutTick) {
                                npc.currentConversationPartner = null;
                            }
                        }

                        // Job Handling
                        if (npc.currentJob != JobType.NONE) {
                            World world = null;
                            for (World w : Universe.get().getWorlds().values()) {
                                world = w;
                                break;
                            }
                            if (world != null) {
                                if (currentTick >= npc.jobDepartureTick && currentTick < npc.jobCompletionTick && !npc.isAway) {
                                    // DEPARTURE PHASE (5 seconds have passed)
                                    npc.isAway = true;
                                    for (PlayerRef pr : Universe.get().getPlayers()) {
                                        if (pr.getUuid().equals(npc.jobEmployer)) {
                                            pr.sendMessage(Message.raw("<" + npc.name + "> Estou saindo agora! Volto assim que terminar."));
                                        }
                                    }
                                    // TODO: Hide the NPC (teleport to waiting box, add invisibility, or detach ModelComponent)
                                } else if (currentTick >= npc.jobCompletionTick) {
                                    // RETURN AND COMPLETION PHASE
                                    if (npc.jobEmployer != null) {
                                        sendLootToPlayer(npc, world, accessor);
                                    }
                                    npc.currentJob = JobType.NONE;
                                    npc.jobEmployer = null;
                                    npc.isAway = false;
                                    // TODO: Show the NPC again (teleport back, remove invisibility, reattach ModelComponent)
                                }
                            }
                        } else {
                            // Simple autonomous interaction: 5% chance to socialize with themselves
                            if (Math.random() < 0.05) {
                                InteractionManager.performInteraction(npc, npc, InteractionType.RANDOM);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log or ignore if the accessor doesn't support getComponents or reflection fails
        }
    }

    private void sendLootToPlayer(SimNPCComponent npc, World world, ComponentAccessor<EntityStore> accessor) {
        try {
            // Find the employer in the universe's player list
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid().equals(npc.jobEmployer)) {
                    pr.sendMessage(Message.raw("<" + npc.name + "> Terminei meu trabalho de " + npc.currentJob.getPortugueseName() + "!"));
                    
                    // Hand over the items
                    List<JobLootTable.LootEntry> loots = JobLootTable.getLootForJob(npc.currentJob);
                    for (JobLootTable.LootEntry loot : loots) {
                        int qty = loot.rollQty();
                        if (qty > 0) {
                            pr.sendMessage(Message.raw(" Recebido: " + qty + "x " + loot.itemId));
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}
