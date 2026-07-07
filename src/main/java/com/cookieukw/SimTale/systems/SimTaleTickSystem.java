package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookieukw.SimTale.logic.InteractionType;
import com.cookieukw.SimTale.logic.JobLootTable;
import com.cookieukw.SimTale.logic.JobType;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public class SimTaleTickSystem extends EntityTickingSystem<EntityStore> {

    @NullableDecl
    @Override

    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        NPCEntity npcEntity = chunk.getComponent(index, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity == null || npcEntity.getRoleName() == null || !npcEntity.getRoleName().startsWith("SimTale_")) {
            return;
        }
        
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
                UUID uuid = uuidComp.getUuid();
                SimNPCData data = Caskara.load(uuid.toString(), SimNPCData.class);
                if (data != null) {
                    HytaleLogger.forEnclosingClass().atInfo().log("SimTale: NPC " + data.name + " remontado ao entrar no mundo/carregar chunk!");
                    npc = new SimNPCComponent(uuid, data.name);
                    
                    Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(uuid);
                    npc.entityRef = entityRef;
                    
                    SimNPCPersistence.loadNPC(npc);
                    
                    if (entityRef != null) {
                        commandBuffer.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    }
                    
                    final UUID targetId = uuid;
                    SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId));
                    SimTale.ACTIVE_NPCS.add(npc);
                }
            }
            return;
        }

        if (!SimTale.ACTIVE_NPCS.contains(npc)) {
            SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(npc.entityId));
            SimNPCPersistence.loadNPC(npc);
            SimTale.ACTIVE_NPCS.add(npc);
        }

        if (absoluteTick % 600 == 0) {
            SimNPCPersistence.saveNPC(npc);
        }

        // Daily natural pregnancy check for married female NPCs
        if (absoluteTick % 24000 == 0 && npc.gender == Gender.FEMALE && npc.family.isMarried && npc.family.spouseId != null) {
            if (npc.pregnancy == null || !npc.pregnancy.pregnant) {
                Relationship spouseRel = npc.getRelationship(npc.family.spouseId);
                // 25% chance of getting pregnant daily if romance is high (romance >= 75)
                if (spouseRel.romance >= 75 && Math.random() < 0.25) {
                    LifecycleManager.startPregnancy(npc, npc.family.spouseId, absoluteTick);
                }
            }
        }

        npc.needs.tickDecay(npc.personality.traits);

        if (npc.currentConversationPartner != null) {
            if (absoluteTick > npc.conversationTimeoutTick) {
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr.getUuid().equals(npc.currentConversationPartner)) {
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("simtale.tick.chat.timeout", 3)).param("name", npc.name));
                    }
                }
                npc.currentConversationPartner = null;
            }
        }

        if (npc.currentJob != JobType.NONE) {
            if (absoluteTick >= npc.jobDepartureTick && absoluteTick < npc.jobCompletionTick && !npc.isAway) {
                npc.isAway = true;
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr.getUuid().equals(npc.jobEmployer)) {
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("simtale.tick.job.depart", 3)).param("name", npc.name));
                    }
                }
                Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (ref != null) {
                    TransformComponent npcTransform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (npcTransform != null) {
                        npcTransform.setPosition(new Vector3d(0, 1000, 0));
                    }
                }
            } else if (absoluteTick >= npc.jobCompletionTick) {
                if (npc.jobEmployer != null) {
                    sendLootToPlayer(npc);
                }
                
                Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (ref != null) {
                    if (npc.jobEmployer != null) {
                        Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(npc.jobEmployer);
                        if (playerRef != null) {
                            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
                            TransformComponent npcTransform = store.getComponent(ref, TransformComponent.getComponentType());
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

    private void sendLootToPlayer(SimNPCComponent npc) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid().equals(npc.jobEmployer)) {
                    pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("simtale.tick.job.complete", 3)).param("name", npc.name).param("job_name", npc.currentJob.getPortugueseName()));
                    
                    List<JobLootTable.LootEntry> loots = JobLootTable.getLootForJob(npc.currentJob);
                    for (JobLootTable.LootEntry loot : loots) {
                        int qty = loot.rollQty();
                        if (qty > 0) {
                            pr.sendMessage(Message.raw("<" + npc.name + "> Coletou: " + qty + "x " + loot.itemId()));
                            try {
                                CommandManager.get().handleCommand(pr, "give " + pr.getUsername() + " " + loot.itemId() + " --quantity=" + qty);
                            } catch (Exception cmdEx) {
                                cmdEx.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


    
    
        
    
                            

                    