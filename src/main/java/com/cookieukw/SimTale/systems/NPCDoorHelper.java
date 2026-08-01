package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.DoorBlockUtils;
import com.hypixel.hytale.server.core.modules.interaction.DoorBlockUtils.DoorState;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens and closes doors for NPCs.
 *
 * <p>Replaces the old {@code HouseDoorManager}, which had two independent bugs — and
 * either of them alone would prevent the entire system from working.
 *
 * <h3>Bug 1: no door opened, for anyone</h3>
 * The code constructed the new state via string manipulation:
 * <pre>
 *   if (state.toLowerCase().contains("closed"))                 // tested in lowercase
 *       openState = state.replace("closed","open")              // but replaced in the original string
 *                        .replace("CLOSED","OPEN");
 * </pre>
 * The actual door states in Hytale are {@code CloseDoorIn}, {@code CloseDoorOut},
 * {@code OpenDoorIn}, {@code OpenDoorOut}, and {@code DoorBlocked}. {@code "CloseDoorIn"} in
 * lowercase becomes {@code "closedoorin"}, which <em>contains</em> {@code "closed"} — so the
 * {@code if} statement passed by accident. However, the original string does not contain {@code "closed"}
 * nor {@code "CLOSED"}, so both {@code replace} calls changed nothing and {@code openState} came out
 * <b>identical</b> to the closed state. The door was "opened" to the state it already had.
 * Auto-close had the exact same mirrored bug.
 *
 * <h3>Bug 2: only looked at the registered house doors of the NPC themselves</h3>
 * The lookup started from {@code HouseManager.OWNER_TO_HOUSE_ID.get(npc.entityId)}. An NPC without a house,
 * an NPC visiting another house, or any door not belonging to a registered house
 * (village gate, workshop door) were never even considered.
 *
 * <h3>How it works now</h3>
 * Scans blocks around the NPC and delegates all decisions to the engine's own API
 * ({@link DoorBlockUtils} and {@link DoorInteraction#getDoorAtPosition}), instead of reimplementing
 * door logic with text. Nothing here depends on a registered house.
 */
public final class NPCDoorHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(NPCDoorHelper.class);

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

    /** Open doors with remaining ticks before closing. */
    public static final Map<HouseBlockPos, Integer> OPENED_DOORS = new ConcurrentHashMap<>();

    private static long lastAutoCloseTick = -1L;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    public static void handleNpcDoors(World world, SimNPCComponent npc, TransformComponent transform) {
        if (world == null || npc == null || transform == null || npc.entityId == null) return;

        long tick = world.getTick();
        if (tick != lastAutoCloseTick) {
            lastAutoCloseTick = tick;
            tickAutoClose(world);
        }

        if ((tick + Math.abs(npc.entityId.hashCode())) % CHECK_INTERVAL != 0) return;

        Vector3d npcPos = transform.getPosition();
        int baseX = (int) Math.floor(npcPos.x);
        int baseY = (int) Math.floor(npcPos.y);
        int baseZ = (int) Math.floor(npcPos.z);

        // A double-height door appears in multiple scan cells, and getDoorAtPosition
        // normalizes all of them to the same position. Without this, the same door would be processed
        // up to six times in the same tick.
        Set<Vector3i> handled = new HashSet<>();

        for (int dx = -SCAN_XZ; dx <= SCAN_XZ; dx++) {
            for (int dy = -SCAN_DOWN; dy <= SCAN_UP; dy++) {
                for (int dz = -SCAN_XZ; dz <= SCAN_XZ; dz++) {
                    tryOpenDoorAt(world, npc, npcPos, baseX + dx, baseY + dy, baseZ + dz, handled);
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Opening
    // ---------------------------------------------------------------------

    private static void tryOpenDoorAt(World world, SimNPCComponent npc, Vector3d npcPos,
                                      int x, int y, int z, Set<Vector3i> handled) {
        try {
            BlockType type = world.getBlockType(x, y, z);
            // isDoor() is a true property flag of the BlockType (IsDoor in the asset JSON). The old
            // code used getId().toLowerCase().contains("door"), which besides being fragile caught
            // any block with "door" in the name — trapdoor, doorframe, decoration.
            if (type == null || !type.isDoor()) return;

            ChunkStore chunkStore = world.getChunkStore();
            Rotation yaw = RotationTuple.get(world.getBlockRotationIndex(x, y, z)).yaw();

            DoorInteraction.DoorInfo door =
                    DoorInteraction.getDoorAtPosition(chunkStore, x, y, z, yaw);
            if (door == null) return;

            Vector3i doorPos = door.getBlockPosition();
            if (doorPos == null || !handled.add(new Vector3i(doorPos))) return;

            DoorState current = door.getDoorState();
            if (current != DoorState.CLOSED) {
                // Already open. Renew the cooldown to prevent closing in front of a passing NPC.
                OPENED_DOORS.computeIfPresent(
                        toKey(doorPos), (k, v) -> AUTO_CLOSE_TICKS);
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
            if (interactionState == null) return;

            // Covers the DoorBlocked state: an obstructed door cannot open.
            if (!DoorBlockUtils.canOpenDoor(chunkStore, doorPos, interactionState)) return;

            world.setBlockInteractionState(doorPos, door.getBlockType(), interactionState);
            OPENED_DOORS.put(toKey(doorPos), AUTO_CLOSE_TICKS);

            LOGGER.debug("[SimTale] NPC '{}' abriu porta em ({}, {}, {}) -> {}",
                    npc.name, doorPos.x, doorPos.y, doorPos.z, interactionState);
        } catch (Exception e) {
            // A problematic door shouldn't crash the entire NPC AI tick.
            LOGGER.debug("[SimTale] Falha ao abrir porta em ({}, {}, {}): {}", x, y, z, e.toString());
        }
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
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null || !type.isDoor()) return;

            ChunkStore chunkStore = world.getChunkStore();
            Rotation yaw = RotationTuple.get(world.getBlockRotationIndex(pos.x, pos.y, pos.z)).yaw();

            DoorInteraction.DoorInfo door =
                    DoorInteraction.getDoorAtPosition(chunkStore, pos.x, pos.y, pos.z, yaw);
            if (door == null) return;

            DoorState current = door.getDoorState();
            if (current == DoorState.CLOSED) return;

            String interactionState = DoorBlockUtils.getInteractionState(current, DoorState.CLOSED);
            if (interactionState == null) return;

            world.setBlockInteractionState(door.getBlockPosition(), door.getBlockType(), interactionState);

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
