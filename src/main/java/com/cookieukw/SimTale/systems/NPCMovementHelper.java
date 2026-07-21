package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class NPCMovementHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(NPCMovementHelper.class);
    private static final double LEASH_UPDATE_THRESHOLD_SQ = 0.5 * 0.5;

    public static void moveTo(Ref<EntityStore> ref, RoutineAIComponent ai, World world, Vector3d targetPos) {
        boolean needsUpdate;

        if (ai.lastLeashPos == null) {
            needsUpdate = true;
        } else {
            double d2 = ai.lastLeashPos.distanceSquared(targetPos);
            needsUpdate = d2 > LEASH_UPDATE_THRESHOLD_SQ;
        }

        if (needsUpdate) {
            LOGGER.info("[SimTale-DEBUG] moveTo updating leash point to ({},{},{}) for NPC", targetPos.x, targetPos.y, targetPos.z);
            ai.lastLeashPos = new Vector3d(targetPos);
            ai.lastLeashTick = world.getTick();
            
            NPCEntity npcEntity = ref.getStore().getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null) {
                npcEntity.setLeashPoint(new Vector3d(targetPos.x, targetPos.y, targetPos.z));
                if (npcEntity.getRole() != null) {
                    npcEntity.getRole().getStateSupport().setState(ref, "Moving", null, ref.getStore());
                }
            }
        }
    }

    public static void clearMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai) {
        ai.lastLeashPos = null;
        ai.lastLeashTick = 0;

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            TransformComponent transform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null) {
                npcEntity.setLeashPoint(new Vector3d(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z));
            }
            if (npcEntity.getRole() != null) {
                npcEntity.getRole().getStateSupport().setState(npcRef, "Idle", null, npcRef.getStore());
            }
        }
    }

    public static void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        playAnim(ref, AnimationSlot.Action, anim, name, store);
    }

    public static void playAnim(Ref<EntityStore> ref, AnimationSlot slot, String anim, String name, Store<EntityStore> store) {
        AnimationUtils.playAnimation(ref, slot, anim, name, store);
    }

    public static void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, boolean sleeping) {
        MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (msc == null) return;
        MovementStates ms = msc.getMovementStates();
        ms.idle = true;
        ms.horizontalIdle = true;
        ms.walking = false;
        ms.running = false;
        ms.sprinting = false;
        ms.jumping = false;
        ms.falling = false;
        ms.mantling = false;
        ms.sliding = false;
        ms.mounting = sleeping;
        ms.sleeping = sleeping;
        commandBuffer.replaceComponent(ref, MovementStatesComponent.getComponentType(), msc);
    }

    public static Vector3i getBedApproachPosition(Vector3i bedPos, TransformComponent transform, World world) {
        Vector3i[] candidates = {
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z - 1)
        };

        Vector3d npcPos = transform.getPosition();
        Vector3i best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Vector3i c : candidates) {
            boolean stand = isStandable(c, world);
            if (!stand) continue;
            double dx = (c.x + 0.5) - npcPos.x;
            double dz = (c.z + 0.5) - npcPos.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = c;
            }
        }

        return best != null ? best : bedPos;
    }

    public static boolean isStandable(Vector3i pos, World world) {
        BlockType atPos = world.getBlockType(pos.x, pos.y, pos.z);
        if (atPos != null && atPos.getId() != null && !atPos.getId().equalsIgnoreCase("Empty")) {
            return false;
        }

        BlockType above = world.getBlockType(pos.x, pos.y + 1, pos.z);
        if (above != null && above.getId() != null && !above.getId().equalsIgnoreCase("Empty")) {
            return false;
        }

        BlockType below = world.getBlockType(pos.x, pos.y - 1, pos.z);
        return below != null && below.getId() != null && !below.getId().equalsIgnoreCase("Empty");
    }
}
