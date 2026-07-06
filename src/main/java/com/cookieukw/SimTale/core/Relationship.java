package com.cookieukw.SimTale.core;

import java.util.UUID;



/**
 * Tracks the social bond between a SimNPC and a target.
 */
public class Relationship {
    public UUID target;
    
    // Overall score (-100 to 1000)
    public int affinity = 0; 
    
    // Independent sub-stats
    public int friendship = 0; // -100 to 100
    public int romance = 0; // 0 to 100
    public int trust = 0; // -100 to 100
    
    // Cooldown and memory
    public int interactionsToday = 0;
    public long lastInteractionDayIndex = 0;
    
    public RelationshipStatus status = RelationshipStatus.UNKNOWN;

    public Relationship(UUID target) {
        this.target = target;
        updateStatus();
    }
    
    // For Caskara Jackson serialization
    public Relationship() {
    }

    public void addAffinity(int amount) {
        this.affinity = Math.max(-100, Math.min(1000, this.affinity + amount));
        updateStatus();
    }

    public void addFriendship(int amount) {
        this.friendship = Math.max(-100, Math.min(100, this.friendship + amount));
        updateStatus();
    }

    public void addRomance(int amount) {
        this.romance = Math.max(0, Math.min(100, this.romance + amount));
        updateStatus();
    }

    public void addTrust(int amount) {
        this.trust = Math.max(-100, Math.min(100, this.trust + amount));
        updateStatus();
    }

    private void updateStatus() {
        if (status == RelationshipStatus.MARRIED || status == RelationshipStatus.ENGAGED)
            return;

        // Enemy logic
        if (friendship < -50 || affinity < -50) {
            status = RelationshipStatus.ENEMIES;
            return;
        }
        
        // Romance logic (needs both romance and friendship to be a partner)
        if (romance > 80 && friendship > 60) {
            status = RelationshipStatus.PARTNER;
        } else if (romance > 50 && friendship > 40) {
            status = RelationshipStatus.DATING;
        } else if (romance > 20) {
            status = RelationshipStatus.CRUSH;
        } 
        // Friendship logic
        else if (friendship > 80) {
            status = RelationshipStatus.BEST_FRIEND;
        } else if (friendship > 50) {
            status = RelationshipStatus.GOOD_FRIEND;
        } else if (friendship > 20) {
            status = RelationshipStatus.FRIEND;
        } else if (friendship > 0 || affinity > 10) {
            status = RelationshipStatus.ACQUAINTANCE;
        } else {
            status = RelationshipStatus.STRANGER;
        }
    }
    
    public String getStatusName() {
        return status.name();
    }
}
