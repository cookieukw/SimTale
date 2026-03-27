package com.cookieukw.SimTale.core;

import java.util.UUID;

/**
 * Possible statuses for a relationship.
 */
enum RelationshipStatus {
    STRANGER,
    FRIEND,
    BEST_FRIEND,
    CRUSH,
    PARTNER,
    MARRIED
}

/**
 * Tracks the social bond between a SimNPC and a target.
 */
public class Relationship {
    public final UUID target;
    public int friendship = 0; // -100 to 100
    public int romance = 0; // 0 to 100
    public RelationshipStatus status = RelationshipStatus.STRANGER;

    public Relationship(UUID target) {
        this.target = target;
    }

    public void addFriendship(int amount) {
        this.friendship = Math.max(-100, Math.min(100, this.friendship + amount));
        updateStatus();
    }

    public void addRomance(int amount) {
        this.romance = Math.max(0, Math.min(100, this.romance + amount));
        updateStatus();
    }

    private void updateStatus() {
        if (status == RelationshipStatus.MARRIED)
            return;

        if (romance > 70 && friendship > 50) {
            // Potential for marriage but needs manual trigger or more logic
        } else if (romance > 50) {
            status = RelationshipStatus.PARTNER;
        } else if (romance > 20) {
            status = RelationshipStatus.CRUSH;
        } else if (friendship > 80) {
            status = RelationshipStatus.BEST_FRIEND;
        } else if (friendship > 20) {
            status = RelationshipStatus.FRIEND;
        } else {
            status = RelationshipStatus.STRANGER;
        }
    }
}
