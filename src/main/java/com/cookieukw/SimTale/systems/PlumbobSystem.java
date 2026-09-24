package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.vehicles.CalhambequePhysicsSystem;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
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
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PlumbobSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Cache of scaled Plumbob models per mood to avoid recreating and parsing models on every mood change
    private static final Map<String, Model> SCALED_MODEL_CACHE = new ConcurrentHashMap<>();

    // Maps Player/NPC UUID to Plumbob Entity Ref
    private static final Map<UUID, Ref<EntityStore>> playerPlumbobs = Collections.synchronizedMap(new HashMap<>());
    /**
     * Reverse index of {@link #playerPlumbobs}. The orphan check used
     * {@code playerPlumbobs.containsValue(ref)}, which scans the entire map under its lock for
     * every plumbob on every tick.
     */
    private static final Set<Ref<EntityStore>> trackedPlumbobRefs =
            ConcurrentHashMap.newKeySet();
    /**
     * Refs already queued for removal by {@link #despawnPlumbob} or {@link #removePlumbob}.
     */
    private static final Set<Ref<EntityStore>> pendingDespawns = ConcurrentHashMap.newKeySet();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.or(
                SimTale.SIM_NPC_COMPONENT_TYPE,
                Player.getComponentType(),
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
                new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
                new SystemDependency<>(Order.AFTER, CalhambequePhysicsSystem.class)
        );
    }

    private static Model getOrCreateScaledModel(String moodModelName) {
        return SCALED_MODEL_CACHE.computeIfAbsent(moodModelName, name -> {
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(name);
            if (modelAsset == null) {
                modelAsset = ModelAsset.getAssetMap().getAsset("Plumbob");
            }
            if (modelAsset != null) {
                return Model.createScaledModel(modelAsset, 0.9f, null, Box.ZERO);
            }
            return null;
        });
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        
        PersistentModel pm = chunk.getComponent(index, PersistentModel.getComponentType());
        if (pm != null && pm.getModelReference().getModelAssetId() != null && pm.getModelReference().getModelAssetId().startsWith("Plumbob")) {
            Ref<EntityStore> thisRef = chunk.getReferenceTo(index);
            if (pendingDespawns.remove(thisRef)) {
                return;
            }
            if (!trackedPlumbobRefs.contains(thisRef)) {
                if (thisRef.isValid() && pendingDespawns.add(thisRef)) {
                    commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                    LOGGER.atFine().log("[SimTale] Limpando Plumbob orfao do mundo: " + uuidComp.getUuid());
                }
            }
            return;
        }
        
        boolean isPlayer = chunk.getComponent(index, Player.getComponentType()) != null;
        SimNPCComponent npcHere = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        boolean isNpc = npcHere != null;

        if (!isPlayer && !isNpc) return;

        UUID entityUuid = uuidComp.getUuid();
        if (npcHere != null && npcHere.isReaper) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        if (npcHere != null && NPCWorkHelper.isAwayOnExpedition(store, chunk.getReferenceTo(index))) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        if (chunk.getComponent(index, MountedComponent.getComponentType()) != null) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        TransformComponent entityTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (entityTransform == null) return;
        
        World world = WorldUtil.first();
        if (world == null) return;

        Ref<EntityStore> plumbobRef = playerPlumbobs.get(entityUuid);
        // Get Mood
        String moodModelName = "Plumbob"; // Default fallback
        if (isNpc) {
            SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (npc != null && npc.getMood() != null) {
                moodModelName = "Plumbob_" + npc.getMood().name();
            }
        }

        double height = 2.2;
        BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
        if (box != null) {
            height = box.getBoundingBox().height() + 0.35;
        }

        boolean needsNewPlumbob = false;
        if (plumbobRef == null || !plumbobRef.isValid()) {
            needsNewPlumbob = true;
        } else {
            // Check if the plumbob still exists
            TransformComponent plumbobTransform = store.getComponent(plumbobRef, TransformComponent.getComponentType());
            if (plumbobTransform == null) {
                needsNewPlumbob = true;
            } else {
                store.ensureComponent(plumbobRef, Intangible.getComponentType());
                // Update position in-place without vector allocation
                Vector3d entityPos = entityTransform.getPosition();
                plumbobTransform.getPosition().set(entityPos.x, entityPos.y + height, entityPos.z);
                
                // Update rotation in-place without rotation allocation
                float yaw = (float) ((world.getTick() * 0.04f) % (2.0f * Math.PI));
                plumbobTransform.getRotation().set(0f, yaw, 0f);
                commandBuffer.replaceComponent(plumbobRef, TransformComponent.getComponentType(), plumbobTransform);
                
                // Update Model if mood changed using cached model
                PersistentModel currPm = store.getComponent(plumbobRef, PersistentModel.getComponentType());
                if (currPm != null && !moodModelName.equals(currPm.getModelReference().getModelAssetId())) {
                    Model model = getOrCreateScaledModel(moodModelName);
                    if (model != null) {
                        commandBuffer.replaceComponent(plumbobRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                        commandBuffer.replaceComponent(plumbobRef, ModelComponent.getComponentType(), new ModelComponent(model));
                    }
                }
            }
        }

        if (needsNewPlumbob) {
            Model model = getOrCreateScaledModel(moodModelName);
            if (model != null) {
                Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                Vector3d entityPos = entityTransform.getPosition();
                holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(entityPos.x, entityPos.y + height, entityPos.z), new Rotation3f()));
                holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
                /* Deliberately no BoundingBox and Intangible attached: this is a purely cosmetic
                floating icon, so raycasts (mining, interacting, attacking) must pass straight through it.
                */
                holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
                holder.ensureComponent(UUIDComponent.getComponentType());
                
                Ref<EntityStore> newPlumbob = commandBuffer.addEntity(holder, AddReason.SPAWN);
                Ref<EntityStore> replaced = playerPlumbobs.put(entityUuid, newPlumbob);
                if (replaced != null) {
                    trackedPlumbobRefs.remove(replaced);
                }
                trackedPlumbobRefs.add(newPlumbob);
                LOGGER.atFine().log("[SimTale] Spawned Plumbob for entity " + entityUuid);
            } else {
                LOGGER.atWarning().log("[SimTale-ERROR] Plumbob ModelAsset not found!");
            }
        }
    }

    /**
     * Untracks and removes the plumbob owned by an entity, if it has one.
     * <p>
     * Untracking alone would work eventually — the orphan sweep at the top of the tick reaps
     * plumbobs nobody claims — but only once that entity's chunk happens to be ticked again, so the
     * crystal lingers visibly in the meantime. Removing it here makes it disappear on the same tick
     * the NPC does.
     */
    private static void despawnPlumbob(UUID entityUuid, CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> existing = playerPlumbobs.remove(entityUuid);
        if (existing == null) return;
        trackedPlumbobRefs.remove(existing);
        if (existing.isValid() && pendingDespawns.add(existing)) {
            commandBuffer.removeEntity(existing, RemoveReason.REMOVE);
        }
    }

    public static void removePlumbob(UUID entityUuid) {
        Ref<EntityStore> removed = playerPlumbobs.remove(entityUuid);
        if (removed != null) {
            trackedPlumbobRefs.remove(removed);
        }
        LOGGER.atFine().log("[SimTale] Plumbob untracked para a entidade: " + entityUuid);
    }
}
