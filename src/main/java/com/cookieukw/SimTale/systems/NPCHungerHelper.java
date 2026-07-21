package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import org.joml.Vector3d;
import org.joml.Vector3i;

public class NPCHungerHelper {
    private static final int FOOD_SEARCH_COOLDOWN_TICKS = 100;

    public static void handleHungerLogic(
            Ref<EntityStore> ref, 
            SimNPCComponent npc, 
            RoutineAIComponent ai, 
            TransformComponent transform, 
            World world, 
            Store<EntityStore> store, 
            CommandBuffer<EntityStore> commandBuffer
    ) {
        // --- FINDING_FOOD (Otimizado por Registro da Própria Casa) ---
        if (ai.currentTask == TaskType.FINDING_FOOD && world.getTick() - ai.taskStartTime >= FOOD_SEARCH_COOLDOWN_TICKS) {
            ai.taskStartTime = world.getTick();
            
            HouseBlockPos closestChest = null;
            UUID houseId = HouseManager.OWNER_TO_HOUSE_ID.get(npc.entityId);
            if (houseId != null) {
                HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
                if (house != null && house.chests != null && !house.chests.isEmpty()) {
                    // Pega o primeiro baú cadastrado da própria casa do NPC
                    closestChest = house.chests.iterator().next();
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
                ai.currentTask = TaskType.EATING;
                ai.taskStartTime = world.getTick();
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, pos.y, ai.targetBlockPosition.z + 0.5));
            }
        }

        // --- EATING ---
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
