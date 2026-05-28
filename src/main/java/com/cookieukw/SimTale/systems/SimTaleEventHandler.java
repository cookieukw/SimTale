package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.function.Consumer;

/**
 * Handles interactions between players and NPCs.
 */

public class SimTaleEventHandler implements Consumer<PlayerInteractEvent> {

    @Override
    public void accept(PlayerInteractEvent event) {
        Entity target = event.getTargetEntity();
        
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG]: PlayerInteractEvent DISPARADO. Alvo: " + (target != null ? target.getClass().getSimpleName() : "null"));
        
        if (target == null)
            return;

        // getWorlds() returns a Map<String, World>
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }

        if (world == null)
            return;

        // world.getEntityStore() returns EntityStore, which has getStore() ->
        // Store<EntityStore>
        Store<EntityStore> store = world.getEntityStore().getStore();
        ComponentAccessor<EntityStore> accessor = (ComponentAccessor<EntityStore>) store;
        SimNPCComponent npc = (SimNPCComponent) accessor.getComponent(target.getReference(),
                SimTale.SIM_NPC_COMPONENT_TYPE);

        if (npc == null) {
            UUIDComponent uuidComp = accessor.getComponent(target.getReference(), UUIDComponent.getComponentType());
            if (uuidComp != null) {
                SimNPCData data = Caskara.load(uuidComp.getUuid().toString(), SimNPCData.class);
                if (data != null) {
                    HytaleLogger.forEnclosingClass().atInfo().log("SimTale: NPC " + data.name + " remontado apos carregamento do mundo!");
                    npc = new SimNPCComponent(uuidComp.getUuid(), data.name);
                    npc.entityRef = target.getReference();
                    SimNPCPersistence.loadNPC(npc);
                    accessor.addComponent(target.getReference(), SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    
                    final java.util.UUID targetId = uuidComp.getUuid();
                    SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId));
                    SimTale.ACTIVE_NPCS.add(npc);
                }
            }
        }

        if (npc == null)
            return;

        HytaleLogger.forEnclosingClass().atInfo().log("SimTale: Interacao com NPC detectada: " + npc.name);

        // Get PlayerRef component to open UI
        Player player = event.getPlayer();
        if (player != null) {
            Ref<EntityStore> playerRef = player.getReference();
            ComponentAccessor<EntityStore> playerAccessor = (ComponentAccessor<EntityStore>) playerRef.getStore();
            PlayerRef playerRefComp = (PlayerRef) playerAccessor.getComponent(playerRef,
                    Universe.get().getPlayerRefComponentType());

            if (playerRefComp != null) {
                // Open the NPC interaction page
                player.getPageManager().openCustomPage(playerRef, playerRef.getStore(), new NPCInteractionPage(playerRefComp, player, npc));
            }
        }
    }
}
