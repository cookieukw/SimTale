package com.cookieukw.SimTale.systems;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.FriendshipTier;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.engine.Animal;
import com.cookieukw.SimTale.engine.MagicDataLoader;
import com.cookieukw.SimTale.engine.MagicEngine;
import com.cookieukw.SimTale.engine.Question;
import com.cookieukw.SimTale.logic.JobType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.logger.HytaleLogger;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Handles chat interactions for controlling SimTale NPCs.
 * Talks now vary by FriendshipTier: the SAME response category
 * (e.g. greeting, job refusal) has separate translation pools based on
 * how close the NPC is to the player -- a stranger speaks formally/dryly, a
 * close_friend speaks warmly/informally. This requires creating the corresponding
 * translation keys (see key section at the end of the file).
 */
public class SimTaleChatHandler implements Consumer<PlayerChatEvent> {

    private static final int CONVERSATION_TIMEOUT_TICKS = 600; // 10 seconds

    @Nonnull
    public static String getRandomVariant(String baseKey, int variants) {
        return baseKey + "." + (1 + (int) (Math.random() * variants));
    }

    /**
     * Builds the translation key including the friendship tier:
     * "chat.greeting" + ".friend" + ".2"
     */
    @Nonnull
    public static String getRandomVariant(String baseKey, FriendshipTier tier, int variants) {
        return baseKey + "." + tier.translationKey + "." + (1 + (int) (Math.random() * variants));
    }

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

        // Fallback: If the list is empty after reloading the server, try to reassemble the NPCs
        if (world != null && SimTale.ACTIVE_NPCS.isEmpty()) {
            SimNPCPersistence.reassembleActiveNPCs(world);
        }

        SimNPCComponent targetNpc = findTargetNpc(sender, message);

        HytaleLogger.forEnclosingClass().atInfo().log("SimTale [CHAT DEBUG]: message='" + message + "', foundNPC=" + (targetNpc != null ? targetNpc.name : "null") + ", activeNPCs=" + 
            SimTale.ACTIVE_NPCS.stream().map(n -> n.name).collect(Collectors.joining(", ")));

