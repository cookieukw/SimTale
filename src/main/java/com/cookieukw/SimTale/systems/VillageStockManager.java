package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.systems.VillageManager.Village;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.*;

/**
 * Coordinates communal village storage and surplus inventory.
 * Manages shared village chests, communal food retrieval, and worker deposit fallbacks.
 */
public final class VillageStockManager {

    private static final SimLog LOGGER = SimLog.forClass(VillageStockManager.class);

    private VillageStockManager() {}

    /**
     * Checks if the NPC is associated with the given village (by home bed or family residence).
     */
    public static boolean isNpcInVillage(SimNPCComponent npc, Village village) {
        if (npc == null || village == null) return false;
        if (npc.bedLocation != null && village.contains(npc.bedLocation.x, npc.bedLocation.z)) {
            return true;
        }
        if (npc.family != null && npc.family.hasSharedHome && village.contains(npc.family.homeX, npc.family.homeZ)) {
            return true;
        }
        return false;
    }

    /**
     * Resolves the primary village for the given NPC.
     */
    public static Village getVillageForNpc(SimNPCComponent npc) {
        if (npc == null) return null;
        if (npc.bedLocation != null) {
            Village v = VillageManager.nearest(npc.bedLocation.x, npc.bedLocation.z);
            if (v != null && v.contains(npc.bedLocation.x, npc.bedLocation.z)) return v;
        }
        if (npc.family != null && npc.family.hasSharedHome) {
            Village v = VillageManager.nearest(npc.family.homeX, npc.family.homeZ);
            if (v != null && v.contains(npc.family.homeX, npc.family.homeZ)) return v;
        }
        return null;
    }

    /**
     * Returns all shared communal chests belonging to the given village.
     */
    public static List<HouseBlockPos> getSharedChestsInVillage(Village village) {
        if (village == null) return List.of();
        List<HouseBlockPos> list = new ArrayList<>();
        synchronized (ChestRegistry.SHARED_CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.SHARED_CHESTS) {
                if (village.contains(cp.x, cp.z)) {
                    list.add(cp);
                }
            }
        }
        return list;
    }

    /**
     * Finds the best chest for an NPC to deposit work surplus into.
     * Prefers personal home chest; if unavailable or full, falls back to a village communal chest.
     */
    public static HouseBlockPos findDepositChest(SimNPCComponent npc, World world) {
        if (npc == null || world == null) return null;

        // 1. Try personal home chest first
        HouseBlockPos homeChest = null;
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                if (!ChestRegistry.isShared(cp) && HouseManager.canOpenChest(npc.entityId, cp)) {
                    if (hasFreeSlot(world, cp)) {
                        return cp;
                    }
                    if (homeChest == null) {
                        homeChest = cp;
                    }
                }
            }
        }

        // 2. If home chest is full or none exists, look for a shared village chest
        Village village = getVillageForNpc(npc);
        if (village == null && homeChest != null) {
            village = VillageManager.nearest(homeChest.x, homeChest.z);
        }
        if (village != null) {
            List<HouseBlockPos> shared = getSharedChestsInVillage(village);
            for (HouseBlockPos sc : shared) {
                if (hasFreeSlot(world, sc)) {
                    return sc;
                }
            }
            if (!shared.isEmpty()) {
                return shared.get(0);
            }
        }

        return homeChest;
    }

    /**
     * Checks if a container block has at least one empty slot.
     */
    public static boolean hasFreeSlot(World world, HouseBlockPos pos) {
        if (world == null || pos == null) return false;
        try {
            ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, pos.x, pos.y, pos.z);
            if (cb == null) return false;
            ItemContainer container = cb.getItemContainer();
            if (container == null) return false;
            for (short s = 0; s < container.getCapacity(); s++) {
                ItemStack is = container.getItemStack(s);
                if (is == null || is.isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Counts items stored across all shared chests in a village.
     */
    public static Map<String, Integer> getVillageStockSummary(Village village, World world) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (village == null || world == null) return counts;

        List<HouseBlockPos> shared = getSharedChestsInVillage(village);
        for (HouseBlockPos pos : shared) {
            try {
                ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, pos.x, pos.y, pos.z);
                if (cb == null) continue;
                ItemContainer container = cb.getItemContainer();
                if (container == null) continue;
                for (short s = 0; s < container.getCapacity(); s++) {
                    ItemStack is = container.getItemStack(s);
                    if (is != null && !is.isEmpty() && is.getItemId() != null) {
                        counts.merge(is.getItemId(), is.getQuantity(), Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }
        return counts;
    }
}
