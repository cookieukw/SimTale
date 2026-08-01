package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HouseDoorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseDoorManager.class);
    private static long lastProcessedDoorsTick = 0;
    public static final Map<HouseBlockPos, Integer> OPENED_DOORS_COOLDOWN = new ConcurrentHashMap<>();

    public static void handleNpcDoors(World world, SimNPCComponent npc, TransformComponent transform) {
        long currentWorldTick = world.getTick();
        if (currentWorldTick != lastProcessedDoorsTick) {
            lastProcessedDoorsTick = currentWorldTick;
            decrementAndCloseDoors(world);
        }

        Vector3d npcPos = transform.getPosition();
        UUID houseId = HouseManager.OWNER_TO_HOUSE_ID.get(npc.entityId);
        if (houseId != null) {
            HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
            if (house != null && house.doors != null) {
                for (HouseBlockPos doorPos : house.doors) {
                    double dx = npcPos.x - (doorPos.x + 0.5);
                    double dy = npcPos.y - (doorPos.y + 0.5);
                    double dz = npcPos.z - (doorPos.z + 0.5);
                    if (dx*dx + dy*dy + dz*dz < 2.0 * 2.0) {
                        BlockType bType = world.getBlockType(doorPos.x, doorPos.y, doorPos.z);
                        if (bType != null && bType.getId() != null && bType.getId().toLowerCase().contains("door") && bType.getId().toLowerCase().contains("_closed")) {
                            String openId = bType.getId().replace("_closed", "_open").replace("_CLOSED", "_OPEN");
                            int rot = world.getBlockRotationIndex(doorPos.x, doorPos.y, doorPos.z);
                            world.setBlock(doorPos.x, doorPos.y, doorPos.z, openId, rot);
                            LOGGER.info("[SimTale] NPC '{}' abriu a porta registrada da casa em ({}, {}, {})", npc.name, doorPos.x, doorPos.y, doorPos.z);
                            OPENED_DOORS_COOLDOWN.put(doorPos, 40); // 2 segundos abertas
                        }
                    }
                }
            }
        }
    }

    private static void decrementAndCloseDoors(World world) {
        Iterator<Map.Entry<HouseBlockPos, Integer>> iterator = OPENED_DOORS_COOLDOWN.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HouseBlockPos, Integer> entry = iterator.next();
            HouseBlockPos pos = entry.getKey();
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                boolean npcNearby = false;
                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityRef != null) {
                        TransformComponent tc = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                        if (tc != null) {
                            double dx = tc.getPosition().x - (pos.x + 0.5);
                            double dy = tc.getPosition().y - (pos.y + 0.5);
                            double dz = tc.getPosition().z - (pos.z + 0.5);
                            if (dx*dx + dy*dy + dz*dz < 2.0 * 2.0) {
                                npcNearby = true;
                                break;
                            }
                        }
                    }
                }
                if (!npcNearby) {
                    BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                    if (type != null && type.getId() != null && type.getId().toLowerCase().contains("door") && type.getId().toLowerCase().contains("_open")) {
                        String closedId = type.getId().replace("_open", "_closed").replace("_OPEN", "_CLOSED");
                        int rot = world.getBlockRotationIndex(pos.x, pos.y, pos.z);
                        world.setBlock(pos.x, pos.y, pos.z, closedId, rot);
                        LOGGER.info("[SimTale] Porta registrada em ({}, {}, {}) fechou automaticamente.", pos.x, pos.y, pos.z);
                    }
                    iterator.remove();
                } else {
                    entry.setValue(20); // Reseta cooldown
                }
            } else {
                entry.setValue(remainingTicks);
            }
        }
    }
}
