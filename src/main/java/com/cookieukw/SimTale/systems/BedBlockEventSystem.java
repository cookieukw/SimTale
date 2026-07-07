package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.hypixel.hytale.component.query.Query;

public class BedBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BedBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return com.hypixel.hytale.component.Archetype.empty();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        Vector3i pos = event.getTargetBlock();
        if (pos != null) {
            commandBuffer.getExternalData().getWorld().execute(() -> {
                BlockType bType = store.getExternalData().getWorld().getBlockType(pos.x, pos.y, pos.z);
                if (bType == null || bType.getId() == null || !BedRegistry.isBedId(bType.getId())) {
                    BedRegistry.removeAt(pos.x, pos.y, pos.z);
                }
            });
        }
    }
}
