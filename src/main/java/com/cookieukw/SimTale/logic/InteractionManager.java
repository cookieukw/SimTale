package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;

/**
 * Manages social logic and stat changes.
 */
public class InteractionManager {

    public static void performInteraction(SimNPCComponent actor, SimNPCComponent target, InteractionType type) {
        // Logic for relationship and stat changes
        int baseChange = switch (type) {
            case FRIENDLY -> 5;
            case FUNNY -> 3;
            case ROMANTIC -> 7;
            case MEAN -> -10;
            case RANDOM -> 1;
            default -> 0;
        };

        // Apply changes to target's relationship with actor
        // (In a full implementation, we'd find the specific relationship object)

        // Simulating XP gain
        actor.stats.addXP(Math.abs(baseChange) * 10);

        // Update needs
        actor.needs.social = Math.min(100, actor.needs.social + 10);
    }
}
