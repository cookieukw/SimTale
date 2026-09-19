package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.ThoughtType;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking ECS system that manages Terraria-style floating thought bubbles (Emote Bubbles)
 * above NPC heads. Displays contextual thoughts for 15 seconds and despawns them cleanly.
 */
public class EmoteBubbleSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How long a thought bubble remains visible: 300 ticks (15 seconds). */
    public static final int THOUGHT_LIFETIME_TICKS = 300;

    /** Minimum cooldown between spontaneous idle thoughts: 3 minutes (3600 ticks). */
    public static final long SPONTANEOUS_COOLDOWN_TICKS = 3600;

    public static final float ADULT_BUBBLE_SCALE = 0.065f;
    public static final float CHILD_BUBBLE_SCALE = 0.15f;

    public record ActiveThought(Ref<EntityStore> bubbleRef, long expireTick, ThoughtType thoughtType) {}
    public record DelayedThought(ThoughtType thought, long targetTick) {}

    // Maps NPC UUID -> Active Thought Bubble Data
    private static final Map<UUID, ActiveThought> activeThoughts = new ConcurrentHashMap<>();

    // Queued thought requests from other systems: NPC UUID -> ThoughtType
    private static final Map<UUID, ThoughtType> pendingRequests = new ConcurrentHashMap<>();

    // Delayed thought requests for staggering: NPC UUID -> DelayedThought
    private static final Map<UUID, DelayedThought> delayedRequests = new ConcurrentHashMap<>();

    // Last time a thought was triggered for this NPC
    private static final Map<UUID, Long> lastThoughtTicks = new ConcurrentHashMap<>();

    // Set of all active thought bubble entity refs for orphan detection
    private static final Set<Ref<EntityStore>> trackedBubbleRefs = ConcurrentHashMap.newKeySet();

    private static final Set<Ref<EntityStore>> pendingDespawns = ConcurrentHashMap.newKeySet();

    // Cache of scaled bubble models per model name and scale
    private static final Map<String, Model> SCALED_BUBBLE_MODELS = new ConcurrentHashMap<>();

    private static Model getOrCreateScaledBubble(String modelName, float scale) {
        String key = modelName + "_" + scale;
        return SCALED_BUBBLE_MODELS.computeIfAbsent(key, k -> {
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelName);
            if (modelAsset != null) {
                return Model.createScaledModel(modelAsset, scale);
            }
            return null;
        });
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.or(
                SimTale.SIM_NPC_COMPONENT_TYPE,
                PersistentModel.getComponentType()
        );
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class),
                new SystemDependency<>(Order.AFTER, SteeringSystem.class),
                new SystemDependency<>(Order.AFTER, RoutineAISystem.class),
                new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class)
        );
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        // 1. Orphan bubble cleaner
        PersistentModel pm = chunk.getComponent(index, PersistentModel.getComponentType());
        if (pm != null && pm.getModelReference().getModelAssetId() != null
                && pm.getModelReference().getModelAssetId().startsWith("Thought_")) {
            Ref<EntityStore> thisRef = chunk.getReferenceTo(index);
            if (pendingDespawns.remove(thisRef)) {
                return;
            }
            if (!trackedBubbleRefs.contains(thisRef)) {
                if (thisRef.isValid() && pendingDespawns.add(thisRef)) {
                    commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                    LOGGER.atFine().log("[SimTale] Limpando balao de pensamento orfao: " + uuidComp.getUuid());
                }
            }
            return;
        }

        // 2. Only NPCs own thought bubbles
        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;
        long currentTick = world.getTick();
        UUID entityUuid = uuidComp.getUuid();

        TransformComponent entityTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (entityTransform == null) return;

        ActiveThought currentThought = activeThoughts.get(entityUuid);

        // Check expiration
        if (currentThought != null && currentTick >= currentThought.expireTick()) {
            despawnThought(entityUuid, commandBuffer);
            currentThought = null;
        }

        // Check delayed requests
        DelayedThought dtReq = delayedRequests.get(entityUuid);
        if (dtReq != null && currentTick >= dtReq.targetTick()) {
            delayedRequests.remove(entityUuid);
            pendingRequests.put(entityUuid, dtReq.thought());
        }

        // Check if there is a pending request for a new thought
        ThoughtType requestedThought = pendingRequests.remove(entityUuid);
        if (requestedThought != null) {
            lastThoughtTicks.put(entityUuid, currentTick);
            boolean isChild = InteractionManager.isNpcAChild(npc);
            float scale = isChild ? CHILD_BUBBLE_SCALE : ADULT_BUBBLE_SCALE;

            if (currentThought != null) {
                // Update existing bubble model and refresh timer
                Ref<EntityStore> bubbleRef = currentThought.bubbleRef();
                if (bubbleRef != null && bubbleRef.isValid()) {
                    String modelName = requestedThought.getModelName();
                    Model model = getOrCreateScaledBubble(modelName, scale);
                    if (model != null) {
                        commandBuffer.replaceComponent(bubbleRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                        commandBuffer.replaceComponent(bubbleRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        activeThoughts.put(entityUuid, new ActiveThought(bubbleRef, currentTick + THOUGHT_LIFETIME_TICKS, requestedThought));
                        return;
                    }
                }
            }
            // Spawn new bubble entity
            spawnThought(entityUuid, requestedThought, currentTick, entityTransform, chunk, index, store, commandBuffer, npc);
            return;
        }

        // Update position of active thought bubble
        if (currentThought != null) {
            Ref<EntityStore> bubbleRef = currentThought.bubbleRef();
            if (bubbleRef == null || !bubbleRef.isValid()) {
                activeThoughts.remove(entityUuid);
                return;
            }

            TransformComponent bubbleTransform = store.getComponent(bubbleRef, TransformComponent.getComponentType());
            if (bubbleTransform == null) {
                activeThoughts.remove(entityUuid);
                return;
            }

            boolean isChild = InteractionManager.isNpcAChild(npc);
            double headHeight = 1.55;
            double xOffset = isChild ? 0.54 : 0.60;
            BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
            if (box != null) {
                headHeight = isChild ? (box.getBoundingBox().height() * 0.85) : (box.getBoundingBox().height() * 0.80);
            }

            // Gentle vertical bobbing
            long elapsed = currentTick - (currentThought.expireTick() - THOUGHT_LIFETIME_TICKS);
            double bob = Math.sin(elapsed * 0.18) * 0.04;

            // Position beside the head in-place without vector allocation
            Vector3d entityPos = entityTransform.getPosition();
            bubbleTransform.getPosition().set(
                    entityPos.x + xOffset,
                    entityPos.y + headHeight + bob,
                    entityPos.z
            );
            commandBuffer.replaceComponent(bubbleRef, TransformComponent.getComponentType(), bubbleTransform);
        }
    }

    private void spawnThought(UUID entityUuid, ThoughtType thought, long currentTick,
                              TransformComponent entityTransform, ArchetypeChunk<EntityStore> chunk,
                              int index, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                              SimNPCComponent npc) {
        String modelName = thought.getModelName();
        boolean isChild = InteractionManager.isNpcAChild(npc);
        float scale = isChild ? CHILD_BUBBLE_SCALE : ADULT_BUBBLE_SCALE;

        Model model = getOrCreateScaledBubble(modelName, scale);
        if (model == null) {
            LOGGER.atWarning().log("[SimTale] Thought ModelAsset not found: " + modelName);
            return;
        }

        double headHeight = 1.55;
        double xOffset = isChild ? 0.54 : 0.60;
        BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
        if (box != null) {
            headHeight = isChild ? (box.getBoundingBox().height() * 0.85) : (box.getBoundingBox().height() * 0.80);
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d entityPos = entityTransform.getPosition();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(
                new Vector3d(entityPos.x + xOffset, entityPos.y + headHeight, entityPos.z),
                new Rotation3f()
        ));
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        Ref<EntityStore> newBubble = commandBuffer.addEntity(holder, AddReason.SPAWN);
        trackedBubbleRefs.add(newBubble);
        activeThoughts.put(entityUuid, new ActiveThought(newBubble, currentTick + THOUGHT_LIFETIME_TICKS, thought));
    }

    private void despawnThought(UUID entityUuid, CommandBuffer<EntityStore> commandBuffer) {
        ActiveThought thought = activeThoughts.remove(entityUuid);
        if (thought != null && thought.bubbleRef() != null && thought.bubbleRef().isValid()) {
            Ref<EntityStore> bubbleRef = thought.bubbleRef();
            trackedBubbleRefs.remove(bubbleRef);
            if (pendingDespawns.add(bubbleRef)) {
                commandBuffer.removeEntity(bubbleRef, RemoveReason.REMOVE);
            }
        }
    }

    /**
     * Requests a thought bubble to appear above the NPC's head immediately.
     */
    public static void triggerThought(UUID npcUuid, ThoughtType thought) {
        if (npcUuid == null || thought == null) return;
        pendingRequests.put(npcUuid, thought);
    }

    /**
     * Requests a thought bubble to appear above the NPC's head with a delay in ticks,
     * allowing staggered thoughts between nearby NPCs so they don't pop simultaneously.
     */
    public static void triggerThoughtWithDelay(UUID npcUuid, ThoughtType thought, int delayTicks) {
        if (npcUuid == null || thought == null) return;
        if (delayTicks <= 0) {
            triggerThought(npcUuid, thought);
            return;
        }
        World world = WorldUtil.first();
        long currentTick = world != null ? world.getTick() : 0;
        delayedRequests.put(npcUuid, new DelayedThought(thought, currentTick + delayTicks));
    }

    /**
     * Triggers a spontaneous thought if the NPC's spontaneous cooldown has elapsed.
     */
    public static boolean triggerSpontaneousThought(UUID npcUuid, ThoughtType thought, long currentTick) {
        if (npcUuid == null || thought == null) return false;
        Long lastTick = lastThoughtTicks.get(npcUuid);
        if (lastTick != null && (currentTick - lastTick) < SPONTANEOUS_COOLDOWN_TICKS) {
            return false;
        }
        triggerThought(npcUuid, thought);
        return true;
    }

    /**
     * Cleans up any thought bubble for an NPC when they despawn or die.
     */
    public static void removeThought(UUID npcUuid) {
        if (npcUuid == null) return;
        pendingRequests.remove(npcUuid);
        delayedRequests.remove(npcUuid);
        ActiveThought at = activeThoughts.remove(npcUuid);
        if (at != null && at.bubbleRef() != null) {
            trackedBubbleRefs.remove(at.bubbleRef());
        }
    }
}
