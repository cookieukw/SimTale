package com.cookieukw.SimTale.systems;


import java.util.UUID;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import org.joml.Vector3i;


public final class BedWorldBootstrap {
    private static final SimLog LOGGER = SimLog.forClass(BedWorldBootstrap.class);
    private BedWorldBootstrap() {
    }

    public static void bootstrapLoadedRadius(World world, Vector3d center, int radius) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);
        
        int bedsFound = BedRegistry.size();
        int chestsFound = ChestRegistry.size();
        int fishingPostsFound = FishingPostRegistry.POSTS.size();
        int lumberPostsFound = LumberPostRegistry.POSTS.size();
        int farmPostsFound = FarmPostRegistry.POSTS.size();
        int farmlandFound = FarmlandRegistry.FARMLAND.size();
        int cropsFound = CropRegistry.CROPS.size();
        int bathsFound = BathRegistry.BATHS.size();
        int leisureFound = LeisureRegistry.LEISURE_BLOCKS.size();
        int markersFound = 0;
        LOGGER.debug("[SimTale-DEBUG] Starting simple radius scan around (" + px + "," + py + "," + pz + ") with radius " + radius);

        // Scan a cube around the player position
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = Math.max(0, py - 16); y <= Math.min(319, py + 16); y++) {
                    BlockType type = world.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;

                    if (BedRegistry.isBedId(type.getId())) {
                        registerBedAt(world, x, y, z);
                        continue;
                    }

                    // Chests the player placed before the server came up were invisible to the
                    // NPCs, since only the place event ever registered them.
                    if (ChestRegistry.isContainerAt(world, x, y, z)) {
                        Vector3i chestAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        ChestRegistry.add(chestAnchor.x, chestAnchor.y, chestAnchor.z);
                    }

                    // Work posts (fishing/lumber/farm) have the exact same gap the chests did:
                    // registered only by the place event, so a fresh server boot forgot every one
                    // placed in an earlier session even though the block was still standing there.
                    if (FishingPostRegistry.isFishingPostId(type.getId())) {
                        FishingPostRegistry.registerAt(world, x, y, z);
                    } else if (LumberPostRegistry.isLumberPostId(type.getId())) {
                        LumberPostRegistry.registerAt(world, x, y, z);
                    } else if (FarmPostRegistry.isFarmPostId(type.getId())) {
                        Vector3i scarecrowAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        FarmPostRegistry.registerAt(scarecrowAnchor.x, scarecrowAnchor.y, scarecrowAnchor.z);
                    }
                    
                    if (BathRegistry.isBathId(type.getId())) {
                        Vector3i bathAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        BathRegistry.add(bathAnchor.x, bathAnchor.y, bathAnchor.z);
                    }
                    if (LeisureRegistry.isLeisureId(type.getId())) {
                        Vector3i leisureAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        LeisureRegistry.add(leisureAnchor.x, leisureAnchor.y, leisureAnchor.z, LeisureRegistry.getHobbyForId(type.getId()));
                    }

                    // Same gap again, this time on the farmland/crops themselves — tilled soil or
                    // a planted crop from an earlier session was invisible to scanForFarmland/
                    // scanForCrops (which only ever read the registry, never the live world)
                    // until the server was restarted again after a place event re-registered it.
                    if (FarmlandRegistry.isFarmlandId(type.getId())) {
                        FarmlandRegistry.add(x, y, z);
                    } else if (CropRegistry.isCropId(type.getId())) {
                        CropRegistry.add(x, y, z);
                    }

                    // Preview sessions live only in memory, so a marker block survived a restart
                    // with no hologram and no site behind it — the block was still standing but
                    // nothing could be confirmed or forced from it. Rebuilding the session from
                    // the block is the same trick the beds, chests and posts above already use:
                    // the world is the source of truth, the registry is just a cache of it.
                    if (BedPlaceBlockEventSystem.isBlueprintMarker(type.getId())) {
                        Vector3i markerPos = new Vector3i(x, y, z);
                        UUID siteId = ConstructionPreviewManager.idForBlock(markerPos);
                        if (ConstructionPreviewManager.get(siteId) == null) {
                            ConstructionSiteComponent site =
                                    ConstructionPreviewManager.start(siteId, "TavernHouse", markerPos);
                            ConstructionHelper.placePreview(world, site);
                            markersFound++;
                        }
                    }
                }
            }
        }

        int newBeds = BedRegistry.size() - bedsFound;
        int newChests = ChestRegistry.size() - chestsFound;
        int newFishingPosts = FishingPostRegistry.POSTS.size() - fishingPostsFound;
        int newLumberPosts = LumberPostRegistry.POSTS.size() - lumberPostsFound;
        int newFarmPosts = FarmPostRegistry.POSTS.size() - farmPostsFound;
        int newFarmland = FarmlandRegistry.FARMLAND.size() - farmlandFound;
        int newCrops = CropRegistry.CROPS.size() - cropsFound;
        int newBaths = BathRegistry.BATHS.size() - bathsFound;
        int newLeisure = LeisureRegistry.LEISURE_BLOCKS.size() - leisureFound;
        if (markersFound > 0) {
            LOGGER.info("[SimTale] Scan restored {} blueprint marker preview(s) from blocks left in the world", markersFound);
        }
        if (newBeds > 0 || newChests > 0 || newFishingPosts > 0 || newLumberPosts > 0 || newFarmPosts > 0
                || newFarmland > 0 || newCrops > 0 || newBaths > 0 || newLeisure > 0) {
            // At info level: this now runs on join, and it is the one line that tells whether the
            // world's existing furniture was picked up at all.
            LOGGER.info("[SimTale] Scan found {} new beds, {} new chests, {} new fishing posts, {} new lumber posts, {} new farm posts, {} new farmland, {} new crops, {} new baths, {} new leisure. Totals: {} beds, {} chests, {} fishing, {} lumber, {} farm, {} farmland, {} crops, {} baths, {} leisure",
                    newBeds, newChests, newFishingPosts, newLumberPosts, newFarmPosts, newFarmland, newCrops, newBaths, newLeisure,
                    BedRegistry.size(), ChestRegistry.size(), FishingPostRegistry.POSTS.size(), LumberPostRegistry.POSTS.size(), FarmPostRegistry.POSTS.size(),
                    FarmlandRegistry.FARMLAND.size(), CropRegistry.CROPS.size(), BathRegistry.BATHS.size(), LeisureRegistry.LEISURE_BLOCKS.size());
        } else {
            LOGGER.debug("[SimTale-DEBUG] Scan finished: nothing new. Totals: "
                    + BedRegistry.size() + " beds, " + ChestRegistry.size() + " chests");
        }
    }

    /**
     * Registers the bed that owns the given block, resolving the anchor and the lying axis.
     *
     * <p>Shared by the radius scan and by {@code BedPlaceBlockEventSystem} so a bed placed by hand
     * and a bed found by the scan end up as the exact same entry. Any of the six blocks may be
     * passed in; they all converge on one registration.
     *
     * <p>A bed spans SIX blocks. Registering each of them as an independent bed is what made
     * {@code /simtale debugnear} report twelve beds where there were two — and, worse, made the NPC
     * mount on an arbitrary block of the furniture instead of the anchor. Since the asset's mount
     * point is measured from the anchor, mounting on a filler offsets the sleeping pose by the
     * distance from that filler to it.
     *
     * <p>The earlier heuristic ({@code isPrimaryBedBlock}, "the neighbour is at +X or +Z") tried to
     * guess the anchor from the neighbourhood. Now the engine answers, through the same filler data
     * the game's own {@code /inspectfiller} reads.
     */
    public static void registerBedAt(World world, int x, int y, int z) {
        Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);

        float yaw = 0f;
        if (isBed(world.getBlockType(anchor.x + 1, anchor.y, anchor.z))
                || isBed(world.getBlockType(anchor.x - 1, anchor.y, anchor.z))) {
            yaw = (float) (Math.PI / 2.0); // lying along the X axis
        } else if (isBed(world.getBlockType(anchor.x, anchor.y, anchor.z + 1))
                || isBed(world.getBlockType(anchor.x, anchor.y, anchor.z - 1))) {
            yaw = 0f; // lying along the Z axis
        }

        // addOrReplace dedupes by position, so the six blocks converge on a single record.
        BedRegistry.addOrReplace(anchor.x, anchor.y, anchor.z, yaw);
    }

    public static boolean isPrimaryBedBlock(World world, int x, int y, int z) {
        BlockType type = world.getBlockType(x, y, z);
        if (!isBed(type)) {
            return false;
        }

        boolean posX = isBed(world.getBlockType(x + 1, y, z));
        boolean negX = isBed(world.getBlockType(x - 1, y, z));
        boolean posZ = isBed(world.getBlockType(x, y, z + 1));
        boolean negZ = isBed(world.getBlockType(x, y, z - 1));

        int adjacentBeds = 0;
        if (posX) adjacentBeds++;
        if (negX) adjacentBeds++;
        if (posZ) adjacentBeds++;
        if (negZ) adjacentBeds++;

        if (adjacentBeds != 1) {
            return false;
        }

        return posX || posZ;
    }

    private static boolean isBed(BlockType type) {
        return type != null && type.getId() != null && BedRegistry.isBedId(type.getId());
    }
}
