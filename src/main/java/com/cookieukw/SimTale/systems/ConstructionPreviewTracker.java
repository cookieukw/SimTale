package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
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

    /** How far in front of the player (along their look direction) the preview anchor sits. */
    private static final double FOLLOW_DISTANCE = 4.0;

    /** Re-check every few ticks rather than every single one — the hologram redraw this can
     *  trigger (a full re-scan of the prefab's footprint for obstructions) is not cheap enough
     *  to run 20x/second for a value that only visibly changes when the player crosses a block
     *  boundary or flips which cardinal direction is nearest. */
    private static final int UPDATE_INTERVAL_TICKS = 5;

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
        if (playerRef == null) return;

        World world = WorldUtil.first();
        if (world == null) return;

        if (world.getTick() % UPDATE_INTERVAL_TICKS != 0) return;

        ConstructionSiteComponent site = ConstructionPreviewManager.get(playerRef.getUuid());
        if (site == null) return;

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d playerPos = transform.getPosition();
        // Hytale's forward axis is -Z (same convention as RoutineAISystem/NPCInteractionPage's
        // atan2(-dx, -dz) — see those for the fuller explanation), so walking "forward" moves
        // along (-sin(yaw), -cos(yaw)).
        double yaw = transform.getRotation().yaw();
        double dirX = -Math.sin(yaw);
        double dirZ = -Math.cos(yaw);

        int anchorX = (int) Math.floor(playerPos.x + dirX * FOLLOW_DISTANCE);
        int anchorZ = (int) Math.floor(playerPos.z + dirZ * FOLLOW_DISTANCE);
        int anchorY = findGroundY(world, anchorX, (int) Math.floor(playerPos.y), anchorZ);

        // Face back toward the player from the new anchor — the direction the front of the
        // house should point is the opposite of "anchor relative to player", i.e. straight at
        // them.
        double towardPlayerX = playerPos.x - (anchorX + 0.5);
        double towardPlayerZ = playerPos.z - (anchorZ + 0.5);
        double facingYawDegrees = Math.toDegrees(Math.atan2(-towardPlayerX, -towardPlayerZ));
        Rotation4 newFacing = Rotation4.fromYawDegrees(facingYawDegrees);

        Vector3i newAnchor = new Vector3i(anchorX, anchorY, anchorZ);
        if (newAnchor.equals(site.anchor) && newFacing == site.facing) {
            return; // Nothing visibly changed — skip the redraw.
        }

        site.anchor = newAnchor;
        site.facing = newFacing;
        site.roofFacing = newFacing;
        ConstructionHelper.placePreview(world, site);
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
