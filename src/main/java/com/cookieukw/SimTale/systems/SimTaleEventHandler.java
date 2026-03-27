package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookieukw.SimTale.logic.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import java.util.function.Consumer;

/**
 * Handles interactions between players and NPCs.
 */
public class SimTaleEventHandler implements Consumer<PlayerInteractEvent> {

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void accept(PlayerInteractEvent event) {
        Entity target = event.getTargetEntity();
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

        if (npc == null)
            return;

        // Trigger a friendly interaction when player clicks NPC
        InteractionManager.performInteraction(npc, npc, InteractionType.FRIENDLY);

        // Get PlayerRef component to send message
        Player player = event.getPlayer();
        if (player != null) {
            Ref<EntityStore> playerRef = player.getReference();
            ComponentAccessor<EntityStore> playerAccessor = (ComponentAccessor<EntityStore>) playerRef.getStore();
            PlayerRef playerRefComp = (PlayerRef) playerAccessor.getComponent(playerRef,
                    Universe.get().getPlayerRefComponentType());

            if (playerRefComp != null) {
                playerRefComp.sendMessage(Message.raw("Você interagiu com " + npc.name + "!"));
            }
        }
    }
}
