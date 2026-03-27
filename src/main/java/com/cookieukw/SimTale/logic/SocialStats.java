package com.cookieukw.SimTale.logic;

/**
 * Tracks social level and experience points.
 */
public class SocialStats {
    public int level = 1;
    public int xp = 0;

    public void addXP(int amount) {
        this.xp += amount;
        if (this.xp >= level * 100) {
            this.xp -= level * 100;
            this.level++;
        }
    }

    public int getNextLevelXP() {
        return level * 100;
    }
}
