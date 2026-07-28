package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.RemoveReason;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPCWorkHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(NPCWorkHelper.class);

    private static final int GATHER_WORK_DURATION_TICKS = 40; // 2 seconds
    private static final double WORK_REACH_DISTANCE_SQ = 2.5 * 2.5;

    private static final String ANIM_WALK = "Characters/Animations/Actions/Walk.blockyanim";
    private static final String ANIM_SMITH = "Characters/Animations/Actions/Smith.blockyanim";
    private static final String ANIM_IDLE = "Characters/Animations/Actions/Idle.blockyanim";

    // keyword -> value, checked in insertion order; falls back to the first entry's value if nothing matches
    private static final Map<String, String> CROP_TO_FOOD = orderedMap(
            "carrot", "hytale:food_carrot",
            "wheat", "hytale:food_bread",
            "tomato", "hytale:food_tomato",
            "corn", "hytale:food_corn"
    );

    private static final Map<String, String> CROP_TO_SEED = orderedMap(
            "carrot", "hytale:Plant_Seeds_Carrot",
            "wheat", "hytale:Plant_Seeds_Wheat",
            "tomato", "hytale:Plant_Seeds_Tomato",
            "corn", "hytale:Plant_Seeds_Corn"
    );

    private static final Map<String, String> SEED_TO_CROP_BLOCK = orderedMap(
            "carrot", "hytale:Plant_Crop_Carrot_Block",
            "wheat", "hytale:Plant_Crop_Wheat_Block",
            "tomato", "hytale:Plant_Crop_Tomato_Block",
            "corn", "hytale:Plant_Crop_Corn_Block"
    );

    private static final Map<String, String> ANIMAL_TO_MEAT = orderedMap(
            "pig", "hytale:food_pork_raw",
            "cow", "hytale:food_beef_raw",
            "bull", "hytale:food_beef_raw",
            "chicken", "hytale:food_chicken_raw",
            "hen", "hytale:food_chicken_raw"
    );

    public static void handleWorkLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        // Evaluate Transition to Work/Deposit from IDLE
        if (ai.currentTask == TaskType.IDLE && (npc.profession == Profession.FARMER || npc.profession == Profession.HUNTER)) {
            ItemContainer inventory = getInventory(store, ref);
            boolean hasItemsToDeposit = hasAnyItem(inventory);

            if (hasItemsToDeposit) {
                // Find a chest owned by NPC to deposit items
                HouseBlockPos depositChest = findHomeChest(npc);
                if (depositChest != null) {
                    ai.targetBlockPosition = new Vector3i(depositChest.x, depositChest.y, depositChest.z);
                    ai.currentTask = TaskType.MOVING_TO_DEPOSIT;
                    playWalk(ref, store);
                    return;
                }
            }

            // Stagger scans (e.g. random chance or time check)
            if (world.getTick() % 100 == 0 || ai.forcedByDebug) {
                ai.forcedByDebug = false;
                if (npc.profession == Profession.FARMER) {
                    // Try to harvest first
                    Vector3i cropPos = scanForCrops(transform.getPosition());
                    if (cropPos != null) {
                        ai.targetBlockPosition = cropPos;
                        ai.currentTask = TaskType.MOVING_TO_WORK;
                        playWalk(ref, store);
                    } else {
                        String seed = findSeedInInventory(inventory);
                        if (seed != null) {
                            Vector3i farmPos = scanForFarmland(transform.getPosition(), world);
                            if (farmPos != null) {
                                ai.targetBlockPosition = farmPos;
                                ai.currentTask = TaskType.MOVING_TO_WORK;
                                playWalk(ref, store);
                            }
                        }
                    }
                } else if (npc.profession == Profession.HUNTER) {
                    // Scan for animals
                    Ref<EntityStore> animalRef = scanForAnimals(transform.getPosition(), store);
                    if (animalRef != null && animalRef.isValid()) {
                        UUIDComponent uuidComp =
                            animalRef.getStore().getComponent(animalRef, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        if (uuidComp != null) {
                            ai.workTargetEntityId = uuidComp.getUuid();
                            ai.currentTask = TaskType.MOVING_TO_WORK;
                            playWalk(ref, store);
                        }
                    }
                }
            }
        }

        // MOVING_TO_WORK
        if (ai.currentTask == TaskType.MOVING_TO_WORK) {
            Vector3d npcPos = transform.getPosition();
            if (npc.profession == Profession.FARMER) {
                if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
                if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    // Determine if harvesting or planting
                    BlockType blockType = world.getBlockType(ai.targetBlockPosition.x, ai.targetBlockPosition.y, ai.targetBlockPosition.z);
                    if (blockType != null && blockType.getId() != null && blockType.getId().toLowerCase().contains("crop")) {
                        ai.currentTask = TaskType.FARMING;
                    } else {
                        ai.currentTask = TaskType.PLANTING;
                    }
                    ai.taskStartTime = world.getTick();
                } else {
                    NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
                }
            } else if (npc.profession == Profession.HUNTER) {
                if (ai.workTargetEntityId == null) { ai.currentTask = TaskType.IDLE; return; }
                Ref<EntityStore> animalRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
                if (animalRef == null || !animalRef.isValid()) {
                    ai.currentTask = TaskType.IDLE;
                    return;
                }
                TransformComponent animalTrans = animalRef.getStore().getComponent(animalRef, TransformComponent.getComponentType());
                if (animalTrans == null) { ai.currentTask = TaskType.IDLE; return; }
                Vector3d animalPos = animalTrans.getPosition();

                if (isNear(npcPos, animalPos.x, animalPos.z)) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    ai.currentTask = TaskType.HUNTING;
                    ai.taskStartTime = world.getTick();
                } else {
                    NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(animalPos.x, npcPos.y, animalPos.z));
                }
            }
        }

        // FARMING State
        if (ai.currentTask == TaskType.FARMING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i cropPos = ai.targetBlockPosition;
                BlockType blockType = world.getBlockType(cropPos.x, cropPos.y, cropPos.z);
                if (blockType != null && blockType.getId() != null && blockType.getId().toLowerCase().contains("crop")) {
                    String cropId = blockType.getId();
                    // Replace with empty
                    world.setBlock(cropPos.x, cropPos.y, cropPos.z, "hytale:empty");
                    CropRegistry.removeAt(cropPos.x, cropPos.y, cropPos.z);

                    // Map to food item & seed item
                    String meatOrVeg = lookup(CROP_TO_FOOD, cropId, "hytale:food_carrot");
                    String seedItem = lookup(CROP_TO_SEED, cropId, "hytale:Plant_Seeds_Carrot");
                    ItemContainer inv = getInventory(store, ref);
                    if (inv != null) {
                        inv.addItemStack(new ItemStack(meatOrVeg, 1));

                        // 100% chance to drop 1-2 seeds
                        int seedAmount = 1 + (int)(Math.random() * 2);
                        inv.addItemStack(new ItemStack(seedItem, seedAmount));

                        LOGGER.debug("[SimTale] Farmer NPC {} harvested crop {} (gained {} seeds)", npc.name, cropId, seedAmount);
                    }
                }
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // PLANTING State
        if (ai.currentTask == TaskType.PLANTING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i plantPos = ai.targetBlockPosition;
                ItemContainer inv = getInventory(store, ref);
                if (inv != null) {
                    String seed = findSeedInInventory(inv);
                    if (seed != null) {
                        short slot = findSeedSlot(inv, seed);
                        if (slot != -1) {
                            inv.removeItemStackFromSlot(slot, 1);
                            String cropBlock = getCropBlockFromSeed(seed);
                            world.setBlock(plantPos.x, plantPos.y, plantPos.z, cropBlock);
                            LOGGER.debug("[SimTale] Farmer NPC {} planted {} at {}", npc.name, cropBlock, plantPos);
                        }
                    }
                }
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // HUNTING State
        if (ai.currentTask == TaskType.HUNTING) {
            if (ai.workTargetEntityId == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Ref<EntityStore> animalRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
                if (animalRef != null && animalRef.isValid()) {
                    PersistentModel pm = animalRef.getStore().getComponent(animalRef, PersistentModel.getComponentType());
                    if (pm != null) {
                        String modelId = pm.getModelReference().getModelAssetId();
                        String meatId = lookup(ANIMAL_TO_MEAT, modelId, "hytale:food_wildmeat_raw");

                        // Destroy animal
                        animalRef.getStore().removeEntity(animalRef, RemoveReason.REMOVE);

                        // Put meat in storage
                        ItemContainer inv = getInventory(store, ref);
                        if (inv != null) {
                            inv.addItemStack(new ItemStack(meatId, 1));
                        }
                        LOGGER.debug("[SimTale] Hunter NPC {} hunted animal {}", npc.name, modelId);
                    }
                }
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // MOVING_TO_DEPOSIT
        if (ai.currentTask == TaskType.MOVING_TO_DEPOSIT) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            Vector3d npcPos = transform.getPosition();
            if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                NPCMovementHelper.clearMoveTarget(ref, ai);

                // Deposit items to chest
                Vector3i chestPos = ai.targetBlockPosition;
                ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                if (cb != null) {
                    ItemContainer chestInv = cb.getItemContainer();
                    ItemContainer npcInv = getInventory(store, ref);
                    if (chestInv != null && npcInv != null) {
                        int moved = 0;
                        for (short slot = 0; slot < npcInv.getCapacity(); slot++) {
                            ItemStack item = npcInv.getItemStack(slot);
                            if (item == null || item.isEmpty()) continue;

                            // Copy first and check the chest has room, then clear the NPC slot.
                            // The old order removed the item from the NPC and handed the
                            // now-emptied reference to the chest, destroying the loot whenever
                            // the chest was full.
                            ItemStack toDeposit = new ItemStack(item.getItemId(), item.getQuantity());
                            if (!chestInv.canAddItemStack(toDeposit)) continue;

                            chestInv.addItemStack(toDeposit);
                            npcInv.removeItemStackFromSlot(slot, item.getQuantity());
                            moved++;
                        }
                        LOGGER.debug("[SimTale] NPC {} deposited {} stack(s) into chest at {}", npc.name, moved, chestPos);
                    }
                }
                ai.currentTask = TaskType.IDLE;
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
            }
        }
    }

    // ── Animation shortcuts ──────────────────────────────────────────────────

    private static void playWalk(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_WALK, "Walk", store);
    }

    private static void playSmith(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_SMITH, "Smith", store);
    }

    private static void playIdleAnim(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
    }

    // ── Inventory / proximity helpers ────────────────────────────────────────

    private static ItemContainer getInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        return (storage != null) ? storage.getInventory() : null;
    }

    private static boolean hasAnyItem(ItemContainer container) {
        if (container == null) return false;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty()) return true;
        }
        return false;
    }

    /**
     *
     */
    private static boolean isNear(Vector3d pos, double targetX, double targetZ) {
        double dx = targetX - pos.x;
        double dz = targetZ - pos.z;
        return dx * dx + dz * dz < NPCWorkHelper.WORK_REACH_DISTANCE_SQ;
    }

    // ── Crop / seed / animal keyword lookups ─────────────────────────────────

    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Returns the value of the first entry whose key is contained in {@code id} (case-insensitive), or {@code fallback}. */
    private static String lookup(Map<String, String> table, String id, String fallback) {
        String lower = id.toLowerCase();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return fallback;
    }

    // ── Scanning helpers ──────────────────────────────────────────────────────

    private static HouseBlockPos findHomeChest(SimNPCComponent npc) {
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                if (HouseManager.canOpenChest(npc.entityId, cp)) {
                    return cp;
                }
            }
        }
        return null;
    }

    private static Vector3i scanForCrops(Vector3d center) {
        Vector3i closest = null;
        double minDistSq = 15.0 * 15.0;
        synchronized (CropRegistry.CROPS) {
            for (HouseBlockPos cp : CropRegistry.CROPS) {
                double dx = cp.x - center.x;
                double dy = cp.y - center.y;
                double dz = cp.z - center.z;
                double distSq = dx*dx + dy*dy + dz*dz;
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    closest = new Vector3i(cp.x, cp.y, cp.z);
                }
            }
        }
        return closest;
    }

    private static Ref<EntityStore> scanForAnimals(Vector3d center, Store<EntityStore> store) {
        try {
            SpatialResource<Ref<EntityStore>, EntityStore> spatial =
                store.getResource(EntityModule.get().getEntitySpatialResourceType());

            List<Ref<EntityStore>> results = new ArrayList<>();
            spatial.getSpatialStructure().collect(center, 15.0, results);

            for (Ref<EntityStore> target : results) {
                if (target == null || !target.isValid()) continue;
                PersistentModel pm = target.getStore().getComponent(target, PersistentModel.getComponentType());
                if (pm != null) {
                    String modelId = pm.getModelReference().getModelAssetId().toLowerCase();
                    if (modelId.contains("creature") &&
                        (modelId.contains("pig") || modelId.contains("sheep") || modelId.contains("cow") || modelId.contains("chicken") || modelId.contains("hen") || modelId.contains("goat"))) {
                        return target;
                    }
                }
            }
        } catch (Exception e) {
            // The spatial index can be mutated concurrently mid-scan; that is expected and
            // recoverable, but swallowing it silently hid real errors here for a long time.
            LOGGER.debug("[SimTale] Falha ao varrer animais proximos (ignorada)", e);
        }
        return null;
    }

    public static Vector3i scanForFarmland(Vector3d center, World world) {
        Vector3i closest = null;
        double minDistSq = 15.0 * 15.0;

        synchronized (FarmlandRegistry.FARMLAND) {
            for (HouseBlockPos fp : FarmlandRegistry.FARMLAND) {
                // Check if block above is empty (so we can plant something)
                BlockType above = world.getBlockType(fp.x, fp.y + 1, fp.z);
                if (above == null || above.getId() == null || above.getId().equalsIgnoreCase("hytale:empty") || above.getId().equalsIgnoreCase("empty")) {
                    double dx = fp.x + 0.5 - center.x;
                    double dy = fp.y + 1.5 - center.y;
                    double dz = fp.z + 0.5 - center.z;
                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq < minDistSq) {
                        minDistSq = distSq;
                        closest = new Vector3i(fp.x, fp.y + 1, fp.z);
                    }
                }
            }
        }
        return closest;
    }

    public static String findSeedInInventory(ItemContainer container) {
        if (container == null) return null;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty()) {
                String id = item.getItemId();
                if (id.startsWith("hytale:Plant_Seeds_") || id.startsWith("Plant_Seeds_") || id.toLowerCase().contains("seeds_")) {
                    return id;
                }
            }
        }
        return null;
    }

    public static short findSeedSlot(ItemContainer container, String seedId) {
        if (container == null || seedId == null) return -1;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty() && item.getItemId().equals(seedId)) {
                return slot;
            }
        }
        return -1;
    }

    /** Kept public: called from outside this class. */
    public static String getCropBlockFromSeed(String seedId) {
        return lookup(SEED_TO_CROP_BLOCK, seedId, "hytale:Plant_Crop_Carrot_Block");
    }
}