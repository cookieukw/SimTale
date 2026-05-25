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
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import java.util.Collection;
import java.util.List;

/**
 * Core ticking system for SimTale.
 * Handles needs decay and autonomous NPC logic.
 */
public class SimTaleTickSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return (Query<EntityStore>) (Object) SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }

        if (world == null) return;
        long absoluteTick = world.getTick();

        // Run logic only once per second (every 20 ticks) to save performance
        if (absoluteTick % 20 != 0) return;

        // Auto-register NPCs loaded from world save
        boolean found = false;
        for (SimNPCComponent active : SimTale.ACTIVE_NPCS) {
            if (active.entityId != null && active.entityId.equals(npc.entityId)) {
                found = true;
                break;
            }
        }
        if (!found) {
            // Load memory state from Caskara database when spawning/restarting
            SimNPCPersistence.loadNPC(npc);
            SimTale.ACTIVE_NPCS.add(npc);
        }

        // Auto-save logic (every 30 seconds = 600 ticks)
        if (absoluteTick % 600 == 0) {
            SimNPCPersistence.saveNPC(npc);
        }

        npc.needs.tickDecay();

        // Conversation Timeout Handling
        if (npc.currentConversationPartner != null) {
            if (absoluteTick > npc.conversationTimeoutTick) {
                npc.currentConversationPartner = null;
            }
        }

        // Job Handling
        if (npc.currentJob != JobType.NONE) {
            if (absoluteTick >= npc.jobDepartureTick && absoluteTick < npc.jobCompletionTick && !npc.isAway) {
                // DEPARTURE PHASE (5 seconds have passed)
                npc.isAway = true;
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr.getUuid().equals(npc.jobEmployer)) {
                        pr.sendMessage(Message.raw("<" + npc.name + "> Estou saindo agora! Volto assim que terminar."));
                    }
                }
                Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (ref != null) {
                    ComponentAccessor<EntityStore> accessor = (ComponentAccessor<EntityStore>) store;
                    TransformComponent npcTransform = accessor.getComponent(ref, TransformComponent.getComponentType());
                    if (npcTransform != null) {
                        // Teleport to the sky so the client naturally unloads them (and bypasses -32 limit)
                        npcTransform.setPosition(new Vector3d(0, 1000, 0));
                    }
                }
            } else if (absoluteTick >= npc.jobCompletionTick) {
                // RETURN AND COMPLETION PHASE
                ComponentAccessor<EntityStore> accessor = (ComponentAccessor<EntityStore>) store;
                if (npc.jobEmployer != null) {
                    sendLootToPlayer(npc, world, accessor);
                }
                
                Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (ref != null) {
                    if (npc.jobEmployer != null) {
                        Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(npc.jobEmployer);
                        if (playerRef != null) {
                            TransformComponent playerTransform = accessor.getComponent(playerRef, TransformComponent.getComponentType());
                            TransformComponent npcTransform = accessor.getComponent(ref, TransformComponent.getComponentType());
                            if (playerTransform != null && npcTransform != null) {
                                // Teleport slightly offset from the player to prevent taking accidental damage
                                npcTransform.setPosition(new Vector3d(
                                    playerTransform.getPosition().x + 2, 
                                    playerTransform.getPosition().y, 
                                    playerTransform.getPosition().z + 2
                                ));
                            }
                        }
                    }
                }

                npc.currentJob = JobType.NONE;
                npc.jobEmployer = null;
                npc.isAway = false;
            }
        } else {
            // Simple autonomous interaction: 5% chance to socialize with themselves
            if (Math.random() < 0.05) {
                InteractionManager.performInteraction(npc, npc, InteractionType.RANDOM);
            }
        }
    }

    private void sendLootToPlayer(SimNPCComponent npc, World world, ComponentAccessor accessor) {
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
                            pr.sendMessage(Message.raw("<" + npc.name + "> Coletou: " + qty + "x " + loot.itemId));
                            try {
                                com.hypixel.hytale.server.core.command.system.CommandManager.get().handleCommand(pr, "give " + pr.getUsername() + " " + loot.itemId + " --quantity=" + qty);
                            } catch (Exception cmdEx) {
                                // Ignore failure if item doesn't exist
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}


    
    
        
        // 
    
                            

                    