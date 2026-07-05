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
import java.util.Objects;

/**
 * Sistema de tick que atualiza o crescimento de filhos ativos no mundo.
 *
 * Ao invés de iterar por entidades, itera pela lista global de filhos
 * ativos em {@link LifecycleManager#ACTIVE_CHILDREN}.
 *
 * Este tick roda a cada 100 ticks (~5 segundos) para performance.
 */
public class GrowthTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int TICK_INTERVAL = 100; // A cada ~5 segundos
    private long lastTick = 0;

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        // Precisamos de um query válido para ser registrado.
        // Usamos NPCEntity como base — na prática iteramos a lista global.
        return NPCEntity.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Executar apenas uma vez por intervalo, no primeiro index
        if (index != 0) return;

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

        // Throttle: só roda a cada TICK_INTERVAL ticks
        if (worldTick - lastTick < TICK_INTERVAL) return;
        lastTick = worldTick;

        // Iterar todos os filhos ativos
        for (int i = LifecycleManager.ACTIVE_CHILDREN.size() - 1; i >= 0; i--) {
            GrowthComponent child = LifecycleManager.ACTIVE_CHILDREN.get(i);
            LifecycleManager.tickGrowth(child, worldTick);

            // Se ficou adulto, remover da lista (o onBecameAdult já foi chamado)
            if (child.isAdult()) {
                LifecycleManager.ACTIVE_CHILDREN.remove(i);
            }
        }

        // Tick da IA materna para cada mãe com filhos que precisam de cuidado
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
