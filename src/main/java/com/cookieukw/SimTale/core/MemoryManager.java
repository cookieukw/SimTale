package com.cookieukw.SimTale.core;

import java.util.LinkedList;
import java.util.UUID;

public class MemoryManager {
    public LinkedList<Memory> recentMemories = new LinkedList<>();

    public void addMemory(MemoryEvent event, UUID player) {
        // Limita a memória aos últimos 10 acontecimentos (Short-term memory)
        if (recentMemories.size() >= 10) {
            recentMemories.removeFirst();
        }
        recentMemories.add(new Memory(event, System.currentTimeMillis(), player));
    }

    public boolean remembers(MemoryEvent event, UUID player, long maxAgeMillis) {
        String pId = player != null ? player.toString() : null;
        for (Memory m : recentMemories) {
            if (m.event == event && (pId == null || pId.equals(m.playerSource))) {
                if (System.currentTimeMillis() - m.timestamp < maxAgeMillis) {
                    return true;
                }
            }
        }
        return false;
    }
}
