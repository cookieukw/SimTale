package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.SimTale;
import java.util.UUID;

/**
 * Factory for creating SimTale NPCs with proper models and components.
 */
public class SimNPCFactory {

    public enum NPCType {
        SLOTHIAN("simtale:sim_npc_slothian"),
        TRORK("simtale:sim_npc_trork");

        public final String modelId;

        NPCType(String modelId) {
            this.modelId = modelId;
        }
    }

    public static Ref<EntityStore> spawnNPC(Store<EntityStore> store, Vector3d position, NPCType type) {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        UUID entityId = UUID.randomUUID();

        // 1. Basic Components
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityId));
        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(position, new Vector3f(0, 0, 0)));

        // 2. Model & Physics
        ModelAsset modelAsset = (ModelAsset) ModelAsset.getAssetMap().getAsset(type.modelId);
        if (modelAsset == null) {
            // Fallback to a basic model if the mod model isn't found
            modelAsset = (ModelAsset) ModelAsset.getAssetMap()
                    .getAsset("NPC/Intelligent/Slothian/Models/Model.blockymodel");
        }

        Model model = Model.createScaledModel(modelAsset, 1.0f);
        holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
        holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.addComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));

        // 3. Network Identity
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));

        // 4. SimTale Logic
        SimNPCComponent simComponent = new SimNPCComponent(entityId,
                type.name() + "_" + entityId.toString().substring(0, 4));

        // Try to load existing data if available
        SimNPCPersistence.loadNPC(simComponent);

        holder.addComponent(SimTale.SIM_NPC_COMPONENT_TYPE, simComponent);

        // 5. Add to Store
        return store.addEntity(holder, AddReason.SPAWN);
    }
}
