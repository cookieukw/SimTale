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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

public class BedRegistrySystem extends RefChangeSystem<EntityStore, PersistentModel> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
                    LOGGER.atInfo().log("[SimTale] Bed added to registry at: " + bp.x + ", " + bp.y + ", " + bp.z + " with yaw: " + yaw + " (Total active: " + BEDS.size() + ")");
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
                    LOGGER.atInfo().log("[SimTale] Bed removed from registry at: " + bp.x + ", " + bp.y + ", " + bp.z + " (Total active: " + BEDS.size() + ")");
                }
            }
        }
    }

    public static class BedChunkLoadSystem extends HolderSystem<ChunkStore> {
        @Override
        public Query<ChunkStore> getQuery() {
            return WorldChunk.getComponentType();
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<ChunkStore> holder, @Nonnull com.hypixel.hytale.component.AddReason reason, @Nonnull com.hypixel.hytale.component.Store<ChunkStore> store) {
            WorldChunk chunk = holder.getComponent(WorldChunk.getComponentType());
            if (chunk != null) {
                long chunkIndex = chunk.getIndex();
                int cx = ChunkUtil.xOfChunkIndex(chunkIndex);
                int cz = ChunkUtil.zOfChunkIndex(chunkIndex);
                int startX = cx << 4;
                int startZ = cz << 4;

                for (int x = startX; x < startX + 16; x++) {
                    for (int z = startZ; z < startZ + 16; z++) {
                        for (int y = 0; y < 320; y++) {
                            BlockType bType = chunk.getBlockType(x, y, z);
                            if (bType != null && bType.getId() != null) {
                                String name = bType.getId().toLowerCase();
                                if (name.contains("bed") || name.contains("cama") || name.contains("furniture_village_bed")) {
                                    BedPos bp = new BedPos(x, y, z, 0f);
                                    synchronized (BEDS) {
                                        boolean exists = false;
                                        for (BedPos existing : BEDS) {
                                            if (existing.x == x && existing.y == y && existing.z == z) {
                                                exists = true;
                                                break;
                                            }
                                        }
                                        if (!exists) {
                                            BEDS.add(bp);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<ChunkStore> holder, @Nonnull com.hypixel.hytale.component.RemoveReason reason, @Nonnull com.hypixel.hytale.component.Store<ChunkStore> store) {
            WorldChunk chunk = holder.getComponent(WorldChunk.getComponentType());
            if (chunk != null) {
                long chunkIndex = chunk.getIndex();
                synchronized (BEDS) {
                    BEDS.removeIf(bp -> ChunkUtil.indexChunkFromBlock(bp.x, bp.z) == chunkIndex);
                }
            }
        }
    }

    public static class BedBlockPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        public BedBlockPlaceSystem() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
            Vector3i pos = event.getTargetBlock();
            commandBuffer.getExternalData().getWorld().execute(() -> {
                BlockType bType = store.getExternalData().getWorld().getBlockType(pos.x, pos.y, pos.z);
                if (bType != null && bType.getId() != null) {
                    String name = bType.getId().toLowerCase();
                    if (name.contains("bed") || name.contains("cama") || name.contains("furniture_village_bed")) {
                        BedPos bp = new BedPos(pos.x, pos.y, pos.z, 0f);
                        synchronized (BEDS) {
                            boolean exists = false;
                            for (BedPos existing : BEDS) {
                                if (existing.x == bp.x && existing.y == bp.y && existing.z == bp.z) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                BEDS.add(bp);
                            }
                        }
                    }
                }
            });
        }

        @Override
        @Nullable
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    public static class BedBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        public BedBlockBreakSystem() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store, @Nonnull com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> commandBuffer, @Nonnull com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event) {
            org.joml.Vector3i pos = event.getTargetBlock();
            BlockType bType = event.getBlockType();
            if (bType != null && bType.getId() != null) {
                String name = bType.getId().toLowerCase();
                if (name.contains("bed") || name.contains("cama") || name.contains("furniture_village_bed")) {
                    synchronized (BEDS) {
                        BEDS.removeIf(bp -> bp.x == pos.x && bp.y == pos.y && bp.z == pos.z);
                    }
                }
            }
        }

        @Override
        @Nullable
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
