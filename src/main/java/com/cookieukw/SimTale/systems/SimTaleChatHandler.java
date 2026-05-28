package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.engine.Animal;
import com.cookieukw.SimTale.engine.MagicDataLoader;
import com.cookieukw.SimTale.engine.MagicEngine;
import com.cookieukw.SimTale.engine.Question;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles chat interactions for controlling SimTale NPCs.
 */
public class SimTaleChatHandler implements Consumer<PlayerChatEvent> {

    private static final int CONVERSATION_TIMEOUT_TICKS = 200; // 10 seconds

    @Override
    public void accept(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        String message = event.getContent();

        if (sender == null || message == null || message.trim().isEmpty()) {
            return;
        }

        message = message.toLowerCase();

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }

        // Fallback: Se a lista estiver vazia após recarregar o servidor, tenta remontar os NPCs
        if (world != null && SimTale.ACTIVE_NPCS.isEmpty()) {
            SimNPCPersistence.reassembleActiveNPCs(world);
        }

        // Use the tracking list to find NPCs instead of broken reflection
        SimNPCComponent targetNpc = null;
        SimNPCComponent approximateNpc = null;
        int bestDistance = Integer.MAX_VALUE;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            // Check if currently focused on this player
            if (sender.getUuid().equals(npc.currentConversationPartner)) {
                targetNpc = npc;
                break;
            }

            if (npc.name == null) continue;

            String lowerName = npc.name.toLowerCase();
            // Exact substring check first
            if (message.contains(lowerName)) {
                targetNpc = npc;
                break;
            }

            // Fuzzy check by comparing words
            String[] messageWords = message.split("\\s+");
            String[] nameWords = lowerName.split("\\s+");
            
