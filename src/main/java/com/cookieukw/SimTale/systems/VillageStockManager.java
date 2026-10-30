package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.config.SimTaleConfig;
import com.cookieukw.SimTale.config.SimTaleConfigManager;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.systems.VillageManager.Village;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates communal village storage and surplus inventory.
 * Manages shared village chests, communal food retrieval, and worker deposit fallbacks.
 */
public final class VillageStockManager {

    private VillageStockManager() {}

    /**
     * Checks if the NPC is associated with the given village (by home bed or family residence).
     */
    public static boolean isNpcInVillage(SimNPCComponent npc, Village village) {
        if (npc == null || village == null) return false;
        if (npc.bedLocation != null && village.contains(npc.bedLocation.x, npc.bedLocation.z)) {
            return true;
        }
        return npc.family != null && npc.family.hasSharedHome && village.contains(npc.family.homeX, npc.family.homeZ);
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
     * Returns all communal chests belonging to the given village.
     *
     * <p>Reads the full chest registry instead of only {@link ChestRegistry#SHARED_CHESTS},
     * because village-global chests are classified live from their position rather than being
     * written into that set. A chest the player dropped in the village square therefore appears
     * here immediately, and stops appearing the moment it is moved inside a house.
     */
    public static List<HouseBlockPos> getSharedChestsInVillage(Village village) {
        if (village == null) return List.of();
        List<HouseBlockPos> list = new ArrayList<>();
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                if (village.contains(cp.x, cp.z) && ChestRegistry.isShared(cp)) {
                    list.add(cp);
                }
            }
        }
        return list;
    }

    public static List<HouseBlockPos> getSharedChestsInVillage(World world, Village village) {
        if (world == null || village == null) return List.of();
        List<HouseBlockPos> list = new ArrayList<>();
        for (HouseBlockPos cp : ChestRegistry.registeredChests(world)) {
            if (village.contains(cp.x, cp.z) && ChestRegistry.isShared(world, cp)) list.add(cp);
        }
        return list;
    }

    /**
     * Fallback chance that a worker with room in her own pantry still walks surplus to the
     * village chest. Without this the communal chest only ever filled once every home chest was
     * already full, which made the warehouse look permanently empty even in a busy village.
     *
     * <p>The live value comes from {@link SimTaleConfig#villageChestDepositChance}; this constant
     * only covers early boot/tests where the config manager is not ready yet.
     */
    private static final double DEFAULT_VILLAGE_DEPOSIT_CHANCE = 0.30;

    /**
     * Finds the best chest for an NPC to deposit work surplus into.
     *
     * <p>Order of preference:
     * <ol>
     *   <li>a village-global/shared chest, either because the home chests are full or because of
     *       an occasional {@link SimTaleConfig#villageChestDepositChance} roll;</li>
     *   <li>the NPC's own home chest;</li>
     *   <li>a shared chest even when full, so an existing save keeps its old fallback.</li>
     * </ol>
     */
    public static HouseBlockPos findDepositChest(SimNPCComponent npc, World world) {
        if (npc == null || world == null) return null;

        HouseBlockPos homeWithRoom = null;
        HouseBlockPos homeFull = null;
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.registeredChests(world)) {
                if (ChestRegistry.isShared(world, cp)) continue;
                if (!HouseManager.canOpenChest(npc.entityId, cp)) continue;

                if (hasFreeSlot(world, cp)) {
                    if (homeWithRoom == null) homeWithRoom = cp;
                } else if (homeFull == null) {
                    homeFull = cp;
                }
            }
        }

        Village village = resolveVillage(npc, homeWithRoom != null ? homeWithRoom : homeFull);

        HouseBlockPos villageWithRoom = null;
        HouseBlockPos villageFull = null;
        if (village != null) {
            for (HouseBlockPos cp : getSharedChestsInVillage(world, village)) {
                if (hasFreeSlot(world, cp)) {
                    villageWithRoom = cp;
                    break;
                }
                if (villageFull == null) villageFull = cp;
            }
        }

        /* Occasional trip to the village chest even when the home pantry has room. This is what
        keeps the communal stock moving instead of turning the warehouse into a last resort.
        */
        if (villageWithRoom != null
                && (homeWithRoom == null
                    || ThreadLocalRandom.current().nextDouble() < villageDepositChance())) {
            return villageWithRoom;
        }

        if (homeWithRoom != null) return homeWithRoom;
        if (villageWithRoom != null) return villageWithRoom;
        if (villageFull != null) return villageFull;
        return homeFull;
    }

    /** Live config value, clamped so a malformed JSON file cannot break the roll. */
    private static double villageDepositChance() {
        try {
            SimTaleConfig cfg = SimTaleConfigManager.getConfig();
            if (cfg != null) {
                return Math.max(0.0, Math.min(1.0, cfg.villageChestDepositChance));
            }
        } catch (RuntimeException ignored) {
            // Config not ready: fall through to the historical default.
        }
        return DEFAULT_VILLAGE_DEPOSIT_CHANCE;
    }

    /**
     * Resolves which village an NPC should deposit into.
     *
     * <p>Home first, then the village the NPC is physically standing in — homeless workers
     * (guards, miners) still live in the settlement and should use its warehouse instead of
     * keeping every ore in their pockets — then the village around the fallback chest.
     */
    private static Village resolveVillage(SimNPCComponent npc, HouseBlockPos fallbackChest) {
        Village village = getVillageForNpc(npc);
        if (village != null) return village;

        if (npc.entityRef != null && npc.entityRef.isValid()) {
            TransformComponent transform = npc.entityRef.getStore()
                    .getComponent(npc.entityRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                village = VillageManager.nearest(pos.x, pos.z);
                if (village != null && village.contains(pos.x, pos.z)) {
                    return village;
                }
            }
        }

        if (fallbackChest != null) {
            village = VillageManager.nearest(fallbackChest.x, fallbackChest.z);
            if (village != null && village.contains(fallbackChest.x, fallbackChest.z)) {
                return village;
            }
        }
        return null;
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
                for (short s = 0; s < container.getCapacity(); s++) {
                    ItemStack is = container.getItemStack(s);
                    if (is != null && !is.isEmpty()) {
                        counts.merge(is.getItemId(), is.getQuantity(), Integer::sum);
                    }
                }
            } catch (Exception ignored) {}
        }
        return counts;
    }
}
