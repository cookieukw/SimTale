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

import org.joml.Vector3d;
import javax.annotation.Nonnull;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;

public class ConstructionSystem extends EntityTickingSystem<EntityStore> {

    // Speed of construction
    public static int GLOBAL_SPEED = 1;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.CONSTRUCTION_COMPONENT_TYPE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        ConstructionSiteComponent site = chunk.getComponent(index, SimTale.CONSTRUCTION_COMPONENT_TYPE);
        if (site == null) return;

        if (!SimTale.ACTIVE_SITES.contains(site)) {
            SimTale.ACTIVE_SITES.add(site);
        }

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;

        if (!site.isBuilding) return;

        Prefab prefab = PrefabManager.getPrefab(site.prefabName);
        if (prefab == null || prefab.getBlocks() == null) {
            commandBuffer.removeComponent(chunk.getReferenceTo(index), SimTale.CONSTRUCTION_COMPONENT_TYPE);
            SimTale.ACTIVE_SITES.remove(site);
            return;
        }

        // Count nearby active builders
        int builderCount = 0;
        for (SimNPCComponent builderNpc : SimTale.ACTIVE_NPCS) {
            if (builderNpc.entityRef != null && builderNpc.entityRef.isValid()) {
                RoutineAIComponent ai = store.getComponent(builderNpc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                TransformComponent tComp = store.getComponent(builderNpc.entityRef, TransformComponent.getComponentType());
                if (ai != null && ai.currentTask == RoutineAIComponent.TaskType.BUILDING && tComp != null) {
                    Vector3d pos = tComp.getPosition();
                    double dx = site.anchor.x - pos.x;
                    double dz = site.anchor.z - pos.z;
                    if ((dx*dx + dz*dz) < 16.0 * 16.0) {
                        builderCount++;
                    }
                }
            }
        }

        site.activeBuilders = builderCount;
        int effectiveBuilders = builderCount + site.simulatedBuilders;

        if (effectiveBuilders == 0 && !site.forceBuild) {
            return; // Paused, no builders and not forced
        }

        int ticksPerBlock = 1;
        if (!site.forceBuild) {
            ticksPerBlock = Math.max(2, 40 - (effectiveBuilders * 10)); // Base 40 ticks, -10 per builder, min 2
        }

        site.ticksSinceLastBlock++;
        if (site.ticksSinceLastBlock >= ticksPerBlock) {
            site.ticksSinceLastBlock = 0;

            if (site.currentIndex >= prefab.getBlocks().size()) {
                // Finished construction
                commandBuffer.removeComponent(chunk.getReferenceTo(index), SimTale.CONSTRUCTION_COMPONENT_TYPE);
                SimTale.ACTIVE_SITES.remove(site);
                return;
            }

            // Build multiple blocks per tick if we want to speed it up, or just 1
            int blocksToBuild = site.forceBuild ? (10 * GLOBAL_SPEED) : (GLOBAL_SPEED);
            for (int i = 0; i < blocksToBuild; i++) {
                if (site.currentIndex >= prefab.getBlocks().size()) break;

                PrefabBlock blockInfo = prefab.getBlocks().get(site.currentIndex);
                
                int worldX = site.anchor.x + blockInfo.getX();
                int worldY = site.anchor.y + blockInfo.getY();
                int worldZ = site.anchor.z + blockInfo.getZ();

                String type = blockInfo.getName();
                if (type == null) {
                    type = "Empty"; // Fallback to empty if null
                }

                if (blockInfo.getRotation() != null) {
                    world.setBlock(worldX, worldY, worldZ, type, blockInfo.getRotation());
                } else {
                    world.setBlock(worldX, worldY, worldZ, type);
                }
                site.currentIndex++;
            }
        }
    }
}
