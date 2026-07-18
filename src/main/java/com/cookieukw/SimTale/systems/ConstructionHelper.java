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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ConstructionHelper {

    private static final String FOOTPRINT_MARKER = "Soil_Clay_Smooth_Red";
    private static final String BODY_MARKER = "Soil_Clay_Smooth_Lime";
    private static final String ROOF_MARKER = "Soil_Clay_Smooth_Orange";

    private static class YBounds {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
    }

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

    private static void restoreBlock(World world, ConstructionSiteComponent site, int x, int y, int z, long packed) {
        ConstructionSiteComponent.OriginalBlockState original = site.originalBlocks.remove(packed);
        if (original != null && original.type != null && original.type.getId() != null) {
            world.setBlock(x, y, z, original.type.getId(), original.rotation);
        } else {
            world.setBlock(x, y, z, "Empty");
        }
    }

    public static void placePreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;
        
        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null || prefab.getBlocks().isEmpty()) {
            return;
        }

        // Clear any old preview first
        clearPreview(world, site);

        // Fetch player position to create a collision-free bubble
        Vector3d playerPos = null;
        if (site.ownerId != null) {
            Ref<EntityStore> playerRef = world.getEntityStore().getRefFromUUID(site.ownerId);
            if (playerRef != null && playerRef.isValid()) {
                TransformComponent tc = world.getEntityStore().getStore().getComponent(playerRef, TransformComponent.getComponentType());
                if (tc != null) {
                    playerPos = tc.getPosition();
                }
            }
        }

        // Find prefab bounds globally
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        // Find bounds per Y level to calculate accurate walls
        Map<Integer, YBounds> yBoundsMap = new HashMap<>();

        for (PrefabBlock b : prefab.getBlocks()) {
            if (b.getX() < minX) minX = b.getX();
            if (b.getX() > maxX) maxX = b.getX();
            if (b.getY() < minY) minY = b.getY();
            if (b.getY() > maxY) maxY = b.getY();
            if (b.getZ() < minZ) minZ = b.getZ();
            if (b.getZ() > maxZ) maxZ = b.getZ();

            YBounds yb = yBoundsMap.computeIfAbsent(b.getY(), k -> new YBounds());
            if (b.getX() < yb.minX) yb.minX = b.getX();
            if (b.getX() > yb.maxX) yb.maxX = b.getX();
            if (b.getZ() < yb.minZ) yb.minZ = b.getZ();
            if (b.getZ() > yb.maxZ) yb.maxZ = b.getZ();
        }
        int height = maxY - minY + 1;
        int roofStartY = maxY - Math.max(1, height / 3);

        List<PlacedPreviewBlock> bodyList = new ArrayList<>();
        List<PlacedPreviewBlock> roofList = new ArrayList<>();

        for (PrefabBlock b : prefab.getBlocks()) {
            int lx = b.getX() - minX;
            int ly = b.getY() - minY;
            int lz = b.getZ() - minZ;

            boolean isRoof = b.getY() >= roofStartY;
            boolean isFootprint = b.getY() == minY;
            
            // Only preview shell (outer walls) at this specific Y level, and footprint to avoid rendering filled inside blocks
            YBounds yb = yBoundsMap.get(b.getY());
            boolean isShell = yb != null && (b.getX() == yb.minX || b.getX() == yb.maxX || b.getZ() == yb.minZ || b.getZ() == yb.maxZ);

            if (isRoof) {
                Vector3i rot = rotate(new Vector3i(lx, ly, lz), site.roofFacing);
                roofList.add(new PlacedPreviewBlock(rot.x, rot.y, rot.z, false));
            } else if (isFootprint || isShell) {
                Vector3i rot = rotate(new Vector3i(lx, ly, lz), site.facing);
                bodyList.add(new PlacedPreviewBlock(rot.x, rot.y, rot.z, isFootprint));
            }
        }

        // Align Body/Footprint preview blocks to site anchor
        int minBodyX = Integer.MAX_VALUE, minBodyZ = Integer.MAX_VALUE;
        for (PlacedPreviewBlock b : bodyList) {
            if (b.x < minBodyX) minBodyX = b.x;
            if (b.z < minBodyZ) minBodyZ = b.z;
        }

        for (PlacedPreviewBlock b : bodyList) {
            int wx = site.anchor.x + (b.x - minBodyX);
            int wy = site.anchor.y + b.y; // Keep vertical height intact
            int wz = site.anchor.z + (b.z - minBodyZ);

            // Skip placing block if too close to the player to avoid collision
            if (playerPos != null) {
                double dx = wx - playerPos.x;
                double dy = wy - playerPos.y;
                double dz = wz - playerPos.z;
                if ((dx * dx + dy * dy + dz * dz) < 3.5 * 3.5) {
                    continue;
                }
            }

            String blockType = b.isFootprint ? FOOTPRINT_MARKER : BODY_MARKER;
            
            BlockType originalType = world.getBlockType(wx, wy, wz);
            int originalRotation = world.getBlockRotationIndex(wx, wy, wz);

            long packed = pack(wx, wy, wz);
            site.originalBlocks.putIfAbsent(packed, new ConstructionSiteComponent.OriginalBlockState(originalType, originalRotation));

            world.setBlock(wx, wy, wz, blockType);
            site.previewBody.add(packed);
        }

        // Align Roof preview blocks to site anchor
        int minRoofX = Integer.MAX_VALUE, minRoofZ = Integer.MAX_VALUE;
        for (PlacedPreviewBlock b : roofList) {
            if (b.x < minRoofX) minRoofX = b.x;
            if (b.z < minRoofZ) minRoofZ = b.z;
        }

        for (PlacedPreviewBlock b : roofList) {
            int wx = site.anchor.x + (b.x - minRoofX);
            int wy = site.anchor.y + b.y;
            int wz = site.anchor.z + (b.z - minRoofZ);

            // Skip placing block if too close to the player to avoid collision
            if (playerPos != null) {
                double dx = wx - playerPos.x;
                double dy = wy - playerPos.y;
                double dz = wz - playerPos.z;
                if ((dx * dx + dy * dy + dz * dz) < 3.5 * 3.5) {
                    continue;
                }
            }

            BlockType originalType = world.getBlockType(wx, wy, wz);
            int originalRotation = world.getBlockRotationIndex(wx, wy, wz);

            long packed = pack(wx, wy, wz);
            site.originalBlocks.putIfAbsent(packed, new ConstructionSiteComponent.OriginalBlockState(originalType, originalRotation));

            world.setBlock(wx, wy, wz, ROOF_MARKER);
            site.previewRoof.add(packed);
        }
    }

    public static Vector3i rotate(Vector3i local, Rotation4 facing) {
        return switch (facing) {
            case NORTH -> new Vector3i(local.x, local.y, local.z);
            case EAST -> new Vector3i(-local.z, local.y, local.x);
            case SOUTH -> new Vector3i(-local.x, local.y, -local.z);
            case WEST -> new Vector3i(local.z, local.y, -local.x);
        };
    }

    private static long pack(int x, int y, int z) {
        long bx = (long) x + 1_048_576L;
        long by = (long) y + 1_048_576L;
        long bz = (long) z + 1_048_576L;
        return (bx << 42) | (by << 21) | bz;
    }

    private static int[] unpack(long packed) {
        long mask = (1L << 21) - 1L;
        int bz = (int) (packed & mask);
        int by = (int) ((packed >> 21) & mask);
        int bx = (int) ((packed >> 42) & mask);
        return new int[]{
            bx - 1_048_576,
            by - 1_048_576,
            bz - 1_048_576
        };
    }

    private static class PlacedPreviewBlock {
        int x, y, z;
        boolean isFootprint;

        PlacedPreviewBlock(int x, int y, int z, boolean isFootprint) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.isFootprint = isFootprint;
        }
    }
}
