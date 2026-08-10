package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
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
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class NPCDoorHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCDoorHelper.class);

    private NPCDoorHelper() {
        // Utility class.
    }

    /** How many blocks around the NPC to scan for a door. A door only matters if it is within reach. */
    private static final int SCAN_XZ = 1;
    private static final int SCAN_DOWN = 1;
    private static final int SCAN_UP = 1;

    /** Ticks that the door remains open before trying to close. */
    private static final int AUTO_CLOSE_TICKS = 40;

    /** If there is still an NPC within this radius when the cooldown reaches zero, the door remains open. */
    private static final double KEEP_OPEN_RADIUS_SQ = 2.0 * 2.0;

    /** Each NPC only checks doors once every 5 ticks, staggered by ID to spread the load. */
    private static final int CHECK_INTERVAL = 5;

    /**
     * How directly the NPC must be facing a door before it opens it.
     *
     * <p>Cosine of the angle between where the NPC is looking and where the door is, so 0.5 is a
     * 60-degree cone ahead. Proximity alone used to be enough, and the scan is a 3x3x3 cube — so an
     * NPC merely walking along a wall opened the door beside it, with no intention of going through.
     *
     * <p>Facing is the best available proxy for intent here: the engine turns an NPC toward wherever
     * it is walking, so "the door is in front of me" is effectively "I am walking into it".
     */
    private static final double FACING_DOT_THRESHOLD = 0.5;

    /** Open doors with remaining ticks before closing. */
    public static final Map<HouseBlockPos, Integer> OPENED_DOORS = new ConcurrentHashMap<>();

    private static long lastAutoCloseTick = -1L;

    /**
     * @param destination where the NPC is walking to (its leash point), or null when unknown.
     */
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

        // Forward vector from the body's yaw. The engine's own convention:
        // PhysicsMath.headingFromDirection computes atan2(-dx, -dz), so forward is
        // (-sin(yaw), -cos(yaw)). Same formula faceConversationPartner relies on.
        float yawRad = transform.getRotation().yaw();
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);

        int baseX = (int) Math.floor(npcPos.x);
        int baseY = (int) Math.floor(npcPos.y);
        int baseZ = (int) Math.floor(npcPos.z);
        Set<Vector3i> handled = new HashSet<>();

        for (int dx = -SCAN_XZ; dx <= SCAN_XZ; dx++) {
            for (int dy = -SCAN_DOWN; dy <= SCAN_UP; dy++) {
                for (int dz = -SCAN_XZ; dz <= SCAN_XZ; dz++) {
                    tryOpenDoorAt(world, npc, npcPos, forwardX, forwardZ, destination,
                            baseX + dx, baseY + dy, baseZ + dz, handled);
                }
            }
        }
    }

    private static void tryOpenDoorAt(World world, SimNPCComponent npc, Vector3d npcPos,
                                      double forwardX, double forwardZ, Vector3d destination,
                                      int x, int y, int z, Set<Vector3i> handled) {
        try {
            // world.getBlockType() blocks and drains the world's task queue when the chunk isn't
            // already resident — safe from a command or a plain call, but this runs from inside
            // RoutineAISystem's own tick, and draining the queue mid-tick can run a chunk's
            // "start ticking" callback while the store is still processing, which throws
            // "Store is currently processing!" deep in engine code (visible as "[ChunkStore]
            // Failed to set chunk ticking!" in the log, over and over as NPCs keep wandering near
            // unloaded chunks). getChunkIfLoaded never blocks or queues anything — it just returns
            // null for a chunk that isn't already in memory, which here simply means "nothing to
            // check yet", same as any other position with no door.
            BlockAccessor chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) return;

            BlockType type = chunk.getBlockType(x, y, z);
            if (type == null || !type.isDoor()) return;

            ChunkStore chunkStore = world.getChunkStore();
            Rotation yaw = RotationTuple.get(world.getBlockRotationIndex(x, y, z)).yaw();

            DoorInteraction.DoorInfo door =
                    DoorInteraction.getDoorAtPosition(chunkStore, x, y, z, yaw);
            if (door == null) {
                return;
            }

           Vector3i doorPos = FurnitureAnchorHelper.anchorOf(world, door.getBlockPosition());
            if (doorPos == null || !handled.add(new Vector3i(doorPos))) return;

            // Does the NPC actually need to go through THIS door, or is it merely nearby/facing it?
            //
            // A facing cone alone said nothing about intent — an NPC walking along a wall, or just
            // loitering in a room after arriving, faces every door it passes within 60 degrees, and
            // used to open (or keep open) every one of them. The precise question is geometric: is
            // the destination on the opposite side of this specific door from the NPC? If so, the
            // NPC has to cross it; if not, this door is irrelevant no matter how it is facing.
            // Falls back to the facing cone only when there is no destination to compare against.
            boolean intentToCross = destination != null
                    ? DoorBlockUtils.isInFrontOfDoor(doorPos, yaw, npcPos)
                            != DoorBlockUtils.isInFrontOfDoor(doorPos, yaw, destination)
                    : isFacing(npcPos, forwardX, forwardZ, doorPos);
            if (!intentToCross) return;

            DoorState current = door.getDoorState();
            if (current != DoorState.CLOSED) {
                // Already open and this NPC genuinely intends to cross it: refresh the timer so it
                // doesn't close mid-crossing.
                OPENED_DOORS.put(toKey(doorPos), AUTO_CLOSE_TICKS);
                return;
            }

            // Which side to open. This rule is not a guess: it is the same one used by
            // DoorInteraction.interactWithBlock — whoever is in front of the door opens it
            // outwards, so the door leaf never swings over the person who opened it.
            DoorState target = DoorBlockUtils.isInFrontOfDoor(doorPos, yaw, npcPos)
                    ? DoorState.OPENED_OUT
                    : DoorState.OPENED_IN;

            // Argument order: (current state, desired state). Confirmed in the bytecode of
            // DoorInteraction.activateDoor, where the call is getInteractionState(fromState, doorState).
            String interactionState = DoorBlockUtils.getInteractionState(current, target);
            if (interactionState == null) {
                LOGGER.debug("[PORTA] ({},{},{}) getInteractionState({} -> {}) devolveu null",
                        doorPos.x, doorPos.y, doorPos.z, current, target);
                return;
            }

            // Covers the DoorBlocked state: an obstructed door cannot open.
            if (!DoorBlockUtils.canOpenDoor(chunkStore, doorPos, interactionState)) {
                LOGGER.debug("[PORTA] ({},{},{}) canOpenDoor recusou o estado '{}'",
                        doorPos.x, doorPos.y, doorPos.z, interactionState);
                return;
            }

            world.setBlockInteractionState(doorPos, door.getBlockType(), interactionState);
            OPENED_DOORS.put(toKey(doorPos), AUTO_CLOSE_TICKS);

            LOGGER.debug("[PORTA] '{}' ABRIU ({},{},{}) tipo='{}' {} -> {} (estado '{}')",
                    npc.name, doorPos.x, doorPos.y, doorPos.z,
                    door.getBlockType() != null ? door.getBlockType().getId() : "null",
                    current, target, interactionState);
        } catch (Exception e) {
            // A problematic door shouldn't crash the entire NPC AI tick.
            LOGGER.debug("[PORTA] falha em ({},{},{}): {}", x, y, z, e.toString());
        }
    }

    /**
     * Whether the door sits inside the cone the NPC is facing.
     *
     * <p>Compares only the horizontal plane: a door one block above or below is still the same door
     * from the walker's point of view, and folding Y in would reject it for no reason.
     */
    private static boolean isFacing(Vector3d npcPos, double forwardX, double forwardZ, Vector3i doorPos) {
        double toDoorX = (doorPos.x + 0.5) - npcPos.x;
        double toDoorZ = (doorPos.z + 0.5) - npcPos.z;

        double distance = Math.sqrt(toDoorX * toDoorX + toDoorZ * toDoorZ);
        // Standing inside the doorway: there is no meaningful direction, so let it through.
        if (distance < 0.001) return true;

        double dot = (forwardX * toDoorX + forwardZ * toDoorZ) / distance;
        return dot >= FACING_DOT_THRESHOLD;
    }

    // ---------------------------------------------------------------------
    // Auto-closing
    // ---------------------------------------------------------------------

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
                // There are still people passing: postpone closing.
                entry.setValue(AUTO_CLOSE_TICKS / 2);
                continue;
            }

            closeDoor(world, pos);
            it.remove();
        }
    }

    private static void closeDoor(World world, HouseBlockPos pos) {
        try {
            // Same reasoning as tryOpenDoorAt: avoid world.getBlockType() triggering a mid-tick
            // chunk load wait. tickAutoClose runs from the same NPC-tick call chain.
            BlockAccessor chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunk == null) return;

            BlockType type = chunk.getBlockType(pos.x, pos.y, pos.z);
            if (type == null || !type.isDoor()) return;

            ChunkStore chunkStore = world.getChunkStore();
            Rotation yaw = RotationTuple.get(world.getBlockRotationIndex(pos.x, pos.y, pos.z)).yaw();

            DoorInteraction.DoorInfo door =
                    DoorInteraction.getDoorAtPosition(chunkStore, pos.x, pos.y, pos.z, yaw);
            if (door == null) {
                LOGGER.debug("[PORTA] fechar ({},{},{}): getDoorAtPosition devolveu null", pos.x, pos.y, pos.z);
                return;
            }

            DoorState current = door.getDoorState();
            if (current == DoorState.CLOSED) return;

            String interactionState = DoorBlockUtils.getInteractionState(current, DoorState.CLOSED);
            if (interactionState == null) {
                LOGGER.debug("[PORTA] fechar ({},{},{}): getInteractionState({} -> CLOSED) devolveu null",
                        pos.x, pos.y, pos.z, current);
                return;
            }

            world.setBlockInteractionState(door.getBlockPosition(), door.getBlockType(), interactionState);
            LOGGER.debug("[PORTA] FECHOU ({},{},{}) {} -> CLOSED (estado '{}')",
                    pos.x, pos.y, pos.z, current, interactionState);

            LOGGER.debug("[SimTale] Porta em ({}, {}, {}) fechou sozinha -> {}",
                    pos.x, pos.y, pos.z, interactionState);
        } catch (Exception e) {
            LOGGER.debug("[SimTale] Falha ao fechar porta em ({}, {}, {}): {}",
                    pos.x, pos.y, pos.z, e.toString());
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
            if (dx * dx + dy * dy + dz * dz < KEEP_OPEN_RADIUS_SQ) return true;
        }
        return false;
    }

    private static HouseBlockPos toKey(Vector3i pos) {
        return new HouseBlockPos(pos.x, pos.y, pos.z);
    }
}
