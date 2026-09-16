package com.cookieukw.SimTale.logic;

/**
 * Tracks social level and experience points.
 */
public class SocialStats {
    public int level = 1;
    public int xp = 0;

    public void addXP(int amount) {
        if (amount <= 0) return;
        this.xp += amount;
        /* `while`, not `if`: a single large gain used to grant only one level and leave the
        NPC parked above the threshold until the next interaction.
        */
        while (this.xp >= level * 100) {
            this.xp -= level * 100;
            this.level++;
        }
    }

    public int getNextLevelXP() {
        return level * 100;
    }
}
