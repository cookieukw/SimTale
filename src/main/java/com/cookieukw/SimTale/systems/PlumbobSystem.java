package com.cookieukw.SimTale.systems;
import com.cookieukw.SimTale.SimTale;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

import com.cookieukw.SimTale.core.Mood;

public class PlumbobSystem extends EntityTickingSystem<EntityStore> {

    // Maps Player UUID to Plumbob Entity Ref
    private static final Map<UUID, Ref<EntityStore>> playerPlumbobs = new HashMap<>();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return UUIDComponent.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        
        PersistentModel pm = chunk.getComponent(index, PersistentModel.getComponentType());
        if (pm != null && "Plumbob".equals(pm.getModelReference().getModelAssetId())) {
            Ref<EntityStore> thisRef = chunk.getReferenceTo(index);
            if (!playerPlumbobs.containsValue(thisRef)) {
                commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                System.out.println("[SimTale] Limpando Plumbob orfão do mundo: " + uuidComp.getUuid());
            }
            return;
        }
        
        boolean isPlayer = chunk.getComponent(index, Player.getComponentType()) != null;
        boolean isNpc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE) != null;
        
        if (!isPlayer && !isNpc) return;
        
        UUID entityUuid = uuidComp.getUuid();
        TransformComponent entityTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (entityTransform == null) return;
        
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;

        Ref<EntityStore> plumbobRef = playerPlumbobs.get(entityUuid);
        // Get Mood
        String moodModelName = "Plumbob"; // Default fallback
        Mood currentMood;
        if (isNpc) {
            SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (npc != null && npc.getMood() != null) {
                currentMood = npc.getMood();
                moodModelName = "Plumbob_" + currentMood.name();
            }
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
                    entityTransform.getPosition().y + 2.2,
                    entityTransform.getPosition().z
                ));
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
                holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(entityTransform.getPosition()), new Rotation3f()));
                holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                assert model.getBoundingBox() != null;
                holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
                holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
                holder.ensureComponent(UUIDComponent.getComponentType());
                
                Ref<EntityStore> newPlumbob = commandBuffer.addEntity(holder, AddReason.SPAWN);
                playerPlumbobs.put(entityUuid, newPlumbob);
                System.out.println("[SimTale] Spawned Plumbob for entity " + entityUuid);
            } else {
                System.out.println("[SimTale-ERROR] Plumbob ModelAsset not found!");
            }
        }
    }
}
