package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.World;
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
        World world = store.getExternalData().getWorld();

        // Furniture spans several blocks, and the break event reports whichever one was hit.
        // Registries key on the anchor, so resolve it before removing; otherwise breaking a
        // chest from its far side leaves a phantom entry and NPCs keep walking to it.
        Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);

        BedRegistry.removeAt(anchor.x, anchor.y, anchor.z);
        ChestRegistry.removeAt(anchor.x, anchor.y, anchor.z);

        // Crops and farmland are single blocks, so they use the hit position directly.
        CropRegistry.removeAt(pos.x, pos.y, pos.z);
        FarmlandRegistry.removeAt(pos.x, pos.y, pos.z);
        FishingPostRegistry.removeAt(pos.x, pos.y, pos.z);
        LumberPostRegistry.removeAt(pos.x, pos.y, pos.z);
        // The post itself may still stand while its registered tree gets chopped (by a player or
        // the lumberjack NPC it sent) — that leaves the post pointing at an empty spot forever
        // unless the tree's own removal deregisters it too.
        LumberPostRegistry.removeByTree(pos.x, pos.y, pos.z);
        FarmPostRegistry.removeAt(pos.x, pos.y, pos.z);
    }
}
