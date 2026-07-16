package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabBlock;
import com.cookieukw.SimTale.core.PrefabManager;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

public class ConstructionHelper {

    private static final String FOOTPRINT_MARKER = "Soil_Clay_Smooth_Red";
    private static final String BODY_MARKER = "Soil_Clay_Smooth_Lime";
    private static final String ROOF_MARKER = "Soil_Clay_Smooth_Orange";

    public static void clearPreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;
        
        for (long packed : site.previewBody) {
            int[] pos = unpack(packed);
            world.setBlock(pos[0], pos[1], pos[2], "Empty");
        }
        site.previewBody.clear();

        for (long packed : site.previewRoof) {
            int[] pos = unpack(packed);
            world.setBlock(pos[0], pos[1], pos[2], "Empty");
        }
        site.previewRoof.clear();
    }

    public static void placePreview(World world, ConstructionSiteComponent site) {
        if (site == null) return;
        
        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null || prefab.getBlocks().isEmpty()) {
            return;
        }

        // Clear any old preview first
        clearPreview(world, site);

        // Find prefab bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PrefabBlock b : prefab.getBlocks()) {
            if (b.getX() < minX) minX = b.getX();
            if (b.getX() > maxX) maxX = b.getX();
            if (b.getY() < minY) minY = b.getY();
            if (b.getY() > maxY) maxY = b.getY();
            if (b.getZ() < minZ) minZ = b.getZ();
            if (b.getZ() > maxZ) maxZ = b.getZ();
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
            
            // Only preview shell (outer walls) and footprint to avoid rendering filled inside blocks
            boolean isShell = (b.getX() == minX || b.getX() == maxX || b.getZ() == minZ || b.getZ() == maxZ);

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

            String blockType = b.isFootprint ? FOOTPRINT_MARKER : BODY_MARKER;
            world.setBlock(wx, wy, wz, blockType);
            site.previewBody.add(pack(wx, wy, wz));
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

            world.setBlock(wx, wy, wz, ROOF_MARKER);
            site.previewRoof.add(pack(wx, wy, wz));
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
