package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabBlock;
import com.cookieukw.SimTale.core.PrefabManager;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.joml.Vector3i;

/**
 * Handles the preview for a construction site: scanning the target volume for obstructions and
 * driving the engine hologram that shows the player what will be built.
 *
 * <p>Also owns the local-to-world coordinate maths ({@link #mapperFor}) that both the hologram
 * and the NPC builders go through, so the preview and the finished building cannot disagree.
 */
public final class ConstructionHelper {

    private ConstructionHelper() {
        // Utility class, not meant to be instantiated.
    }

    private static final String EMPTY_BLOCK_ID = "Empty";

    // Coordinate packing: 3 signed ints -> 1 long key, 21 bits per axis (+/-1,048,576 range).
    private static final int COORD_BITS = 21;
    private static final long COORD_OFFSET = 1L << 20;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1L;

    /* ---------------------------------------------------------------------
    Public API
    ---------------------------------------------------------------------
    */

    /** Removes the site's hologram, plus any leftover marker blocks from a pre-migration preview. */
    public static void clearPreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;

        PrefabGhostHelper.hide(world, site);

        /* Everything below only ever finds something for a preview that was placed by the old
        marker-block code earlier in this same session. New previews touch no blocks at all.
        */
        for (long packed : site.previewBody) {
            int[] pos = unpack(packed);
            restoreBlock(world, site, pos[0], pos[1], pos[2], packed);
        }
        site.previewBody.clear();

