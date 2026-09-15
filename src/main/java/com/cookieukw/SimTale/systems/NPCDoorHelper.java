package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
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
import java.util.UUID;
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

    /** Minimum dot product between forward vector and door direction (60 degree cone). */
    private static final double FACING_DOT_THRESHOLD = 0.5;

    /**
     * How far ahead of the NPC to look when asking "am I about to cross this door's plane?".
     * Longer than a step so the answer is about where the NPC is heading rather than where it is
     * standing, and short enough that it stays within the doorway instead of reaching into the room.
     */
    private static final double CROSSING_PROBE_DISTANCE = 1.5;

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
                    checkAndOpenDoor(world, npc, npcPos, forwardX, forwardZ, destination, probe);
                }
            }
        }
    }

    private static void checkAndOpenDoor(World world, SimNPCComponent npc, Vector3d npcPos,
                                         double forwardX, double forwardZ, Vector3d destination,
                                         Vector3i pos) {
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

            // 1. Angle check: the NPC must be facing the door
            if (!isFacing(npcPos, forwardX, forwardZ, anchorPos)) {
                return;
            }

            boolean npcSide = DoorBlockUtils.isInFrontOfDoor(anchorPos, yaw, npcPos);

            // 2. House boundary & intent check: if destination is known, verify that the NPC actually needs this door
            if (destination != null) {
                UUID houseAtDoor = houseForDoor(anchorPos);
                if (houseAtDoor != null) {
                    UUID houseAtNpc = houseIdAt(npcPos);
                    UUID houseAtDest = houseIdAt(destination);
                    // Outside NPC whose destination is NOT in this house has no reason to enter
                    if (!houseAtDoor.equals(houseAtNpc) && !houseAtDoor.equals(houseAtDest)) {
                        return;
                    }
                }

                // Geometric check: are the NPC and destination on opposite sides of the door?
                boolean destSide = DoorBlockUtils.isInFrontOfDoor(anchorPos, yaw, destination);
                if (npcSide == destSide) {
                    return;
                }
            } else {
                // If destination is unknown, do not open house doors
                if (houseForDoor(anchorPos) != null) {
                    return;
                }
            }

            // 3. Cross-plane probe check:
            // Probing where the NPC will be shortly separates "heading through" from "walking alongside":
            // a step taken parallel to a wall stays on the same side of it, a step taken into a doorway does not.
            Vector3d crossProbe = new Vector3d(
                    npcPos.x + forwardX * CROSSING_PROBE_DISTANCE,
                    npcPos.y,
                    npcPos.z + forwardZ * CROSSING_PROBE_DISTANCE);
            if (npcSide == DoorBlockUtils.isInFrontOfDoor(anchorPos, yaw, crossProbe)) {
                return;
            }

            HouseBlockPos doorKey = toKey(anchorPos);

            /* Bug #5 (docs/ROADMAP.md, testing_checklist.md sec. 2): two NPCs hitting the same
            doorway in the same window could each read door.getDoorState() == CLOSED, each decide
            their own transition, and each call setBlockInteractionState on top of the other --
            which is exactly the "one closes while the other is opening" symptom that kept
            reproducing even after the geometric checks got good. isInFrontOfDoor only decides
            WHICH door and WHICH direction a given NPC should use; it does nothing to stop two
            NPCs from both committing to the SAME door in the same window. putIfAbsent turns "is
            someone already handling this door?" and "if not, claim it" into one atomic step: the
            first NPC to reach this line in a given window owns the door until she stops renewing
            it (by walking away) or the shared timer runs out; everyone else just renews that same
            timer and backs off without touching the state. That is the per-door lock the roadmap
            asked for ("a NPC que esta no meio do gesto de abrir segura o estado ate terminar"),
            reusing OPENED_DOORS itself as the lock instead of adding a second map -- it already
            meant "door an NPC is actively managing," it just needed to be claimed before deciding
            what to do, not after. */
            if (OPENED_DOORS.putIfAbsent(doorKey, AUTO_CLOSE_TICKS) != null) {
                OPENED_DOORS.put(doorKey, AUTO_CLOSE_TICKS);
                return;
            }

            DoorState current = door.getDoorState();
            if (current != DoorState.CLOSED) {
                // Already open independently of us (most commonly: a player opened it by hand).
                // We now hold the claim and keep its timer alive; nothing else to do.
                return;
            }

            DoorState target = npcSide
                    ? DoorState.OPENED_OUT
                    : DoorState.OPENED_IN;

            String interactionState = DoorBlockUtils.getInteractionState(current, target);
            if (interactionState == null) {
                OPENED_DOORS.remove(doorKey, AUTO_CLOSE_TICKS);
                return;
            }

            // If primary direction is obstructed (e.g. wall/furniture), try opposite
            if (!DoorBlockUtils.canOpenDoor(world.getChunkStore(), anchorPos, interactionState)) {
                target = DoorBlockUtils.getOppositeDoorState(target);
                interactionState = DoorBlockUtils.getInteractionState(current, target);
                if (interactionState == null || !DoorBlockUtils.canOpenDoor(world.getChunkStore(), anchorPos, interactionState)) {
                    // Blocked on both sides: release the claim, or the door would stay "reserved"
                    // for two full seconds without ever actually opening, locking every other NPC
                    // out of even trying.
                    OPENED_DOORS.remove(doorKey, AUTO_CLOSE_TICKS);
                    return;
                }
            }

            world.setBlockInteractionState(anchorPos, door.getBlockType(), interactionState);

            LOGGER.debug("[PORTA] '{}' abriu porta em ({},{},{}) {} -> {}",
                    npc.name, anchorPos.x, anchorPos.y, anchorPos.z, current, target);
        } catch (Exception e) {
            LOGGER.debug("[PORTA] erro ao verificar porta em ({},{},{}): {}", pos.x, pos.y, pos.z, e.toString());
        }
    }

    /** House that owns the interior block at this position, or null when it belongs to none. */
    private static UUID houseIdAt(Vector3d pos) {
        if (pos == null) return null;
        HouseBlockPos block = new HouseBlockPos(
                (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
        UUID direct = HouseManager.BLOCK_TO_HOUSE_ID.get(block);
        if (direct != null) return direct;

        HouseBlockPos above = new HouseBlockPos(block.x, block.y + 1, block.z);
        return HouseManager.BLOCK_TO_HOUSE_ID.get(above);
    }

    /** Returns the house this door belongs to, or null if unassigned/outdoor. */
    private static UUID houseForDoor(Vector3i doorPos) {
        HouseBlockPos key = toKey(doorPos);
        for (HouseData house : HouseManager.HOUSES_BY_ID.values()) {
            if (house.doors != null && house.doors.contains(key)) {
                try {
                    return UUID.fromString(house.houseId);
                } catch (Exception ignored) {}
            }
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            HouseBlockPos neighbor = new HouseBlockPos(doorPos.x + off[0], doorPos.y, doorPos.z + off[1]);
            UUID houseId = HouseManager.BLOCK_TO_HOUSE_ID.get(neighbor);
            if (houseId != null) return houseId;
        }
        return null;
    }

    /**
     * Whether the door sits inside the cone the NPC is facing.
     * Compares only horizontal plane (XZ).
     */
    private static boolean isFacing(Vector3d npcPos, double forwardX, double forwardZ, Vector3i doorPos) {
        double toDoorX = (doorPos.x + 0.5) - npcPos.x;
        double toDoorZ = (doorPos.z + 0.5) - npcPos.z;

        double distance = Math.sqrt(toDoorX * toDoorX + toDoorZ * toDoorZ);
        // Standing right inside doorway: always allow
        if (distance < 0.001) return true;

        double dot = (forwardX * toDoorX + forwardZ * toDoorZ) / distance;
        return dot >= FACING_DOT_THRESHOLD;
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
