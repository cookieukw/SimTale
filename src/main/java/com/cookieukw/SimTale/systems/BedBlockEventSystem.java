package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

/**
 * Deregisters furniture when its block is broken.
 *
 * <p>Same conversion, same reason as {@link BedPlaceBlockEventSystem}: {@code BreakBlockEvent}
 * also has an actor (it carries the item in hand) and is delivered through the entity path.
 * {@code EnvironmentBreakBlockEvent} is the actor-less sibling and is the one that belongs on a
 * world system — which is exactly the split vanilla makes.
 *
 * <p>Consequence while this was broken: breaking a bed or a chest left the registry entry behind,
 * so NPCs kept walking to furniture that no longer existed until the next radius scan rebuilt the
 * registry from the world.
 */
public class BedBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BedBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return UUIDComponent.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event) {
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
        // Registered by anchor (the scarecrow is 3 blocks tall) — remove by anchor too, or
        // breaking a non-anchor block of it leaves the registration behind.
        FarmPostRegistry.removeAt(anchor.x, anchor.y, anchor.z);

        // Breaking a blueprint marker cancels its preview — safe to call unconditionally, same
        // as every removeAt above: a no-op if nothing was ever registered at this position (e.g.
        // it was some other block, or the site had already been confirmed via '/build start' /
        // the right-click confirm and so is no longer a pending session at all).
        ConstructionPreviewManager.clear(ConstructionPreviewManager.idForBlock(pos), world);
    }
}
