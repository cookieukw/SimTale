package com.cookieukw.SimTale.core;

import java.util.UUID;

public class Memory {
    public MemoryEvent event;
    public long timestamp;
    public String playerSource;

    public Memory() {}

    public Memory(MemoryEvent event, long timestamp, UUID playerSource) {
        this.event = event;
        this.timestamp = timestamp;
        this.playerSource = playerSource != null ? playerSource.toString() : null;
    }
}
