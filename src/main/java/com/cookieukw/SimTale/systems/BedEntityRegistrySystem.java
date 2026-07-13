package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
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
import java.util.HashMap;
import java.util.Map;

public class BedEntityRegistrySystem extends RefChangeSystem<EntityStore, PersistentModel> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Map<Ref<EntityStore>, BedPos> trackedBeds = new HashMap<>();

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
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, PersistentModel pm, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        if (pm.getModelReference().getModelAssetId() == null) return;
        String id = pm.getModelReference().getModelAssetId();
        
        // DEBUG: Log ALL entity model IDs to find the real bed ID
        System.out.println("[SimTale-DEBUG] PersistentModel entity spawned: '" + id + "' isBed=" + BedRegistry.isBedId(id));
        
        if (!BedRegistry.isBedId(id)) return;

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            System.out.println("[SimTale-DEBUG] Bed entity has NO TransformComponent, skipping!");
            return;
        }

        Vector3d pos = tc.getPosition();
        Rotation3f rot = tc.getRotation();
        float yaw = rot.yaw();

        BedPos bedPos = new BedPos((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z), yaw);
        trackedBeds.put(ref, bedPos);
        BedRegistry.addOrReplace(bedPos.x, bedPos.y, bedPos.z, yaw);
        System.out.println("[SimTale] Bed entity registered: " + id + " at (" + bedPos.x + "," + bedPos.y + "," + bedPos.z + ") total=" + BedRegistry.size());
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, PersistentModel oldPm, @Nonnull PersistentModel newPm, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        onComponentRemoved(ref, oldPm, store, cb);
        onComponentAdded(ref, newPm, store, cb);
    }


    @Nonnull
    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, PersistentModel pm, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        if (pm.getModelReference().getModelAssetId() == null) return;
        String id = pm.getModelReference().getModelAssetId();
        if (!BedRegistry.isBedId(id)) return;

        BedPos cachedPos = trackedBeds.remove(ref);
        if (cachedPos != null) {
            BedRegistry.removeAt(cachedPos.x, cachedPos.y, cachedPos.z);
            System.out.println("[SimTale] Bed entity removed (from cache): " + id + " at (" + cachedPos.x + "," + cachedPos.y + "," + cachedPos.z + ") total=" + BedRegistry.size());
            return;
        }

        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d pos = tc.getPosition();
            BedRegistry.removeAt((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
            System.out.println("[SimTale] Bed entity removed (fallback): " + id + " total=" + BedRegistry.size());
        }
    }
}