        for (long packed : site.previewRoof) {
            int[] pos = unpack(packed);
            restoreBlock(world, site, pos[0], pos[1], pos[2], packed);
        }
        site.previewRoof.clear();
        site.originalBlocks.clear();
    }

    /**
     * Recomputes and shows the preview for {@code site}: clears the old one, checks whether the
     * target volume is obstructed, then displays the prefab as an engine hologram.
     *
     * <p>Previously this painted an edge-only wireframe box out of coloured marker blocks, which
     * meant the "preview" was a real, destructive edit to the world and could only ever suggest
     * the building's bounding box. The hologram shows the actual prefab and leaves the terrain
     * untouched — see {@link PrefabGhostHelper}.
     */
    public static void placePreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;

        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null || prefab.getBlocks().isEmpty()) {
            return;
        }

        clearPreview(world, site);

        site.isClear = !hasObstruction(world, site, prefab);

        PrefabGhostHelper.show(world, site, prefab, site.isClear);
    }

    /**
     * Re-runs the obstruction scan for a preview that is already showing, and touches the
     * hologram only if clear/blocked actually flipped since the last check.
     *
     * <p>Called periodically by {@link ConstructionPreviewSweepSystem} so a preview reflects
     * blocks placed or removed near it after it first went up — {@link #placePreview} only ever
     * checks once, at the moment the preview appears. Comparing before writing matters here in a
     * way it didn't for {@link #placePreview}: that runs once per user action, but this runs on a
     * timer against every pending site, so re-spawning the hologram (via {@link PrefabGhostHelper#show})
     * on every sweep instead of only on an actual change would mean periodic despawn/respawn
     * churn for every preview in the world, all over again.
     */
    public static void recheckObstruction(World world, ConstructionSiteComponent site) {
        if (site == null) return;

        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null || prefab.getBlocks().isEmpty()) {
            return;
        }

        boolean nowClear = !hasObstruction(world, site, prefab);
        if (nowClear == site.isClear) return;

        site.isClear = nowClear;
        PrefabGhostHelper.show(world, site, prefab, site.isClear);
    }

    /**
     * Maps prefab-local coordinates to offsets from the site anchor, applying a facing.
     *
     * <p>Shared on purpose. The preview and the builders used to derive positions independently:
     * the preview rotated through {@link #rotate}, while ConstructionSystem placed blocks at a
     * plain {@code anchor + local}, ignoring facing entirely. With a wireframe box the mismatch
     * was invisible; with a hologram of the real prefab, a rotated preview that builds unrotated
     * would be obvious and infuriating. One mapper now feeds both.
     *
     * <p>Built once per prefab rather than per block: the rotation offset depends only on the
     * prefab's bounds and the facing, so recomputing it inside the loop would rescan every block
     * for every block.
     */
    public record OffsetMapper(Rotation4 facing) {
        public Vector3i offset(int lx, int ly, int lz) {
            return rotate(new Vector3i(lx, ly, lz), facing);
        }
    }

    /**
     * Builds the {@link OffsetMapper} for a prefab rotated to {@code facing}.
     *
     * <p>The anchor is the prefab's own local origin — the house rotates <em>around the marker</em>
     * instead of being re-normalised to sit in the +X/+Z quadrant of it.
     *
     * <p>It used to subtract the rotated box's minimum corner, which kept the whole footprint on
     * the positive side of the anchor no matter which way it faced. That is tidy in world
     * coordinates and useless in practice: the house always grew towards +X/+Z, so which part of
     * it landed next to the marker changed with every rotation, and the player had no way to
     * predict where the walls would end up before committing. Anchoring the local origin instead
     * means the same corner of the house is always the block you placed.
     */
    public static OffsetMapper mapperFor(Prefab prefab, Rotation4 facing) {
        return new OffsetMapper(facing);
    }

    /** Rotates a local offset around the Y axis to match one of the four cardinal facings. */
    public static Vector3i rotate(Vector3i local, Rotation4 facing) {
        return switch (facing) {
            case NORTH -> new Vector3i(local.x, local.y, local.z);
            case EAST -> new Vector3i(-local.z, local.y, local.x);
            case SOUTH -> new Vector3i(-local.x, local.y, -local.z);
            case WEST -> new Vector3i(local.z, local.y, -local.x);
        };
    }

    /* ---------------------------------------------------------------------
    Bounds & rotation helpers
    ---------------------------------------------------------------------
    */

    private record BoxSize(int sizeX, int sizeY, int sizeZ) {}

    private static BoxSize computeBoxSize(Prefab prefab) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (PrefabBlock block : prefab.getBlocks()) {
            minX = Math.min(minX, block.getX());
            maxX = Math.max(maxX, block.getX());
            minY = Math.min(minY, block.getY());
            maxY = Math.max(maxY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxZ = Math.max(maxZ, block.getZ());
        }

        return new BoxSize(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    /* ---------------------------------------------------------------------
    Obstruction scan
    ---------------------------------------------------------------------
    */

    private static boolean hasObstruction(World world, ConstructionSiteComponent site, Prefab prefab) {
        BoxSize size = computeBoxSize(prefab);
        OffsetMapper mapper = mapperFor(prefab, site.facing);

        for (int lx = 0; lx < size.sizeX(); lx++) {
            for (int ly = 0; ly < size.sizeY(); ly++) {
                for (int lz = 0; lz < size.sizeZ(); lz++) {
                    Vector3i offset = mapper.offset(lx, ly, lz);
                    int wx = site.anchor.x + offset.x;
                    int wy = site.anchor.y + offset.y;
                    int wz = site.anchor.z + offset.z;
                    /* The anchor cell itself is where whatever placed this preview is standing
                    (a player, or — for a marker-block-triggered site — the marker block
                    itself). Either way it's occupied on purpose and will be consumed/replaced
                    once building actually starts, not a real obstruction to warn about.
                    */
                    if (wx == site.anchor.x && wy == site.anchor.y && wz == site.anchor.z) continue;
                    if (isOccupied(world, wx, wy, wz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isOccupied(World world, int x, int y, int z) {
        return isOccupied(NPCMovementHelper.getBlockTypeSafe(world, x, y, z));
    }

    /**
     * True when the cell holds a real block rather than air.
     * <p>
     * This was called {@code isEmptyBlock} while returning the exact opposite, so every call site
     * read as a negation of what it did. It happened to be used correctly, but only barely.
     */
    private static boolean isOccupied(BlockType type) {
        return type != null && !type.getId().equalsIgnoreCase(EMPTY_BLOCK_ID);
    }

    /* ---------------------------------------------------------------------
    Legacy marker cleanup
    ---------------------------------------------------------------------
    */

    private static void restoreBlock(World world, ConstructionSiteComponent site, int x, int y, int z, long packed) {
        ConstructionSiteComponent.OriginalBlockState original = site.originalBlocks.remove(packed);
        if (original != null && original.type != null && original.type.getId() != null) {
            world.setBlock(x, y, z, original.type.getId(), original.rotation);
        } else {
            world.setBlock(x, y, z, EMPTY_BLOCK_ID);
        }
    }

    /* ---------------------------------------------------------------------
    Coordinate packing (x, y, z -> single long key for map storage)
    ---------------------------------------------------------------------
    */

    private static int[] unpack(long packed) {
        long bz = packed & COORD_MASK;
        long by = (packed >> COORD_BITS) & COORD_MASK;
        long bx = (packed >> (COORD_BITS * 2)) & COORD_MASK;
        return new int[]{
            (int) (bx - COORD_OFFSET),
            (int) (by - COORD_OFFSET),
            (int) (bz - COORD_OFFSET)
        };
    }
}