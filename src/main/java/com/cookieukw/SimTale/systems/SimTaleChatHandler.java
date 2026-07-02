package com.cookieukw.SimTale.systems;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.SimTale;
import com.hypixel.hytale.server.core.universe.world.World;

import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.engine.Animal;
import com.cookieukw.SimTale.engine.MagicDataLoader;
import com.cookieukw.SimTale.engine.MagicEngine;
import com.cookieukw.SimTale.engine.Question;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles chat interactions for controlling SimTale NPCs.
 */
public class SimTaleChatHandler implements Consumer<PlayerChatEvent> {
    @javax.annotation.Nonnull
    public static String getRandomVariant(String baseKey, int variants) {
        return baseKey + "." + (1 + (int)(Math.random() * variants));
    }

    private static final int CONVERSATION_TIMEOUT_TICKS = 600; // 10 seconds

    @Override
    public void accept(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        String message = event.getContent();

        if (message.trim().isEmpty()) {
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
                if (targetNpc.currentConversationPartner != null && world.getTick() >= targetNpc.conversationTimeoutTick) {
                    targetNpc.currentConversationPartner = null;
                }
                
                if (targetNpc.currentConversationPartner != null && !targetNpc.currentConversationPartner.equals(sender.getUuid())) {
                    sendReply(sender, Message.translation(getRandomVariant("simtale.chat.busy_multiplayer", 3)).param("name", targetNpc.name));
                    return;
                }
                
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

        boolean wantCancel = message.equals("tchau") || message.equals("adeus") || message.equals("sair") || message.equals("cancelar") || message.contains("deixa pra lá") || message.contains("deixa pra la") || message.contains("esquece");
        boolean wantChangeProfession = message.contains("vire ") || message.contains("seja ") || message.contains("trabalhe como ");
        boolean wantPlayMagicGinn = message.contains("jogar magic") || message.contains("play magic") || message.contains("akinator");
        boolean isGreeting = message.contains("olá") || message.contains("ola") || message.contains("hello") || message.contains("hi") || message.contains("oi") || message.contains("eae");
        boolean wantMine = message.contains("mine") || message.contains("minerar");
        boolean wantFish = message.contains("fish") || message.contains("pescar");
        boolean wantFarm = message.contains("farm") || message.contains("farmar") || message.contains("plantar");
        boolean wantGather = message.contains("gather") || message.contains("catar") || message.contains("coletar");
        boolean wantExplore = message.contains("explore") || message.contains("explorar");
        boolean wantCome = message.contains("vem") || message.contains("come") || message.contains("aqui");
        
        // Verifica se a mensagem é só o nome do NPC (ex: "Fizan")
        boolean justCalledName = message.trim().equalsIgnoreCase(npc.name) || message.trim().equalsIgnoreCase(npc.name + "!");

        if (wantCancel) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.cancel", 3)).param("name", npc.name));
            npc.currentConversationPartner = null;
            return;
        }

        if (wantChangeProfession) {
            handleProfessionChange(sender, message, npc);
            return;
        }

        if (wantPlayMagicGinn) {
            int affinity = npc.getRelationship(sender.getUuid()).friendship;
            if (affinity >= 0) {
                npc.activeMagicGame = new MagicEngine(MagicDataLoader.getAnimals(), MagicDataLoader.getQuestions());
                npc.currentConversationPartner = sender.getUuid();
                npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS * 5;
                sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.start", 3)).param("name", npc.name));
                sendNextMagicQuestion(sender, npc);
            } else {
                sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.reject", 3)).param("name", npc.name));
            }
            return;
        }

        if (npc.currentJob != JobType.NONE && !wantCome) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.busy_job", 3)).param("name", npc.name).param("job", npc.currentJob.getPortugueseName()));
            npc.currentConversationPartner = null;
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
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.come", 3)).param("name", npc.name));
            npc.currentJob = JobType.NONE;
            npc.currentConversationPartner = null;
        } else if (isGreeting || justCalledName) {
            npc.currentConversationPartner = sender.getUuid();
            npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;
            
            String[] greetings = {
                "simtale.chat.greeting.1",
                "simtale.chat.greeting.2",
                "simtale.chat.greeting.3",
                "simtale.chat.greeting.4",
                "simtale.chat.greeting.5"
            };
            String replyKey = greetings[(int)(Math.random() * greetings.length)];
            sendReply(sender, Message.translation(replyKey).param("name", npc.name));
        } else {
            npc.currentConversationPartner = sender.getUuid();
            npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;
            
            String[] smallTalks = {
                "simtale.chat.smalltalk.1",
                "simtale.chat.smalltalk.2",
                "simtale.chat.smalltalk.3",
                "simtale.chat.smalltalk.4",
                "simtale.chat.smalltalk.5",
                "simtale.chat.smalltalk.6",
                "simtale.chat.smalltalk.7",
                "simtale.chat.smalltalk.8",
                "simtale.chat.smalltalk.9",
                "simtale.chat.smalltalk.10"
            };
            String replyKey = smallTalks[(int)(Math.random() * smallTalks.length)];
            sendReply(sender, Message.translation(replyKey).param("name", npc.name));
        }
    }

    private void handleMagicGameCommand(PlayerRef sender, String message, SimNPCComponent npc, World world) {
        npc.conversationTimeoutTick = world.getTick() + CONVERSATION_TIMEOUT_TICKS * 5; // Refresh timeout

        if (message.contains("sair") || message.contains("stop") || message.contains("quit") || message.contains("parar") || message.contains("chega")) {
            npc.activeMagicGame = null;
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.cancel", 3)).param("name", npc.name));
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
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.invalid", 3)).param("name", npc.name));
            return;
        }

        MagicEngine engine = npc.activeMagicGame;
        engine.answerQuestion(engine.getBestQuestion(), weight);

        Animal victoryAnimal = engine.checkVictory();
        if (victoryAnimal != null) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.win", 3)).param("name", npc.name).param("animal_name", victoryAnimal.getName().getPt()));
            npc.activeMagicGame = null;
            return;
        }

        sendNextMagicQuestion(sender, npc);
    }

    private void sendNextMagicQuestion(PlayerRef sender, SimNPCComponent npc) {
        MagicEngine engine = npc.activeMagicGame;
        String nextQuestionId = engine.getBestQuestion();

        if (nextQuestionId == null) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.lose", 3)).param("name", npc.name));
            npc.activeMagicGame = null;
            return;
        }

        Question question = engine.getQuestions().stream()
                .filter(q -> q.getId().equals(nextQuestionId))
                .findFirst()
                .orElse(null);

        if (question != null) {
            int qNum = engine.getAskedQuestions().size() + 1;
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.magic.question", 3)).param("name", npc.name).param("num", qNum).param("question", question.getText().getPt()));
        }
    }

    private void handleProfessionChange(PlayerRef sender, String message, SimNPCComponent npc) {
        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 20) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.prof.reject", 3)).param("name", npc.name));
            return;
        }

        Profession newProf = null;
        if (message.contains("minerador") || message.contains("mineiro")) newProf = Profession.MINER;
        else if (message.contains("fazendeiro") || message.contains("agricultor")) newProf = Profession.FARMER;
        else if (message.contains("pescador")) newProf = Profession.FISHERMAN;
        else if (message.contains("lenhador")) newProf = Profession.LUMBERJACK;
        else if (message.contains("guarda") || message.contains("soldado")) newProf = Profession.GUARD;
        else if (message.contains("explorador") || message.contains("aventureiro")) newProf = Profession.EXPLORER;

        if (newProf != null) {
            npc.profession = newProf;
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.prof.accept", 3)).param("name", npc.name).param("prof_name", newProf.ptName));
            npc.currentConversationPartner = null;
        } else {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.prof.invalid", 3)).param("name", npc.name));
        }
    }

    private void assignJob(PlayerRef sender, SimNPCComponent npc, long currentTick, JobType job) {
        if (!npc.profession.canDoJob(job)) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.job.wrong_prof", 3)).param("name", npc.name).param("prof_name", npc.profession.ptName).param("job_name", job.getPortugueseName()));
            return;
        }

        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 10) {
            sendReply(sender, Message.translation(getRandomVariant("simtale.chat.job.reject", 3)).param("name", npc.name));
            return;
        }

        npc.currentJob = job;
        npc.jobDepartureTick = currentTick + 100L; // 5 seconds preparation phase
        npc.jobCompletionTick = npc.jobDepartureTick + (job.getDurationSeconds() * 20L);
        npc.jobEmployer = sender.getUuid();
        npc.isAway = false;
        npc.currentConversationPartner = null; // Unlock conversation now that intent is clear
        sendReply(sender, Message.translation(getRandomVariant("simtale.chat.job.accept", 3)).param("name", npc.name).param("job_name", job.getPortugueseName()));
    }

    private void sendReply(PlayerRef sender, Message text) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(150); // 150ms delay to ensure player's chat message is printed first
            } catch (InterruptedException e) {
                // Ignore
            }
            sender.sendMessage(text);
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
