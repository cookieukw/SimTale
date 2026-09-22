package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

/**
 * Stores persistent data associated with a player in SimTale (e.g. gender).
 */
public class SimPlayerComponent implements Component<EntityStore> {
    
    public UUID playerUuid;
    public Gender gender;
    public PregnancyComponent pregnancy;

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
        SimPlayerComponent clone = new SimPlayerComponent(this.playerUuid, this.gender);
        if (this.pregnancy != null) {
            clone.pregnancy = new PregnancyComponent();
            clone.pregnancy.pregnant = this.pregnancy.pregnant;
            clone.pregnancy.fatherId = this.pregnancy.fatherId;
            clone.pregnancy.startTick = this.pregnancy.startTick;
            clone.pregnancy.durationTicks = this.pregnancy.durationTicks;
            clone.pregnancy.trimester = this.pregnancy.trimester;
        }
        return clone;
    }
}
