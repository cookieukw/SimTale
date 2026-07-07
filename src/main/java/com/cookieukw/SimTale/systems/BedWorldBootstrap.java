package com.cookieukw.SimTale.systems;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3d;

public final class BedWorldBootstrap {
    private BedWorldBootstrap() {}

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
                    BedRegistry.addOrReplace(x, y, z, 0f);
                }
            }
        }
    }
}
