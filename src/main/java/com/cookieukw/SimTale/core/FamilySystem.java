package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the marriage, shared home, and child list of an NPC.
 * Pregnancy is now managed by the {@link PregnancyComponent} in SimNPCComponent,
 * and growth by the {@link GrowthComponent} in LifecycleManager.
 */
public class FamilySystem {
    public boolean isMarried = false;
    public UUID spouseId = null;
    
    public boolean hasSharedHome = false;
    public double homeX, homeY, homeZ;
    
    public List<Child> children = new ArrayList<>();
    
    public FamilySystem() {}
    
  
    public void marry(UUID newSpouseId, Vector3d sharedHome) {
        this.isMarried = true;
        this.spouseId = newSpouseId;
        if (sharedHome != null) {
            this.hasSharedHome = true;
            this.homeX = sharedHome.x;
            this.homeY = sharedHome.y;
            this.homeZ = sharedHome.z;
        }
    }

  
    public void divorce() {
        this.isMarried = false;
        this.spouseId = null;
    }

    public Vector3d getSharedHomeLocation() {
        if (!hasSharedHome) return null;
        return new Vector3d(homeX, homeY, homeZ);
    }

   
    public void setSharedHome(Vector3d pos) {
        if (pos != null) {
            this.hasSharedHome = true;
            this.homeX = pos.x;
            this.homeY = pos.y;
            this.homeZ = pos.z;
        }
    }

    
    public int getChildCount() {
        return children.size();
    }

    
    public boolean canHaveMoreChildren() {
        return children.size() < 4;
    }
}
