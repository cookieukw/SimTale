package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The House Blueprint item: right-click a bed to see whether that room counts as a house, and to
 * see how far it reaches.
 *
 * <p>Deliberately read-only. It answers "is this a house yet, and where does it end" and changes
 * nothing — registering a residence from an inspection tool would mean you could create one by
 * accident while checking, which is the opposite of what the item is for. Houses are still created
 * by an NPC claiming the bed.
 *
 * <p>It is not a reskin of {@code /simtale housecheck} either, on one point that matters in a
 * village: the command guesses which bed you meant by taking the nearest within 16 blocks, while
 * the item knows, because you pointed at it.
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

    /**
     * Handles a right-click on {@code clicked} while holding the blueprint.
     *
     * @return true when the click was consumed
     */
    public static boolean inspect(World world, PlayerRef playerRef, Vector3i clicked) {
        if (world == null || playerRef == null || clicked == null) return false;

        // The bed is the identity of a house, so the blueprint only has an answer when pointed at
        // one. Anywhere else it would have to guess which room you meant, which is the ambiguity
        // this item exists to remove.
        HouseBlockPos bed = resolveBed(world, clicked);
        if (bed == null) {
            playerRef.sendMessage(Message.translation("general.blueprint.not_a_bed"));
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

    /**
     * Finds the bed for the clicked block.
     *
     * <p>A bed spans several blocks and the registry stores only the anchor, so clicking the foot
     * of your own bed has to resolve to the same house as clicking the head. Falls back to the
     * registry within a block, which is what {@code FurnitureAnchorHelper} normalises to.
     */
    private static HouseBlockPos resolveBed(World world, Vector3i clicked) {
        synchronized (BedRegistry.BEDS) {
            for (com.cookieukw.SimTale.db.SimBedData.BedPos bed : BedRegistry.BEDS) {
                if (Math.abs(bed.x - clicked.x) <= 1
                        && Math.abs(bed.y - clicked.y) <= 1
                        && Math.abs(bed.z - clicked.z) <= 1) {
                    return new HouseBlockPos(bed.x, bed.y, bed.z);
                }
            }
        }
        return null;
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
        String blockId = valid ? OUTLINE_VALID : OUTLINE_INVALID;

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
