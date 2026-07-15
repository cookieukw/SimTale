package com.cookieukw.SimTale.core.lifecycle;

public class BabyCareData {
    public String childId;
    public String motherId;
    public String fatherId;
    public String currentHolderId;      // Player UUID or NPC UUID, or "none" if on ground
    public String currentTurnOwnerId;   // Whose turn it is (motherId or fatherId)
    public long turnStartTime;          // Epoch millis when current turn started
    public long nextSwapAllowedTime;    // Epoch millis when next swap is allowed (cooldown/min time)
    public long lastInteractionTime;    // Epoch millis of last time calculations were applied

    public BabyCareData() {
    }

    public BabyCareData(String childId, String motherId, String fatherId) {
        this.childId = childId;
        this.motherId = motherId;
        this.fatherId = fatherId;
        this.currentHolderId = motherId; // Default mother starts holding
        this.currentTurnOwnerId = motherId;
        long now = System.currentTimeMillis();
        this.turnStartTime = now;
        // Default swap cooldown: 4 hours for natural gameplay pacing
        this.nextSwapAllowedTime = now + BabyCareManager.TURN_DURATION;
        this.lastInteractionTime = now;
    }
}
