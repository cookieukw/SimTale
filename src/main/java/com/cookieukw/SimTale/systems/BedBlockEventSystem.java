package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Archetype;
public class BedBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BedBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        Vector3i pos = event.getTargetBlock();
        BedRegistry.removeAt(pos.x, pos.y, pos.z);
        BedRegistry.removeAt(pos.x - 1, pos.y, pos.z);
        BedRegistry.removeAt(pos.x + 1, pos.y, pos.z);
        BedRegistry.removeAt(pos.x, pos.y, pos.z - 1);
        BedRegistry.removeAt(pos.x, pos.y, pos.z + 1);
    }
}
