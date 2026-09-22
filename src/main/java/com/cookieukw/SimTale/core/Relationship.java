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

    /**
     * Scoldings received from this person today. Reset by the same daily rollover as
     * {@link #interactionsToday}.
     * <p>
     * Counted separately because it drives an escalation the generic counter cannot express: one
     * telling-off is a bad moment, the fourth in the same day is a pattern, and only the second
     * kind should be eating away at trust and affinity.
     */
    public int scoldingsToday = 0;
    
    public RelationshipStatus status = RelationshipStatus.UNKNOWN;

    public Relationship(UUID target) {
        this.target = target;
        updateStatus();
    }
    
    // For Caskara Jackson serialization
    public Relationship() {
    }

    public void addAffinity(int amount) {
        this.affinity = Math.clamp(this.affinity + amount, -100, 1000);
        updateStatus();
    }

    public void addFriendship(int amount) {
        this.friendship = Math.clamp(this.friendship + amount, -100, 100);
        updateStatus();
    }

    public void addRomance(int amount) {
        this.romance = Math.clamp(this.romance + amount, 0, 100);
        updateStatus();
    }

    public void addTrust(int amount) {
        this.trust = Math.clamp(this.trust + amount, -100, 100);
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
        }
        /* A crush only outranks friendship up to BEST_FRIEND. Previously `romance > 20` was
        checked first, so a BEST_FRIEND who happened to have a bit of romance was demoted
        to CRUSH — a downgrade the player saw as the relationship going backwards.
        */
        else if (romance > 20 && friendship <= 80) {
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
