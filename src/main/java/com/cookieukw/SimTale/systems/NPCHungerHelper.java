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
import com.cookieukw.SimTale.core.SimLog;

public class NPCHungerHelper {
    private static final SimLog LOGGER = SimLog.forClass(NPCHungerHelper.class);

    /** Shared with RoutineAISystem so the "search immediately" bypass actually bypasses the cooldown. */
    public static final int FOOD_SEARCH_COOLDOWN_TICKS = 100;

    /**
     * Give up walking to the chest after 30s. Without this the NPC kept re-issuing the same
     * leash point forever whenever the chest was unreachable (walled in, on the far side of a
     * ravine, chunk unloaded), and only the low-energy interrupt could ever free it.
     */
    private static final int MOVE_TIMEOUT_TICKS = 600;

    /** How far an NPC will walk for a meal. */
    private static final double CHEST_SEARCH_RADIUS = 24.0;

    /** Best stack found in one chest, together with how appealing it is to the searching NPC. */
    private record ChestFood(HouseBlockPos chest, short slot, int score, double distSq) {}

    /**
     * Finds the best-scoring food in a chest, or null when it holds nothing this NPC would eat.
     */
    private static ChestFood bestFoodIn(World world, HouseBlockPos chestPos, SimNPCComponent npc, double distSq) {
        ItemContainerBlock cb = BlockModule.getComponent(
                ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
        if (cb == null) return null;

        ItemContainer container = cb.getItemContainer();
        ChestFood best = null;

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            int score = NPCFoodHelper.scoreFor(item, npc.preferences);
            if (score == NPCFoodHelper.NOT_FOOD) continue;

            if (best == null || score > best.score()) {
                best = new ChestFood(chestPos, slot, score, distSq);
            }
        }
        return best;
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
            
            // Pick the tastiest meal in reach, not the nearest chest that happens to hold food.
            // Distance only breaks ties between equally appealing options.
            ChestFood best = null;

            synchronized (ChestRegistry.CHESTS) {
                for (HouseBlockPos chestPos : ChestRegistry.CHESTS) {
                    double dx = pos.x - (chestPos.x + 0.5);
                    double dy = pos.y - (chestPos.y + 0.5);
                    double dz = pos.z - (chestPos.z + 0.5);
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq > CHEST_SEARCH_RADIUS * CHEST_SEARCH_RADIUS) continue;
                    if (!HouseManager.canOpenChest(npc.entityId, chestPos)) continue;

                    ChestFood candidate = bestFoodIn(world, chestPos, npc, distSq);
                    if (candidate == null) continue;

                    if (best == null
                            || candidate.score() > best.score()
                            || (candidate.score() == best.score() && candidate.distSq() < best.distSq())) {
                        best = candidate;
                    }
                }
            }

            if (best != null) {
                ai.targetBlockPosition = new Vector3i(best.chest().x, best.chest().y, best.chest().z);
                ai.currentTask = TaskType.MOVING_TO_FOOD;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
            } else {
                ai.currentTask = TaskType.IDLE;
            }
        }

        // --- MOVING_TO_FOOD ---
        if (ai.currentTask == TaskType.MOVING_TO_FOOD) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }

            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] NPC {} desistiu de chegar ao bau de comida", npc.name);
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.targetBlockPosition = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            Vector3d pos = transform.getPosition();
            double dx = (ai.targetBlockPosition.x + 0.5) - pos.x;
            double dz = (ai.targetBlockPosition.z + 0.5) - pos.z;
            if (dx*dx + dz*dz < 2.0 * 2.0) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                
                // Re-pick on arrival: another NPC may have emptied the chest during the walk.
                Vector3i chestPos = ai.targetBlockPosition;
                ChestFood chosen = bestFoodIn(world,
                        new HouseBlockPos(chestPos.x, chestPos.y, chestPos.z), npc, 0.0);

                if (chosen != null) {
                    ItemContainerBlock cb = BlockModule.getComponent(
                            ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                    ItemStack item = cb.getItemContainer().getItemStack(chosen.slot());

                    ai.eatingTier = NPCFoodHelper.tierOf(item);
                    ai.eatingWasHated = NPCFoodHelper.isHated(item, npc.preferences);
                    ai.eatingWasFavorite = NPCFoodHelper.isFavorite(item, npc.preferences);

                    LOGGER.debug("[COMIDA] {} pegou {} (tier {}, score {}) no bau {}",
                            npc.name, item.getItemId(), ai.eatingTier, chosen.score(), chestPos);

                    cb.getItemContainer().removeItemStackFromSlot(chosen.slot(), 1);
                    ai.currentTask = TaskType.EATING;
                    ai.taskStartTime = world.getTick();
                } else {
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
                float restored = NPCFoodHelper.hungerRestored(ai.eatingTier);
                npc.needs.hunger = Math.min(100f, npc.needs.hunger + restored);

                if (ai.eatingWasFavorite) {
                    npc.needs.fun = Math.min(100f, npc.needs.fun + 10f);
                } else if (ai.eatingWasHated) {
                    npc.needs.fun = Math.max(0f, npc.needs.fun - 10f);
                }

                LOGGER.debug("[COMIDA] {} terminou de comer (tier {}, +{} fome, fome agora {})",
                        npc.name, ai.eatingTier, restored, npc.needs.hunger);

                ai.eatingTier = 0;
                ai.eatingWasHated = false;
                ai.eatingWasFavorite = false;
                ai.currentTask = TaskType.IDLE;
                NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
            }
        }
    }
}
