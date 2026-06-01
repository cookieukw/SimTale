package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabBlock;
import com.cookieukw.SimTale.core.PrefabManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

public class ConstructionSystem extends EntityTickingSystem<EntityStore> {

    private static final int TICKS_PER_BLOCK = 2; // Speed of construction

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return (Query<EntityStore>) (Object) SimTale.CONSTRUCTION_COMPONENT_TYPE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        ConstructionSiteComponent site = chunk.getComponent(index, SimTale.CONSTRUCTION_COMPONENT_TYPE);
        if (site == null) return;

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;

        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null) {
            commandBuffer.removeComponent(chunk.getReferenceTo(index), SimTale.CONSTRUCTION_COMPONENT_TYPE);
            return;
        }

        site.ticksSinceLastBlock++;
        if (site.ticksSinceLastBlock >= TICKS_PER_BLOCK) {
            site.ticksSinceLastBlock = 0;

            if (site.currentIndex >= prefab.getBlocks().size()) {
                // Finished construction
                commandBuffer.removeComponent(chunk.getReferenceTo(index), SimTale.CONSTRUCTION_COMPONENT_TYPE);
                return;
            }

            // Build multiple blocks per tick if we want to speed it up, or just 1
            int blocksToBuild = 1;
            for (int i = 0; i < blocksToBuild; i++) {
                if (site.currentIndex >= prefab.getBlocks().size()) break;

                PrefabBlock blockInfo = prefab.getBlocks().get(site.currentIndex);
                
                int worldX = site.anchor.x + blockInfo.getX();
                int worldY = site.anchor.y + blockInfo.getY();
                int worldZ = site.anchor.z + blockInfo.getZ();

                String type = blockInfo.getName();
                if (type == null) {
                    type = "air"; // Fallback to air if null
                }

                world.setBlock(worldX, worldY, worldZ, type);
                site.currentIndex++;
            }
        }
    }
}
