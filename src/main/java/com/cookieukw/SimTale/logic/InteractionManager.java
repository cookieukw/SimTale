package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.db.SimNPCPersistence;

import java.util.UUID;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import java.util.Random;

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
                    response = getRandomOption(
                        npc.name + ": ...isso era pra ser engraçado?",
                        npc.name + " suspira: Não estou no clima para gracinhas hoje.",
                        npc.name + " te encara sério: Por favor, me poupe das suas piadas agora.",
                        npc.name + " balança a cabeça: Sem graça. Muito sem graça.",
                        npc.name + " cruza os braços: Estou com problemas reais aqui e você me vem com piadinhas?"
                    );
                } else if (npc.personality.traits.contains(Trait.FUNNY)) {
                    friendshipChange = 5;
                    affinityChange = 15;
                    trustChange = 2;
                    response = getRandomOption(
                        npc.name + ": HAHAHA! Boa! Você leva jeito pra comédia.",
                        npc.name + " gargalha: Meu Deus, essa foi genial! Vou ter que contar pro pessoal.",
                        npc.name + " chora de rir: Ah não, pare! Minha barriga está doendo de tanto rir! Hahaha!",
                        npc.name + " te dá um tapa amigável no ombro: Hahaha, você é uma figura! Adorei!",
                        npc.name + " ri alto: Muito boa! Sabia que você era engraçado, mas essa superou."
                    );
                } else {
                    friendshipChange = 3;
                    affinityChange = 5;
                    trustChange = 1;
                    response = getRandomOption(
                        "Você contou uma piada! " + npc.name + " riu bastante.",
                        npc.name + " dá uma risada leve: Heh, essa foi boa. Valeu pelo sorriso.",
                        npc.name + " sorri com humor: Boa tentativa! Essa eu não conhecia.",
                        "Você compartilhou uma piada engraçada e " + npc.name + " esboçou um belo sorriso.",
                        npc.name + " solta um riso abafado: Hahaha, ok, essa foi engraçadinha."
                    );
                }
            }
            case ROMANTIC -> {
                memEvent = MemoryEvent.FLIRTED;
                int affinity = npc.getRelationship(playerUuid).affinity;
                if (affinity < 20 || mood == Mood.ANGRY) {
                    romanceChange = -10;
                    affinityChange = -15;
                    trustChange = -2;
                    response = getRandomOption(
                        npc.name + ": Cara... que? Sai pra lá.",
                        npc.name + " te olha com desdém: Menos, bem menos. Não temos essa intimidade.",
                        npc.name + " dá um passo para trás: Por favor, guarde esses comentários para você.",
                        npc.name + " cruza os braços revirando os olhos: Acho que você errou de pessoa. Me erra.",
                        npc.name + " diz friamente: Que cantada barata. Não caio nessa."
                    );
                } else if (npc.personality.traits.contains(Trait.SHY)) {
                    romanceChange = 15;
                    affinityChange = 10;
                    trustChange = 2;
                    response = getRandomOption(
                        npc.name + " cora e desvia o olhar: O-obrigado... você também não é nada mal.",
                        npc.name + " gagueja vermelho: Ah... e-eu... obrigado. Você me pega de surpresa assim.",
                        npc.name + " cobre o rosto tímido: P-pare de me olhar assim, está me deixando sem jeito!",
                        npc.name + " dá um sorriso envergonhado: Você sempre diz essas coisas tão de repente... bobo.",
                        npc.name + " morde o lábio de vergonha: Ah... obrigado pelo elogio. Fico feliz."
                    );
                } else {
                    romanceChange = 10;
                    affinityChange = 5;
                    trustChange = 1;
                    response = getRandomOption(
                        npc.name + ": Heh... continua falando. Estou gostando de ouvir.",
                        npc.name + " dá uma piscadela: É mesmo? Quem sabe se você insistir mais um pouco...",
                        npc.name + " sorri de canto: Olha só, um conquistador por aqui. Interessante.",
                        npc.name + " sorri convencido: Eu sei que sou incrível, mas é sempre bom ouvir de você.",
                        npc.name + " dá um sorriso charmoso: Você sabe mesmo como encantar alguém, não é?"
                    );
                }
            }
            case MEAN -> {
                memEvent = MemoryEvent.INSULTED;
                trustChange = -15;
                if (npc.personality.traits.contains(Trait.AGGRESSIVE)) {
                    friendshipChange = -10;
                    affinityChange = -20;
                    response = getRandomOption(
                        npc.name + " saca a arma: Você quer resolver isso agora?!",
                        npc.name + " cerra os punhos: Repete isso na minha cara se tiver coragem!",
                        npc.name + " te empurra: Quem você pensa que é pra falar assim comigo?!",
                        npc.name + " grita furioso: Cala a boca antes que eu te faça calar!",
                        npc.name + " cospe no chão: Você vai se arrepender de ter aberto essa boca."
                    );
                } else if (npc.personality.traits.contains(Trait.NEEDY)) {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = getRandomOption(
                        npc.name + " quase chora: Por que você é tão mau comigo? O que eu te fiz?",
                        npc.name + " fica com os olhos cheios de lágrimas: Pensei que fôssemos amigos...",
                        npc.name + " funga magoado: Suas palavras realmente machucam, sabia?",
                        npc.name + " abaixa a cabeça triste: Não precisava ser tão rude... eu só estava aqui.",
                        npc.name + " diz com a voz trêmula: Eu... eu achei que você era uma pessoa melhor."
                    );
                } else {
                    friendshipChange = -5;
                    affinityChange = -15;
                    response = getRandomOption(
                        "Você insultou " + npc.name + "! A conversa ficou extremamente tensa.",
                        npc.name + " te ignora friamente, virando as costas.",
                        npc.name + " te olha com nojo: Você deve ter uma vida bem triste para falar assim dos outros.",
                        npc.name + " rebate com desdém: Falou o exemplo de pessoa. Olha pro espelho.",
                        npc.name + " responde de forma seca: Não perco meu tempo conversando com gente do seu tipo."
                    );
                }
            }
            case RANDOM -> {
                affinityChange = 1;
                response = "Interação Autônoma";
            }
            case GIFT -> {
                Ref<EntityStore> pRef = playerRef != null ? playerRef.getReference() : null;
                if (pRef == null || !pRef.isValid()) {
                    // Revert interaction counter increment
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Erro: Jogador inválido.";
                }
                
                InventoryComponent.Hotbar hotbar = 
                    pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
                if (hotbar == null) {
                    // Revert interaction counter increment
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Erro: Inventário indisponível.";
                }
                
                byte activeSlot = hotbar.getActiveSlot();
                ItemStack heldItem = hotbar.getActiveItem();
                if (heldItem == null || heldItem.isEmpty()) {
                    // Revert interaction counter increment
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Você não está segurando nenhum item em mãos para presentear!";
                }
                
                String itemName = heldItem.getDisplayName().getAnsiMessage();
                String itemId = heldItem.getItemId();
                
                // Consume 1 item from the active slot
                hotbar.getInventory().removeItemStackFromSlot(activeSlot, 1);
                
                memEvent = MemoryEvent.GIFTED;
                
                boolean loves = false;
                boolean hates = false;
                
                if (npc.preferences != null) {
                    if (npc.preferences.favoriteFoods != null) {
                        for (String fav : npc.preferences.favoriteFoods) {
                            if (fav.equalsIgnoreCase(itemName) || fav.equalsIgnoreCase(itemId)) {
                                loves = true;
                                break;
                            }
                        }
                    }
                    if (npc.preferences.hatedFoods != null) {
                        for (String hate : npc.preferences.hatedFoods) {
                            if (hate.equalsIgnoreCase(itemName) || hate.equalsIgnoreCase(itemId)) {
                                hates = true;
                                break;
                            }
                        }
                    }
                }
                
                boolean isTrash = itemId.contains("dirt") || itemId.contains("soil_sand") || itemId.contains("rock_stone") || itemId.contains("spiderweb") || itemId.contains("gravel") || itemId.contains("deco_trash") ||
                                  itemName.toLowerCase().contains("terra") || itemName.toLowerCase().contains("pedra") || itemName.toLowerCase().contains("teia") || itemName.toLowerCase().contains("cascalho") || itemName.toLowerCase().contains("areia") || itemName.toLowerCase().contains("lixo");
                
                if (loves) {
                    friendshipChange = 15;
                    affinityChange = 25;
                    trustChange = 8;
                    response = npc.name + " fica radiante: Oh meu deus, " + itemName + "?! Esse é o meu prato favorito! Você me conhece tão bem!";
                } else if (hates) {
                    friendshipChange = -15;
                    affinityChange = -20;
                    trustChange = -10;
                    response = npc.name + " faz uma careta de nojo/raiva: Eca... " + itemName + "? Eu detesto isso! Você está tentando me insultar?";
                } else if (isTrash) {
                    friendshipChange = -10;
                    affinityChange = -15;
                    trustChange = -5;
                    response = npc.name + " franze a testa com raiva: " + itemName + "? Por que você está me dando lixo?!";
                } else {
                    if (npc.personality.traits.contains(Trait.GREEDY)) {
                        friendshipChange = 10;
                        affinityChange = 20;
                        trustChange = 5;
                        response = npc.name + " arregala os olhos: Pra mim?! Haha, " + itemName + "! Finalmente alguém que me valoriza!";
                    } else if (npc.personality.traits.contains(Trait.PARANOID)) {
                        friendshipChange = -5;
                        affinityChange = -10;
                        trustChange = -10;
                        response = npc.name + " olha o presente (" + itemName + ") com suspeita: Isso não tem veneno, tem?";
                    } else {
                        friendshipChange = 5;
                        affinityChange = 10;
                        trustChange = 3;
                        response = npc.name + " sorri: Uau, " + itemName + "! Muito obrigado pelo presente.";
                    }
                }
            }
            case ASSIGN_PROFESSION -> {
                Ref<EntityStore> pRef = playerRef != null ? playerRef.getReference() : null;
                if (pRef == null || !pRef.isValid()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Erro: Jogador inválido.";
                }
                
                InventoryComponent.Hotbar hotbar = 
                    pRef.getStore().getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
                if (hotbar == null) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Erro: Inventário indisponível.";
                }
                
                byte activeSlot = hotbar.getActiveSlot();
                ItemStack heldItem = hotbar.getActiveItem();
                if (heldItem == null || heldItem.isEmpty()) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return "Segure um item representativo da profissão (picareta, espada, enxada, etc.) para atribuir um emprego!";
                }
                
                String itemId = heldItem.getItemId();
                String itemName = heldItem.getDisplayName().getAnsiMessage();
                Profession targetProf = Profession.fromItemId(itemId);
                
                if (targetProf == null) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return npc.name + " olha confuso para o " + itemName + ": ...e o que eu faço com isso? Isso não é uma ferramenta de trabalho!";
                }
                
                // Already has that profession
                if (npc.profession == targetProf) {
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    return npc.name + ": Eu já sou " + targetProf.ptName + ", esqueceu? Estou trabalhando nisso todo dia!";
                }
                
                // Check if NPC will refuse based on preferences and traits
                boolean refuses = false;
                String refuseReason = "";
                
                // Check disliked professions from preferences
                if (npc.preferences != null && npc.preferences.dislikedProfessions.contains(targetProf)) {
                    refuses = true;
                    refuseReason = getRandomOption(
                        npc.name + " faz cara de nojo: " + targetProf.ptName + "?! Nem morto! Eu odeio esse tipo de trabalho!",
                        npc.name + " cruza os braços: Não, obrigado. " + targetProf.ptName + " não é pra mim. Detesto isso.",
                        npc.name + " empurra o " + itemName + " de volta: Tá de brincadeira? Eu desprezaria ser " + targetProf.ptName + "!",
                        npc.name + " balança a cabeça: Nem pensar. " + targetProf.ptName + " é a última coisa que eu quero ser."
                    );
                }
                
                // Trait-based refusal chances (if not already refusing)
                if (!refuses) {
                    boolean isHeavyWork = (targetProf == Profession.MINER || targetProf == Profession.LUMBERJACK);
                    boolean isPeacefulWork = (targetProf == Profession.FARMER || targetProf == Profession.FISHERMAN);
                    
                    if (npc.personality.traits.contains(Trait.LAZY) && isHeavyWork && Math.random() < 0.6) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            npc.name + " boceja: " + targetProf.ptName + "? Isso dá muito trabalho... Não tenho energia pra isso.",
                            npc.name + " se espreguiça: Trabalho pesado? Eu? Hahaha, boa piada.",
                            npc.name + " suspira: Olha, agradeço a oferta, mas... não rola. Muito suor envolvido."
                        );
                    } else if (npc.personality.traits.contains(Trait.AGGRESSIVE) && isPeacefulWork && Math.random() < 0.7) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            npc.name + " rosna: " + targetProf.ptName + "?! Eu pareço alguém que fica plantando florzinha?!",
                            npc.name + " bate no peito: Eu sou um guerreiro, não um camponês! Me respeita!",
                            npc.name + " cospe no chão: Trabalho manso demais pra mim. Me dá algo com ação!"
                        );
                    } else if (mood == Mood.ANGRY && Math.random() < 0.5) {
                        refuses = true;
                        refuseReason = getRandomOption(
                            npc.name + " grita: NÃO ESTOU NO HUMOR PRA ISSO! Volta depois!",
                            npc.name + " te olha com raiva: Agora não! Estou puto demais pra pensar em emprego!",
                            npc.name + " empurra o item: Sai! Não me venha com propostas agora!"
                        );
                    }
                }
                
                if (refuses) {
                    // Don't consume item on refusal
                    rel.interactionsToday = Math.max(0, rel.interactionsToday - 1);
                    affinityChange = -5;
                    friendshipChange = -3;
                    response = refuseReason;
                } else {
                    // Accept the profession!
                    // If NPC already had a profession, give old trigger item back to player
                    if (npc.profession != null && npc.profession != Profession.UNEMPLOYED 
                        && !npc.profession.triggerItemKeyword.isEmpty()) {
                        // Return old profession item to player's chat as message
                        response = npc.name + " te devolve suas ferramentas de " + npc.profession.ptName + ". ";
                    } else {
                        response = "";
                    }
                    
                    Profession oldProf = npc.profession;
                    npc.profession = targetProf;
                    
                    // Consume 1 item from the active slot
                    hotbar.getInventory().removeItemStackFromSlot(activeSlot, 1);
                    
                    friendshipChange = 8;
                    affinityChange = 15;
                    trustChange = 5;
                    
                    // Check if it's a liked profession for bonus
                    if (npc.preferences != null && npc.preferences.likedProfessions.contains(targetProf)) {
                        friendshipChange = 15;
                        affinityChange = 25;
                        trustChange = 10;
                        response += getRandomOption(
                            npc.name + " pega o " + itemName + " com brilho nos olhos: " + targetProf.ptName + "?! Eu SEMPRE quis fazer isso! Obrigado!",
                            npc.name + " quase pula de alegria: Sério que vou ser " + targetProf.ptName + "?! Esse é o meu sonho!",
                            npc.name + " segura o " + itemName + " com carinho: Finalmente alguém reconhece meu talento! Vou ser o melhor " + targetProf.ptName + " de todos!"
                        );
                    } else {
                        response += getRandomOption(
                            npc.name + " pega o " + itemName + " e acena: Ok, " + targetProf.ptName + " parece bom. Vou dar o meu melhor!",
                            npc.name + " examina o " + itemName + ": Hmm, " + targetProf.ptName + "... Posso tentar. Valeu pela oportunidade!",
                            npc.name + " sorri: " + targetProf.ptName + "? Pode ser! Vou começar agora mesmo.",
                            npc.name + " guarda o " + itemName + ": Beleza, aceito ser " + targetProf.ptName + ". Conta comigo!",
                            npc.name + " testa o " + itemName + ": Nunca trabalhei com isso, mas estou animado pra aprender!"
                        );
                    }
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

        if (memEvent != null) {
            npc.memory.addMemory(memEvent, playerUuid);
        }
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
                Ref<EntityStore> pRef = playerRef.getReference();
                if (pRef != null && pRef.isValid()) {
                    EntityStatMap statMap = pRef.getStore().getComponent(pRef, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue healthVal = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (healthVal != null) {
                            float health = healthVal.get();
                            if (health > 0 && health <= 20f) {
                                return npc.name + " arregala os olhos: Meu deus, você está sangrando! Precisa de ajuda?!";
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

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
            return getRandomOption(
                npc.name + " esfrega as mãos: Tem algum minério ou item sobrando pra mim hoje?",
                npc.name + " olha pra sua mochila: Hmmm, quanta coisa brilhante aí dentro... não quer dividir?",
                npc.name + " pisca: Olá! Veio me trazer presentes ou só jogar conversa fora?"
            );
        } else if (npc.personality.traits.contains(Trait.PARANOID)) {
            return getRandomOption(
                npc.name + " olha pros lados suando frio: Shh! Você escutou isso?...",
                npc.name + " sussurra nervoso: Eles estão nos observando... aja naturalmente!",
                npc.name + " te puxa de canto: Você viu aquele arbusto se mexendo? Tenho certeza que era um Trork espião."
            );
        } else if (npc.personality.traits.contains(Trait.LAZY)) {
            return getRandomOption(
                npc.name + " boceja: Ah, oi... Me acorda quando a janta estiver pronta.",
                npc.name + " se espreguiça: Oi... Que sono. Falou algo importante?",
                npc.name + " suspira preguiçosamente: Oi. Se for pra trabalhar, diz que eu não estou."
            );
        }

        return getRandomOption(
            npc.name + " sorri: Olá! Que bom te ver por aqui hoje.",
            npc.name + " acena: Opa! Como estão as coisas?",
            npc.name + ": E aí! Novidades por Orbis?",
            npc.name + " sorri amigavelmente: Oi! Fico feliz que tenha vindo conversar.",
            npc.name + ": Olá! Estava mesmo pensando em quando nos veríamos de novo."
        );
    }

    private static final Random rand = new Random();
    private static String getRandomOption(String... options) {
        return options[rand.nextInt(options.length)];
    }
}
