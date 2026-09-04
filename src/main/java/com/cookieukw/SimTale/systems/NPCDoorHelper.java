package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.DoorBlockUtils;
import com.hypixel.hytale.server.core.modules.interaction.DoorBlockUtils.DoorState;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class NPCDoorHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCDoorHelper.class);

    private NPCDoorHelper() {
        // Utility class.
    }

    /** Ticks that the door remains open before closing (2 seconds). */
    private static final int AUTO_CLOSE_TICKS = 40;

    /** Keep door open if any NPC is within this radius. */
    private static final double KEEP_OPEN_RADIUS_SQ = 2.5 * 2.5;

    /** Cadence: each NPC checks doors every 3 ticks, staggered by entity ID. */
    private static final int CHECK_INTERVAL = 3;

    /** Distances ahead along the walking vector to probe for doors. */
    private static final double[] PROBE_DISTANCES = {0.0, 1.0, 1.8, 2.4};

    /** Open doors with remaining ticks before closing. */
    public static final Map<HouseBlockPos, Integer> OPENED_DOORS = new ConcurrentHashMap<>();

    private static long lastAutoCloseTick = -1L;

    public static void handleNpcDoors(World world, SimNPCComponent npc, TransformComponent transform,
                                      Vector3d destination) {
        if (world == null || npc == null || transform == null || npc.entityId == null) return;

        long tick = world.getTick();
        if (tick != lastAutoCloseTick) {
            lastAutoCloseTick = tick;
            tickAutoClose(world);
        }

        if ((tick + Math.abs(npc.entityId.hashCode())) % CHECK_INTERVAL != 0) return;

        Vector3d npcPos = transform.getPosition();
        float yawRad = transform.getRotation().yaw();
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);

        int baseY = (int) Math.floor(npcPos.y);
        Set<Vector3i> visited = new HashSet<>();

        // Probes only the blocks directly in the walking direction of the NPC
        for (double dist : PROBE_DISTANCES) {
            int px = (int) Math.floor(npcPos.x + forwardX * dist);
            int pz = (int) Math.floor(npcPos.z + forwardZ * dist);

            for (int dy = -1; dy <= 1; dy++) {
                int py = baseY + dy;
                Vector3i probe = new Vector3i(px, py, pz);
                if (visited.add(probe)) {
                    checkAndOpenDoor(world, npc, npcPos, probe);
                }
            }
        }
    }

    private static void checkAndOpenDoor(World world, SimNPCComponent npc, Vector3d npcPos, Vector3i pos) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            BlockChunk blockChunk = world.getChunkStore().getChunkComponent(chunkIndex, BlockChunk.getComponentType());
            if (blockChunk == null) return;

            int blockId = blockChunk.getBlock(pos.x, pos.y, pos.z);
            if (blockId == 0) return;

            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            if (type == null || !type.isDoor()) return;

            BlockSection section = blockChunk.getSectionAtBlockY(pos.y);
            int filler = section != null ? section.getFiller(pos.x, pos.y, pos.z) : 0;
            Vector3i anchorPos = filler == 0
                    ? pos
                    : new Vector3i(
                            pos.x - FillerBlockUtil.unpackX(filler),
                            pos.y - FillerBlockUtil.unpackY(filler),
                            pos.z - FillerBlockUtil.unpackZ(filler));

            long anchorChunkIndex = ChunkUtil.indexChunkFromBlock(anchorPos.x, anchorPos.z);
            BlockChunk anchorBlockChunk = world.getChunkStore().getChunkComponent(anchorChunkIndex, BlockChunk.getComponentType());
            if (anchorBlockChunk == null) return;

            BlockSection anchorSection = anchorBlockChunk.getSectionAtBlockY(anchorPos.y);
            Rotation yaw = anchorSection != null ? anchorSection.getRotation(anchorPos.x, anchorPos.y, anchorPos.z).yaw() : RotationTuple.NONE.yaw();
            DoorInteraction.DoorInfo door = DoorInteraction.getDoorAtPosition(world.getChunkStore(), anchorPos.x, anchorPos.y, anchorPos.z, yaw);
            if (door == null) return;

            DoorState current = door.getDoorState();
            if (current != DoorState.CLOSED) {
                OPENED_DOORS.put(toKey(anchorPos), AUTO_CLOSE_TICKS);
                return;
            }

            DoorState target = DoorBlockUtils.isInFrontOfDoor(anchorPos, yaw, npcPos)
                    ? DoorState.OPENED_OUT
                    : DoorState.OPENED_IN;

            String interactionState = DoorBlockUtils.getInteractionState(current, target);
            if (interactionState == null) return;

            // If primary direction is obstructed (e.g. wall/furniture), try opposite
            if (!DoorBlockUtils.canOpenDoor(world.getChunkStore(), anchorPos, interactionState)) {
                target = DoorBlockUtils.getOppositeDoorState(target);
                interactionState = DoorBlockUtils.getInteractionState(current, target);
                if (interactionState == null || !DoorBlockUtils.canOpenDoor(world.getChunkStore(), anchorPos, interactionState)) {
                    return;
                }
            }

            world.setBlockInteractionState(anchorPos, door.getBlockType(), interactionState);
            OPENED_DOORS.put(toKey(anchorPos), AUTO_CLOSE_TICKS);

            LOGGER.debug("[PORTA] '{}' abriu porta em ({},{},{}) {} -> {}",
                    npc.name, anchorPos.x, anchorPos.y, anchorPos.z, current, target);
        } catch (Exception e) {
            LOGGER.debug("[PORTA] erro ao verificar porta em ({},{},{}): {}", pos.x, pos.y, pos.z, e.toString());
        }
    }

    private static void tickAutoClose(World world) {
        if (OPENED_DOORS.isEmpty()) return;

        Iterator<Map.Entry<HouseBlockPos, Integer>> it = OPENED_DOORS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<HouseBlockPos, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;

            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }

            HouseBlockPos pos = entry.getKey();
            if (isAnyNpcNear(pos)) {
                entry.setValue(AUTO_CLOSE_TICKS / 2);
                continue;
            }

            closeDoor(world, pos);
            it.remove();
        }
    }

    private static void closeDoor(World world, HouseBlockPos pos) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            BlockChunk blockChunk = world.getChunkStore().getChunkComponent(chunkIndex, BlockChunk.getComponentType());
            if (blockChunk == null) return;

            int blockId = blockChunk.getBlock(pos.x, pos.y, pos.z);
            if (blockId == 0) return;

            BlockType type = BlockType.getAssetMap().getAsset(blockId);
            if (type == null || !type.isDoor()) return;

            BlockSection section = blockChunk.getSectionAtBlockY(pos.y);
            Rotation yaw = section != null ? section.getRotation(pos.x, pos.y, pos.z).yaw() : RotationTuple.NONE.yaw();
            DoorInteraction.DoorInfo door = DoorInteraction.getDoorAtPosition(world.getChunkStore(), pos.x, pos.y, pos.z, yaw);
            if (door == null) return;

            DoorState current = door.getDoorState();
            if (current == DoorState.CLOSED) return;

            String interactionState = DoorBlockUtils.getInteractionState(current, DoorState.CLOSED);
            if (interactionState != null) {
                world.setBlockInteractionState(door.getBlockPosition(), door.getBlockType(), interactionState);
                LOGGER.debug("[PORTA] fechou porta em ({},{},{})", pos.x, pos.y, pos.z);
            }
        } catch (Exception e) {
            LOGGER.debug("[PORTA] falha ao fechar porta em ({},{},{}): {}", pos.x, pos.y, pos.z, e.toString());
        }
    }

    private static boolean isAnyNpcNear(HouseBlockPos pos) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef == null || !npc.entityRef.isValid()) continue;

            TransformComponent tc = npc.entityRef.getStore()
                    .getComponent(npc.entityRef, TransformComponent.getComponentType());
            if (tc == null) continue;

            Vector3d p = tc.getPosition();
            double dx = p.x - (pos.x + 0.5);
            double dy = p.y - (pos.y + 0.5);
            double dz = p.z - (pos.z + 0.5);
            if (dx * dx + dz * dz < KEEP_OPEN_RADIUS_SQ && Math.abs(dy) <= 2.2) return true;
        }
        return false;
    }

    private static HouseBlockPos toKey(Vector3i pos) {
        return new HouseBlockPos(pos.x, pos.y, pos.z);
    }
}
