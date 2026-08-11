package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The House Blueprint item: use it next to a bed to see whether that room counts as a house, and to
 * see how far it reaches.
 *
 * <p>Deliberately read-only. It answers "is this a house yet, and where does it end" and changes
 * nothing — registering a residence from an inspection tool would mean you could create one by
 * accident while checking, which is the opposite of what the item is for. Houses are still created
 * by an NPC claiming the bed.
 *
 * <p>The outline is what the item adds over {@code /simtale housecheck}: the command can tell you a
 * room is too large or not enclosed, and leaves you to guess which wall it meant. Targeting is the
 * same nearest-bed search in both, because RuneCore's item callback exposes no click target — see
 * {@link #inspect}.
 */
public final class HouseBlueprintHelper {

    private static final SimLog LOGGER = SimLog.forClass(HouseBlueprintHelper.class);

    /** Item id that triggers this. */
    public static final String ITEM_ID = "HouseBlueprint";

    /**
     * Blocks used to draw the outline. Both ship with the mod and exist as block types, not only
     * as items — the hologram takes block ids.
     */
    private static final String OUTLINE_VALID = "Green_Block_Preview";
    private static final String OUTLINE_INVALID = "Red_Block_Preview";

    /** How long the outline stays up. Long enough to walk the perimeter, short enough to not litter. */
    private static final int OUTLINE_TICKS = 20 * 12;

    /** Live outlines, keyed by player, so a second click replaces the first instead of stacking. */
    private static final Map<UUID, Outline> ACTIVE = new ConcurrentHashMap<>();

    private record Outline(Ref<EntityStore> ghost, long expiryTick) {
    }

    private HouseBlueprintHelper() {
    }

    /** Beds further than this from the player are not what they meant to inspect. */
    private static final int SEARCH_RADIUS = 8;

    /**
     * Inspects the house whose bed is nearest the player.
     *
     * <p>Nearest, and not the bed the player pointed at, which is what this was written to do
     * first. RuneCore's item callback carries the player and nothing else — no target block, no
     * target entity — and every custom item in this mod goes through it. Pointing was the nicer
     * design and simply is not reachable from here; the radius is kept tight so "nearest" stays
     * unambiguous in practice.
     *
     * @return true when the click was consumed
     */
    public static boolean inspect(World world, PlayerRef playerRef, Vector3d playerPos) {
        if (world == null || playerRef == null || playerPos == null) return false;

        HouseBlockPos bed = nearestBed(playerPos);
        if (bed == null) {
            playerRef.sendMessage(Message.translation("general.blueprint.no_bed_nearby")
                    .param("radius", SEARCH_RADIUS));
            return true;
        }

        HouseManager.HouseScanResult raw = HouseManager.scanHouseFromBed(world, bed);
        HouseManager.HouseCompatibilityResult result =
                HouseManager.checkFullCompatibility(world, bed, playerRef.getUuid());

        playerRef.sendMessage(HouseManager.buildCompatibilityReport(result));
        playerRef.sendMessage(Message.translation("general.blueprint.summary")
                .param("interior", raw.interiorBlocks().size())
                .param("doors", raw.doorBlocks().size())
                .param("chests", raw.chestBlocks().size()));

        showOutline(world, playerRef.getUuid(), raw, result.fullyCompatible());
        LOGGER.info("[SimTale] Blueprint inspecionou a cama ({},{},{}): {} blocos internos, valida={}",
                bed.x, bed.y, bed.z, raw.interiorBlocks().size(), result.fullyCompatible());
        return true;
    }

    /** Closest registered bed within {@link #SEARCH_RADIUS}, or null when there is none. */
    private static HouseBlockPos nearestBed(Vector3d from) {
        HouseBlockPos best = null;
        double bestDist = (double) SEARCH_RADIUS * SEARCH_RADIUS;

        synchronized (BedRegistry.BEDS) {
            for (com.cookieukw.SimTale.db.SimBedData.BedPos bed : BedRegistry.BEDS) {
                double dx = from.x - (bed.x + 0.5);
                double dy = from.y - (bed.y + 0.5);
                double dz = from.z - (bed.z + 0.5);
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= bestDist) {
                    bestDist = distSq;
                    best = new HouseBlockPos(bed.x, bed.y, bed.z);
                }
            }
        }
        return best;
    }

    /**
     * Draws the floor of the room.
     *
     * <p>Only the lowest layer, not the whole interior: filling 350 blocks of air with a solid
     * hologram would replace the room with a coloured brick and hide everything the player is
     * trying to look at. The footprint answers the actual question — how far the house reaches —
     * and leaves the room visible.
     */
    private static void showOutline(World world, UUID viewer, HouseManager.HouseScanResult raw, boolean valid) {
        clear(world, viewer);

        Set<HouseBlockPos> interior = raw.interiorBlocks();
        if (interior.isEmpty()) return;

        int floorY = Integer.MAX_VALUE;
        for (HouseBlockPos pos : interior) {
            if (pos.y < floorY) floorY = pos.y;
        }

        Set<HouseBlockPos> footprint = new HashSet<>();
        for (HouseBlockPos pos : interior) {
            if (pos.y == floorY) footprint.add(pos);
        }
        if (footprint.isEmpty()) return;

        HouseBlockPos anchorPos = footprint.iterator().next();
        Vector3i anchor = new Vector3i(anchorPos.x, anchorPos.y, anchorPos.z);

        // BlockChange takes the numeric block id, not the name — resolved once for the whole
        // footprint rather than per block.
        String blockName = valid ? OUTLINE_VALID : OUTLINE_INVALID;
        int blockId = BlockType.getBlockIdOrUnknown(
                blockName, "SimTale: bloco de contorno desconhecido: %s", blockName);

        List<BlockChange> blocks = new ArrayList<>(footprint.size());
        for (HouseBlockPos pos : footprint) {
            blocks.add(new BlockChange(pos.x - anchor.x, pos.y - anchor.y, pos.z - anchor.z, blockId, (byte) 0));
        }

        Ref<EntityStore> ghost = PrefabGhostHelper.showRaw(
                world, anchor, blocks.toArray(new BlockChange[0]),
                valid ? PrefabGhostHelper.TINT_CLEAR : PrefabGhostHelper.TINT_BLOCKED);
        if (ghost != null) {
            ACTIVE.put(viewer, new Outline(ghost, world.getTick() + OUTLINE_TICKS));
        }
    }

    /** Drops the outline a player currently has up, if any. */
    public static void clear(World world, UUID viewer) {
        Outline existing = ACTIVE.remove(viewer);
        if (existing != null) {
            PrefabGhostHelper.hideRaw(world, existing.ghost());
        }
    }

    /**
     * Removes outlines whose time is up. Called from the mod's own tick.
     * <p>
     * Without this the hologram would live until the player inspected something else, and one left
     * on a house they walked away from is indistinguishable from a bug.
     */
    public static void tickExpiry(World world) {
        if (world == null || ACTIVE.isEmpty()) return;
        long now = world.getTick();
        for (Map.Entry<UUID, Outline> entry : ACTIVE.entrySet()) {
            if (now >= entry.getValue().expiryTick()) {
                PrefabGhostHelper.hideRaw(world, entry.getValue().ghost());
                ACTIVE.remove(entry.getKey());
            }
        }
    }
}
