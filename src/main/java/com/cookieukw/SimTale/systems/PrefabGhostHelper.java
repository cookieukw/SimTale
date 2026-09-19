package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabBlock;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.BlockChange;
import com.hypixel.hytale.protocol.packets.interface_.FluidChange;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PrefabPreview;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds construction previews with the engine's own prefab hologram instead of painting
 * marker blocks into the world.
 *
 * <p>The mod used to fake a preview by overwriting real blocks with a coloured marker and
 * remembering what was underneath so it could put them back. That approach edits the actual
 * world for something purely visual: any crash, chunk unload or missed restore between placing
 * and clearing left the marker permanently baked into the terrain, and the "preview" could only
 * ever be a wireframe box because a solid one would have destroyed everything inside it.
 *
 * <p>The engine already ships the right tool. {@code PrefabPreview} is a client-side hologram:
 * the server attaches the component to a normal entity, {@code PrefabPreviewSystems} streams it
 * to nearby players, and no block in the world is ever touched. Removing the entity removes the
 * hologram — there is nothing to restore.
 *
 * <p>We deliberately attach {@link PrefabPreview} directly rather than going through
 * {@code PersistentPrefabPreview.spawn(...)}. That helper resolves a {@code prefabKey} against
 * the server's {@code PrefabStore}, and SimTale's prefabs are plain JSON resources
 * ({@code /prefabs/*.prefab.json}) that the store has never heard of. The setup system that
 * backs the helper does nothing but translate a stored prefab into {@code BlockChange[]} and
 * hand it to {@code PrefabPreview} — so we skip it and supply the array ourselves. The network
 * tracker queries {@code PrefabPreview} alone, so it drives the hologram either way.
 */
public final class PrefabGhostHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PrefabGhostHelper() {
        // Utility class, not meant to be instantiated.
    }

    /**
     * Tint applied to the hologram, as plain {@code 0xRRGGBB}. These mirror the engine's own
     * {@code PrefabPreviewSystems.DEFAULT_BIOME_TINT} (0x5B9E28) and {@code DEFAULT_WATER_TINT}
     * (0x0A3355).
     *
     * <p>Worth being precise about what this does, because the name oversells it: it is the
     * <em>biome</em> tint, so it only recolours blocks that sample a biome tint at all — grass,
     * leaves, foliage. It will not wash a whole stone house red. The obstruction state is
     * therefore reported to the player in words by BuildCommand, and this is only a hint on top.
     */
    public static final int TINT_CLEAR = 0x5B9E28;
    public static final int TINT_BLOCKED = 0xB03A2E;
    private static final int WATER_TINT = 0x0A3355;

    /* ---------------------------------------------------------------------
    Public API
    ---------------------------------------------------------------------
    */

    /**
     * Shows (or reshows) the hologram for {@code site}, replacing any hologram it already had.
     * The entity reference is stored on the site itself, so callers do not have to track it.
     */
    public static void show(World world, ConstructionSiteComponent site, Prefab prefab, boolean isClear) {
        if (world == null || site == null || prefab == null) return;

        BlockChange[] blocks = buildBlockChanges(prefab, site.prefabName, site.facing);
        if (blocks.length == 0) return;

        int layers = countLayers(prefab);
        int tint = isClear ? TINT_CLEAR : TINT_BLOCKED;

        /* Spawning an entity is a structural store write and blows up with
        "Store is currently processing!" if it lands mid-tick. placePreview is reachable from
        an event handler, so that is a real possibility rather than a theoretical one.
        */
        runOnWorldThread(world, () -> {
            hideNow(world, site);
            site.previewGhost = spawnGhost(world, site.anchor, blocks, tint, layers);
        });
    }

    /** Removes the hologram for {@code site}, if it has one. Safe to call repeatedly. */
    public static void hide(World world, ConstructionSiteComponent site) {
        if (world == null || site == null || site.previewGhost == null) return;
        runOnWorldThread(world, () -> hideNow(world, site));
    }

    /**
     * Reveals the hologram only up to {@code visibleLayers} horizontal slices.
     * Lets a site under construction show the part that has not been built yet.
     */
    public static void setVisibleLayers(World world, ConstructionSiteComponent site, int visibleLayers) {
        if (world == null || site == null || site.previewGhost == null) return;

        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> ref = site.previewGhost;
        if (!ref.isValid()) return;

        /* A plain field write on an existing component: no structural change, so this one is
        safe mid-tick and needs no deferral.
        */
        PrefabPreview preview = store.getComponent(ref, PrefabPreview.getComponentType());
        if (preview != null) {
            preview.setVisibleLayerCount(Math.max(0, visibleLayers));
        }
    }

    /* ---------------------------------------------------------------------
    Geometry
    ---------------------------------------------------------------------
    */

    /**
     * Converts the mod's prefab blocks into the protocol's {@link BlockChange} array.
     *
     * <p>Coordinates are emitted local to the hologram entity, which is spawned at the site
     * anchor — the engine renders them relative to the entity's transform.
     *
     * <p>Rotation is baked into the positions here rather than handed to the entity's
     * {@code Rotation3f}, so that the hologram is produced by exactly the same arithmetic that
     * {@link ConstructionHelper#mapperFor} gives the builders. A hologram that disagreed
     * with the finished house by even one axis would be worse than no hologram at all.
     */
    private static BlockChange[] buildBlockChanges(Prefab prefab, String prefabName, Rotation4 facing) {
        List<PrefabBlock> source = prefab.getBlocks();
        if (source == null || source.isEmpty()) return new BlockChange[0];

        ConstructionHelper.OffsetMapper mapper = ConstructionHelper.mapperFor(facing);

        List<BlockChange> out = new ArrayList<>(source.size());
        for (PrefabBlock block : source) {
            String name = block.getName();
            // Empty is the prefab's way of saying "carve this out". There is nothing to draw.
            if (name == null || name.isEmpty() || name.equalsIgnoreCase("Empty")) continue;

            int blockId = BlockType.getBlockIdOrUnknown(
                    name, "SimTale: bloco desconhecido no prefab '%s': %s", prefabName, name);

            Vector3i offset = mapper.offset(block.getX(), block.getY(), block.getZ());

            byte rotation = (byte) (block.getRotation() != null ? block.getRotation() : 0);
            out.add(new BlockChange(offset.x, offset.y, offset.z, blockId, rotation));
        }
        return out.toArray(new BlockChange[0]);
    }

    /** Height of the prefab in blocks, i.e. how many layers the hologram can reveal. */
    private static int countLayers(Prefab prefab) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (PrefabBlock block : prefab.getBlocks()) {
            minY = Math.min(minY, block.getY());
            maxY = Math.max(maxY, block.getY());
        }
        return maxY >= minY ? (maxY - minY + 1) : 0;
    }

    /* ---------------------------------------------------------------------
    Entity lifecycle
    ---------------------------------------------------------------------
    */

    /**
     * Spawns a hologram of arbitrary blocks, not tied to a construction site.
     * <p>
     * Exists so the house blueprint can outline a room without inventing a fake
     * ConstructionSiteComponent to carry the reference. Caller owns the returned ref and is
     * responsible for removing it.
     *
     * @param blocks offsets relative to {@code anchor}
     * @return the hologram entity, or null if it could not be created
     */
    public static Ref<EntityStore> showRaw(World world, Vector3i anchor, BlockChange[] blocks, int tint) {
        if (world == null || anchor == null || blocks == null || blocks.length == 0) return null;
        return spawnGhost(world, anchor, blocks, tint, Integer.MAX_VALUE);
    }

    /** Removes a hologram created by {@link #showRaw}. Safe to call with null or a stale ref. */
    public static void hideRaw(World world, Ref<EntityStore> ref) {
        if (world == null || ref == null || !ref.isValid()) return;
        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: falha ao remover o holograma: " + e);
        }
    }

    private static Ref<EntityStore> spawnGhost(World world, Vector3i anchor, BlockChange[] blocks,
                                               int biomeTint, int layers) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            EntityStore entityStore = (EntityStore) store.getExternalData();

            Holder<EntityStore> holder = store.getRegistry().newHolder();
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(entityStore.takeNextNetworkId()));
            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(
                            new Vector3d(anchor.x, anchor.y, anchor.z), new Rotation3f()));
            /* Argument order is (blocks, fluids, visibleLayerCount, biomeTint, waterTint) — note
            that it does NOT follow the field declaration order, which lists the tints first.
            Verified against the constructor bytecode rather than inferred: swapping them
            compiles fine and simply renders the hologram almost black.
            */
            holder.addComponent(PrefabPreview.getComponentType(),
                    new PrefabPreview(blocks, new FluidChange[0], layers, biomeTint, WATER_TINT));
            holder.ensureComponent(UUIDComponent.getComponentType());

            return store.addEntity(holder, AddReason.SPAWN);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: falha ao criar o holograma de preview: " + e);
            return null;
        }
    }

    /** Actually despawns the hologram. Must already be on the world thread. */
    private static void hideNow(World world, ConstructionSiteComponent site) {
        Ref<EntityStore> ref = site.previewGhost;
        /* Cleared first: if removeEntity throws, the site must not keep pointing at an entity
        it can no longer manage, or every later show() would leak another hologram.
        */
        site.previewGhost = null;
        if (ref == null || !ref.isValid()) return;

        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: falha ao remover o holograma de preview: " + e);
        }
    }

    /**
     * Runs now when it is already safe to write to the store, otherwise defers to the world thread.
     *
     * <p>Same shape as {@code NpcFreezeUtil}: {@code world.execute} queues onto the world thread,
     * which by definition drains outside a system's processing window, so a deferred task can no
     * longer hit "Store is currently processing!".
     */
    private static void runOnWorldThread(World world, Runnable task) {
        if (world.isTicking()) {
            world.execute(task);
        } else {
            task.run();
        }
    }
}
