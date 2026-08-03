package com.cookieukw.SimTale.systems;

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
        LOGGER.debug("[SimTale-DEBUG] Starting simple radius scan around (" + px + "," + py + "," + pz + ") with radius " + radius);
        
        // Scan a cube around the player position
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = Math.max(0, py - 16); y <= Math.min(319, py + 16); y++) {
                    BlockType type = world.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;
                    if (!BedRegistry.isBedId(type.getId())) continue;

                    registerBedAt(world, x, y, z);
                }
            }
        }
        
        int newBeds = BedRegistry.size() - bedsFound;
        if (newBeds > 0) {
            // At info level: this now runs on join, and it is the one line that tells whether the
            // world's existing beds were picked up at all.
            LOGGER.info("[SimTale] Scan found {} new beds. Total: {}", newBeds, BedRegistry.size());
        } else {
            LOGGER.debug("[SimTale-DEBUG] Simple scan finished: no new beds. Total: " + BedRegistry.size());
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
