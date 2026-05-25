package com.cookieukw.SimTale.logic;

import java.util.UUID;

import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;

/**
 * Manages social logic and stat changes.
 */
public class InteractionManager {

    public static void performInteraction(SimNPCComponent npc, UUID playerUuid, InteractionType type) {
        // Logic for relationship and stat changes
        int baseChange = switch (type) {
            case FRIENDLY -> 5;
            case FUNNY -> 3;
            case ROMANTIC -> 7;
            case MEAN -> -10;
            case RANDOM -> 1;
            default -> 0;
        };

       MemoryEvent memEvent = switch (type) {
            case FRIENDLY -> MemoryEvent.CHATTED;
            case FUNNY -> MemoryEvent.JOKED;
            case ROMANTIC -> MemoryEvent.FLIRTED;
            case MEAN -> MemoryEvent.INSULTED;
            default -> MemoryEvent.CHATTED;
        };

        // Salvar Memoria Curta e Afinidade
        npc.memory.addMemory(memEvent, playerUuid);
        npc.getRelationship(playerUuid).addFriendship(baseChange);

        // Simulating XP gain
        npc.stats.addXP(Math.abs(baseChange) * 10);

        // Update needs
        npc.needs.social = Math.min(100, npc.needs.social + 10);

        // Caskara real-time saving
        SimNPCPersistence.saveNPC(npc);
    }
}
