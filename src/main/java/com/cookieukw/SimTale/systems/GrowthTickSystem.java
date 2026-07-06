package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

/**
 * Tick system for child growth.
 */
public class GrowthTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int TICK_INTERVAL = 100; 
    private long lastTick = 0;

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        // Needs a valid query to be registered.
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Run only once per interval, at the first index
        if (index != 0) return;

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

        // Throttle: Only runs every TICK_INTERVAL ticks
        if (worldTick - lastTick < TICK_INTERVAL) return;
        lastTick = worldTick;

        // Iterate all active children
        for (int i = LifecycleManager.ACTIVE_CHILDREN.size() - 1; i >= 0; i--) {
            GrowthComponent child = LifecycleManager.ACTIVE_CHILDREN.get(i);
            LifecycleManager.tickGrowth(child, worldTick);

            // If became adult, remove from list (onBecameAdult has already been called)
            if (child.isAdult()) {
                LifecycleManager.ACTIVE_CHILDREN.remove(i);
            }
        }

        // Mother AI tick for each mother with children needing care
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId == null) continue;
            java.util.List<GrowthComponent> needingCare =
                LifecycleManager.findChildrenNeedingCare(npc.entityId);
            for (GrowthComponent baby : needingCare) {
                LifecycleManager.tickMotherAI(npc, baby, worldTick);
            }
        }
    }
}
