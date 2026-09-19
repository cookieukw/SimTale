package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3i;

/**
 * Discovers which block is the ANCHOR of a multi-block furniture piece.
 *
 * <h3>The problem this solves</h3>
 * Furniture pieces in Hytale do not occupy a single block. A bed occupies <b>six</b>. The game
 * represents this with an anchor block carrying the actual furniture, plus <i>filler</i> blocks
 * that merely reserve occupied space.
 *
 * <p>SimTale previously ignored this distinction and treated each block as an independent furniture piece.
 * The direct symptom showed up in {@code /simtale debugnear}: two physical beds became <b>twelve</b>
 * registered beds.
 *
 * <p>The real damage was during sleep. {@code BlockMountAPI.mountOnBlock} calculates where the body lies
 * from the mounting point declared in the asset — for the village bed,
 * {@code Beds: [{Offset {X:0.4, Y:0.4, Z:1.0}}]} — and that offset is measured <b>from the anchor block</b>.
 * Passing a filler offsets the body by exactly the distance from that filler to the anchor.
 *
 * <p>This explains why the NPC was always "almost" in the right place, and why position offsets never
 * fixed it: the error was not constant, but varied depending on which of the six blocks was chosen.
 *
 * <h3>How the engine flags this</h3>
 * Each filler stores an integer with the offset to its anchor. The game itself has an {@code /inspectfiller}
 * command that reads this via {@code BlockSection.getFiller(x,y,z)} + {@code FillerBlockUtil.unpackX/Y/Z}.
 * Doors already used the same mechanism — {@code DoorInteraction.DoorInfo} carries a {@code filler} field.
 */
public final class FurnitureAnchorHelper {

    private static final SimLog LOGGER = SimLog.forClass(FurnitureAnchorHelper.class);

    private FurnitureAnchorHelper() {
        // Utility class.
    }

    /**
     * Returns the anchor of the furniture piece occupying {@code pos}, or {@code pos} itself when
     * it is already the anchor (or when it cannot be determined).
     *
     * <p>Never returns null: when in doubt, returns the input. This avoids special cases for callers,
     * and worst-case behavior matches the behavior prior to this class's existence.
     */
    public static Vector3i anchorOf(World world, int x, int y, int z) {
        Vector3i entrada = new Vector3i(x, y, z);
        if (world == null) return entrada;

        try {
            int filler = readFiller(world, x, y, z);
            if (filler == 0) {
                // Zero means "not a filler" — this block is already the anchor.
                return entrada;
            }

            int dx = FillerBlockUtil.unpackX(filler);
            int dy = FillerBlockUtil.unpackY(filler);
            int dz = FillerBlockUtil.unpackZ(filler);

            /* The offset sign is not documented, and guessing wrong would point to the
            opposite side of the furniture piece. Instead of assuming, both directions are tested
            and the one that actually looks like an anchor is accepted: same block type and zero filler.
            */
            Vector3i menos = new Vector3i(x - dx, y - dy, z - dz);
            if (looksLikeAnchor(world, menos, x, y, z)) return menos;

            Vector3i mais = new Vector3i(x + dx, y + dy, z + dz);
            if (looksLikeAnchor(world, mais, x, y, z)) return mais;

            LOGGER.debug("[SimTale] filler at ({},{},{}) = {} did not lead to a valid anchor; using block itself.",
                    x, y, z, filler);
            return entrada;
        } catch (Exception e) {
            /* Unloaded chunk, missing section, divergent API: sticking with the original block is
            always better than aborting NPC sleep.
            */
            LOGGER.debug("[SimTale] failed to resolve anchor of ({},{},{}): {}", x, y, z, e.toString());
            return entrada;
        }
    }

    /** Convenience overload for callers with a {@link Vector3i}. */
    public static Vector3i anchorOf(World world, Vector3i pos) {
        return pos == null ? null : anchorOf(world, pos.x, pos.y, pos.z);
    }

    /** True when {@code pos} is a block of the same furniture piece and does not point to another anchor. */
    private static boolean looksLikeAnchor(World world, Vector3i pos, int origemX, int origemY, int origemZ) {
        if (pos.x == origemX && pos.y == origemY && pos.z == origemZ) return false;

        BlockType tipo = NPCMovementHelper.getBlockTypeSafe(world, pos.x, pos.y, pos.z);
        if (tipo == null || tipo.getId() == null) return false;

        BlockType origem = NPCMovementHelper.getBlockTypeSafe(world, origemX, origemY, origemZ);
        if (origem == null || origem.getId() == null) return false;

        /* Same id: blocks of a furniture piece share the same type. This avoids accepting an
        unrelated block that happens to sit at the calculated offset.
        */
        if (!tipo.getId().equals(origem.getId())) return false;

        // The anchor is nobody's filler.
        return readFiller(world, pos.x, pos.y, pos.z) == 0;
    }

    /**
     * Reads a block's filler integer.
     * <p>
     * Path copied from the game's own {@code /inspectfiller}: the information lives in the
     * {@code BlockSection}, not in the {@code World}, so we must traverse down to the chunk section.
     * The synchronous variant {@code getChunkSectionReferenceAtBlock} avoids dealing with
     * {@code CompletableFuture} in the middle of a tick.
     */
    private static int readFiller(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();

        Ref<ChunkStore> secaoRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (secaoRef == null || !secaoRef.isValid()) return 0;

        BlockSection secao = secaoRef.getStore().getComponent(secaoRef, BlockSection.getComponentType());
        if (secao == null) return 0;

        return secao.getFiller(x, y, z);
    }
}
