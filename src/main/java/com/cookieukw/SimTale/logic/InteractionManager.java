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
        int trustChange = 0;
        String response = "";
        MemoryEvent memEvent = MemoryEvent.CHATTED;

        switch (type) {
            case FRIENDLY -> {
                baseChange = 5;
                trustChange = 1;
                memEvent = MemoryEvent.CHATTED;
                response = getContextualGreeting(npc, playerUuid);
            }
            case FUNNY -> {
                memEvent = MemoryEvent.JOKED;
                if (mood == Mood.ANGRY || mood == Mood.SAD) {
                    baseChange = -5;
                    response = npc.name + ": ...isso era pra ser engraçado?";
                } else if (npc.personality.traits.contains(Trait.FUNNY)) {
                    baseChange = 10;
                    trustChange = 2;
                    response = npc.name + ": HAHAHA! Boa! Você leva jeito pra comédia.";
                } else {
                    baseChange = 5;
                    trustChange = 1;
                    response = "Você contou uma piada! " + npc.name + " riu bastante.";
                }
            }
            case ROMANTIC -> {
                memEvent = MemoryEvent.FLIRTED;
                int affinity = npc.getRelationship(playerUuid).friendship;
                if (affinity < 20 || mood == Mood.ANGRY) {
                    baseChange = -10;
                    trustChange = -2;
                    response = npc.name + ": Cara... que? Sai pra lá.";
                } else if (npc.personality.traits.contains(Trait.SHY)) {
                    baseChange = 5;
                    trustChange = 2;
                    response = npc.name + " cora e desvia o olhar: O-obrigado...";
                } else {
                    baseChange = 10;
                    trustChange = 1;
                    response = npc.name + ": Heh... continua falando.";
                }
            }
            case MEAN -> {
                memEvent = MemoryEvent.INSULTED;
                trustChange = -15;
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
            case GIFT -> {
                memEvent = MemoryEvent.GIFTED;
                if (npc.personality.traits.contains(Trait.GREEDY)) {
                    baseChange = 15;
                    trustChange = 5;
                    response = npc.name + " arregala os olhos: Pra mim?! Haha, FINALMENTE alguém que me valoriza!";
                } else if (npc.personality.traits.contains(Trait.PARANOID)) {
                    baseChange = -5;
                    trustChange = -10;
                    response = npc.name + " olha o presente com suspeita: Isso não tem veneno, tem?";
                } else {
                    baseChange = 10;
                    trustChange = 3;
                    response = npc.name + " sorri: Uau! Muito obrigado pelo presente.";
                }
            }
        }

        // Salvar Memoria Curta e Afinidade/Confiança
        npc.memory.addMemory(memEvent, playerUuid);
        npc.getRelationship(playerUuid).addFriendship(baseChange);
        npc.getRelationship(playerUuid).addTrust(trustChange);

        // Simulating XP gain
        npc.stats.addXP(Math.abs(baseChange) * 10);

        // Update needs
        npc.needs.social = Math.min(100, npc.needs.social + 10);

        // Caskara real-time saving
        SimNPCPersistence.saveNPC(npc);
        
        return response;
    }

    private static String getContextualGreeting(SimNPCComponent npc, UUID playerUuid) {
        // 1. Checa as memórias recentes do próprio NPC
        if (npc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 300000)) {
            return npc.name + " cruza os braços: O que você quer? Já não me insultou o bastante hoje?";
        }

        // 2. Checa a fofoca da vila (Busca memórias recentes de outros NPCs)
        for (SimNPCComponent otherNpc : com.cookieukw.SimTale.SimTale.ACTIVE_NPCS) {
            if (otherNpc != npc && otherNpc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 600000)) {
                return npc.name + " te olha torto: Eu soube o que você fez com " + otherNpc.name + ". É bom andar na linha.";
            }
        }

        // 3. Fallbacks de Lore baseado nos Traits de Personalidade
        if (npc.personality.traits.contains(Trait.GREEDY)) {
            return npc.name + " esfrega as mãos: Tem algum minério ou item sobrando pra mim hoje?";
        } else if (npc.personality.traits.contains(Trait.PARANOID)) {
            return npc.name + " olha pros lados suando frio: Shh! Você escutou isso?...";
        } else if (npc.personality.traits.contains(Trait.LAZY)) {
            return npc.name + " boceja: Ah, oi... Me acorda quando a janta estiver pronta.";
        }

        return npc.name + " sorri: Olá! Que bom te ver.";
    }
}
