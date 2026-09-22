package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.UUID;

/**
 * Handles persistence for SimPlayerComponent data using the Caskara database.
 */
public class SimPlayerPersistence {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void savePlayer(SimPlayerComponent component) {
        if (component == null || component.playerUuid == null) return;
        try {
            SimNPCPersistence.DB_SHELL.core(SimPlayerComponent.class).preserve("player_" + component.playerUuid.toString(), component);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Failed to save player data: " + e.getMessage());
        }
    }

    public static SimPlayerComponent loadPlayer(UUID playerUuid) {
        if (playerUuid == null) return null;
        try {
            SimPlayerComponent comp = SimNPCPersistence.DB_SHELL.core(SimPlayerComponent.class).extract("player_" + playerUuid.toString()).sync().orElse(null);
            if (comp != null) {
                comp.playerUuid = playerUuid;
                return comp;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Failed to load player data: " + e.getMessage());
        }
        return null;
    }
}
