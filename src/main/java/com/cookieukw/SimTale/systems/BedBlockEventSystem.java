package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

public class BedBlockEventSystem extends WorldEventSystem<EntityStore, BreakBlockEvent> {

    public BedBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        Vector3i pos = event.getTargetBlock();
        BedRegistry.removeAt(pos.x, pos.y, pos.z);
        BedRegistry.removeAt(pos.x - 1, pos.y, pos.z);
        BedRegistry.removeAt(pos.x + 1, pos.y, pos.z);
        BedRegistry.removeAt(pos.x, pos.y, pos.z - 1);
        BedRegistry.removeAt(pos.x, pos.y, pos.z + 1);

        ChestRegistry.removeAt(pos.x, pos.y, pos.z);
        CropRegistry.removeAt(pos.x, pos.y, pos.z);
        FarmlandRegistry.removeAt(pos.x, pos.y, pos.z);
    }
}
