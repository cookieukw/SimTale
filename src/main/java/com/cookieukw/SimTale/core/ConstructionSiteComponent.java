package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

public class ConstructionSiteComponent implements Component<EntityStore> {
    public String prefabName;
    public Vector3i anchor;
    public int currentIndex;
    public int ticksSinceLastBlock;
    
    public boolean previewSpawned;
    
    public ConstructionSiteComponent() {
    }

    public ConstructionSiteComponent(String prefabName, Vector3i anchor) {
        this.prefabName = prefabName;
        this.anchor = anchor;
        this.currentIndex = 0;
        this.ticksSinceLastBlock = 0;
        this.previewSpawned = false;
    }

    @Override
    public ConstructionSiteComponent clone() {
        ConstructionSiteComponent clone = new ConstructionSiteComponent();
        clone.prefabName = this.prefabName;
        if (this.anchor != null) {
            clone.anchor = new Vector3i(this.anchor);
        }
        clone.currentIndex = this.currentIndex;
        clone.ticksSinceLastBlock = this.ticksSinceLastBlock;
        clone.previewSpawned = this.previewSpawned;
        return clone;
    }
}
