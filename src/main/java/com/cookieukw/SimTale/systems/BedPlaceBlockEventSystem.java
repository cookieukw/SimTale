package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.WorldUtil;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Registers furniture the moment it is placed.
 *
 * <p>This was a {@link com.hypixel.hytale.component.system.WorldEventSystem} and therefore never
 * ran. {@code PlaceBlockEvent} is fired *at* the entity that placed the block — it carries no
 * entity reference of its own, only the item, position and rotation — so it is delivered through
 * the entity dispatch path, to systems with a query. Across the 37k classes of the server jar,
 * every consumer of this event ({@code BlockHealthModule$PlaceBlockEventSystem},
 * {@code TriggerVolumeBlockEventSystems$BlockPlaced}) extends {@code EntityEventSystem}, and none
 * extends {@code WorldEventSystem}. The clearest proof is inside one vanilla file:
 * {@code TriggerVolumeBlockEventSystems$BlockPlaced} is an entity system while its sibling
 * {@code $EnvironmentBlockBroken} is a world system — the difference being whether the event has
 * an actor.
 *
 * <p>The failure was invisible for a long time because every way of inspecting the registries
 * ({@code debugbeds}, {@code debugchests}, {@code housecheck}, {@code chestcheck}, {@code rescan},
 * and the join handler) runs a radius scan first, so furniture always looked registered "on
 * placement". The blueprint marker was the first consumer with no scan behind it, which is why it
 * was the one that visibly did nothing.
 */
