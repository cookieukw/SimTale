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
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseButtonState;
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

@SuppressWarnings("null")
public class SimTaleEventHandler implements Consumer<PlayerMouseButtonEvent> {

    @Override
    public void accept(PlayerMouseButtonEvent event) {
        if (event.getMouseButton() == null ||
            event.getMouseButton().mouseButtonType != MouseButtonType.Right ||
            event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }

        Ref<EntityStore> targetRef = event.getTargetEntityRef();
        
        HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG]: PlayerMouseButtonEvent (Right Click) DISPARADO. Alvo Ref: " + (targetRef != null ? targetRef.toString() : "null"));
        
        if (targetRef == null)
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
        SimNPCComponent npc = (SimNPCComponent) accessor.getComponent(targetRef,
                SimTale.SIM_NPC_COMPONENT_TYPE);

        if (npc == null) {
            UUIDComponent uuidComp = accessor.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                SimNPCData data = Caskara.load(uuidComp.getUuid().toString(), SimNPCData.class);
                if (data != null) {
                    HytaleLogger.forEnclosingClass().atInfo().log("SimTale: NPC " + data.name + " remontado apos carregamento do mundo!");
                    npc = new SimNPCComponent(uuidComp.getUuid(), data.name);
                    npc.entityRef = targetRef;
                    SimNPCPersistence.loadNPC(npc);
                    accessor.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    
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
