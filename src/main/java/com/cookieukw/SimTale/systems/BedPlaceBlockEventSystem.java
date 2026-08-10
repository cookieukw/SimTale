package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;

public class BedPlaceBlockEventSystem extends WorldEventSystem<EntityStore, PlaceBlockEvent> {
    private static final SimLog LOGGER = SimLog.forClass(BedPlaceBlockEventSystem.class);
    // Unconditional (not gated behind /simtale debug) — temporary, to confirm handle() itself is
    // being invoked at all before chasing anything further downstream.
    private static final HytaleLogger RAW_LOGGER = HytaleLogger.forEnclosingClass();

    public BedPlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull PlaceBlockEvent event) {
        RAW_LOGGER.atInfo().log("SimTale Debug: BedPlaceBlockEventSystem.handle() fired, targetBlock=" + event.getTargetBlock());

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

        if (FishingPostRegistry.isFishingPostId(type.getId())) {
            FishingPostRegistry.registerAt(world, pos.x, pos.y, pos.z);
        }

        if (LumberPostRegistry.isLumberPostId(type.getId())) {
            LumberPostRegistry.registerAt(world, pos.x, pos.y, pos.z);
        }

        if (FarmPostRegistry.isFarmPostId(type.getId())) {
            // The scarecrow is 3 blocks tall — anchor first, or each constituent block becomes
            // its own separate (and redundant) registered post.
            Vector3i scarecrowAnchor = FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z);
            FarmPostRegistry.registerAt(scarecrowAnchor.x, scarecrowAnchor.y, scarecrowAnchor.z);
        }

        // Blueprint marker block: the hologram preview shows up the instant this is placed, and
        // the obstruction check runs exactly once, right here — not continuously. An earlier
        // version had the preview follow the placing player around and re-check on every turn,
        // which meant re-scanning the whole prefab's footprint for obstructions several times a
        // second; expensive enough on its own to visibly stall the server. A block sidesteps all
        // of that: it just sits where it was placed, same as a scarecrow or a fishing post.
        if (isBlueprintMarker(type.getId())) {
            LOGGER.info("[SimTale] Blueprint marker placed at ({},{},{}), block id '{}'",
                    pos.x, pos.y, pos.z, type.getId());
            UUID siteId = ConstructionPreviewManager.idForBlock(pos);
            ConstructionSiteComponent site = ConstructionPreviewManager.start(siteId, "TavernHouse", pos);
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
        if (id == null) return false;
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").contains("blueprinttavernhouse");
    }
}