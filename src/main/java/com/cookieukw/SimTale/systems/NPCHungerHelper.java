package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;


import org.joml.Vector3d;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPCHungerHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NPCHungerHelper.class);

    /** Shared with RoutineAISystem so the "search immediately" bypass actually bypasses the cooldown. */
    public static final int FOOD_SEARCH_COOLDOWN_TICKS = 100;

    /** Single source of truth for "is this item edible?" heuristics. */
    private static boolean isFoodId(String itemIdLower) {
        return itemIdLower.contains("food_") || itemIdLower.contains("_food") || itemIdLower.startsWith("food");
    }

    public static void handleHungerLogic(
            Ref<EntityStore> ref, 
            SimNPCComponent npc, 
            RoutineAIComponent ai, 
            TransformComponent transform, 
            World world, 
            Store<EntityStore> store
    ) {
        if (ai.currentTask == TaskType.FINDING_FOOD && world.getTick() - ai.taskStartTime >= FOOD_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            Vector3d pos = transform.getPosition();
            
            HouseBlockPos closestChest = null;
            double minChestDistSq = Double.MAX_VALUE;

            synchronized (ChestRegistry.CHESTS) {
                for (HouseBlockPos chestPos : ChestRegistry.CHESTS) {
                    double dx = pos.x - (chestPos.x + 0.5);
                    double dy = pos.y - (chestPos.y + 0.5);
                    double dz = pos.z - (chestPos.z + 0.5);
                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq <= 10.0 * 10.0 && distSq < minChestDistSq) {
                        if (HouseManager.canOpenChest(npc.entityId, chestPos)) {
                            // Check if chest contains food
                            ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                            boolean hasFood = false;
                            if (cb != null) {
                                ItemContainer container = cb.getItemContainer();
                                for (short slot = 0; slot < container.getCapacity(); slot++) {
                                    ItemStack item = container.getItemStack(slot);
                                    if (item != null && !item.isEmpty() && isFoodId(item.getItemId().toLowerCase())) {
                                        hasFood = true;
                                        break;
                                    }
                                }
                            }
                            if (hasFood) {
                                minChestDistSq = distSq;
                                closestChest = chestPos;
                            }
                        }
                    }
                }
            }

            if (closestChest != null) {
                ai.targetBlockPosition = new Vector3i(closestChest.x, closestChest.y, closestChest.z);
                ai.currentTask = TaskType.MOVING_TO_FOOD;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else {
                ai.currentTask = TaskType.IDLE;
            }
        }

        // --- MOVING_TO_FOOD ---
        if (ai.currentTask == TaskType.MOVING_TO_FOOD) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 2.0 * 2.0) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                
                // Try to consume 1 food item from the chest
                boolean foodConsumed = false;
                Vector3i chestPos = ai.targetBlockPosition;
                ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                if (cb != null) {
                    ItemContainer container = cb.getItemContainer();
                    for (short slot = 0; slot < container.getCapacity(); slot++) {
                        ItemStack item = container.getItemStack(slot);
                        if (item != null && !item.isEmpty() && isFoodId(item.getItemId().toLowerCase())) {
                            container.removeItemStackFromSlot(slot, 1);
                            foodConsumed = true;
                            LOGGER.debug("[SimTale] NPC {} consumed 1x {} from chest at {}", npc.name, item.getItemId(), chestPos);
                            break;
                        }
                    }
                }

                if (foodConsumed) {
                    ai.currentTask = TaskType.EATING;
                    ai.taskStartTime = world.getTick();
                } else {
                    // No food left (or chunk unloaded/chest broken)
                    ai.currentTask = TaskType.IDLE;
                }
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        if (ai.currentTask == TaskType.EATING) {
            if (world.getTick() - ai.taskStartTime == 1) {
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Eat.blockyanim", "Eat", store);
            }
            if (world.getTick() - ai.taskStartTime > 60) {
                npc.needs.hunger = Math.min(100f, npc.needs.hunger + 40f);
                ai.currentTask = TaskType.IDLE;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }
    }
}
