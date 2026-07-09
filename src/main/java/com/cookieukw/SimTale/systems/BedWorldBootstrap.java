package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3d;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;


public final class BedWorldBootstrap {
    private BedWorldBootstrap() {
    }

    public static void bootstrapLoadedRadius(World world, Vector3d center, int radius) {
        int sx = (int) Math.floor(center.x);
        int sz = (int) Math.floor(center.z);

        for (int cx = (sx - radius) >> 4; cx <= (sx + radius) >> 4; cx++) {
            for (int cz = (sz - radius) >> 4; cz <= (sz + radius) >> 4; cz++) {
                long index = ChunkUtil.indexChunk(cx, cz);
                WorldChunk chunk = world.getChunkIfInMemory(index);
                if (chunk == null) continue;
                scanChunk(chunk, cx, cz);
            }
        }
    }

    public static void scanChunk(WorldChunk chunk, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = 0; y <= 319; y++) {
                    BlockType type = chunk.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;
                    if (!BedRegistry.isBedId(type.getId())) continue;
                    if (isPrimaryBedBlock(chunk, x, y, z)) continue;
                    BedRegistry.addOrReplace(x, y, z, 0f);
                }
            }
        }
    }

    public static boolean isPrimaryBedBlock(WorldChunk chunk, int x, int y, int z) {
        BlockType type = chunk.getBlockType(x, y, z);
        if (!isBed(type)) {
            return false;
        }

        boolean posX = isBed(chunk.getBlockType(x + 1, y, z));
        boolean negX = isBed(chunk.getBlockType(x - 1, y, z));
        boolean posZ = isBed(chunk.getBlockType(x, y, z + 1));
        boolean negZ = isBed(chunk.getBlockType(x, y, z - 1));

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
