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
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestRegistry {
    private static final SimLog LOGGER = SimLog.forClass(ChestRegistry.class);
    private ChestRegistry() {}

    public static final Set<HouseBlockPos> CHESTS = Collections.synchronizedSet(new HashSet<>());
    public static final Set<HouseBlockPos> SHARED_CHESTS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Set<HouseBlockPos>> CHESTS_BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<String, Set<HouseBlockPos>> SHARED_BY_WORLD = new ConcurrentHashMap<>();

    private static String worldKey(World world) {
        return world != null ? world.getName() : "";
    }

    private static Set<HouseBlockPos> chests(World world) {
        return CHESTS_BY_WORLD.computeIfAbsent(worldKey(world), _ -> Collections.synchronizedSet(new HashSet<>()));
    }

    private static Set<HouseBlockPos> sharedChests(World world) {
        return SHARED_BY_WORLD.computeIfAbsent(worldKey(world), _ -> Collections.synchronizedSet(new HashSet<>()));
    }

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

    /**
     * Whether an NPC may treat this chest as communal storage.
     *
     * <p>Two things make a chest communal:
     * <ul>
     *   <li>it was explicitly marked shared with {@code /simtale chestshare}; or</li>
     *   <li>it stands inside a village and belongs to no house. Those are the "global" chests:
     *       the player drops a chest in the village square, nobody owns it, and every resident
     *       may take food from it or leave surplus there.</li>
     * </ul>
     *
     * <p>The second case is computed from the live house/village state instead of being written
     * into {@link #SHARED_CHESTS}. That keeps it honest: move the chest into a house and it
     * becomes private again; level the last house and the village (and the global status) goes
     * away with it.
     */
    public static boolean isShared(HouseBlockPos pos) {
        return pos != null && (isExplicitlyShared(pos) || isVillageGlobal(pos));
    }

    public static boolean isShared(World world, HouseBlockPos pos) {
        return pos != null && (isExplicitlyShared(world, pos) || isVillageGlobal(world, pos));
    }

    public static boolean isShared(int x, int y, int z) {
        return isShared(new HouseBlockPos(x, y, z));
    }

    /** True only when {@code /simtale chestshare} (or a saved flag) marked this chest shared. */
    public static boolean isExplicitlyShared(HouseBlockPos pos) {
        return pos != null && SHARED_CHESTS.contains(pos);
    }

    public static boolean isExplicitlyShared(World world, HouseBlockPos pos) {
        return pos != null && sharedChests(world).contains(pos);
    }

    /**
     * A chest inside a village that is not part of any house.
     *
     * <p>This is the automatic "global chest" rule. {@link HouseManager#findHouseIdForChest}
     * deliberately resolves a chest that merely touches a room, so a chest placed inside a
     * bedroom stays private even when the house is inside the village.
     */
    public static boolean isVillageGlobal(HouseBlockPos pos) {
        if (pos == null) return false;
        if (HouseManager.findHouseIdForChest(pos) != null) return false;

        VillageManager.Village village = VillageManager.nearest(pos.x, pos.z);
        return village != null && village.contains(pos.x, pos.z);
    }

    public static boolean isVillageGlobal(World world, HouseBlockPos pos) {
        if (pos == null) return false;
        if (HouseManager.findHouseIdForChest(world, pos) != null) return false;
        VillageManager.Village village = VillageManager.nearest(pos.x, pos.z);
        return village != null && village.contains(pos.x, pos.z);
    }

    public static void setShared(World world, int x, int y, int z, boolean shared) {
        HouseBlockPos pos = new HouseBlockPos(x, y, z);
        chests(world).add(pos);
        if (shared) sharedChests(world).add(pos); else sharedChests(world).remove(pos);
        persist(world, x, y, z, shared);
    }

    public static void add(World world, int x, int y, int z) {
        if (chests(world).add(new HouseBlockPos(x, y, z))) persist(world, x, y, z);
    }

    private static void persist(World world, int x, int y, int z) {
        persist(world, x, y, z, sharedChests(world).contains(new HouseBlockPos(x, y, z)));
    }

    private static void persist(World world, int x, int y, int z, boolean shared) {
        try {
            SimNPCPersistence.worldShell(world).core(ChestData.class)
                    .preserve(ChestData.key(worldKey(world), x, y, z),
                            new ChestData(worldKey(world), x, y, z, shared));
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao salvar o registro do bau ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    public static void removeAt(World world, int x, int y, int z) {
        HouseBlockPos pos = new HouseBlockPos(x, y, z);
        chests(world).remove(pos);
        sharedChests(world).remove(pos);
        try {
            SimNPCPersistence.worldShell(world).core(ChestData.class)
                    .discard(ChestData.key(worldKey(world), x, y, z));
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao apagar o registro do bau ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    public static void setShared(int x, int y, int z, boolean shared) {
        HouseBlockPos pos = new HouseBlockPos(x, y, z);
        CHESTS.add(pos);
        if (shared) {
            SHARED_CHESTS.add(pos);
        } else {
            SHARED_CHESTS.remove(pos);
        }
        persist(x, y, z, shared);
        LOGGER.info("[SimTale] Bau em ({},{},{}) definido como compartilhado: {}", x, y, z, shared);
    }

    public static void add(int x, int y, int z) {
        if (CHESTS.add(new HouseBlockPos(x, y, z))) {
            /* Only on a genuine addition: the boot sweep re-registers everything it walks past, and
            writing on every one of those would be a disk write per chest per join.
            */
            persist(x, y, z);
        }
        LOGGER.debug("[SimTale] Chest registered at (" + x + ", " + y + ", " + z + "). Total: " + CHESTS.size());
    }

    public static void removeAt(int x, int y, int z) {
        HouseBlockPos pos = new HouseBlockPos(x, y, z);
        CHESTS.remove(pos);
        SHARED_CHESTS.remove(pos);
        try {
            SimNPCPersistence.worldShell().core(ChestData.class).discard(ChestData.key(x, y, z));
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao apagar o registro do bau ({},{},{}): {}", x, y, z, e.getMessage());
        }
    }

    private static void persist(int x, int y, int z) {
        /* Only the explicit flag is persisted. Village-global status is derived from the live
        house/village layout, so saving it would make a chest stay global after the player moved
        it into a house or the village disappeared.
        */
        persist(x, y, z, SHARED_CHESTS.contains(new HouseBlockPos(x, y, z)));
    }

    private static void persist(int x, int y, int z, boolean shared) {
        try {
            SimNPCPersistence.worldShell().core(ChestData.class)
                    .preserve(ChestData.key(x, y, z), new ChestData(x, y, z, shared));
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
                        && world.getChunkStore().getChunkComponent(ChunkUtil.indexChunkFromBlock(data.x, data.z), WorldChunk.getComponentType()) != null
                        && !isContainerAt(world, data.x, data.y, data.z)) {
                    removeAt(data.x, data.y, data.z);
                    dropped++;
                    continue;
                }

                HouseBlockPos pos = new HouseBlockPos(data.x, data.y, data.z);
                CHESTS.add(pos);
                if (data.shared) {
                    SHARED_CHESTS.add(pos);
                }
                restored++;
            }
            LOGGER.info("[SimTale] {} bau(s) recarregado(s) do banco ({} compartilhados), {} descartado(s)",
                    restored, SHARED_CHESTS.size(), dropped);
        } catch (Exception e) {
            LOGGER.warn("[SimTale] falha ao recarregar os baus: {}", e.getMessage());
        }
    }

    public static int size() {
        return CHESTS.size();
    }
}
