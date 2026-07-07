package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabManager;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.PrefabBlock;

public class ConstructionHelper {

    public static void clearPreview(World world, ConstructionSiteComponent site) {
        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab != null && !prefab.getBlocks().isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (com.cookieukw.SimTale.core.PrefabBlock b : prefab.getBlocks()) {
                if (b.getX() < minX) minX = b.getX();
                if (b.getX() > maxX) maxX = b.getX();
                if (b.getY() < minY) minY = b.getY();
                if (b.getY() > maxY) maxY = b.getY();
                if (b.getZ() < minZ) minZ = b.getZ();
                if (b.getZ() > maxZ) maxZ = b.getZ();
            }
            int px = site.anchor.x;
            int py = site.anchor.y;
            int pz = site.anchor.z;
            // Clear Bottom and Top edges
            for (int x = minX; x <= maxX; x++) {
                world.setBlock(px + x, py + minY, pz + minZ, "Air");
                world.setBlock(px + x, py + minY, pz + maxZ, "Air");
                world.setBlock(px + x, py + maxY, pz + minZ, "Air");
                world.setBlock(px + x, py + maxY, pz + maxZ, "Air");
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlock(px + minX, py + minY, pz + z, "Air");
                world.setBlock(px + maxX, py + minY, pz + z, "Air");
                world.setBlock(px + minX, py + maxY, pz + z, "Air");
                world.setBlock(px + maxX, py + maxY, pz + z, "Air");
            }
            // Clear Vertical pillars
            for (int y = minY; y <= maxY; y++) {
                world.setBlock(px + minX, py + y, pz + minZ, "Air");
                world.setBlock(px + maxX, py + y, pz + minZ, "Air");
                world.setBlock(px + minX, py + y, pz + maxZ, "Air");
                world.setBlock(px + maxX, py + y, pz + maxZ, "Air");
            }
        }
    }

    public static void placePreview(World world, Store<EntityStore> eStore, Vector3i playerAnchor, String prefabName) {
        Prefab prefab = PrefabManager.getPrefab(prefabName);
        if (prefab == null) {
            return;
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        ConstructionSiteComponent site = new ConstructionSiteComponent(prefabName, playerAnchor);
        holder.addComponent(SimTale.CONSTRUCTION_COMPONENT_TYPE, site);

        SimTale.ACTIVE_SITES.add(site);
        eStore.addEntity(holder, AddReason.SPAWN);

        // Place wireframe blocks for preview
        if (!prefab.getBlocks().isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (PrefabBlock b : prefab.getBlocks()) {
                if (b.getX() < minX) minX = b.getX();
                if (b.getX() > maxX) maxX = b.getX();
                if (b.getY() < minY) minY = b.getY();
                if (b.getY() > maxY) maxY = b.getY();
                if (b.getZ() < minZ) minZ = b.getZ();
                if (b.getZ() > maxZ) maxZ = b.getZ();
            }
            String markerBlock = "Soil_Clay_Smooth_Lime"; 
            int px = playerAnchor.x;
            int py = playerAnchor.y;
            int pz = playerAnchor.z;
            
            // Bottom and Top edges
            for (int x = minX; x <= maxX; x++) {
                world.setBlock(px + x, py + minY, pz + minZ, markerBlock);
                world.setBlock(px + x, py + minY, pz + maxZ, markerBlock);
                world.setBlock(px + x, py + maxY, pz + minZ, markerBlock);
                world.setBlock(px + x, py + maxY, pz + maxZ, markerBlock);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlock(px + minX, py + minY, pz + z, markerBlock);
                world.setBlock(px + maxX, py + minY, pz + z, markerBlock);
                world.setBlock(px + minX, py + maxY, pz + z, markerBlock);
                world.setBlock(px + maxX, py + maxY, pz + z, markerBlock);
            }
            // Vertical pillars
            for (int y = minY; y <= maxY; y++) {
                world.setBlock(px + minX, py + y, pz + minZ, markerBlock);
                world.setBlock(px + maxX, py + y, pz + minZ, markerBlock);
                world.setBlock(px + minX, py + y, pz + maxZ, markerBlock);
                world.setBlock(px + maxX, py + y, pz + maxZ, markerBlock);
            }
        }
    }
}