public class BedPlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    private static final SimLog LOGGER = SimLog.forClass(BedPlaceBlockEventSystem.class);
    // Unconditional (not gated behind /simtale debug) — temporary, to confirm handle() itself is
    // being invoked at all before chasing anything further downstream.
    private static final HytaleLogger RAW_LOGGER = HytaleLogger.forEnclosingClass();

    public BedPlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    /**
     * Matches the placer. {@code UUIDComponent} is what vanilla's own block-placed system queries
     * on; anything that can place a block carries one.
     */
    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return UUIDComponent.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event) {
        RAW_LOGGER.atInfo().log("SimTale Debug: BedPlaceBlockEventSystem.handle() fired, targetBlock=" + event.getTargetBlock());

        Vector3i pos = event.getTargetBlock();

        World world = store.getExternalData().getWorld();

        // What is being placed comes from the item, not from the world.
        //
        // PlaceBlockEvent extends CancellableEcsEvent and exposes setTargetBlock/setRotation/
        // setConsumeItem — it fires *before* the block exists, so reading getBlockType at the
        // target position returns whatever was there previously (usually air). Every id test
        // below was therefore being run against the wrong block, which is why nothing was ever
        // registered on placement even once the event started arriving.
        String placedId = null;
        ItemStack inHand = event.getItemInHand();
        if (inHand != null) {
            placedId = inHand.getItemId();
        }
        if (placedId == null) {
            // Fallback for any path that fires post-placement after all.
            BlockType existing = world.getBlockType(pos.x, pos.y, pos.z);
            if (existing != null) placedId = existing.getId();
        }
        if (placedId == null) return;

        LOGGER.debug("[SimTale] Block placed: " + placedId + " isBed=" + BedRegistry.isBedId(placedId));

        // Everything below reads the world at the target position, and the block is not there yet.
        //
        // PlaceBlockEvent is a pre-event (see the class javadoc), so this handler runs in the
        // window where the placement can still be cancelled. Identifying *what* is being placed
        // works, because that comes off the item — but resolving the multi-block anchor, the bed
        // yaw, or asking the engine whether the block carries an item container all query a cell
        // that still holds air. Registration was landing on the wrong anchor, and chests fell
        // through to the name heuristic that was the original bug this handler exists to fix.
        //
        // Deferring to the world thread puts the work one step after the placement completes.
        // The deferred task re-reads the block first: if the event was cancelled downstream, or
        // the player broke it immediately, there is nothing to register and it bails.
        final Vector3i placedAt = new Vector3i(pos);
        WorldUtil.execute(() -> registerPlacedBlock(world, placedAt, placedId));

        // The blueprint preview stays on this side on purpose: it needs no world lookup at the
        // marker cell (the obstruction scan skips the anchor by design), and showing the hologram
        // in the same frame as the click is what makes the placement feel responsive.
        //
        // Blueprint marker block: the hologram preview shows up the instant this is placed, and
        // the obstruction check runs exactly once, right here — not continuously. An earlier
        // version had the preview follow the placing player around and re-check on every turn,
        // which meant re-scanning the whole prefab's footprint for obstructions several times a
        // second; expensive enough on its own to visibly stall the server. A block sidesteps all
        // of that: it just sits where it was placed, same as a scarecrow or a fishing post.
        if (isBlueprintMarker(placedId)) {
            LOGGER.info("[SimTale] Blueprint marker placed at ({},{},{}), block id '{}'",
                    pos.x, pos.y, pos.z, placedId);
            UUID siteId = ConstructionPreviewManager.idForBlock(pos);
            ConstructionSiteComponent site = ConstructionPreviewManager.start(siteId, "TavernHouse", pos);

            // Orient the house by where the placer is looking.
            //
            // ConstructionSiteComponent.facing defaults to NORTH and nothing on this path ever
            // changed it, so every marker-placed house came out facing north regardless of the
            // player. '/build' already did this correctly (BuildCommand:152) — this is the same
            // two lines, which is the point: the two entry points should not disagree about
            // orientation.
            TransformComponent placer = chunk.getComponent(index, TransformComponent.getComponentType());
            if (placer != null) {
                Rotation4 facing = Rotation4.fromYawDegrees(Math.toDegrees(placer.getRotation().yaw()));
                site.facing = facing;
                site.roofFacing = facing;
            }

            ConstructionHelper.placePreview(world, site);

            if (!site.isClear) {
                // The hologram *does* tint red/green (PrefabGhostHelper.TINT_BLOCKED/CLEAR), but
                // that is a biome tint — it only recolours grass/leaves/foliage, so on a
                // stone-and-wood prefab like this one the difference is barely visible. A chat
                // message is the only reliable way to actually tell the player.
                Message warning = Message.raw("[SimTale] A área ao redor do marcador de construção está obstruída — libere o espaço ou remova o bloco pra tentar em outro lugar.");
                Universe.get().getPlayers().forEach(p -> p.sendMessage(warning));
            }
        }
    }

    /**
     * Matches the blueprint marker no matter how the engine decorates the id.
     *
     * <p>This was an exact {@code "Blueprint_TavernHouse".equals(id)} — the only check in this
     * whole handler that did not go through a tolerant helper, and the only one that silently did
     * nothing. Hytale hands back state-variant ids with a {@code *} in front and the variant name
     * appended, and this block declares {@code "VariantRotation": "NESW"}, so what comes out of
     * {@code getBlockType} on a placed marker is not the bare asset name. Same family of bug as
     * {@code CropRegistry.isCropId} and {@code FarmlandRegistry.isFarmlandId}, which is why both
     * of those use {@code contains} instead of {@code startsWith}.
     *
     * <p>Stripping everything that is not a letter or digit also covers the {@code _} in the
     * asset name disappearing or being replaced, which is how the wedding ring id evaded three
     * separate guesses.
     */
    static boolean isBlueprintMarker(String id) {
        return AssetIds.matchesAsset(id, "Blueprint_TavernHouse");
    }

    /**
     * Registers furniture once the block genuinely exists, one step after the placement event.
     *
     * <p>{@code placedId} comes from the item in hand rather than being re-read here: state and
     * rotation variants mean the id in the world can differ from the id that was placed, and the
     * checks are calibrated against the latter.
     */
    private static void registerPlacedBlock(World world, Vector3i pos, String placedId) {
        if (world == null) return;

        // The event is cancellable. If something downstream vetoed the placement — or the player
        // broke the block in the meantime — the cell is empty and there is nothing to register.
        BlockType placed = world.getBlockType(pos.x, pos.y, pos.z);
        if (placed == null || placed.getId() == null || placed.getId().equalsIgnoreCase("Empty")) {
            return;
        }

        if (BedRegistry.isBedId(placedId)) {
            BedWorldBootstrap.registerBedAt(world, pos.x, pos.y, pos.z);
        }

        // Ask the engine whether the block holds an item container instead of guessing from its
        // name. The name heuristic silently missed any storage block Hytale does not happen to
        // call chest/barrel/cupboard/cabinet, which is why chestcheck reported nothing after
        // three chests had been placed — and it is exactly what this check fell back to while it
        // was running before the block existed.
        if (ChestRegistry.isContainerAt(world, pos.x, pos.y, pos.z)
                || ChestRegistry.isChestId(placedId)) {
            // Register the anchor so placement and removal agree on one position per chest.
            Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);
            ChestRegistry.add(anchor.x, anchor.y, anchor.z);
            LOGGER.debug("[SimTale] Chest registered from placement: {} at ({},{},{})",
                    placedId, anchor.x, anchor.y, anchor.z);
        }

        if (CropRegistry.isCropId(placedId)) {
            CropRegistry.add(pos.x, pos.y, pos.z);
        }

        if (FarmlandRegistry.isFarmlandId(placedId)) {
            FarmlandRegistry.add(pos.x, pos.y, pos.z);
        }

        if (FishingPostRegistry.isFishingPostId(placedId)) {
            FishingPostRegistry.registerAt(world, pos.x, pos.y, pos.z);
        }

        if (LumberPostRegistry.isLumberPostId(placedId)) {
            LumberPostRegistry.registerAt(world, pos.x, pos.y, pos.z);
        }

        if (FarmPostRegistry.isFarmPostId(placedId)) {
            // The scarecrow is 3 blocks tall — anchor first, or each constituent block becomes
            // its own separate (and redundant) registered post.
            Vector3i scarecrowAnchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);
            FarmPostRegistry.registerAt(scarecrowAnchor.x, scarecrowAnchor.y, scarecrowAnchor.z);
        }
    }
}