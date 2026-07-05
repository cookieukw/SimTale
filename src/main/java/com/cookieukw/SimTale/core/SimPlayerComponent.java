package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * Stores persistent data associated with a player in SimTale (e.g. gender).
 */
public class SimPlayerComponent implements Component<EntityStore> {
    
    public UUID playerUuid;
    public Gender gender;

    public SimPlayerComponent() {
    }

    public SimPlayerComponent(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public SimPlayerComponent(UUID playerUuid, Gender gender) {
        this.playerUuid = playerUuid;
        this.gender = gender;
    }

    @Override
    public SimPlayerComponent clone() {
        return new SimPlayerComponent(this.playerUuid, this.gender);
    }
}
