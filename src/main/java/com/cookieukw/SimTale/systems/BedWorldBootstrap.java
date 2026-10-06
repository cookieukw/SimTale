package com.cookieukw.SimTale.systems;


import com.cookieukw.SimTale.core.WorldUtil;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
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

    /* What the scan needs to know about a block type, computed once per type id instead of
    running ~10 id classifiers (each lower-casing / normalising the id) on every one of the
    ~140k blocks of a join scan. */
    private static final int F_BED = 1, F_FISHING = 1 << 1, F_LUMBER = 1 << 2, F_FARM_POST = 1 << 3,
            F_BATH = 1 << 4, F_LEISURE = 1 << 5, F_CHAIR = 1 << 6, F_FARMLAND = 1 << 7, F_CROP = 1 << 8,
            F_MARKER = 1 << 9, F_BLOCK_ENTITY = 1 << 10;
    private static final Map<String, Integer> FLAGS = new ConcurrentHashMap<>();

    private static int flagsOf(BlockType type) {
        String id = type.getId();
        Integer cached = FLAGS.get(id);
        if (cached != null) return cached;
        int f = 0;
        if (BedRegistry.isBedId(id)) f |= F_BED;
        if (FishingPostRegistry.isFishingPostId(id)) f |= F_FISHING;
        if (LumberPostRegistry.isLumberPostId(id)) f |= F_LUMBER;
        if (FarmPostRegistry.isFarmPostId(id)) f |= F_FARM_POST;
        if (BathRegistry.isBathId(id)) f |= F_BATH;
        if (LeisureRegistry.isLeisureId(id)) f |= F_LEISURE;
        if (ChairRegistry.isChair(id)) f |= F_CHAIR;
        if (FarmlandRegistry.isFarmlandId(id)) f |= F_FARMLAND;
        if (CropRegistry.isCropId(id)) f |= F_CROP;
        if (BedPlaceBlockEventSystem.isBlueprintMarker(id)) f |= F_MARKER;
        /* Only a block type with a block-entity template can carry an ItemContainerBlock, so the
        per-position container lookup is skipped for everything else (terrain, mostly). */
        if (type.getBlockEntity() != null) f |= F_BLOCK_ENTITY;
        FLAGS.put(id, f);
        return f;
    }

    /** Registry sizes before a scan, to report what the scan added. */
    private static final class Before {
        final int beds = BedRegistry.size(), chests = ChestRegistry.size(),
                fishing = FishingPostRegistry.POSTS.size(), lumber = LumberPostRegistry.POSTS.size(),
                farmPosts = FarmPostRegistry.POSTS.size(), farmland = FarmlandRegistry.FARMLAND.size(),
                crops = CropRegistry.CROPS.size(), baths = BathRegistry.BATHS.size(),
                leisure = LeisureRegistry.LEISURE_BLOCKS.size();
        int markers;

        void log() {
            int newBeds = BedRegistry.size() - beds;
            int newChests = ChestRegistry.size() - chests;
            int newFishingPosts = FishingPostRegistry.POSTS.size() - fishing;
            int newLumberPosts = LumberPostRegistry.POSTS.size() - lumber;
            int newFarmPosts = FarmPostRegistry.POSTS.size() - farmPosts;
            int newFarmland = FarmlandRegistry.FARMLAND.size() - farmland;
            int newCrops = CropRegistry.CROPS.size() - crops;
            int newBaths = BathRegistry.BATHS.size() - baths;
            int newLeisure = LeisureRegistry.LEISURE_BLOCKS.size() - leisure;
            if (markers > 0) {
                LOGGER.info("[SimTale] Scan restored {} blueprint marker preview(s) from blocks left in the world", markers);
            }
            if (newBeds > 0 || newChests > 0 || newFishingPosts > 0 || newLumberPosts > 0 || newFarmPosts > 0
                    || newFarmland > 0 || newCrops > 0 || newBaths > 0 || newLeisure > 0) {
                /* At info level: this now runs on join, and it is the one line that tells whether the
                world's existing furniture was picked up at all.
                */
                LOGGER.info("[SimTale] Scan found {} new beds, {} new chests, {} new fishing posts, {} new lumber posts, {} new farm posts, {} new farmland, {} new crops, {} new baths, {} new leisure. Totals: {} beds, {} chests, {} fishing, {} lumber, {} farm, {} farmland, {} crops, {} baths, {} leisure",
                        newBeds, newChests, newFishingPosts, newLumberPosts, newFarmPosts, newFarmland, newCrops, newBaths, newLeisure,
                        BedRegistry.size(), ChestRegistry.size(), FishingPostRegistry.POSTS.size(), LumberPostRegistry.POSTS.size(), FarmPostRegistry.POSTS.size(),
                        FarmlandRegistry.FARMLAND.size(), CropRegistry.CROPS.size(), BathRegistry.BATHS.size(), LeisureRegistry.LEISURE_BLOCKS.size());
            } else {
                LOGGER.debug("[SimTale-DEBUG] Scan finished: nothing new. Totals: "
                        + BedRegistry.size() + " beds, " + ChestRegistry.size() + " chests");
            }
        }
    }

    /** Scans the whole cube now (the diagnostic commands read the registries right after). */
    public static void bootstrapLoadedRadius(World world, Vector3d center, int radius) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);
        Before before = new Before();
        LOGGER.debug("[SimTale-DEBUG] Starting simple radius scan around (" + px + "," + py + "," + pz + ") with radius " + radius);
        before.markers += scanColumns(world, px - radius, px + radius, py, pz, radius);
        before.log();
    }

    /** Columns of x per world-thread task in {@link #bootstrapLoadedRadiusSpread}. */
    private static final int SLICE_COLUMNS = 8;
    private static final long SLICE_DELAY_MS = 50;

    /**
     * Same scan, split into slices of {@link #SLICE_COLUMNS} x-columns run about one tick apart,
     * so the join scan (~140k blocks at radius 32) no longer lands on a single tick as a visible
     * hitch. Nothing waits on its result.
     */
    public static void bootstrapLoadedRadiusSpread(World world, Vector3d center, int radius) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);
        Before before = new Before();
        int xEnd = px + radius;
        int slice = 0;
        for (int x0 = px - radius; x0 <= xEnd; x0 += SLICE_COLUMNS, slice++) {
            final int from = x0;
            final int to = Math.min(xEnd, x0 + SLICE_COLUMNS - 1);
            final boolean last = to == xEnd;
            WorldUtil.executeLater(() -> {
                before.markers += scanColumns(world, from, to, py, pz, radius);
                if (last) before.log();
            }, slice * SLICE_DELAY_MS);
        }
    }

    /** Registers the furniture, posts, farmland and markers in x = xFrom..xTo of the cube. */
    private static int scanColumns(World world, int xFrom, int xTo, int py, int pz, int radius) {
        int markersFound = 0;
        for (int x = xFrom; x <= xTo; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = Math.max(0, py - 16); y <= Math.min(319, py + 16); y++) {
                    BlockType type = world.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;
                    int f = flagsOf(type);
                    if (f == 0) continue;

                    if ((f & F_BED) != 0) {
                        registerBedAt(world, x, y, z);
                        continue;
                    }

                    /* Chests the player placed before the server came up were invisible to the
                    NPCs, since only the place event ever registered them.
                    */
                    if ((f & F_BLOCK_ENTITY) != 0 && ChestRegistry.isContainerAt(world, x, y, z)) {
                        Vector3i chestAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        ChestRegistry.add(chestAnchor.x, chestAnchor.y, chestAnchor.z);
                    }

                    /* Work posts (fishing/lumber/farm) have the exact same gap the chests did:
                    registered only by the place event, so a fresh server boot forgot every one
                    placed in an earlier session even though the block was still standing there.
                    */
                    if ((f & F_FISHING) != 0) {
                        FishingPostRegistry.registerAt(world, x, y, z);
                    } else if ((f & F_LUMBER) != 0) {
                        LumberPostRegistry.registerAt(world, x, y, z);
                    } else if ((f & F_FARM_POST) != 0) {
                        Vector3i scarecrowAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        FarmPostRegistry.registerAt(scarecrowAnchor.x, scarecrowAnchor.y, scarecrowAnchor.z);
                    }

                    if ((f & F_BATH) != 0) {
                        Vector3i bathAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        BathRegistry.add(bathAnchor.x, bathAnchor.y, bathAnchor.z);
                    }
                    if ((f & F_LEISURE) != 0) {
                        Vector3i leisureAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        LeisureRegistry.add(leisureAnchor.x, leisureAnchor.y, leisureAnchor.z, LeisureRegistry.getHobbyForId(type.getId()));
                    }
                    if ((f & F_CHAIR) != 0) {
                        Vector3i chairAnchor = FurnitureAnchorHelper.anchorOf(world, x, y, z);
                        ChairRegistry.add(chairAnchor.x, chairAnchor.y, chairAnchor.z);
                    }

                    /* Same gap again, this time on the farmland/crops themselves — tilled soil or
                    a planted crop from an earlier session was invisible to scanForFarmland/
                    scanForCrops (which only ever read the registry, never the live world)
                    until the server was restarted again after a place event re-registered it.
                    */
                    if ((f & F_FARMLAND) != 0) {
                        FarmlandRegistry.add(x, y, z);
                    } else if ((f & F_CROP) != 0) {
                        CropRegistry.add(x, y, z);
                    }

                    /* Preview sessions live only in memory, so a marker block survived a restart
                    with no hologram and no site behind it — the block was still standing but
                    nothing could be confirmed or forced from it. Rebuilding the session from
                    the block is the same trick the beds, chests and posts above already use:
                    the world is the source of truth, the registry is just a cache of it.
                    */
                    if ((f & F_MARKER) != 0) {
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
        return markersFound;
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
