package com.cookieukw.SimTale.core;

import java.util.UUID;

public class Memory {
    public MemoryEvent event;
    public long timestamp;
    public String playerSource;
    public boolean isGossip;
    public String gossipTargetName;

    public Memory() {}

    public Memory(MemoryEvent event, long timestamp, UUID playerSource) {
        this(event, timestamp, playerSource, false, null);
    }

    public Memory(MemoryEvent event, long timestamp, UUID playerSource, boolean isGossip, String gossipTargetName) {
        this.event = event;
        this.timestamp = timestamp;
        this.playerSource = playerSource != null ? playerSource.toString() : null;
        this.isGossip = isGossip;
        this.gossipTargetName = gossipTargetName;
    }
}

