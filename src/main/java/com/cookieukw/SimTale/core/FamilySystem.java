package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    /**
     * How many children this specific NPC personally wants, total, ever — not a couple-level
     * fact, so two spouses can (and do) disagree. -1 means "not rolled yet"; rolled lazily on
     * first read and then persisted, so it never changes underneath an NPC once decided, and
     * NPCs saved before this field existed just roll theirs the first time it matters.
     * <p>
     * Uniform 0-4: about a fifth of NPCs turn out child-free, the rest want anywhere from one to
     * a full house (4 is also {@link #canHaveMoreChildren}'s own ceiling, so "wants more than
     * that" was never a real option to begin with).
     */
    public int desiredChildren = -1;

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

    /**
     * Whether this NPC, personally, still wants a(nother) child right now.
     * <p>
     * Deliberately per-NPC rather than per-couple: a marriage where one spouse wants more and the
     * other does not is a real relationship to have, not a bug, and this is what lets a periodic
     * pregnancy check require both sides to agree instead of just checking the couple's combined
     * child count.
     */
    public boolean wantsAnotherChild() {
        if (desiredChildren < 0) {
            desiredChildren = ThreadLocalRandom.current().nextInt(0, 5);
        }
        return children.size() < desiredChildren;
    }
}
