package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookieukw.SimTale.logic.InteractionType;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.logic.JobLootTable;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import org.joml.Vector3d;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

public class SimTaleTickSystem extends EntityTickingSystem<EntityStore> {

    @Override
    @Nonnull
    
    public Query<EntityStore> getQuery() {
        return (Query<EntityStore>) (Object) UUIDComponent.getComponentType();
    }

    @Override
    @SuppressWarnings({ "null" })
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long absoluteTick = world.getTick();
        
        if (npc == null) {
            UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                SimNPCData data = Caskara.load(uuidComp.getUuid().toString(), SimNPCData.class);
                if (data != null) {
                    HytaleLogger.forEnclosingClass().atInfo().log("SimTale: NPC " + data.name + " remontado ao entrar no mundo/carregar chunk!");
                    npc = new SimNPCComponent(uuidComp.getUuid(), data.name);
                    
                    Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(uuidComp.getUuid());
                    npc.entityRef = entityRef;
                    
                    SimNPCPersistence.loadNPC(npc);
                    
                    if (entityRef != null) {
                        commandBuffer.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    }
                    
                    final UUID targetId = uuidComp.getUuid();
                    SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId));
                    SimTale.ACTIVE_NPCS.add(npc);
                }
            }
            return;
        }

        if (!SimTale.ACTIVE_NPCS.contains(npc)) {
            final UUID targetId2 = npc.entityId;
            SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId2));
            SimNPCPersistence.loadNPC(npc);
            SimTale.ACTIVE_NPCS.add(npc);
        }

        if (absoluteTick % 600 == 0) {
            SimNPCPersistence.saveNPC(npc);
        }

        npc.needs.tickDecay();

        if (npc.currentConversationPartner != null) {
            if (absoluteTick > npc.conversationTimeoutTick) {
                npc.currentConversationPartner = null;
            }
        }

        if (npc.currentJob != JobType.NONE) {
            if (absoluteTick >= npc.jobDepartureTick && absoluteTick < npc.jobCompletionTick && !npc.isAway) {
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
                        npcTransform.setPosition(new Vector3d(0, 1000, 0));
                    }
                }
            } else if (absoluteTick >= npc.jobCompletionTick) {
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
            if (Math.random() < 0.05) {
                InteractionManager.performInteraction(npc, npc.entityId, null, InteractionType.RANDOM);
            }
        }
    }

    private void sendLootToPlayer(SimNPCComponent npc, World world, ComponentAccessor<EntityStore> accessor) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid().equals(npc.jobEmployer)) {
                    pr.sendMessage(Message.raw("<" + npc.name + "> Terminei meu trabalho de " + npc.currentJob.getPortugueseName() + "!"));
                    
                    List<JobLootTable.LootEntry> loots = JobLootTable.getLootForJob(npc.currentJob);
                    for (JobLootTable.LootEntry loot : loots) {
                        int qty = loot.rollQty();
                        if (qty > 0) {
                            pr.sendMessage(Message.raw("<" + npc.name + "> Coletou: " + qty + "x " + loot.itemId));
                            try {
                                CommandManager.get().handleCommand(pr, "give " + pr.getUsername() + " " + loot.itemId + " --quantity=" + qty);
                            } catch (Exception cmdEx) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}


    
    
        
    
                            

                    