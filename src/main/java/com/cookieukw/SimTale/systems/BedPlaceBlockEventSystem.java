package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Archetype;

public class BedPlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public BedPlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        Vector3i pos = event.getTargetBlock();
        if (pos != null) {
            commandBuffer.getExternalData().getWorld().execute(() -> {
                BlockType bType = store.getExternalData().getWorld().getBlockType(pos.x, pos.y, pos.z);
                if (bType != null && bType.getId() != null && BedRegistry.isBedId(bType.getId())) {
                    BedRegistry.addOrReplace(pos.x, pos.y, pos.z, 0f);
                }
            });
        }
    }
}
