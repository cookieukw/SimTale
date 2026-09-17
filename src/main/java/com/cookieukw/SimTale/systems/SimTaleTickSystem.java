package com.cookieukw.SimTale.systems;


import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
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
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;

import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import org.joml.Vector3i;

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
        
        World world = WorldUtil.fromEntityRef(chunk.getReferenceTo(index));
        if (world == null) return;
        long absoluteTick = world.getTick();

        /* The world map runs on its own thread and cannot touch the ECS, so it reads a snapshot
        taken here instead. Self-throttled; calling it from every NPC's tick is cheap because
        all but one call returns immediately.
        */
        SimTaleMarkerProvider.captureSnapshot(world, store);

        /* Same free ride: a no-op unless somebody has a house outline up, and it needs a tick from
        somewhere to expire on its own rather than lingering until the next inspection.
        */
        HouseBlueprintHelper.tickExpiry(world);

        /* Same free ride again: checks the calendar at most once every CHECK_INTERVAL_TICKS
        (all other NPCs ticked in that same tick just compare a long and return). Applies/
        removes seasonal costumes automatically -- see SeasonalCostumeHelper and
        docs/experimentos.md for the full reasoning (13/09).
        */
        SeasonalCostumeHelper.tick(world, store, absoluteTick);

        // Process mounted/sleeping NPCs whose routine AI ticks are suspended by the engine
        processMountedSleepingNPCs(world, commandBuffer);
        
        if (npc == null) {
            UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                UUID uuid = uuidComp.getUuid();
                /* Caskara.load() resolves to the "default" shell; NPC data lives in "simtale".
                This used to always return null, so this whole re-attach path never ran.
                */
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

        /* The codec restores only id and name, so a component that came back with the entity
        still needs its real data pulled from Caskara before anything reads or saves it.
        */
        if (!npc.dataLoaded) {
            SimNPCPersistence.loadNPC(npc);
        }

        /* The entity's own reference, taken from the chunk currently being ticked.

        This fixes a side effect of making SimNPCComponent persist natively. Previously, a
        reloaded NPC arrived WITHOUT the component, fell into the `npc == null` branch above,
        and received entityRef via getRefFromUUID. Once the component started returning with the
        entity, that branch stopped running — and it was the only place populating entityRef.

        Result: the NPC entered ACTIVE_NPCS with entityRef == null. Since every subcommand
        filters with `if (npc.entityRef != null)`, the mod responded "No NPCs nearby" with the
        NPC standing right in front of the player. The F key kept working because it receives the
        reference directly from the interaction event without passing through ACTIVE_NPCS — and
        it was precisely that contrast that revealed the issue.

        entityRef is transient on purpose (a live reference cannot be serialized), so the
        correct source is the chunk itself, not the database.
        */
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
            /* The tracked instance is what commands inspect, so it also needs a valid
            reference — fixing only the copy from the chunk is not enough.
            */
            if (activeMatch.entityRef == null || !activeMatch.entityRef.isValid()) {
                activeMatch.entityRef = selfRef;
            }
            // Replace the chunk's component with our official tracked instance which holds command changes
            commandBuffer.replaceComponent(selfRef, SimTale.SIM_NPC_COMPONENT_TYPE, activeMatch);
            npc = activeMatch;
        }

        /* The Reaper is deliberately never written to SimTale's own database -- she is ephemeral
        by design (SimNPCFactory.spawnNPC: "O Reaper fica de fora de proposito"). If the server
        restarts, or a chunk saves and reloads, while one is mid-ceremony, the engine's own
        entity codec restores this component with only id and name intact (see the comment on
        `!npc.dataLoaded` above) -- isReaper silently comes back false, and with no SimTale DB
        record to load either, she re-enters ACTIVE_NPCS as an ordinary, permanently
        interactable villager literally named "Grim Reaper" (13/09 report: found standing
        around with a normal mood plumbob, interactable like anyone else). Her ceremonial model
        is the one part of her that survives that round trip intact, so it's what identifies
        her here, after the isReaper flag itself is already gone.
        */
        if (!npc.isReaper) {
            PersistentModel selfModel = store.getComponent(selfRef, PersistentModel.getComponentType());
            if (selfModel != null && selfModel.getModelReference() != null
                    && SimNPCFactory.REAPER_MODEL_ASSET_ID.equals(selfModel.getModelReference().getModelAssetId())) {
                LOGGER.info("[SimTale] Reaper orfa encontrada apos reload (isReaper perdido no restart) — removendo em vez de deixa-la como NPC comum");
                SimTale.untrackNpc(npc);
                PlumbobSystem.removePlumbob(npc.entityId);
                commandBuffer.removeEntity(selfRef, RemoveReason.REMOVE);
                return;
            }
        }

        /* Staggered by entity id: `absoluteTick % 600` made every NPC in the world write to
        disk on the very same tick, producing a periodic I/O spike proportional to the roster.
        */
        if (npc.entityId != null && Math.floorMod(absoluteTick + npc.entityId.hashCode(), 600) == 0) {
            SimNPCPersistence.saveNPC(npc);
        }

        /* Same staggering idea as the save above, offset by one tick so the two don't pile onto
        the same frame for every NPC sharing a hash bucket. Cheap no-op for the common case
        (no armour ever given, or already equipped correctly).
        */
        if (npc.entityId != null && Math.floorMod(absoluteTick + npc.entityId.hashCode() + 1, 200) == 0) {
            NPCArmorHelper.ensureArmorEquipped(npc.entityRef, npc, store);
        }

        // Daily natural pregnancy check for married female NPCs
        if (absoluteTick % 24000 == 0 && npc.gender == Gender.FEMALE && npc.family.isMarried && npc.family.spouseId != null) {
            if (npc.pregnancy == null || !npc.pregnancy.pregnant) {
                Relationship spouseRel = npc.getRelationship(npc.family.spouseId);
                // 25% chance of getting pregnant daily if romance is high (romance >= 75)
                if (spouseRel.romance >= 75 && Math.random() < 0.25) {
                    /* The spouse being another NPC (not a player) is the only case where we can
                    ask "does the spouse actually want this too?" — a player has no FamilySystem
                    to hold that preference, so a player marriage keeps its original behaviour
                    exactly as it was: romance + chance is the whole gate.
                    */
                    SimNPCComponent spouseNpc = LifecycleUtils.findNPCById(npc.family.spouseId);
                    boolean bothWantIt = spouseNpc == null || (npc.family.wantsAnotherChild() && spouseNpc.family.wantsAnotherChild());
                    if (bothWantIt) {
                        LifecycleManager.startPregnancy(npc, npc.family.spouseId, absoluteTick);
                    }
                }
            }
        }

        if (npc.personality == null) return;

        NeedsHelper.tickDecay(store, npc.entityRef, npc.personality.traits);

        /* Decay emotion intensity over time.

        0.001 per tick burned a full-intensity mood in 45 s, and the ambient HAPPY at 0.3 in ten.
        At 0.0002 a strong feeling lasts around 7 min and a mild one around 1.5 min, which is the
        difference between a village with moods and a village with flickering icons.
        */
        if (npc.activeEmotion != Mood.NEUTRAL) {
            npc.emotionIntensity = Math.max(0f, npc.emotionIntensity - 0.0002f);
            if (npc.emotionIntensity < 0.1f) {
                npc.activeEmotion = Mood.NEUTRAL;
                npc.emotionIntensity = 0f;
                npc.emotionSource = "decay";
            }
        }

        // Apply needs-based passive emotion triggers
        if (npc.memory.remembers(MemoryEvent.ATTACKED, null, 30000)) {
            npc.setEmotion(Mood.SCARED, 0.9f, "damage", absoluteTick);
        } else if (npc.memory.remembers(MemoryEvent.INSULTED, null, 30000)) {
            npc.setEmotion(Mood.ANGRY, 0.8f, "insult", absoluteTick);
        } else {
            if (npc.personality.traits.contains(Trait.AGGRESSIVE) && (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HUNGER_ID) < 50 || NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) < 50)) {
                npc.setEmotion(Mood.ANGRY, 0.7f, "needs", absoluteTick);
            } else if (NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) < 20) {
                npc.setEmotion(Mood.SLEEPY, 0.8f, "tiredness", absoluteTick);
            } else if (NeedsHelper.isMiserable(store, npc.entityRef)) {
                npc.setEmotion(Mood.SAD, 0.6f, "misery", absoluteTick);
            } else {
                Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (entityRef != null) {
                    boolean hasWellness = NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.HUNGER_ID) > 60
                            && NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.ENERGY_ID) > 60
                            && NeedsHelper.getNeed(store, npc.entityRef, NeedsHelper.SOCIAL_ID) > 60;

                    if (hasWellness) {
                        npc.setEmotion(Mood.HAPPY, 0.3f, "wellness", absoluteTick);
                    } else {
                        RoutineAIComponent aiComp = store.getComponent(entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                        if (aiComp != null && (aiComp.currentTask == RoutineAIComponent.TaskType.IDLE || aiComp.currentTask == RoutineAIComponent.TaskType.WANDERING)) {
                            /* 0.005 per tick is one in ten seconds — boredom arrived almost the moment
                            an NPC stopped moving. At 0.0004 it takes around two minutes of idling,
                            which is closer to what "bored" is supposed to mean.
                            */
                            if (Math.random() < 0.0004) {
                                npc.setEmotion(Mood.BORED, 0.4f, "idleness", absoluteTick);
                            }
                        }
                    }
                }
            }
        }

        if (npc.currentConversationPartner != null) {
            if (absoluteTick > npc.conversationTimeoutTick) {
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr.getUuid().equals(npc.currentConversationPartner)) {
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("npc-dialogues.tick.chat.timeout", 3)).param("name", npc.name));
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
                        pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("npc-dialogues.tick.job.depart", 3)).param("name", npc.name));
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
            /* Previously this rolled every single tick, which meant roughly one interaction
            per second per NPC — each of those writes the NPC to disk via saveNPC().
            */
            InteractionManager.performInteraction(npc, npc.entityId, null, InteractionType.RANDOM);
        }
    }

    private void sendLootToPlayer(SimNPCComponent npc) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid().equals(npc.jobEmployer)) {
                    pr.sendMessage(Message.translation(SimTaleChatHandler.getRandomVariant("npc-dialogues.tick.job.complete", 3)).param("name", npc.name).param("job_name", Message.translation(npc.currentJob.translationKey())));
                    
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

    private static final SimLog LOGGER = SimLog.forClass(SimTaleTickSystem.class);

    /**
     * Tick of the last full pass over {@code ACTIVE_NPCS} made by
     * {@link #processMountedSleepingNPCs}, or -1 before the first one.
     *
     * <p>{@code tick()} above calls this once per NPC per real tick, because that is the only
     * hook available here -- but the work inside is a pass over every active NPC, not
     * per-caller work. Without this guard, N active NPCs meant N full scans of the same list per
     * real tick (O(N^2) per tick). Same self-throttle idiom already used by
     * {@link SimTaleMarkerProvider#captureSnapshot}: all but one call per tick returns
     * immediately. Added during the 13/09 optimization pass.
     */
    private static volatile long lastMountedSleepingPassTick = -1L;

    private static void processMountedSleepingNPCs(World world, CommandBuffer<EntityStore> commandBuffer) {
        if (world == null || SimTale.ACTIVE_NPCS.isEmpty() || commandBuffer == null) return;

        long tick = world.getTick();
        if (tick == lastMountedSleepingPassTick) return;
        lastMountedSleepingPassTick = tick;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) continue;

            Ref<EntityStore> ref = npc.entityRef;
            Store<EntityStore> npcStore = ref.getStore();
            if (npcStore == null) continue;

            RoutineAIComponent ai = npcStore.getComponent(ref, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai == null) continue;

            MountedComponent mounted = npcStore.getComponent(ref, MountedComponent.getComponentType());
            boolean isSleepingTask = ai.currentTask == TaskType.SLEEPING || ai.currentTask == TaskType.WAKING;

            if (mounted == null && !isSleepingTask) continue;

            /* A carried child's own AI/needs tick is suspended by the engine exactly like a
            sleeping one's, so this is the only place left that can still keep her energy need
            from starving while she is up there -- she is not doing anything, so let her recover
            the whole time she is carried. This must never by itself end the carry, though: the
            ONLY place in this whole codebase that ever creates a MountedComponent is
            ChildCarryHelper, so an entity can be "mounted" here purely by being carried, with
            nothing to do with sleep. A previous version of this method used `mounted != null` as
            an extra way into the wake-up/un-mount logic below (removing MountedComponent,
            resetting the task, teleporting to the bed) -- which fired on the very next tick after
            ANY daytime pickup, since `sleepPeriodClosed` just means "it is currently not night".
            Confirmed in game (13/09): the pickup message and its log both fired, then this
            method's own "waking up" log fired ~30ms later and silently undid the pickup. Energy
            top-up now happens unconditionally for a carried child; the wake-up/un-mount path
            below only ever runs for an entity genuinely on a sleeping/waking task.
            */
            if (mounted != null) {
                NeedsHelper.setNeed(npcStore, ref, NeedsHelper.ENERGY_ID, 100f);
            }
            if (!isSleepingTask) continue;

            boolean sleepPeriodClosed = !NPCSleepHelper.isSleepPeriod(npc, world);
            boolean doneSleeping;
            if (ai.sleepingOnSchedule) {
                doneSleeping = sleepPeriodClosed;
            } else {
                doneSleeping = sleepPeriodClosed
                        || NeedsHelper.getNeed(npcStore, ref, NeedsHelper.ENERGY_ID) >= 100
                        || (world.getTick() - ai.taskStartTime >= RoutineAISystem.SLEEP_DURATION_TICKS);
            }

            if (doneSleeping) {
                LOGGER.info("[SimTale] Sleeping NPC '{}' waking up! (sleepPeriodClosed={}, doneSleeping={})",
                        npc.name, sleepPeriodClosed, doneSleeping);

                NeedsHelper.setNeed(npcStore, ref, NeedsHelper.ENERGY_ID, 100f);

                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
                NPCMovementHelper.setSleepingState(ref, npcStore, commandBuffer, false);

                AnimationUtils.stopAnimation(ref, AnimationSlot.Status, true, npcStore);
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Default/Idle.blockyanim", "Idle", npcStore);

                if (npc.bedLocation != null) {
                    TransformComponent transform = npcStore.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3i bedAnchor = FurnitureAnchorHelper.anchorOf(
                                world, npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                        Vector3i exitPos = NPCMovementHelper.findStandableBeside(bedAnchor, transform, world);
                        if (exitPos != null) {
                            transform.teleportPosition(new Vector3d(exitPos.x + 0.5, exitPos.y, exitPos.z + 0.5));
                            commandBuffer.replaceComponent(ref, TransformComponent.getComponentType(), transform);
                        }
                    }
                }

                ai.sleepingOnSchedule = false;
                ai.currentTask = TaskType.IDLE;
                ai.taskStartTime = world.getTick();
                ai.lastWakeTick = world.getTick();
            }
        }
    }
}


    
    
        
    
                            

                    