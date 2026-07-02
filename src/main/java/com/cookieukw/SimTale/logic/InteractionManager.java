package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import java.util.UUID;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.cookie.runecore.api.PlayerStats;

public class InteractionManager {

    public static String performInteraction(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef, InteractionType type) {
        Mood mood = npc.getMood();
        int friendshipChange = 0;
        int romanceChange = 0;
        int trustChange = 0;
        int affinityChange = 0;
        String response = "";
        MemoryEvent memEvent = MemoryEvent.CHATTED;

        Relationship rel = npc.getRelationship(playerUuid);
        long currentDayIndex = System.currentTimeMillis() / 1200000L;
        
        boolean missedLongTime = false;
        if (rel.lastInteractionDayIndex > 0 && rel.lastInteractionDayIndex < currentDayIndex) {
            long daysMissed = currentDayIndex - rel.lastInteractionDayIndex;
            if (daysMissed >= 3) {
                missedLongTime = true;
            }
            rel.interactionsToday = 0;
        } else if (rel.lastInteractionDayIndex == 0) {
            rel.interactionsToday = 0;
        }
        rel.lastInteractionDayIndex = currentDayIndex;

        boolean cooldownActive = rel.interactionsToday >= 3;
        rel.interactionsToday++;

        switch (type) {
            case FRIENDLY -> {
                friendshipChange = 5;
                affinityChange = 5;
                trustChange = 1;
                response = getContextualGreeting(npc, playerUuid, playerRef);
            }
            case FUNNY -> {
                memEvent = MemoryEvent.JOKED;
                if (mood == Mood.ANGRY || mood == Mood.SAD) {
                    friendshipChange = -2;
                    affinityChange = -5;
                    response = npc.name + ": ...isso era pra ser engraçado?";
                } else if (npc.personality.traits.contains(Trait.FUNNY)) {
                    friendshipChange = 5;
                    affinityChange = 15;
                    trustChange = 2;
                    response = npc.name + ": HAHAHA! Boa! Você leva jeito pra comédia.";
                } else {
                    friendshipChange = 3;
                    affinityChange = 5;
                    trustChange = 1;
                    response = "Você contou uma piada! " + npc.name + " riu bastante.";
                }
            }
            case ROMANTIC -> {
                memEvent = MemoryEvent.FLIRTED;
                int affinity = npc.getRelationship(playerUuid).affinity;
                if (affinity < 20 || mood == Mood.ANGRY) {
                    romanceChange = -10;
                    affinityChange = -15;
                    trustChange = -2;
                    response = npc.name + ": Cara... que? Sai pra lá.";
                } else if (npc.personality.traits.contains(Trait.SHY)) {
                    romanceChange = 15;
                    affinityChange = 10;
                    trustChange = 2;
                    response = npc.name + " cora e desvia o olhar: O-obrigado...";
                } else {
                    romanceChange = 10;
                    affinityChange = 5;
                    trustChange = 1;
                    response = npc.name + ": Heh... continua falando.";
                }
            }
            case MEAN -> {
                memEvent = MemoryEvent.INSULTED;
                trustChange = -15;
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    friendshipChange = -10;
                    affinityChange = -20;
                    response = npc.name + " saca a arma: Você quer resolver isso agora?!";
                } else if (npc.personality.traits.contains(Trait.NEEDY)) {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = npc.name + " quase chora: Por que você é tão mau comigo?";
                } else {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = "Você insultou o " + npc.name + "!";
                }
            }
            case RANDOM -> {
                affinityChange = 1;
                response = "Interação Autônoma";
            }
            case GIFT -> {
                memEvent = MemoryEvent.GIFTED;
                if (npc.personality.traits.contains(Trait.GREEDY)) {
                    friendshipChange = 10;
                    affinityChange = 20;
                    trustChange = 5;
                    response = npc.name + " arregala os olhos: Pra mim?! Haha, FINALMENTE alguém que me valoriza!";
                } else if (npc.personality.traits.contains(Trait.PARANOID)) {
                    friendshipChange = -5;
                    affinityChange = -10;
                    trustChange = -10;
                    response = npc.name + " olha o presente com suspeita: Isso não tem veneno, tem?";
                } else {
                    friendshipChange = 5;
                    affinityChange = 10;
                    trustChange = 3;
                    response = npc.name + " sorri: Uau! Muito obrigado pelo presente.";
                }
            }
        }
        
        if (cooldownActive) {
            affinityChange = 0;
            friendshipChange = 0;
            romanceChange = 0;
            trustChange = 0;
            if (type == InteractionType.FRIENDLY) {
                response = npc.name + " acena rapidamente: Oi, já conversamos bastante hoje, né?";
            } else if (type == InteractionType.GIFT) {
                response = npc.name + ": Mais presentes? Ah, ok... obrigado.";
            } else {
                response = npc.name + " parece ocupado(a) e apenas murmura algo.";
            }
        } else if (missedLongTime && type == InteractionType.FRIENDLY) {
            response = npc.name + " arregala os olhos: Nossa, faz dias que não te vejo! Onde você estava?! " + response;
        }

        npc.memory.addMemory(memEvent, playerUuid);
        npc.getRelationship(playerUuid).addAffinity(affinityChange);
        npc.getRelationship(playerUuid).addFriendship(friendshipChange);
        npc.getRelationship(playerUuid).addRomance(romanceChange);
        npc.getRelationship(playerUuid).addTrust(trustChange);

        npc.stats.addXP(Math.abs(affinityChange) * 10);

        npc.needs.social = Math.min(100, npc.needs.social + 10);

        SimNPCPersistence.saveNPC(npc);
        
        return response;
    }

    private static String getContextualGreeting(SimNPCComponent npc, UUID playerUuid, PlayerRef playerRef) {
        if (playerRef != null) {
            try {
                PlayerStats stats = new PlayerStats(playerRef);
                float health = stats.getHealth().getNow(-1f);
                if (health > 0 && health <= 20f) {
                    return npc.name + " arregala os olhos: Meu deus, você está sangrando! Precisa de ajuda?!";
                }
            } catch (Exception ignored) {}

            World world = null;
            for (World w : Universe.get().getWorlds().values()) {
                world = w;
                break;
            }
            if (world != null) {
                WorldTimeResource timeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
                float dayProgress = timeResource.getDayProgress();
                if (dayProgress < 0.25f || dayProgress > 0.75f) {
                    return npc.name + " sussurra: É perigoso andar por aqui à noite... Tome cuidado.";
                }
            }
        }

        if (npc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 300000)) {
            return npc.name + " cruza os braços: O que você quer? Já não me insultou o bastante hoje?";
        }

        for (SimNPCComponent otherNpc : SimTale.ACTIVE_NPCS) {
            if (otherNpc != npc && otherNpc.memory.remembers(MemoryEvent.INSULTED, playerUuid, 600000)) {
                return npc.name + " te olha torto: Eu soube o que você fez com " + otherNpc.name + ". É bom andar na linha.";
            }
        }

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
