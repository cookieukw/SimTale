package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

public class BedPlaceBlockEventSystem extends WorldEventSystem<EntityStore, PlaceBlockEvent> {
    private static final SimLog LOGGER = SimLog.forClass(BedPlaceBlockEventSystem.class);

    public BedPlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        Vector3i pos = event.getTargetBlock();

        World world = store.getExternalData().getWorld();
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null || type.getId() == null) return;

        LOGGER.debug("[SimTale] Block placed: " + type.getId() + " isBed=" + BedRegistry.isBedId(type.getId()));



        if (ChestRegistry.isChestId(type.getId())) {
            // Register the anchor so placement and removal agree on one position per chest.
            Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);
            ChestRegistry.add(anchor.x, anchor.y, anchor.z);
        }

        if (CropRegistry.isCropId(type.getId())) {
            CropRegistry.add(pos.x, pos.y, pos.z);
        }
        
        if (FarmlandRegistry.isFarmlandId(type.getId())) {
            FarmlandRegistry.add(pos.x, pos.y, pos.z);
        }
    }
}