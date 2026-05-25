package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;

/**
 * Manages social logic and stat changes.
 */
public class InteractionManager {

    public static void performInteraction(SimNPCComponent npc, java.util.UUID playerUuid, InteractionType type) {
        // Logic for relationship and stat changes
        int baseChange = switch (type) {
            case FRIENDLY -> 5;
            case FUNNY -> 3;
            case ROMANTIC -> 7;
            case MEAN -> -10;
            case RANDOM -> 1;
            default -> 0;
        };

        com.cookieukw.SimTale.core.MemoryEvent memEvent = switch (type) {
            case FRIENDLY -> com.cookieukw.SimTale.core.MemoryEvent.CHATTED;
            case FUNNY -> com.cookieukw.SimTale.core.MemoryEvent.JOKED;
            case ROMANTIC -> com.cookieukw.SimTale.core.MemoryEvent.FLIRTED;
            case MEAN -> com.cookieukw.SimTale.core.MemoryEvent.INSULTED;
            default -> com.cookieukw.SimTale.core.MemoryEvent.CHATTED;
        };

        // Salvar Memoria Curta e Afinidade
        npc.memory.addMemory(memEvent, playerUuid);
        npc.getRelationship(playerUuid).addFriendship(baseChange);

        // Simulating XP gain
        npc.stats.addXP(Math.abs(baseChange) * 10);

        // Update needs
        npc.needs.social = Math.min(100, npc.needs.social + 10);

        // Caskara real-time saving
        com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
    }
}
