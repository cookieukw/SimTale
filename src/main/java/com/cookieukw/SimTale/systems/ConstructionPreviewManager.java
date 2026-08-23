package com.cookieukw.SimTale.systems;


import java.util.Collection;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConstructionPreviewManager {

    private static final Map<UUID, ConstructionSiteComponent> SESSIONS = new ConcurrentHashMap<>();

    private ConstructionPreviewManager() {
    }

    /**
     * Deterministic session key for a marker-block-triggered site (TavernBlueprint's place/break
     * handlers) — the {@code UUID} key here has never actually had to be a real player id, just
     * something stable and unique per site. A block only ever exists at one position at a time,
     * so the position itself is a fine identity: the same block always maps to the same key,
     * with no bookkeeping needed to remember which key went with which position.
     */
    public static UUID idForBlock(Vector3i pos) {
        return new UUID(0L, ((long) pos.x << 40) ^ ((long) pos.z << 20) ^ (long) (pos.y & 0xFFFFF));
    }

    public static ConstructionSiteComponent start(UUID playerId, String prefabName, Vector3i anchor) {
        ConstructionSiteComponent site = new ConstructionSiteComponent(prefabName, new Vector3i(anchor));
        site.ownerId = playerId;
        SESSIONS.put(playerId, site);
        return site;
    }

    public static void update(UUID playerId, World world, Vector3i anchor, Rotation4 facing) {
        ConstructionSiteComponent site = SESSIONS.get(playerId);
        if (site == null) {
            return;
        }

        if (!site.anchor.equals(anchor) || site.facing != facing) {
            ConstructionHelper.clearPreview(world, site);
            site.anchor = new Vector3i(anchor);
            site.facing = facing;
            site.roofFacing = facing;
            ConstructionHelper.placePreview(world, site);
        }
    }

    public static void rotate(UUID playerId, World world) {
        ConstructionSiteComponent site = SESSIONS.get(playerId);
        if (site == null) {
            return;
        }

        ConstructionHelper.clearPreview(world, site);
        int nextIdx = (site.facing.ordinal() + 1) % Rotation4.values().length;
        site.facing = Rotation4.values()[nextIdx];
        site.roofFacing = site.facing;
        ConstructionHelper.placePreview(world, site);
    }

    public static void rotateRoof(UUID playerId, World world) {
        ConstructionSiteComponent site = SESSIONS.get(playerId);
        if (site == null) {
            return;
        }

        ConstructionHelper.clearPreview(world, site);
        int nextIdx = (site.roofFacing.ordinal() + 1) % Rotation4.values().length;
        site.roofFacing = Rotation4.values()[nextIdx];
        ConstructionHelper.placePreview(world, site);
    }

    public static void clear(UUID playerId, World world) {
        ConstructionSiteComponent site = SESSIONS.remove(playerId);
        if (site == null) {
            return;
        }

        ConstructionHelper.clearPreview(world, site);
    }

    public static ConstructionSiteComponent commit(UUID playerId, World world) {
        ConstructionSiteComponent site = SESSIONS.remove(playerId);
        if (site == null) {
            return null;
        }

        // Deliberately NOT clearing the hologram here. Confirming does not mean a single block
        // exists yet — ConstructionSystem only starts placing real blocks once a builder NPC is
        // actually nearby and working (effectiveBuilders == 0 && !forceBuild is a no-op tick),
        // so clearing the ghost at this exact moment used to leave nothing at all visible for
        // however long that took. The ghost now stays up as a placeholder and is cleared once
        // the real building finishes (ConstructionSystem's "Finished construction" branch).

        Store<EntityStore> eStore = world.getEntityStore().getStore();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        
        holder.addComponent(SimTale.CONSTRUCTION_COMPONENT_TYPE, site);
        SimTale.ACTIVE_SITES.add(site);
        eStore.addEntity(holder, AddReason.SPAWN);

        return site;
    }

    public static ConstructionSiteComponent get(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    /**
     * Every session still pending confirmation — i.e. still just a hologram, not yet committed to
     * a real build. {@code commit()} removes a session from {@code SESSIONS} the moment it stops
     * being a preview, so nothing further to filter here.
     *
     * <p>Backed directly by the live map (a {@link ConcurrentHashMap}, so iterating it while
     * another thread calls {@code start}/{@code clear}/{@code commit} is safe — no snapshot copy
     * needed) for {@link ConstructionPreviewSweepSystem}'s periodic obstruction recheck.
     */
    public static Collection<ConstructionSiteComponent> allPending() {
        return SESSIONS.values();
    }
}