            for (String mWord : messageWords) {
                for (String nWord : nameWords) {
                    if (nWord.length() > 3) {
                        int dist = getLevenshteinDistance(mWord, nWord);
                        int maxDist = nWord.length() <= 5 ? 1 : 2; // Allow 1 typo for small names, 2 for larger
                        if (dist <= maxDist && dist < bestDistance) {
                            bestDistance = dist;
                            approximateNpc = npc;
                        }
                    }
                }
            }
        }

        if (targetNpc == null && approximateNpc != null) {
            targetNpc = approximateNpc; // Accept the closest fuzzy match
        }

        if (targetNpc != null) {
            if (world != null) {
                handleNpcCommand(sender, message, targetNpc, world);
            }
        }
    }

    private void handleNpcCommand(PlayerRef sender, String message, SimNPCComponent npc, World world) {
        long currentTick = world.getTick();

        if (npc.activeMagicGame != null) {
            handleMagicGameCommand(sender, message, npc, world);
            return;
        }

        boolean wantPlayMagicGinn = message.contains("jogar magic") || message.contains("play magic") || message.contains("akinator");
        boolean isGreeting = message.contains("olá") || message.contains("ola") || message.contains("hello") || message.contains("hi") || message.contains("oi") || message.contains("eae");
        boolean wantMine = message.contains("mine") || message.contains("minerar");
        boolean wantFish = message.contains("fish") || message.contains("pescar");
        boolean wantFarm = message.contains("farm") || message.contains("farmar") || message.contains("plantar");
        boolean wantGather = message.contains("gather") || message.contains("catar") || message.contains("coletar");
        boolean wantExplore = message.contains("explore") || message.contains("explorar");
        boolean wantCome = message.contains("vem") || message.contains("come") || message.contains("aqui");

        if (wantPlayMagicGinn) {
            int affinity = npc.getRelationship(sender.getUuid()).friendship;
            if (affinity >= 0) {
                npc.activeMagicGame = new MagicEngine(MagicDataLoader.getAnimals(), MagicDataLoader.getQuestions());
                npc.currentConversationPartner = sender.getUuid();
                npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS * 5; // Long timeout for game
                sendReply(sender, "<" + npc.name + "> Oba! Vamos jogar MagicGinn. Pense em um animal e me responda com Sim (Yes) ou Não (No).");
                sendNextMagicQuestion(sender, npc);
            } else {
                sendReply(sender, "<" + npc.name + "> Nós não somos tão próximos para eu brincar com você. / We are not close enough for me to play with you.");
            }
            return;
        }

        if (npc.currentJob != JobType.NONE && !wantCome) {
            sendReply(sender, "<" + npc.name + "> Já estou ocupado com meu trabalho de " + npc.currentJob.getPortugueseName() + "!");
            npc.currentConversationPartner = null; // Free the lock so player can talk to others
            return;
        }

        if (wantMine) {
            assignJob(sender, npc, currentTick, JobType.MINE);
        } else if (wantFish) {
            assignJob(sender, npc, currentTick, JobType.FISH);
        } else if (wantFarm) {
            assignJob(sender, npc, currentTick, JobType.FARM);
        } else if (wantGather) {
            assignJob(sender, npc, currentTick, JobType.GATHER);
        } else if (wantExplore) {
            assignJob(sender, npc, currentTick, JobType.EXPLORE);
        } else if (wantCome) {
            sendReply(sender, "<" + npc.name + "> Estou indo!");
            npc.currentJob = JobType.NONE;
            npc.currentConversationPartner = null; // Free lock
        } else if (isGreeting) {
            npc.currentConversationPartner = sender.getUuid();
            npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;
            sendReply(sender, "<" + npc.name + "> Olá! O que você precisa que eu faça? (Diga 'pescar', 'minerar', etc)");
        } else {
            // Unclear intent: lock conversation so next chat goes to them without name
            npc.currentConversationPartner = sender.getUuid();
            npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;
            sendReply(sender, "<" + npc.name + "> Hmm, não entendi o que você quis dizer. (Fale 'pescar', 'minerar', 'farmar', 'coletar', 'explorar', 'jogar magicginn')");
        }
    }

    private void handleMagicGameCommand(PlayerRef sender, String message, SimNPCComponent npc, World world) {
        npc.conversationTimeoutTick = world.getTick() + CONVERSATION_TIMEOUT_TICKS * 5; // Refresh timeout

        if (message.contains("sair") || message.contains("stop") || message.contains("quit") || message.contains("parar") || message.contains("chega")) {
            npc.activeMagicGame = null;
            sendReply(sender, "<" + npc.name + "> Ah, que pena! A gente joga depois então.");
            return;
        }

        double weight = 0.0;
        boolean answered = false;

        if (message.equals("sim") || message.equals("s") || message.equals("yes") || message.equals("y")) {
            weight = 1.0;
            answered = true;
        } else if (message.equals("não") || message.equals("nao") || message.equals("n") || message.equals("no")) {
            weight = -1.0;
            answered = true;
        } else if (message.contains("provavelmente sim") || message.contains("acho que sim") || message.contains("probably yes")) {
            weight = 0.5;
            answered = true;
        } else if (message.contains("provavelmente não") || message.contains("provavelmente nao") || message.contains("acho que não") || message.contains("probably no")) {
            weight = -0.5;
            answered = true;
        } else if (message.contains("não sei") || message.contains("nao sei") || message.contains("talvez") || message.contains("idk") || message.contains("don't know")) {
            weight = 0.0;
            answered = true;
        }

        if (!answered) {
            sendReply(sender, "<" + npc.name + "> Não entendi! Responda: Sim (Yes), Não (No), Não sei (Don't know), ou Sair (Quit).");
            return;
        }

        MagicEngine engine = npc.activeMagicGame;
        engine.answerQuestion(engine.getBestQuestion(), weight);

        Animal victoryAnimal = engine.checkVictory();
        if (victoryAnimal != null) {
            sendReply(sender, "<" + npc.name + "> Eu já sei! O animal que você pensou é: " + victoryAnimal.getName().getPt() + " (" + victoryAnimal.getName().getEn() + ")!");
            npc.activeMagicGame = null;
            return;
        }

        sendNextMagicQuestion(sender, npc);
    }

    private void sendNextMagicQuestion(PlayerRef sender, SimNPCComponent npc) {
        MagicEngine engine = npc.activeMagicGame;
        String nextQuestionId = engine.getBestQuestion();

        if (nextQuestionId == null) {
            sendReply(sender, "<" + npc.name + "> Não consegui adivinhar! Você me venceu!");
            npc.activeMagicGame = null;
            return;
        }

        Question question = engine.getQuestions().stream()
                .filter(q -> q.getId().equals(nextQuestionId))
                .findFirst()
                .orElse(null);

        if (question != null) {
            int qNum = engine.getAskedQuestions().size() + 1;
            sendReply(sender, "<" + npc.name + "> Pergunta " + qNum + ": " + question.getText().getPt() + " (" + question.getText().getEn() + ")");
        }
    }

    private void assignJob(PlayerRef sender, SimNPCComponent npc, long currentTick, JobType job) {
        npc.currentJob = job;
        npc.jobDepartureTick = currentTick + 100L; // 5 seconds preparation phase
        npc.jobCompletionTick = npc.jobDepartureTick + (job.getDurationSeconds() * 20L);
        npc.jobEmployer = sender.getUuid();
        npc.isAway = false;
        npc.currentConversationPartner = null; // Unlock conversation now that intent is clear
        sendReply(sender, "<" + npc.name + "> Certo, me preparando para ir " + job.getPortugueseName() + "!");
    }

    private void sendReply(PlayerRef sender, String text) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(150); // 150ms delay to ensure player's chat message is printed first
            } catch (InterruptedException e) {
                // Ignore
            }
            sender.sendMessage(Message.raw(text));
        });
    }

    private int getLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
