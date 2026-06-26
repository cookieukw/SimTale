package com.cookieukw.SimTale.core;

import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mock architecture for Marriage, Pregnancy, and shared housing.
 */
public class FamilySystem {
    public boolean isMarried = false;
    public UUID spouseId = null;
    
    public boolean hasSharedHome = false;
    public double homeX, homeY, homeZ;
    
    public List<Child> children = new ArrayList<>();
    
    public boolean isPregnant = false;
    public int pregnancyDays = 0;
    
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
    
    public void attemptPregnancy() {
        if (isMarried && !isPregnant) {
            // Chance calculation here based on mood/relationship
            this.isPregnant = true;
            this.pregnancyDays = 0;
        }
    }
    
    public void tickPregnancy() {
        if (isPregnant) {
            pregnancyDays++;
            if (pregnancyDays >= 5) { // 5 in-game days
                isPregnant = false;
                pregnancyDays = 0;
                onChildBirth();
            }
        }
    }
    
    private void onChildBirth() {
        // Gera um nome pro filho
        children.add(new Child("Bebê"));
    }
    
    public Vector3d getSharedHomeLocation() {
        if (!hasSharedHome) return null;
        return new Vector3d(homeX, homeY, homeZ);
    }
}
