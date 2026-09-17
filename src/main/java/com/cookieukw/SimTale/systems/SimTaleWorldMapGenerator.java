package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.systems.VillageManager.Village;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.WorldMapSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.palette.BitFieldArr;
import com.hypixel.hytale.server.core.universe.world.map.WorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * Wraps Hytale's chunk world map generator to overlay village territory onto the map textures.
 * Chunks belonging to a village have their pixels tinted with a golden amber wash, and chunks on
 * the outer edge of the village territory receive a solid glowing border.
 */
public class SimTaleWorldMapGenerator implements IWorldMap {

    private static final SimLog LOGGER = SimLog.forClass(SimTaleWorldMapGenerator.class);

    // Color constants in Hytale's packed RGBA format: (r << 24) | (g << 16) | (b << 8) | a
    // Village tint: warm golden amber (R: 255, G: 205, B: 60)
    private static final int TINT_R = 255;
    private static final int TINT_G = 205;
    private static final int TINT_B = 60;
    private static final float TINT_STRENGTH = 0.28f;
    private static final float TERRAIN_STRENGTH = 1.0f - TINT_STRENGTH;

    // Glowing border color: solid bright gold/amber (R: 255, G: 225, B: 90, A: 255)
    private static final int BORDER_COLOR = ((255 & 0xFF) << 24)
            | ((225 & 0xFF) << 16)
            | ((90 & 0xFF) << 8)
            | 0xFF;

    private final IWorldMap delegate;

    public SimTaleWorldMapGenerator(@Nonnull IWorldMap delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate IWorldMap must not be null");
    }

    public IWorldMap getDelegate() {
        return delegate;
    }

    @Nonnull
    @Override
    public WorldMapSettings getWorldMapSettings() {
        return delegate.getWorldMapSettings();
    }

    @Nonnull
    @Override
    public CompletableFuture<WorldMap> generate(@Nonnull World world, int imageWidth, int imageHeight,
            @Nonnull LongSet chunksToGenerate) {
        return delegate.generate(world, imageWidth, imageHeight, chunksToGenerate).thenApply(worldMap -> {
            if (worldMap == null) return null;
            try {
                Long2ObjectMap<MapImage> chunks = worldMap.getChunks();
                if (chunks != null && !chunks.isEmpty()) {
                    applyVillageOverlay(chunks);
                }
            } catch (Throwable t) {
                LOGGER.error("[SimTale] Error applying village overlay to map chunks: {}", t.toString());
            }
            return worldMap;
        });
    }

    @Nonnull
    @Override
    public CompletableFuture<Map<String, MapMarker>> generatePointsOfInterest(@Nonnull World world) {
        return delegate.generatePointsOfInterest(world);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    /**
     * Intercepts the generated MapImage for every chunk and applies the village tint and boundary.
     */
    private void applyVillageOverlay(Long2ObjectMap<MapImage> chunks) {
        for (Long2ObjectMap.Entry<MapImage> entry : chunks.long2ObjectEntrySet()) {
            long chunkIdx = entry.getLongKey();
            int chunkX = ChunkUtil.xOfChunkIndex(chunkIdx);
            int chunkZ = ChunkUtil.zOfChunkIndex(chunkIdx);

            Village village = VillageManager.getVillageForChunk(chunkX, chunkZ);
            if (village == null) continue;

            MapImage image = entry.getValue();
            if (image == null || image.palette == null || image.packedIndices == null) continue;

            boolean westBorder = !VillageManager.isSameVillageChunk(village, chunkX - 1, chunkZ);
            boolean eastBorder = !VillageManager.isSameVillageChunk(village, chunkX + 1, chunkZ);
            boolean northBorder = !VillageManager.isSameVillageChunk(village, chunkX, chunkZ - 1);
            boolean southBorder = !VillageManager.isSameVillageChunk(village, chunkX, chunkZ + 1);

            tintAndBorderChunk(image, westBorder, eastBorder, northBorder, southBorder);
        }
    }

    /**
     * Tints the existing palette with the village amber color, and optionally paints border pixels
     * onto outer perimeter edges.
     */
    private static void tintAndBorderChunk(MapImage image, boolean westBorder, boolean eastBorder,
            boolean northBorder, boolean southBorder) {
        int[] oldPalette = image.palette;
        int oldLen = oldPalette.length;
        if (oldLen == 0) return;

        int[] tintedPalette = new int[oldLen];
        for (int i = 0; i < oldLen; i++) {
            int c = oldPalette[i];
            int r = (c >>> 24) & 0xFF;
            int g = (c >>> 16) & 0xFF;
            int b = (c >>> 8) & 0xFF;
            int a = c & 0xFF;

            int nr = (int) (r * TERRAIN_STRENGTH + TINT_R * TINT_STRENGTH);
            int ng = (int) (g * TERRAIN_STRENGTH + TINT_G * TINT_STRENGTH);
            int nb = (int) (b * TERRAIN_STRENGTH + TINT_B * TINT_STRENGTH);

            tintedPalette[i] = ((nr & 0xFF) << 24) | ((ng & 0xFF) << 16) | ((nb & 0xFF) << 8) | a;
        }

        boolean hasBorder = westBorder || eastBorder || northBorder || southBorder;
        if (!hasBorder) {
            image.palette = tintedPalette;
            return;
        }

        int width = image.width;
        int height = image.height;
        int totalPixels = width * height;
        if (totalPixels <= 0) return;

        int borderIndex = oldLen;
        int[] newPalette = Arrays.copyOf(tintedPalette, oldLen + 1);
        newPalette[borderIndex] = BORDER_COLOR;

        int newBits = calculateBitsRequired(newPalette.length);

        try {
            BitFieldArr oldArr = new BitFieldArr(image.bitsPerIndex, totalPixels);
            oldArr.set(image.packedIndices);

            BitFieldArr newArr = new BitFieldArr(newBits, totalPixels);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;
                    boolean isEdge = (x == 0 && westBorder)
                            || (x == width - 1 && eastBorder)
                            || (y == 0 && northBorder)
                            || (y == height - 1 && southBorder);

                    if (isEdge) {
                        newArr.set(idx, borderIndex);
                    } else {
                        newArr.set(idx, oldArr.get(idx));
                    }
                }
            }

            image.palette = newPalette;
            image.bitsPerIndex = (byte) newBits;
            image.packedIndices = newArr.get();
        } catch (Exception e) {
            // Fallback: apply tinted palette if bit packing fails
            image.palette = tintedPalette;
        }
    }

    private static int calculateBitsRequired(int colors) {
        if (colors <= 16) return 4;
        if (colors <= 256) return 8;
        if (colors <= 4096) return 12;
        return 16;
    }
}
