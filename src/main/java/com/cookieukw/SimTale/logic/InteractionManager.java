package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import java.util.UUID;

/**
 * Manages social logic and stat changes.
 */
public class InteractionManager {

    public static String performInteraction(SimNPCComponent npc, UUID playerUuid, InteractionType type) {
        Mood mood = npc.getMood();
        int baseChange = 0;
        String response = "";
        MemoryEvent memEvent = MemoryEvent.CHATTED;

        switch (type) {
            case FRIENDLY -> {
                baseChange = 5;
                memEvent = MemoryEvent.CHATTED;
                response = "Você bateu papo com " + npc.name + "!";
            }
            case FUNNY -> {
                memEvent = MemoryEvent.JOKED;
                if (mood == Mood.ANGRY || mood == Mood.SAD) {
                    baseChange = -5;
                    response = npc.name + ": ...isso era pra ser engraçado?";
                } else if (npc.personality.traits.contains(Trait.FUNNY)) {
                    baseChange = 10;
                    response = npc.name + ": HAHAHA! Boa! Você leva jeito pra comédia.";
                } else {
                    baseChange = 5;
                    response = "Você contou uma piada! " + npc.name + " riu bastante.";
                }
            }
            case ROMANTIC -> {
                memEvent = MemoryEvent.FLIRTED;
                int affinity = npc.getRelationship(playerUuid).friendship;
                if (affinity < 20 || mood == Mood.ANGRY) {
                    baseChange = -10;
                    response = npc.name + ": Cara... que? Sai pra lá.";
                } else if (npc.personality.traits.contains(Trait.SHY)) {
                    baseChange = 5;
                    response = npc.name + " cora e desvia o olhar: O-obrigado...";
                } else {
                    baseChange = 10;
                    response = npc.name + ": Heh... continua falando.";
                }
            }
            case MEAN -> {
                memEvent = MemoryEvent.INSULTED;
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    baseChange = -20;
                    response = npc.name + " saca a arma: Você quer resolver isso agora?!";
                } else if (npc.personality.traits.contains(Trait.NEEDY)) {
                    baseChange = -10;
                    response = npc.name + " quase chora: Por que você é tão mau comigo?";
                } else {
                    baseChange = -15;
                    response = "Você insultou o " + npc.name + "!";
                }
            }
            case RANDOM -> {
                baseChange = 1;
                response = "Interação Autônoma";
            }
        }

        // Salvar Memoria Curta e Afinidade
        npc.memory.addMemory(memEvent, playerUuid);
        npc.getRelationship(playerUuid).addFriendship(baseChange);

        // Simulating XP gain
        npc.stats.addXP(Math.abs(baseChange) * 10);

        // Update needs
        npc.needs.social = Math.min(100, npc.needs.social + 10);

        // Caskara real-time saving
        SimNPCPersistence.saveNPC(npc);
        
        return response;
    }
}
