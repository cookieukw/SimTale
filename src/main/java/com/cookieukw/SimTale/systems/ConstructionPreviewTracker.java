package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * Keeps an in-progress construction preview (before {@code /build start} commits it) glued to
 * the player who placed it, instead of the one-shot static hologram it used to be.
 * <p>
 * Requested directly by the user after testing the item-based blueprint flow: walking away left
 * the hologram behind forever (nothing ever moved or cleared it), and there was no way to rotate
 * it except the separate {@code /build rotate} command. The fix folds movement itself into the
 * placement tool — the preview now sits a fixed distance in front of wherever the player is
 * looking, at the real ground height there (not the player's own Y — standing in a pit must not
 * plant the house at pit-bottom height), and always turns to face back toward the player. Walking
 * around it is now the rotation control: whichever side currently faces you becomes the front.
 */
public class ConstructionPreviewTracker extends EntityTickingSystem<EntityStore> {

    private static final SimLog LOGGER = SimLog.forClass(ConstructionPreviewTracker.class);

    /** How far in front of the player (along their look direction) the preview anchor sits. */
    private static final double FOLLOW_DISTANCE = 4.0;

    /** Re-check every few ticks rather than every single one. A facing change triggers a full
     *  rebuild — clearPreview + placePreview, which re-scans the entire prefab footprint block
     *  by block for obstructions (a house-sized prefab is easily thousands of block reads) and
     *  despawns/respawns the hologram entity — expensive enough that running it several times a
     *  second visibly bogged down the whole server, not just this feature. This only needs to
     *  feel responsive to a deliberate turn, not to camera micro-motion. */
    private static final int UPDATE_INTERVAL_TICKS = 20;

