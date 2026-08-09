package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

/**
 * Tick system that monitors pregnant Players and triggers birth when the pregnancy duration
 * expires.
 * <p>
 * Used to also drive the construction-preview hologram's real-time follow (every tick, no
 * throttle, no ground-height scan) — that logic briefly lived in its own class,
 * {@code ConstructionPreviewTracker}, which ran concurrently against the same
 * {@code ConstructionPreviewManager} session as this class for a while — each recomputing a
 * different anchor from a different formula and stomping the other's write — which is almost
 * certainly what an unbounded hologram despawn/respawn loop from this class crashed the server
 * over. Both the tracker and this class's copy of the same logic are gone now: the whole
 * live-follow design was scrapped in favor of a placeable marker block
 * (see {@link BedPlaceBlockEventSystem}) with a one-shot obstruction check at place time.
 */
public class PlayerPregnancyTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null) return;

        // Get current world
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

        // --- Pregnancy logic ---
        SimPlayerComponent playerComp = chunk.getComponent(index, SimTale.SIM_PLAYER_COMPONENT_TYPE);
        if (playerComp == null) return;

        PregnancyComponent pregnancy = playerComp.pregnancy;
        if (pregnancy == null || !pregnancy.pregnant) return;

        // Update trimester
        boolean trimesterChanged = pregnancy.updateTrimester(worldTick);
        if (trimesterChanged || worldTick % 100 == 0) {
            LifecycleManager.applyPregnancySpeedDebuff(playerRef, pregnancy);
        }

        // Apply pregnancy behavior
        LifecycleManager.applyPlayerPregnancyBehavior(playerRef, playerComp);

        // Check if it's time to give birth
        if (pregnancy.isReadyToBirth(worldTick)) {
            LOGGER.atInfo().log("SimTale: Player " + playerComp.playerUuid + " is giving birth!");
            LifecycleManager.birthPlayerBaby(playerRef, playerComp, store, worldTick);
        }
    }
}
