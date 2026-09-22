package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.StartingTroop;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.cookieukw.SimTale.logic.PlayerGenderPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.function.Consumer;

import org.joml.Vector3d;
import com.cookieukw.SimTale.core.SimLog;

import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
import com.cookieukw.SimTale.db.SimNPCPersistence;
/**
 * Handles when a player is ready in the world.
 * Loads their SimTale data (gender) and opens the selection screen if they haven't selected one.
 */
public class PlayerJoinHandler implements Consumer<PlayerReadyEvent> {
    private static final SimLog LOGGER = SimLog.forClass(PlayerJoinHandler.class);

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

        // Trigger offline baby care simulation
        try {
            BabyCareManager.simulateOfflineTime(playerRef, simPlayer);
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error simulating offline baby care: " + e.getMessage());
        }

        // Load all houses from database
        try {
            HouseManager.loadAllHouses();
        } catch (Exception e) {
            LOGGER.debug("[SimTale] Error loading houses: " + e.getMessage());
        }

        /* Same gap the houses had, on the growth records: they were written to disk but nothing
        ever read them back into the in-memory list every age check consults. A restart left it
        empty, which froze every child's growth and hid the parent-only interactions.
        */
        try {
            BabyCareManager.loadActiveChildren();
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error loading growing children: " + e.getMessage());
        }

        /* And the same gap again on the chest registry, which was memory only.

        Beds never showed it because every NPC record carries its own bed and re-registers it on
        load; a chest has no owner to bring it back. That left the place event and the boot sweep
        as the only sources, and the sweep only sees chunks that happen to be loaded — so chests
        away from spawn were simply invisible to the mod until placed again.
        */
        try {
            ChestRegistry.loadAll(player.getWorld());
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error loading chest registry: " + e.getMessage());
        }

        // Reassemble active NPCs from persistence so ACTIVE_NPCS is populated on join
        try {
            SimNPCPersistence.reassembleActiveNPCs(player.getWorld());
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error reassembling active NPCs: " + e.getMessage());
        }

        /* Map markers attach here rather than at plugin startup, where the world does not exist
        yet. Idempotent per world, so every join is safe.
        */
        try {
            SimTaleMarkerProvider.ensureRegistered(player.getWorld());
            SimTaleMarkerProvider.captureSnapshot(player.getWorld(), playerRef.getStore());
            SimNpcPlayerListHelper.sendAllToPlayer(playerRefComponent, player.getWorld());
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error registering map marker provider: " + e.getMessage());
        }

        /* Populate the furniture registries for the area the player just loaded into.

        Nothing else does this on join: the place events only cover furniture put down while the
        server is up, so beds that were already in the world stayed invisible to the NPCs until
        someone happened to run /simtale housecheck, which scans as a side effect. Delayed like
        the starting troop so the surrounding chunks have real blocks to read.
        */
        try {
            TransformComponent joinTc =
                playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
            if (joinTc != null) {
                final Vector3d scanCenter = new Vector3d(joinTc.getPosition());
                final World scanWorld = player.getWorld();
                WorldUtil.executeLater(
                    () -> BedWorldBootstrap.bootstrapLoadedRadius(scanWorld, scanCenter, 32),
                    2000L);
            }
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error scheduling furniture scan: " + e.getMessage());
        }

        /* Founding group, only in a world that has never had NPCs.

        Deferred to the world thread and given a moment first: spawning entities is a
        structural store write, and PlayerReadyEvent can fire while the store is still
        processing. The delay also lets the chunks around the spawn point finish loading, so
        the ground search has real blocks to test instead of empty air.
        */
        try {
           TransformComponent tc =
                playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
            if (tc != null) {
                final Vector3d spawnPos = new Vector3d(tc.getPosition());
                final World world = player.getWorld();
                WorldUtil.executeLater(
                    () -> {
                        assert world != null;
                        StartingTroop.spawnIfFreshWorld(world.getEntityStore().getStore(), world, spawnPos);
                    },
                    3000L);
            }
        } catch (Exception e) {
            LOGGER.warn("[SimTale] Error scheduling starting troop: " + e.getMessage());
        }
    }
}
