package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.cookieukw.SimTale.logic.PlayerGenderPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles when a player is ready in the world.
 * Loads their SimTale data (gender) and opens the selection screen if they haven't selected one.
 */
public class PlayerJoinHandler implements Consumer<PlayerReadyEvent> {

    @Override
    public void accept(PlayerReadyEvent event) {
        Player player = event.getPlayer();

        Ref<EntityStore> playerRef = event.getPlayerRef();

        PlayerRef playerRefComponent = playerRef.getStore().getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComponent == null) return;

        UUID playerUuid = playerRefComponent.getUuid();

        // Load or create player persistent data
        SimPlayerComponent simPlayer = SimPlayerPersistence.loadPlayer(playerUuid);

        if (simPlayer == null || simPlayer.gender == null) {
            if (simPlayer == null) {
                simPlayer = new SimPlayerComponent(playerUuid);
            }
            // Ensure the component is registered on the entity
            SimPlayerComponent existing = playerRef.getStore().getComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE);
            if (existing == null) {
                playerRef.getStore().addComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            } else {
                playerRef.getStore().putComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            }

            // Open permanent gender selection page
            player.getPageManager().openCustomPage(playerRef, playerRef.getStore(), new PlayerGenderPage(playerRefComponent, player, simPlayer));
        } else {
            // Player already has a gender set, just load and attach component
            SimPlayerComponent existing = playerRef.getStore().getComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE);
            if (existing == null) {
                playerRef.getStore().addComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            } else {
                playerRef.getStore().putComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            }
        }
    }
}
