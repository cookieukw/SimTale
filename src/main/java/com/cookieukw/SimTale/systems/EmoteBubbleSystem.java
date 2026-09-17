package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.ThoughtType;
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

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking ECS system that manages Terraria-style floating thought bubbles (Emote Bubbles)
 * above NPC heads. Displays contextual thoughts for 3.5 seconds and despawns them cleanly.
 */
public class EmoteBubbleSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How long a thought bubble remains visible: 70 ticks (3.5 seconds). */
    public static final int THOUGHT_LIFETIME_TICKS = 70;

    /** Minimum cooldown between spontaneous idle thoughts: 45 seconds (900 ticks). */
    public static final long SPONTANEOUS_COOLDOWN_TICKS = 900;

    public record ActiveThought(Ref<EntityStore> bubbleRef, long expireTick, ThoughtType thoughtType) {}

    // Maps NPC UUID -> Active Thought Bubble Data
    private static final Map<UUID, ActiveThought> activeThoughts = new ConcurrentHashMap<>();

    // Queued thought requests from other systems: NPC UUID -> ThoughtType
    private static final Map<UUID, ThoughtType> pendingRequests = new ConcurrentHashMap<>();

    // Last time a thought was triggered for this NPC
    private static final Map<UUID, Long> lastThoughtTicks = new ConcurrentHashMap<>();

    // Set of all active thought bubble entity refs for orphan detection
    private static final Set<Ref<EntityStore>> trackedBubbleRefs = ConcurrentHashMap.newKeySet();

    private static final Set<Ref<EntityStore>> pendingDespawns = ConcurrentHashMap.newKeySet();

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
                new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class)
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
            if (!trackedBubbleRefs.contains(thisRef) && !pendingDespawns.remove(thisRef)) {
                commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                LOGGER.atFine().log("[SimTale] Limpando balao de pensamento orfao: " + uuidComp.getUuid());
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

        // Check if there is a pending request for a new thought
        ThoughtType requestedThought = pendingRequests.remove(entityUuid);
        if (requestedThought != null) {
            lastThoughtTicks.put(entityUuid, currentTick);
            if (currentThought != null) {
                // Update existing bubble model and refresh timer
                Ref<EntityStore> bubbleRef = currentThought.bubbleRef();
                if (bubbleRef != null && bubbleRef.isValid()) {
                    String modelName = requestedThought.getModelName();
                    ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelName);
                    if (modelAsset != null) {
                        Model model = Model.createScaledModel(modelAsset, 0.75f);
                        commandBuffer.replaceComponent(bubbleRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                        commandBuffer.replaceComponent(bubbleRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        activeThoughts.put(entityUuid, new ActiveThought(bubbleRef, currentTick + THOUGHT_LIFETIME_TICKS, requestedThought));
                        return;
                    }
                }
            }
            // Spawn new bubble entity
            spawnThought(entityUuid, requestedThought, currentTick, entityTransform, chunk, index, store, commandBuffer);
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

            double height = 2.25;
            BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
            if (box != null) {
                height = box.getBoundingBox().height() + 0.42;
            }

            // Gentle vertical bobbing
            long elapsed = currentTick - (currentThought.expireTick() - THOUGHT_LIFETIME_TICKS);
            double bob = Math.sin(elapsed * 0.18) * 0.04;

            // Offset +0.35m to the side so it does not overlap the Plumbob
            bubbleTransform.teleportPosition(new Vector3d(
                    entityTransform.getPosition().x + 0.35,
                    entityTransform.getPosition().y + height + bob,
                    entityTransform.getPosition().z
            ));
            commandBuffer.replaceComponent(bubbleRef, TransformComponent.getComponentType(), bubbleTransform);
        }
    }

    private void spawnThought(UUID entityUuid, ThoughtType thought, long currentTick,
                              TransformComponent entityTransform, ArchetypeChunk<EntityStore> chunk,
                              int index, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        String modelName = thought.getModelName();
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelName);
        if (modelAsset == null) {
            LOGGER.atWarning().log("[SimTale] Thought ModelAsset not found: " + modelName);
            return;
        }

        double height = 2.25;
        BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
        if (box != null) {
            height = box.getBoundingBox().height() + 0.42;
        }

        Model model = Model.createScaledModel(modelAsset, 0.75f);
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(
                new Vector3d(entityTransform.getPosition().x + 0.35, entityTransform.getPosition().y + height, entityTransform.getPosition().z),
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
            pendingDespawns.add(thought.bubbleRef());
            trackedBubbleRefs.remove(thought.bubbleRef());
            commandBuffer.removeEntity(thought.bubbleRef(), RemoveReason.REMOVE);
        }
    }

    /**
     * Requests a thought bubble to appear above the NPC's head.
     *
     * @param npcUuid NPC entity UUID
     * @param thought The thought/emote type to show
     */
    public static void triggerThought(UUID npcUuid, ThoughtType thought) {
        if (npcUuid == null || thought == null) return;
        pendingRequests.put(npcUuid, thought);
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
        ActiveThought at = activeThoughts.remove(npcUuid);
        if (at != null && at.bubbleRef() != null) {
            trackedBubbleRefs.remove(at.bubbleRef());
            pendingDespawns.add(at.bubbleRef());
        }
    }
}
