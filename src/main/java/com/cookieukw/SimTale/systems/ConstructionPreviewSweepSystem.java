package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

/**
 * Periodically re-checks every pending construction preview for new obstructions, so a preview
 * placed in a clear spot still updates if something gets built (or removed) inside its footprint
 * afterward — {@link ConstructionHelper#placePreview} itself only ever checks once, at the moment
 * a preview first appears.
 *
 * <p>Modeled on {@code MobsAndMates_BH}'s {@code player-build-preview.js}, whose
 * {@code updatePlayerPreviews} runs on a flat {@code system.runInterval(fn, 20)} sweeping every
 * marker at once. That is the part worth copying: one shared, throttled sweep over every pending
 * site, not a recheck driven by player movement — which is exactly what the earlier
 * live-following design (now removed) got wrong and what made it expensive enough to stall the
 * server on its own.
 *
 * <p>Hooked off the {@code Player} query purely as a "fire at least once a tick" trigger; the
 * interval gate below makes the sweep itself run once globally per {@link #SWEEP_INTERVAL_TICKS},
 * not once per online player.
 */
public class ConstructionPreviewSweepSystem extends EntityTickingSystem<EntityStore> {

    private static final int SWEEP_INTERVAL_TICKS = 40;

    private static long lastSweepTick = -1;

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = WorldUtil.first();
        if (world == null) return;

        long currentTick = world.getTick();
        if (currentTick - lastSweepTick < SWEEP_INTERVAL_TICKS) return;
        lastSweepTick = currentTick;

        for (ConstructionSiteComponent site : ConstructionPreviewManager.allPending()) {
            ConstructionHelper.recheckObstruction(world, site);
        }
    }
}
