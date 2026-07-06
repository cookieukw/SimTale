package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;

/**
 * Tick system that monitors pregnant Players and triggers birth
 * when the pregnancy duration expires.
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

        SimPlayerComponent playerComp = chunk.getComponent(index, SimTale.SIM_PLAYER_COMPONENT_TYPE);
        if (playerComp == null) return;

        PregnancyComponent pregnancy = playerComp.pregnancy;
        if (pregnancy == null || !pregnancy.pregnant) return;

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null) return;

        // Get current world tick
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

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
