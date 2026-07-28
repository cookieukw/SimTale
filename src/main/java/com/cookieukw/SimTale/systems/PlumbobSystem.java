package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PlumbobSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Maps Player/NPC UUID to Plumbob Entity Ref
    private static final Map<UUID, Ref<EntityStore>> playerPlumbobs = Collections.synchronizedMap(new HashMap<>());
    /**
     * Reverse index of {@link #playerPlumbobs}. The orphan check used
     * {@code playerPlumbobs.containsValue(ref)}, which scans the entire map under its lock for
     * every plumbob on every tick.
     */
    private static final Set<Ref<EntityStore>> trackedPlumbobRefs =
            ConcurrentHashMap.newKeySet();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Was UUIDComponent, i.e. *every entity in the world*, every tick — dropped items,
        // projectiles, the lot. Only three archetypes matter here: the NPCs and players that
        // own a plumbob, plus modelled entities so orphaned plumbobs can still be reaped.
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
            new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class)
        );
    }
    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        
        PersistentModel pm = chunk.getComponent(index, PersistentModel.getComponentType());
        if (pm != null && pm.getModelReference().getModelAssetId() != null && pm.getModelReference().getModelAssetId().startsWith("Plumbob")) {
            Ref<EntityStore> thisRef = chunk.getReferenceTo(index);
            if (!trackedPlumbobRefs.contains(thisRef)) {
                commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                LOGGER.atFine().log("[SimTale] Limpando Plumbob orfao do mundo: " + uuidComp.getUuid());
            }
            return;
        }
        
        boolean isPlayer = chunk.getComponent(index, Player.getComponentType()) != null;
        boolean isNpc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE) != null;
        
        if (!isPlayer && !isNpc) return;
        
        UUID entityUuid = uuidComp.getUuid();
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
                // Update position
                plumbobTransform.teleportPosition(new Vector3d(
                    entityTransform.getPosition().x,
                    entityTransform.getPosition().y + height,
                    entityTransform.getPosition().z
                ));
                float yaw = (float) ((world.getTick() * 0.04f) % (2.0f * Math.PI));
                plumbobTransform.setRotation(new Rotation3f(0f, yaw, 0f));
                commandBuffer.replaceComponent(plumbobRef, TransformComponent.getComponentType(), plumbobTransform);
                
                // Update Model if mood changed
                PersistentModel currPm = store.getComponent(plumbobRef, PersistentModel.getComponentType());
                if (currPm != null) {
                    if (!moodModelName.equals(currPm.getModelReference().getModelAssetId())) {
                        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(moodModelName);
                        if (modelAsset != null) {
                            Model model = Model.createScaledModel(modelAsset, 0.9f);
                            commandBuffer.replaceComponent(plumbobRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                            commandBuffer.replaceComponent(plumbobRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        }
                    }
                }
            }
        }

        if (needsNewPlumbob) {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(moodModelName);
            if (modelAsset == null) {
                modelAsset = ModelAsset.getAssetMap().getAsset("Plumbob");
            }
            if (modelAsset != null) {
                Model model = Model.createScaledModel(modelAsset, 0.9f);
                holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(entityTransform.getPosition().x, entityTransform.getPosition().y + height, entityTransform.getPosition().z), new Rotation3f()));
                holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                if (model.getBoundingBox() != null) {
                    holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
                }
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

    public static void removePlumbob(UUID entityUuid) {
        Ref<EntityStore> removed = playerPlumbobs.remove(entityUuid);
        if (removed != null) {
            trackedPlumbobRefs.remove(removed);
        }
        LOGGER.atFine().log("[SimTale] Plumbob untracked para a entidade: " + entityUuid);
    }
}