        if (targetNpc != null && world != null) {
            if (targetNpc.currentConversationPartner != null && world.getTick() >= targetNpc.conversationTimeoutTick) {
                targetNpc.currentConversationPartner = null;
            }

            if (targetNpc.currentConversationPartner != null && !targetNpc.currentConversationPartner.equals(sender.getUuid())) {
                FriendshipTier tier = tierFor(targetNpc, sender);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.busy_multiplayer", tier, 3)).param("name", targetNpc.name));
                return;
            }

            handleNpcCommand(sender, message, targetNpc, world);
        }
    }

    /** Uses the tracking list to find the NPC (exact name, or fuzzy match by Levenshtein). */
    @NullableDecl
    private SimNPCComponent findTargetNpc(PlayerRef sender, String message) {
        if (message == null) return null;

        // Clean message: lowercase, remove color codes, strip basic punctuation, normalize spaces
        String cleanMessage = message.toLowerCase().replaceAll("§.", "").replaceAll("[.,!?;:]", " ").replaceAll("\\s+", " ").trim();

        SimNPCComponent approximateNpc = null;
        int bestDistance = Integer.MAX_VALUE;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (sender.getUuid().equals(npc.currentConversationPartner)) {
                return npc;
            }

            if (npc.name == null) continue;

            // Clean NPC name in same way
            String cleanNpcName = npc.name.toLowerCase().replaceAll("§.", "").replaceAll("[.,!?;:]", " ").replaceAll("\\s+", " ").trim();

            // 1. Exact full-name substring match
            if (cleanMessage.contains(cleanNpcName)) {
                return npc;
            }

            String[] nameWords = cleanNpcName.split("\\s+");
            String[] messageWords = cleanMessage.split("\\s+");

            // 2. Standalone exact word match (e.g. typing first name or last name exactly)
            for (String nWord : nameWords) {
                if (nWord.length() >= 3) {
                    for (String mWord : messageWords) {
                        if (mWord.equals(nWord)) {
                            return npc;
                        }
                    }
                }
            }

            // 3. Fallback fuzzy Levenshtein match
            for (String mWord : messageWords) {
                for (String nWord : nameWords) {
                    if (nWord.length() > 3) {
                        int dist = getLevenshteinDistance(mWord, nWord);
                        int maxDist = nWord.length() <= 5 ? 1 : 2;
                        if (dist <= maxDist && dist < bestDistance) {
                            bestDistance = dist;
                            approximateNpc = npc;
                        }
                    }
                }
            }
        }

        return approximateNpc;
    }

    /** Single point to get the friendship tier -- avoids repeating the call everywhere. */
    @Nonnull
    private FriendshipTier tierFor(SimNPCComponent npc, PlayerRef sender) {
        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        return FriendshipTier.fromAffinity(affinity);
    }

    private void handleNpcCommand(PlayerRef sender, String message, SimNPCComponent npc, World world) {
        long currentTick = world.getTick();
        FriendshipTier tier = tierFor(npc, sender);

        if (npc.activeMagicGame != null) {
            handleMagicGameCommand(sender, message, npc, world, tier);
            return;
        }

        ChatIntent intent = ChatIntent.detect(message, npc.name);

        if (intent == ChatIntent.CANCEL) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.cancel", tier, 3)).param("name", npc.name));
            npc.currentConversationPartner = null;
            return;
        }

        if (intent == ChatIntent.CHANGE_PROFESSION) {
            handleProfessionChange(sender, message, npc, tier);
            return;
        }

        if (intent == ChatIntent.PLAY_MAGIC_GAME) {
            handlePlayMagicGame(sender, npc, currentTick, tier);
            return;
        }

        if (npc.currentJob != JobType.NONE && intent != ChatIntent.COME) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.busy_job", tier, 3))
                    .param("name", npc.name).param("job", npc.currentJob.getPortugueseName()));
            npc.currentConversationPartner = null;
            return;
        }

        switch (intent) {
            case MINE -> assignJob(sender, npc, currentTick, JobType.MINE, tier);
            case FISH -> assignJob(sender, npc, currentTick, JobType.FISH, tier);
            case FARM -> assignJob(sender, npc, currentTick, JobType.FARM, tier);
            case GATHER -> assignJob(sender, npc, currentTick, JobType.GATHER, tier);
            case EXPLORE -> assignJob(sender, npc, currentTick, JobType.EXPLORE, tier);
            case COME -> {
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.come", tier, 3)).param("name", npc.name));
                npc.currentJob = JobType.NONE;
                npc.currentConversationPartner = null;
            }
            case GREETING -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.greeting", tier, 5)).param("name", npc.name));
            }
            default -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.smalltalk", tier, 10)).param("name", npc.name));
            }
        }
    }

    private void openConversation(SimNPCComponent npc, PlayerRef sender, long currentTick) {
        npc.currentConversationPartner = sender.getUuid();
        npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;
    }

    private void handlePlayMagicGame(PlayerRef sender, SimNPCComponent npc, long currentTick, FriendshipTier tier) {
        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity >= 0) {
            npc.activeMagicGame = new MagicEngine(MagicDataLoader.getAnimals(), MagicDataLoader.getQuestions());
            npc.currentConversationPartner = sender.getUuid();
            npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS * 5;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.start", tier, 3)).param("name", npc.name));
            sendNextMagicQuestion(sender, npc, tier);
        } else {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.reject", tier, 3)).param("name", npc.name));
        }
    }

    private void handleMagicGameCommand(PlayerRef sender, String message, SimNPCComponent npc, World world, FriendshipTier tier) {
        npc.conversationTimeoutTick = world.getTick() + CONVERSATION_TIMEOUT_TICKS * 5; // Refresh timeout

        if (message.contains("sair") || message.contains("stop") || message.contains("quit") || message.contains("parar") || message.contains("chega")) {
            npc.activeMagicGame = null;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.cancel", tier, 3)).param("name", npc.name));
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
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.invalid", tier, 3)).param("name", npc.name));
            return;
        }

        MagicEngine engine = npc.activeMagicGame;
        engine.answerQuestion(engine.getBestQuestion(), weight);

        Animal victoryAnimal = engine.checkVictory();
        if (victoryAnimal != null) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.win", tier, 3)).param("name", npc.name).param("animal_name", victoryAnimal.getName().getPt()));
            npc.activeMagicGame = null;
            return;
        }

        sendNextMagicQuestion(sender, npc, tier);
    }

    private void sendNextMagicQuestion(PlayerRef sender, SimNPCComponent npc, FriendshipTier tier) {
        MagicEngine engine = npc.activeMagicGame;
        String nextQuestionId = engine.getBestQuestion();

        if (nextQuestionId == null) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.lose", tier, 3)).param("name", npc.name));
            npc.activeMagicGame = null;
            return;
        }

        Question question = engine.getQuestions().stream()
                .filter(q -> q.getId().equals(nextQuestionId))
                .findFirst()
                .orElse(null);

        if (question != null) {
            int qNum = engine.getAskedQuestions().size() + 1;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.magic.question", tier, 3))
                    .param("name", npc.name).param("num", qNum).param("question", question.getText().getPt()));
        }
    }

    private void handleProfessionChange(PlayerRef sender, String message, SimNPCComponent npc, FriendshipTier tier) {
        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 20) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.prof.reject", tier, 3)).param("name", npc.name));
            return;
        }

        Profession newProf = getProfession(message);

        if (newProf != null) {
            npc.profession = newProf;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.prof.accept", tier, 3)).param("name", npc.name).param("prof_name", newProf.ptName));
            npc.currentConversationPartner = null;
        } else {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.prof.invalid", tier, 3)).param("name", npc.name));
        }
    }

    @NullableDecl
    private static Profession getProfession(String message) {
        Profession newProf = null;
        if (message.contains("minerador") || message.contains("mineiro")) newProf = Profession.MINER;
        else if (message.contains("fazendeiro") || message.contains("agricultor")) newProf = Profession.FARMER;
        else if (message.contains("pescador")) newProf = Profession.FISHERMAN;
        else if (message.contains("lenhador")) newProf = Profession.LUMBERJACK;
        else if (message.contains("guarda") || message.contains("soldado")) newProf = Profession.GUARD;
        else if (message.contains("explorador") || message.contains("aventureiro")) newProf = Profession.EXPLORER;
        return newProf;
    }

    private void assignJob(PlayerRef sender, SimNPCComponent npc, long currentTick, JobType job, FriendshipTier tier) {
        if (!npc.profession.canDoJob(job)) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.job.wrong_prof", tier, 3))
                    .param("name", npc.name).param("prof_name", npc.profession.ptName).param("job_name", job.getPortugueseName()));
            return;
        }

        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 10) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.job.reject", tier, 3)).param("name", npc.name));
            return;
        }

        npc.currentJob = job;
        npc.jobDepartureTick = currentTick + 100L; // 5 seconds preparation phase
        npc.jobCompletionTick = npc.jobDepartureTick + (job.getDurationSeconds() * 20L);
        npc.jobEmployer = sender.getUuid();
        npc.isAway = false;
        npc.currentConversationPartner = null; // Unlock conversation now that intent is clear
        sendReply(sender, Message.translation(getRandomVariant("npc-interactions.chat.job.accept", tier, 3)).param("name", npc.name).param("job_name", job.getPortugueseName()));
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

    /**
     * Intent classification extracted from the original boolean mess.
     * Same keywords as before, just organized -- behavior
     * identical to the previous code, easier to extend later.
     */
    private enum ChatIntent {
        CANCEL, CHANGE_PROFESSION, PLAY_MAGIC_GAME, MINE, FISH, FARM, GATHER, EXPLORE, COME, GREETING, SMALLTALK;

        static ChatIntent detect(String message, String npcName) {
            if (message.equals("tchau") || message.equals("adeus") || message.equals("sair")
                    || message.equals("cancelar") || message.contains("deixa pra lá")
                    || message.contains("deixa pra la") || message.contains("esquece")) {
                return CANCEL;
            }
            if (message.contains("vire ") || message.contains("seja ") || message.contains("trabalhe como ")) {
                return CHANGE_PROFESSION;
            }
            if (message.contains("jogar magic") || message.contains("play magic") || message.contains("akinator")) {
                return PLAY_MAGIC_GAME;
            }
            if (message.contains("mine") || message.contains("minerar")) return MINE;
            if (message.contains("fish") || message.contains("pescar")) return FISH;
            if (message.contains("farm") || message.contains("farmar") || message.contains("plantar")) return FARM;
            if (message.contains("gather") || message.contains("catar") || message.contains("coletar")) return GATHER;
            if (message.contains("explore") || message.contains("explorar")) return EXPLORE;
            if (message.contains("vem") || message.contains("come") || message.contains("aqui")) return COME;

            boolean isGreeting = message.contains("olá") || message.contains("ola") || message.contains("hello")
                    || message.contains("hi") || message.contains("oi") || message.contains("eae");
            boolean justCalledName = message.trim().equalsIgnoreCase(npcName) || message.trim().equalsIgnoreCase(npcName + "!");
            if (isGreeting || justCalledName) return GREETING;

            return SMALLTALK;
        }
    }
}

