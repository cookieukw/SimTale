package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;


public final class BedWorldBootstrap {
    private BedWorldBootstrap() {
    }

    public static void bootstrapLoadedRadius(World world, Vector3d center, int radius) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);
        
        int bedsFound = BedRegistry.size();
        System.out.println("[SimTale-DEBUG] Starting simple radius scan around (" + px + "," + py + "," + pz + ") with radius " + radius);
        
        // Scan a cube around the player position
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = Math.max(0, py - 16); y <= Math.min(319, py + 16); y++) {
                    BlockType type = world.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;
                    if (!BedRegistry.isBedId(type.getId())) continue;
                    
                    boolean isPrimary = isPrimaryBedBlock(world, x, y, z);
                    System.out.println("[SimTale-DEBUG] Block scan found bed-like block: '" + type.getId() + "' at (" + x + "," + y + "," + z + ") isPrimary=" + isPrimary);
                    if (isPrimary) continue;
                    
                    // Estimate yaw based on the neighboring bed block orientation
                    float yaw = 0f;
                    if (isBed(world.getBlockType(x + 1, y, z)) || isBed(world.getBlockType(x - 1, y, z))) {
                        yaw = (float) (Math.PI / 2.0); // oriented along X-axis (90 degrees in radians)
                    } else if (isBed(world.getBlockType(x, y, z + 1)) || isBed(world.getBlockType(x, y, z - 1))) {
                        yaw = 0f;  // oriented along Z-axis (0 degrees in radians)
                    }
                    
                    BedRegistry.addOrReplace(x, y, z, yaw);
                }
            }
        }
        
        int newBeds = BedRegistry.size() - bedsFound;
        System.out.println("[SimTale-DEBUG] Simple scan finished: found " + newBeds + " new beds. Total beds: " + BedRegistry.size());
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
