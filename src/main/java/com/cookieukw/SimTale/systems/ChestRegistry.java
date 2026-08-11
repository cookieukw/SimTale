package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.SimLog;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.db.ChestData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestRegistry {
    private static final SimLog LOGGER = SimLog.forClass(ChestRegistry.class);
    private ChestRegistry() {}

    public static final Set<HouseBlockPos> CHESTS = Collections.synchronizedSet(new HashSet<>());

    /**
     * Name-based guess, kept only as a fallback for callers that have an id and no world.
     *
     * <p>Unreliable by nature: it depends on Hytale naming its storage blocks with one of these
     * words, and a chest called something like {@code Furniture_Storage_Crate} slips through. Use
     * {@link #isContainerAt} whenever a world reference is available.
     */
    public static boolean isChestId(String id) {
        return AssetIds.containsAny(id, "chest", "barrel", "cupboard", "cabinet");
    }

    /**
     * Authoritative check: a block is storage when the engine gives it an item container.
     *
     * <p>This is the same component the NPCs already read when looking for food, so registration
     * and consumption can no longer disagree — under the old name heuristic a block could be
     * skipped at registration and still hold food an NPC would happily have eaten.
     */
    public static boolean isContainerAt(World world, int x, int y, int z) {
        if (world == null) return false;
        try {
            return BlockModule.getComponent(
                    ItemContainerBlock.getComponentType(), world, x, y, z) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void add(int x, int y, int z) {
        if (CHESTS.add(new HouseBlockPos(x, y, z))) {
            // Only on a genuine addition: the boot sweep re-registers everything it walks past, and
            // writing on every one of those would be a disk write per chest per join.
            persist(x, y, z);
        }
        LOGGER.debug("[SimTale] Chest registered at (" + x + ", " + y + ", " + z + "). Total: " + CHESTS.size());
    }

    public static void removeAt(int x, int y, int z) {
        CHESTS.remove(new HouseBlockPos(x, y, z));
        try {
            SimNPCPersistence.worldShell().core(ChestData.class).discard(ChestData.key(x, y, z));
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao apagar o registro do bau ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    private static void persist(int x, int y, int z) {
        try {
            SimNPCPersistence.worldShell().core(ChestData.class)
                    .preserve(ChestData.key(x, y, z), new ChestData(x, y, z));
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao salvar o registro do bau ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    /**
     * Reloads the registry from disk, dropping entries whose block is gone.
     *
     * <p>The validation is the important half. Restoring a saved list blindly is what brought five
     * dead babies back to life earlier in this project — a stale record is worse than a missing one,
     * because the rest of the mod trusts the registry completely. A chest whose chunk is not loaded
     * is kept: absent is not the same as gone, and dropping it would quietly un-register every chest
     * away from spawn on each join.
     */
    public static void loadAll(World world) {
        try {
            List<ChestData> all = SimNPCPersistence.worldShell().core(ChestData.class).extractAll();
            if (all == null) return;

            int restored = 0;
            int dropped = 0;
            for (ChestData data : all) {
                if (data == null) continue;

                if (world != null
                        && world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(data.x, data.z)) != null
                        && !isContainerAt(world, data.x, data.y, data.z)) {
                    removeAt(data.x, data.y, data.z);
                    dropped++;
                    continue;
                }

                CHESTS.add(new HouseBlockPos(data.x, data.y, data.z));
                restored++;
            }
            LOGGER.info("[SimTale] {} bau(s) recarregado(s) do banco, {} descartado(s) por nao existirem mais",
                    restored, dropped);
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao recarregar os baus: {}", e.getMessage());
        }
    }

    public static int size() {
        return CHESTS.size();
    }
}
