package com.cookieukw.SimTale.db;

/** Plain Caskara DTO for a player's piggy bank savings, measured in copper units. */
public final class PiggyBankData {
    public String playerId;
    public long copper;

    public PiggyBankData() {
    }

    public PiggyBankData(String playerId, long copper) {
        this.playerId = playerId;
        this.copper = copper;
    }
}