    /** How far up/down from the player's own feet to look for solid ground at the anchor's X/Z.
     *  Bounded on purpose — an unbounded scan is a real cost run this often, and a preview more
     *  than this far above or below the player is not a case worth handling well anyway. */
    private static final int GROUND_SCAN_RANGE = 20;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) {
            LOGGER.debug("[SimTale] ConstructionPreviewTracker: no PlayerRef this tick for index {}", index);
            return;
        }

        World world = WorldUtil.first();
        if (world == null) return;

        if (world.getTick() % UPDATE_INTERVAL_TICKS != 0) return;

        ConstructionSiteComponent site = ConstructionPreviewManager.get(playerRef.getUuid());
        if (site == null) {
            LOGGER.debug("[SimTale] ConstructionPreviewTracker: no active session for {}", playerRef.getUuid());
            return;
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            LOGGER.debug("[SimTale] ConstructionPreviewTracker: no TransformComponent for {}", playerRef.getUuid());
            return;
        }

        Vector3d playerPos = transform.getPosition();
        // Hytale's forward axis is -Z (same convention as RoutineAISystem/NPCInteractionPage's
        // atan2(-dx, -dz) — see those for the fuller explanation), so walking "forward" moves
        // along (-sin(yaw), -cos(yaw)).
        //
        // Snapped to the nearest 22.5° instead of using the raw continuous look yaw: mouse look
        // never holds perfectly still, and with a floor()'d block-position result even a
        // sub-degree wobble right at a boundary flipped the anchor back and forth between two
        // adjacent blocks — a redraw (hide+show the hologram) every time, which read as
        // constant flickering rather than smooth following. 22.5° buckets still give 16 distinct
        // aim directions — fine enough to steer, coarse enough that ordinary hand tremor stays
        // inside one bucket.
        double yawDegrees = Math.toDegrees(transform.getRotation().yaw());
        double snappedYawDegrees = Math.round(yawDegrees / 22.5) * 22.5;
        double yaw = Math.toRadians(snappedYawDegrees);
        double dirX = -Math.sin(yaw);
        double dirZ = -Math.cos(yaw);

        int anchorX = (int) Math.floor(playerPos.x + dirX * FOLLOW_DISTANCE);
        int anchorZ = (int) Math.floor(playerPos.z + dirZ * FOLLOW_DISTANCE);
        // The ground scan (up to GROUND_SCAN_RANGE*2 block reads) is the expensive part of this
        // check — skip it unless the anchor is actually moving to a new column. Turning to look
        // around without walking anywhere used to re-scan for ground on every 10-tick check for
        // no visible benefit, since the column (and so the correct ground level) hadn't changed.
        boolean columnChanged = site.anchor == null || anchorX != site.anchor.x || anchorZ != site.anchor.z;
        int anchorY = columnChanged
                ? findGroundY(world, anchorX, (int) Math.floor(playerPos.y), anchorZ)
                : site.anchor.y;

        // Face back toward the player from the new anchor — the direction the front of the
        // house should point is the opposite of "anchor relative to player", i.e. straight at
        // them.
        double towardPlayerX = playerPos.x - (anchorX + 0.5);
        double towardPlayerZ = playerPos.z - (anchorZ + 0.5);
        double facingYawDegrees = Math.toDegrees(Math.atan2(-towardPlayerX, -towardPlayerZ));
        // Hysteresis around each 90° boundary: standing still is never perfectly still (the
        // engine's own resting-state position jitter is enough on its own, with no mouse
        // movement involved at all), and a raw angle sitting right on a boundary flipped
        // Rotation4.fromYawDegrees's result back and forth forever. Keep the current facing
        // unless the angle has moved clearly past the boundary into the other zone.
        Rotation4 newFacing = resolveFacingWithHysteresis(facingYawDegrees, site.facing);

        Vector3i newAnchor = new Vector3i(anchorX, anchorY, anchorZ);
        boolean anchorChanged = !newAnchor.equals(site.anchor);
        boolean facingChanged = newFacing != site.facing;

        LOGGER.debug("[SimTale] ConstructionPreviewTracker: player=({},{},{}) yaw={} snappedYaw={} "
                        + "oldAnchor={} newAnchor={} oldFacing={} newFacing={} anchorChanged={} facingChanged={} previewGhost={}",
                playerPos.x, playerPos.y, playerPos.z, yawDegrees, snappedYawDegrees,
                site.anchor, newAnchor, site.facing, newFacing, anchorChanged, facingChanged, site.previewGhost);

        if (!anchorChanged && !facingChanged) {
            return; // Nothing visibly changed — skip the redraw.
        }

        site.anchor = newAnchor;

        if (facingChanged) {
            // Rotation is baked into the hologram's block offsets, not the entity's own
            // rotation (see PrefabGhostHelper#buildBlockChanges) — this is the one change that
            // genuinely needs a rebuild, and the one case where a brief redraw is unavoidable.
            site.facing = newFacing;
            site.roofFacing = newFacing;
            LOGGER.debug("[SimTale] ConstructionPreviewTracker: rebuilding hologram (facing changed) at {}", newAnchor);
            ConstructionHelper.placePreview(world, site);
        } else {
            // Position-only move: just slide the existing hologram entity, no despawn/respawn.
            LOGGER.debug("[SimTale] ConstructionPreviewTracker: moving hologram to {}", newAnchor);
            PrefabGhostHelper.move(world, site, newAnchor);
        }
    }

    /** See the hysteresis comment at the call site. {@code marginDegrees} is how far past a
     *  90° boundary the angle has to sit before the facing is allowed to actually flip. */
    private static Rotation4 resolveFacingWithHysteresis(double angleDegrees, Rotation4 current) {
        double normalized = ((angleDegrees % 360.0) + 360.0) % 360.0;
        // Wide on purpose: a rebuild is expensive (see UPDATE_INTERVAL_TICKS), so this needs to
        // filter out everything except a clear, deliberate turn — not just tip the scales away
        // from boundary jitter.
        double marginDegrees = 20.0;
        for (double boundary : new double[]{45.0, 135.0, 225.0, 315.0}) {
            double delta = Math.abs(normalized - boundary);
            delta = Math.min(delta, 360.0 - delta);
            if (delta < marginDegrees) {
                return current; // Too close to a boundary to trust — keep whatever it already was.
            }
        }
        return Rotation4.fromYawDegrees(normalized);
    }

    /** Nearest solid surface to {@code startY} at ({@code x}, {@code z}), searched outward within
     *  {@link #GROUND_SCAN_RANGE}. Falls back to {@code startY} itself if nothing solid is found
     *  in range (e.g. deep in a cave with no floor nearby) rather than guessing further. */
    private static int findGroundY(World world, int x, int startY, int z) {
        int top = Math.min(319, startY + GROUND_SCAN_RANGE);
        int bottom = Math.max(0, startY - GROUND_SCAN_RANGE);
        for (int y = top; y >= bottom; y--) {
            BlockType type = world.getBlockType(x, y, z);
            if (type != null && type.getId() != null && !type.getId().equalsIgnoreCase("Empty")) {
                return y + 1;
            }
        }
        return startY;
    }
}
