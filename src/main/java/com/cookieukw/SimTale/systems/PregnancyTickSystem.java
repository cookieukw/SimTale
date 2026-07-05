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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Sistema de tick que monitora NPCs grávidas e dispara o nascimento
 * quando a duração da gravidez expira.
 *
 * Roda a cada tick para cada NPC SimTale que tenha PregnancyComponent ativo.
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

        // Apenas NPCs femininas podem estar grávidas
        if (npc.gender != Gender.FEMALE) return;

        PregnancyComponent pregnancy = npc.pregnancy;
        if (pregnancy == null || !pregnancy.pregnant) return;

        // Obter tick atual do mundo
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

        // Atualizar trimestre
        pregnancy.updateTrimester(worldTick);

        // Aplicar comportamento de grávida (stub por enquanto)
        LifecycleManager.applyPregnancyBehavior(npc);

        // Verificar se é hora de nascer
        if (pregnancy.isReadyToBirth(worldTick)) {
            LOGGER.atInfo().log("SimTale: " + npc.name + " está dando à luz!");
            LifecycleManager.birthBaby(npc, store, world, worldTick);
        }
    }
}
