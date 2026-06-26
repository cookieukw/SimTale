package com.cookieukw.SimTale.core;

import java.util.UUID;

/**
 * Mock architecture for NPC children, growing up, and life stages.
 */
public class Child {
    public UUID id = UUID.randomUUID();
    public String name;
    
    // 0 = Baby, 1 = Toddler, 2 = Child, 3 = Teenager
    public int ageStage = 0; 
    public long daysInCurrentStage = 0;

    public Child() {
    }

    public Child(String name) {
        this.name = name;
    }

    public void growUp(int currentDayIndex) {
        daysInCurrentStage++;
        if (daysInCurrentStage >= 10 && ageStage < 3) {
            ageStage++;
            daysInCurrentStage = 0;
        }
    }
}
