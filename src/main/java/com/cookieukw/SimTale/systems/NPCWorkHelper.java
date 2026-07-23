package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.CommandBuffer;
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
import java.util.List;
import java.util.UUID;
import org.joml.Vector3d;
import org.joml.Vector3i;

public class NPCWorkHelper {

    private static final int GATHER_WORK_DURATION_TICKS = 40; // 2 seconds

    public static void handleWorkLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer
    ) {
        // Evaluate Transition to Work/Deposit from IDLE
        if (ai.currentTask == TaskType.IDLE && (npc.profession == Profession.FARMER || npc.profession == Profession.HUNTER)) {
            // Check if NPC has items to deposit
            InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
            boolean hasItemsToDeposit = false;
            if (storage != null && storage.getInventory() != null) {
                ItemContainer container = storage.getInventory();
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    ItemStack item = container.getItemStack(slot);
                    if (item != null && !item.isEmpty()) {
                        hasItemsToDeposit = true;
                        break;
                    }
                }
            }

            if (hasItemsToDeposit) {
                // Find a chest owned by NPC to deposit items
                HouseBlockPos depositChest = findHomeChest(npc, world);
                if (depositChest != null) {
                    ai.targetBlockPosition = new Vector3i(depositChest.x, depositChest.y, depositChest.z);
                    ai.currentTask = TaskType.MOVING_TO_DEPOSIT;
                    NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
                    return;
                }
            }

            // Stagger scans (e.g. random chance or time check)
            if (world.getTick() % 100 == 0 || ai.forcedByDebug) {
                ai.forcedByDebug = false;
                if (npc.profession == Profession.FARMER) {
                    // Scan for crop blocks
                    Vector3i cropPos = scanForCrops(transform.getPosition(), world);
                    if (cropPos != null) {
                        ai.targetBlockPosition = cropPos;
                        ai.currentTask = TaskType.MOVING_TO_WORK;
                        NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
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
                            NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
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
                double dx = (ai.targetBlockPosition.x + 0.5) - npcPos.x;
                double dz = (ai.targetBlockPosition.z + 0.5) - npcPos.z;
                if (dx*dx + dz*dz < 2.5 * 2.5) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    ai.currentTask = TaskType.FARMING;
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

                double dx = animalPos.x - npcPos.x;
                double dz = animalPos.z - npcPos.z;
                if (dx*dx + dz*dz < 2.5 * 2.5) {
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
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i cropPos = ai.targetBlockPosition;
                BlockType blockType = world.getBlockType(cropPos.x, cropPos.y, cropPos.z);
                if (blockType != null && blockType.getId() != null && blockType.getId().toLowerCase().contains("crop")) {
                    // Replace with empty
                    world.setBlock(cropPos.x, cropPos.y, cropPos.z, "hytale:empty");
                    CropRegistry.removeAt(cropPos.x, cropPos.y, cropPos.z);

                    // Map to food item
                    String meatOrVeg = getCropItem(blockType.getId());
                    InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                    if (storage != null && storage.getInventory() != null) {
                        storage.getInventory().addItemStack(new ItemStack(meatOrVeg, 1));
                    }
                    System.out.println("[SimTale] Farmer NPC " + npc.name + " harvested crop: " + blockType.getId());
                }
                ai.currentTask = TaskType.IDLE;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

        // HUNTING State
        if (ai.currentTask == TaskType.HUNTING) {
            if (ai.workTargetEntityId == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Smith.blockyanim", "Smith", store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Ref<EntityStore> animalRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
                if (animalRef != null && animalRef.isValid()) {
                    PersistentModel pm = animalRef.getStore().getComponent(animalRef, PersistentModel.getComponentType());
                    if (pm != null && pm.getModelReference() != null) {
                        String modelId = pm.getModelReference().getModelAssetId();
                        String meatId = getAnimalMeat(modelId);
                        
                        // Destroy animal
                        animalRef.getStore().removeEntity(animalRef, RemoveReason.REMOVE);

                        // Put meat in storage
                        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                        if (storage != null && storage.getInventory() != null) {
                            storage.getInventory().addItemStack(new ItemStack(meatId, 1));
                        }
                        System.out.println("[SimTale] Hunter NPC " + npc.name + " hunted animal: " + modelId);
                    }
                }
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }

        // MOVING_TO_DEPOSIT
        if (ai.currentTask == TaskType.MOVING_TO_DEPOSIT) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            Vector3d npcPos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - npcPos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - npcPos.z;
            if (dx*dx + dz*dz < 2.5 * 2.5) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                
                // Deposit items to chest
                Vector3i chestPos = ai.targetBlockPosition;
                ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                if (cb != null && cb.getItemContainer() != null) {
                    ItemContainer chestInv = cb.getItemContainer();
                    InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
                    if (storage != null && storage.getInventory() != null) {
                        ItemContainer npcInv = storage.getInventory();
                        for (short slot = 0; slot < npcInv.getCapacity(); slot++) {
                            ItemStack item = npcInv.getItemStack(slot);
                            if (item != null && !item.isEmpty()) {
                                npcInv.removeItemStackFromSlot(slot, item.getQuantity());
                                chestInv.addItemStack(item);
                            }
                        }
                        System.out.println("[SimTale] NPC " + npc.name + " deposited all items to chest at " + chestPos);
                    }
                }
                ai.currentTask = TaskType.IDLE;
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
            }
        }
    }

    private static HouseBlockPos findHomeChest(SimNPCComponent npc, World world) {
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                if (HouseManager.canOpenChest(npc.entityId, cp)) {
                    return cp;
                }
            }
        }
        return null;
    }

    private static Vector3i scanForCrops(Vector3d center, World world) {
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
            if (spatial == null || spatial.getSpatialStructure() == null) return null;

            List<Ref<EntityStore>> results = new ArrayList<>();
            spatial.getSpatialStructure().collect(center, 15.0, results);

            for (Ref<EntityStore> target : results) {
                if (target == null || !target.isValid()) continue;
                PersistentModel pm = target.getStore().getComponent(target, PersistentModel.getComponentType());
                if (pm != null && pm.getModelReference() != null) {
                    String modelId = pm.getModelReference().getModelAssetId().toLowerCase();
                    if (modelId.contains("creature") && 
                        (modelId.contains("pig") || modelId.contains("sheep") || modelId.contains("cow") || modelId.contains("chicken") || modelId.contains("hen") || modelId.contains("goat"))) {
                        return target;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore spatial scanning race condition errors safely
        }
        return null;
    }

    private static String getCropItem(String blockId) {
        String lower = blockId.toLowerCase();
        if (lower.contains("carrot")) return "hytale:food_carrot";
        if (lower.contains("wheat")) return "hytale:food_bread";
        if (lower.contains("tomato")) return "hytale:food_tomato";
        if (lower.contains("corn")) return "hytale:food_corn";
        return "hytale:food_carrot";
    }

    private static String getAnimalMeat(String modelAssetId) {
        String lower = modelAssetId.toLowerCase();
        if (lower.contains("pig")) return "hytale:food_pork_raw";
        if (lower.contains("cow") || lower.contains("bull")) return "hytale:food_beef_raw";
        if (lower.contains("chicken") || lower.contains("hen")) return "hytale:food_chicken_raw";
        return "hytale:food_wildmeat_raw";
    }
}
