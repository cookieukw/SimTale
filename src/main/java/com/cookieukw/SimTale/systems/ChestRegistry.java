package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.AssetIds;

import com.cookieukw.SimTale.core.SimLog;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Collections;
import java.util.HashSet;
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
        CHESTS.add(new HouseBlockPos(x, y, z));
        LOGGER.debug("[SimTale] Chest registered at (" + x + ", " + y + ", " + z + "). Total: " + CHESTS.size());
    }

    public static void removeAt(int x, int y, int z) {
        CHESTS.remove(new HouseBlockPos(x, y, z));
    }

    public static int size() {
        return CHESTS.size();
    }
}
