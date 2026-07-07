package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

public class BedEntityRegistrySystem extends RefChangeSystem<EntityStore, PersistentModel> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    @Nonnull
    public ComponentType<EntityStore, PersistentModel> componentType() {
        return PersistentModel.getComponentType();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return PersistentModel.getComponentType();
    }

    @Override
    public void onComponentAdded(@NonNullDecl Ref<EntityStore> ref, PersistentModel pm, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> cb) {
        if (pm.getModelReference().getModelAssetId() == null) return;
        String id = pm.getModelReference().getModelAssetId();
        if (!BedRegistry.isBedId(id)) return;

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) return;

        Vector3d pos = tc.getPosition();
        Rotation3f rot = tc.getRotation();
        float yaw = rot.yaw();

        BedRegistry.addOrReplace((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), yaw);
        LOGGER.atInfo().log("[SimTale] Bed entity added: " + id + " total=" + BedRegistry.size());
    }

    @Override
    public void onComponentSet(@NonNullDecl Ref<EntityStore> ref, PersistentModel oldPm, @NonNullDecl PersistentModel newPm, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> cb) {
        onComponentRemoved(ref, oldPm, store, cb);
        onComponentAdded(ref, newPm, store, cb);
    }

    @Override
    public void onComponentRemoved(@NonNullDecl Ref<EntityStore> ref, PersistentModel pm, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> cb) {
        if (pm.getModelReference().getModelAssetId() == null) return;
        String id = pm.getModelReference().getModelAssetId();
        if (!BedRegistry.isBedId(id)) return;

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) return;

        Vector3d pos = tc.getPosition();
        BedRegistry.removeAt((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
        LOGGER.atInfo().log("[SimTale] Bed entity removed: " + id + " total=" + BedRegistry.size());
    }
}
