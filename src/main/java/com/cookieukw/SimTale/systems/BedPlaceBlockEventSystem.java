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

        // Without this a bed placed by hand was never registered. The only paths that populated
        // BedRegistry were the radius scan (which runs solely inside /simtale housecheck) and the
        // entity system (which covers beds that are entities, not blocks), so in a fresh world no
        // NPC could ever claim a bed. It looked like it worked in older worlds only because the
        // registries are static and a housecheck had already been run there.
        if (BedRegistry.isBedId(type.getId())) {
            BedWorldBootstrap.registerBedAt(world, pos.x, pos.y, pos.z);
        }

        // Ask the engine whether the block holds an item container instead of guessing from its
        // name. The name heuristic silently missed any storage block Hytale does not happen to
        // call chest/barrel/cupboard/cabinet, which is why chestcheck reported nothing after
        // three chests had been placed.
        if (ChestRegistry.isContainerAt(world, pos.x, pos.y, pos.z)
                || ChestRegistry.isChestId(type.getId())) {
            // Register the anchor so placement and removal agree on one position per chest.
            Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);
            ChestRegistry.add(anchor.x, anchor.y, anchor.z);
            LOGGER.debug("[SimTale] Chest registered from placement: {} at ({},{},{})",
                    type.getId(), anchor.x, anchor.y, anchor.z);
        }

        if (CropRegistry.isCropId(type.getId())) {
            CropRegistry.add(pos.x, pos.y, pos.z);
        }
        
        if (FarmlandRegistry.isFarmlandId(type.getId())) {
            FarmlandRegistry.add(pos.x, pos.y, pos.z);
        }
    }
}