package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

/**
 * Tick system that monitors pregnant NPCs and triggers birth
 * when the pregnancy duration expires.
 * Runs every tick for each SimTale NPC with an active PregnancyComponent.
 */
public class PregnancyTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) return;

        // Only females can be pregnant
        if (npc.gender != Gender.FEMALE) return;

        PregnancyComponent pregnancy = npc.pregnancy;
        if (pregnancy == null || !pregnancy.pregnant) return;

        World world = WorldUtil.first();
        if (world == null) return;
        long worldTick = world.getTick();

        // Update trimester
        boolean trimesterChanged = pregnancy.updateTrimester(worldTick);
        if (trimesterChanged || worldTick % 100 == 0) {
            LifecycleManager.applyPregnancySpeedDebuff(npc.entityRef, pregnancy);
        }

        // Apply pregnancy behavior
        LifecycleManager.applyPregnancyBehavior(npc);

        // Check if it's time to give birth
        if (pregnancy.isReadyToBirth(worldTick)) {
            LOGGER.atInfo().log("SimTale: " + npc.name + " is giving birth!");
            LifecycleManager.birthBaby(npc, store, worldTick);
        }
    }
}
