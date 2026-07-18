package com.cookieukw.SimTale.systems;

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

        ConstructionHelper.clearPreview(world, site);

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
}
