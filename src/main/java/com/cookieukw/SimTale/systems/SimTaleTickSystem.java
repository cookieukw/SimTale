package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
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
                // Caskara.load() resolves to the "default" shell; NPC data lives in "simtale".
                // This used to always return null, so this whole re-attach path never ran.
                SimNPCData data = SimNPCPersistence.loadData(uuid);
                if (data != null) {
                    HytaleLogger.forEnclosingClass().atInfo().log("SimTale: NPC " + data.name + " remontado ao entrar no mundo/carregar chunk!");
                    npc = new SimNPCComponent(uuid, data.name);
                    
                    Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(uuid);
                    npc.entityRef = entityRef;
                    
                    SimNPCPersistence.loadNPC(npc);
                    
                    if (entityRef != null) {
                        commandBuffer.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    }
                    
                    SimTale.trackNpc(npc);
                }
            }
            return;
        }

        // The codec restores only id and name, so a component that came back with the entity
        // still needs its real data pulled from Caskara before anything reads or saves it.
        if (!npc.dataLoaded) {
            SimNPCPersistence.loadNPC(npc);
        }

        // A referencia da propria entidade, tirada do chunk que esta sendo tickado agora.
        //
        // Isto conserta um efeito colateral da mudanca que fez o SimNPCComponent persistir
        // nativamente. Antes, um NPC recarregado chegava SEM o componente, caia no ramo
        // `npc == null` la em cima e ganhava entityRef via getRefFromUUID. Depois que o
        // componente passou a voltar junto com a entidade, aquele ramo deixou de rodar — e era
        // o unico lugar que preenchia entityRef.
        //
        // Resultado: o NPC entrava em ACTIVE_NPCS com entityRef == null. Como todo subcomando
        // filtra por `if (npc.entityRef != null)`, o mod respondia "Nenhum NPC por perto" com o
        // NPC parado na frente do jogador. A tecla F continuava funcionando porque recebe a
        // referencia direto do evento de interacao, sem passar por ACTIVE_NPCS — e era
        // exatamente esse contraste que denunciava o problema.
        //
        // entityRef e transient de proposito (referencia viva nao se serializa), entao a fonte
        // certa e o proprio chunk, nao o banco.
        Ref<EntityStore> selfRef = chunk.getReferenceTo(index);
        if (npc.entityRef == null || !npc.entityRef.isValid()) {
            npc.entityRef = selfRef;
        }

        // Was a linear scan of the whole roster, once per NPC per tick — O(n²) every tick.
        SimNPCComponent activeMatch = SimTale.findNpc(npc.entityId);

        if (activeMatch == null) {
            SimNPCPersistence.loadNPC(npc);
            SimTale.trackNpc(npc);
        } else if (activeMatch != npc) {
            // A instancia rastreada e a que os comandos enxergam, entao ela tambem precisa de
            // uma referencia valida — nao adianta consertar so a copia que veio do chunk.
            if (activeMatch.entityRef == null || !activeMatch.entityRef.isValid()) {
                activeMatch.entityRef = selfRef;
            }
            // Replace the chunk's component with our official tracked instance which holds command changes
            commandBuffer.replaceComponent(selfRef, SimTale.SIM_NPC_COMPONENT_TYPE, activeMatch);
            npc = activeMatch;
        }

        // Staggered by entity id: `absoluteTick % 600` made every NPC in the world write to
        // disk on the very same tick, producing a periodic I/O spike proportional to the roster.
        if (npc.entityId != null && Math.floorMod(absoluteTick + npc.entityId.hashCode(), 600) == 0) {
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

        if (npc.needs == null || npc.personality == null) return;

        npc.needs.tickDecay(npc.personality.traits);

        // Decay emotion intensity over time
        if (npc.activeEmotion != Mood.NEUTRAL) {
            npc.emotionIntensity = Math.max(0f, npc.emotionIntensity - 0.001f);
            if (npc.emotionIntensity < 0.1f) {
                npc.activeEmotion = Mood.NEUTRAL;
                npc.emotionIntensity = 0f;
                npc.emotionSource = "decay";
            }
        }

        // Apply needs-based passive emotion triggers
        if (npc.memory.remembers(com.cookieukw.SimTale.core.MemoryEvent.ATTACKED, null, 30000)) {
            npc.setEmotion(Mood.SCARED, 0.9f, "damage", absoluteTick);
        } else if (npc.memory.remembers(com.cookieukw.SimTale.core.MemoryEvent.INSULTED, null, 30000)) {
            npc.setEmotion(Mood.ANGRY, 0.8f, "insult", absoluteTick);
        } else {
            if (npc.personality.traits.contains(com.cookieukw.SimTale.core.Trait.AGGRESSIVE) && (npc.needs.hunger < 50 || npc.needs.energy < 50)) {
                npc.setEmotion(Mood.ANGRY, 0.7f, "needs", absoluteTick);
            } else if (npc.needs.energy < 20) {
                npc.setEmotion(Mood.SLEEPY, 0.8f, "tiredness", absoluteTick);
            } else if (npc.needs.isMiserable()) {
                npc.setEmotion(Mood.SAD, 0.6f, "misery", absoluteTick);
            } else {
                Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (entityRef != null) {
                    RoutineAIComponent aiComp = store.getComponent(entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (aiComp != null && (aiComp.currentTask == RoutineAIComponent.TaskType.IDLE || aiComp.currentTask == RoutineAIComponent.TaskType.WANDERING)) {
                        if (Math.random() < 0.005) {
                            npc.setEmotion(Mood.BORED, 0.4f, "idleness", absoluteTick);
                        }
                    } else if (npc.needs.hunger > 60 && npc.needs.energy > 60 && npc.needs.social > 60) {
                        npc.setEmotion(Mood.HAPPY, 0.3f, "wellness", absoluteTick);
                    }
                }
            }
        }

        if (npc.currentConversationPartner != null) {
            if (absoluteTick > npc.conversationTimeoutTick) {
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr.getUuid().equals(npc.currentConversationPartner)) {
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("general.tick.chat.timeout", 3)).param("name", npc.name));
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
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("general.tick.job.depart", 3)).param("name", npc.name));
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
        } else if (absoluteTick % 200 == 0 && Math.random() < 0.05) {
            // Previously this rolled every single tick, which meant roughly one interaction
            // per second per NPC — each of those writes the NPC to disk via saveNPC().
            InteractionManager.performInteraction(npc, npc.entityId, null, InteractionType.RANDOM);
        }
    }

    private void sendLootToPlayer(SimNPCComponent npc) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid().equals(npc.jobEmployer)) {
                    pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("general.tick.job.complete", 3)).param("name", npc.name).param("job_name", Message.translation(npc.currentJob.translationKey())));
                    
                    List<JobLootTable.LootEntry> loots = JobLootTable.getLootForJob(npc.currentJob);
                    for (JobLootTable.LootEntry loot : loots) {
                        int qty = loot.rollQty();
                        if (qty > 0) {
                            pr.sendMessage(Message.raw("<" + npc.name + "> Coletou: " + qty + "x " + loot.itemId()));
                            try {
                                CommandManager.get().handleCommand(pr, "give " + pr.getUsername() + " " + loot.itemId() + " --quantity=" + qty);
                            } catch (Exception cmdEx) {
                                HytaleLogger.forEnclosingClass().atWarning()
                                        .log("SimTale: falha ao entregar loot '" + loot.itemId() + "': " + cmdEx);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning().log("SimTale: falha ao entregar o loot do trabalho: " + e);
        }
    }
}


    
    
        
    
                            

                    