package com.cookieukw.SimTale.systems;

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

    public BedPlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        Vector3i pos = event.getTargetBlock();

        World world = store.getExternalData().getWorld();
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null || type.getId() == null) return;

        System.out.println("[SimTale] Block placed: " + type.getId() + " isBed=" + BedRegistry.isBedId(type.getId()));

        if (BedRegistry.isBedId(type.getId())) {
            BedRegistry.addOrReplace(pos.x, pos.y, pos.z, 0f);
        }

        if (ChestRegistry.isChestId(type.getId())) {
            ChestRegistry.add(pos.x, pos.y, pos.z);
        }

        if (CropRegistry.isCropId(type.getId())) {
            CropRegistry.add(pos.x, pos.y, pos.z);
        }
        
        if (FarmlandRegistry.isFarmlandId(type.getId())) {
            FarmlandRegistry.add(pos.x, pos.y, pos.z);
        }
    }
}