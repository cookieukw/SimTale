package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.component.query.Query;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

import com.hypixel.hytale.math.vector.Rotation3f;

public class BedRegistrySystem extends RefChangeSystem<EntityStore, PersistentModel> {

    public static final Set<BedPos> BEDS = Collections.synchronizedSet(new HashSet<>());

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
        if (pm.getModelReference().getModelAssetId() != null) {
            String mName = pm.getModelReference().getModelAssetId().toLowerCase();
            if (mName.contains("bed") || mName.contains("cama") || mName.contains("furniture_village_bed")) {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    Vector3d bedPos = tc.getPosition();
                    Rotation3f rot = tc.getRotation();
                    float yaw = rot.yaw();
                    BedPos bp = new BedPos((int) Math.floor(bedPos.x), (int) Math.floor(bedPos.y), (int) Math.floor(bedPos.z), yaw);
                    BEDS.add(bp);
                    System.out.println("[SimTale] Bed added to registry at: " + bp.x + ", " + bp.y + ", " + bp.z + " with yaw: " + yaw + " (Total active: " + BEDS.size() + ")");
                }
            }
        }
    }

    @Override
    public void onComponentSet(@NonNullDecl Ref<EntityStore> ref, PersistentModel oldPm, @NonNullDecl PersistentModel newPm, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> cb) {
        // Unlikely to change models, but if it ceases to be a bed or becomes one:
        onComponentRemoved(ref, oldPm, store, cb);
        onComponentAdded(ref, newPm, store, cb);
    }

    @Override
    public void onComponentRemoved(@NonNullDecl Ref<EntityStore> ref, PersistentModel pm, @NonNullDecl Store<EntityStore> store, @NonNullDecl CommandBuffer<EntityStore> cb) {
        if (pm.getModelReference().getModelAssetId() != null) {
            String mName = pm.getModelReference().getModelAssetId().toLowerCase();
            if (mName.contains("bed") || mName.contains("cama") || mName.contains("furniture_village_bed")) {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null) {
                    Vector3d bedPos = tc.getPosition();
                    BedPos bp = new BedPos((int) Math.floor(bedPos.x), (int) Math.floor(bedPos.y), (int) Math.floor(bedPos.z));
                    BEDS.remove(bp);
                    System.out.println("[SimTale] Bed removed from registry at: " + bp.x + ", " + bp.y + ", " + bp.z + " (Total active: " + BEDS.size() + ")");
                }
            }
        }
    }
}
