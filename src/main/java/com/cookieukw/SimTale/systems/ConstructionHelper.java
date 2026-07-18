package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabBlock;
import com.cookieukw.SimTale.core.PrefabManager;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import org.joml.Vector3i;
import org.joml.Vector3d;

/**
 * Handles the ghost/wireframe preview for a construction site: scanning the
 * target volume for obstructions, painting an edge-only wireframe box in the
 * world, and restoring whatever blocks were temporarily replaced.
 */
public final class ConstructionHelper {

    private ConstructionHelper() {
        // Utility class, not meant to be instantiated.
    }

    private static final String EMPTY_BLOCK_ID = "Empty";
    private static final String PREVIEW_MARKER_CLEAR = "simtale:Green_block_preview";
    private static final String PREVIEW_MARKER_BLOCKED = "simtale:Red_block_preview";

    /** Cells within this radius of the owning player are skipped so the preview never traps them. */
    private static final double PLAYER_CLEARANCE_RADIUS = 3.5;
    private static final double PLAYER_CLEARANCE_RADIUS_SQ = PLAYER_CLEARANCE_RADIUS * PLAYER_CLEARANCE_RADIUS;

    // Coordinate packing: 3 signed ints -> 1 long key, 21 bits per axis (+/-1,048,576 range).
    private static final int COORD_BITS = 21;
    private static final long COORD_OFFSET = 1L << 20;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1L;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Removes any preview blocks currently placed for this site and restores what was underneath them. */
    public static void clearPreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;

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
     * Recomputes and paints the wireframe preview for {@code site}: clears the old preview,
     * checks whether the target volume is obstructed, then paints an edge-only box
     * (green if clear, red if blocked), skipping any cell too close to the owning player.
     */
    public static void placePreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;

        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null || prefab.getBlocks().isEmpty()) {
            return;
        }

        clearPreview(world, site);

        BoxSize size = computeBoxSize(prefab);
        RotationOffset offset = computeRotationOffset(size.sizeX(), size.sizeZ(), site.facing);

        boolean isClear = !hasObstruction(world, site, size, offset);
        site.isClear = isClear;

        Vector3d playerPos = resolvePlayerPosition(world, site);
        String markerBlockId = isClear ? PREVIEW_MARKER_CLEAR : PREVIEW_MARKER_BLOCKED;

        placeWireframeBlocks(world, site, size, offset, playerPos, markerBlockId);
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

    // ---------------------------------------------------------------------
    // Bounds & rotation helpers
    // ---------------------------------------------------------------------

    private record BoxSize(int sizeX, int sizeY, int sizeZ) {}

    private record RotationOffset(int minRotX, int minRotZ) {}

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

    /**
     * Finds the offset needed to keep a rotated box anchored at its original corner.
     * Only the four XZ corners are checked since rotation around Y never changes the Y component.
     */
    private static RotationOffset computeRotationOffset(int sizeX, int sizeZ, Rotation4 facing) {
        int minRotX = Integer.MAX_VALUE;
        int minRotZ = Integer.MAX_VALUE;

        for (int dx : new int[]{0, sizeX - 1}) {
            for (int dz : new int[]{0, sizeZ - 1}) {
                Vector3i rotated = rotate(new Vector3i(dx, 0, dz), facing);
                minRotX = Math.min(minRotX, rotated.x);
                minRotZ = Math.min(minRotZ, rotated.z);
            }
        }

        return new RotationOffset(minRotX, minRotZ);
    }

    private static Vector3i toWorldPos(int lx, int ly, int lz, ConstructionSiteComponent site, RotationOffset offset) {
        Vector3i rotated = rotate(new Vector3i(lx, ly, lz), site.facing);
        return new Vector3i(
            site.anchor.x + (rotated.x - offset.minRotX()),
            site.anchor.y + rotated.y,
            site.anchor.z + (rotated.z - offset.minRotZ())
        );
    }

    // ---------------------------------------------------------------------
    // Obstruction scan
    // ---------------------------------------------------------------------

    private static boolean hasObstruction(World world, ConstructionSiteComponent site, BoxSize size, RotationOffset offset) {
        for (int lx = 0; lx < size.sizeX(); lx++) {
            for (int ly = 0; ly < size.sizeY(); ly++) {
                for (int lz = 0; lz < size.sizeZ(); lz++) {
                    Vector3i pos = toWorldPos(lx, ly, lz, site, offset);
                    if (isObstructed(world, pos.x, pos.y, pos.z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isObstructed(World world, int x, int y, int z) {
        return !isEmptyBlock(world.getBlockType(x, y, z));
    }

    private static boolean isEmptyBlock(BlockType type) {
        return type == null || type.getId().equalsIgnoreCase(EMPTY_BLOCK_ID);
    }

    // ---------------------------------------------------------------------
    // Wireframe placement
    // ---------------------------------------------------------------------

    private static void placeWireframeBlocks(World world, ConstructionSiteComponent site, BoxSize size,
                                               RotationOffset offset, Vector3d playerPos, String markerBlockId) {
        for (int lx = 0; lx < size.sizeX(); lx++) {
            for (int ly = 0; ly < size.sizeY(); ly++) {
                for (int lz = 0; lz < size.sizeZ(); lz++) {
                    if (!isEdge(lx, ly, lz, size.sizeX(), size.sizeY(), size.sizeZ())) continue;

                    Vector3i pos = toWorldPos(lx, ly, lz, site, offset);
                    if (isTooCloseToPlayer(pos, playerPos)) continue;

                    tryPlaceMarker(world, site, pos, markerBlockId);
                }
            }
        }
    }

    /** True for cells on at least two axes of the bounding box, i.e. the box's edges/corners. */
    private static boolean isEdge(int lx, int ly, int lz, int sizeX, int sizeY, int sizeZ) {
        int edgesOnAxes = 0;
        if (lx == 0 || lx == sizeX - 1) edgesOnAxes++;
        if (ly == 0 || ly == sizeY - 1) edgesOnAxes++;
        if (lz == 0 || lz == sizeZ - 1) edgesOnAxes++;
        return edgesOnAxes >= 2;
    }

    private static boolean isTooCloseToPlayer(Vector3i pos, Vector3d playerPos) {
        if (playerPos == null) return false;

        double dx = pos.x - playerPos.x;
        double dy = pos.y - playerPos.y;
        double dz = pos.z - playerPos.z;
        return (dx * dx + dy * dy + dz * dz) < PLAYER_CLEARANCE_RADIUS_SQ;
    }

    /** Places the marker block only if the target cell is empty, saving what was there for later restoration. */
    private static void tryPlaceMarker(World world, ConstructionSiteComponent site, Vector3i pos, String markerBlockId) {
        BlockType originalType = world.getBlockType(pos.x, pos.y, pos.z);
        if (!isEmptyBlock(originalType)) return;

        int originalRotation = world.getBlockRotationIndex(pos.x, pos.y, pos.z);
        long packed = pack(pos.x, pos.y, pos.z);
        site.originalBlocks.putIfAbsent(packed, new ConstructionSiteComponent.OriginalBlockState(originalType, originalRotation));

        world.setBlock(pos.x, pos.y, pos.z, markerBlockId);
        site.previewBody.add(packed);
    }

    private static void restoreBlock(World world, ConstructionSiteComponent site, int x, int y, int z, long packed) {
        ConstructionSiteComponent.OriginalBlockState original = site.originalBlocks.remove(packed);
        if (original != null && original.type != null && original.type.getId() != null) {
            world.setBlock(x, y, z, original.type.getId(), original.rotation);
        } else {
            world.setBlock(x, y, z, EMPTY_BLOCK_ID);
        }
    }

    // ---------------------------------------------------------------------
    // Coordinate packing (x, y, z -> single long key for map storage)
    // ---------------------------------------------------------------------

    private static long pack(int x, int y, int z) {
        long bx = x + COORD_OFFSET;
        long by = y + COORD_OFFSET;
        long bz = z + COORD_OFFSET;
        return (bx << (COORD_BITS * 2)) | (by << COORD_BITS) | bz;
    }

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

    // ---------------------------------------------------------------------
    // Player context
    // ---------------------------------------------------------------------

    private static Vector3d resolvePlayerPosition(World world, ConstructionSiteComponent site) {
        if (site.ownerId == null) return null;

        Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(site.ownerId);
        if (playerRef == null || !playerRef.isValid()) return null;

        TransformComponent transform = world.getEntityStore().getStore().getComponent(playerRef, TransformComponent.getComponentType());
        return transform != null ? transform.getPosition() : null;
    }
}