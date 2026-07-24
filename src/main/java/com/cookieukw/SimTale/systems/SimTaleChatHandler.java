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
 * 
 * Conversations are context-aware: the NPC responds differently based on
 * the friendship tier AND what the player actually said (compliment, question,
 * joke, job request, etc). This makes dialogue feel alive and connected
 * rather than a sequence of random phrases.
 */
public class SimTaleChatHandler implements Consumer<PlayerChatEvent> {

    private static final int CONVERSATION_TIMEOUT_TICKS = 1200; // 20 seconds (was 10s)

    @Nonnull
    public static String getRandomVariant(String baseKey, int variants) {
        return baseKey + "." + (1 + (int) (Math.random() * variants));
    }

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
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.busy_multiplayer", tier, 3)).param("name", targetNpc.name));
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
        String cleanMessage = message.toLowerCase().replaceAll("[.,!?;:]", " ").replaceAll("\\s+", " ").trim();

        SimNPCComponent approximateNpc = null;
        int bestDistance = Integer.MAX_VALUE;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (sender.getUuid().equals(npc.currentConversationPartner)) {
                return npc;
            }

            if (npc.name == null) continue;

            // Clean NPC name in same way
            String cleanNpcName = npc.name.toLowerCase().replaceAll("[.,!?;:]", " ").replaceAll("\\s+", " ").trim();

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
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.cancel", tier, 3)).param("name", npc.name));
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
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.busy_job", tier, 3))
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
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.come", tier, 3)).param("name", npc.name));
                npc.currentJob = JobType.NONE;
                npc.currentConversationPartner = null;
            }
            case GREETING -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.greeting", tier, 5)).param("name", npc.name));
            }
            // --- NEW CONVERSATIONAL INTENTS ---
            case COMPLIMENT -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.compliment", tier, 5)).param("name", npc.name));
            }
            case PERSONAL_QUESTION -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.personal_question", tier, 5)).param("name", npc.name)
                    .param("prof_name", npc.profession != null ? npc.profession.ptName : "nada"));
            }
            case SELF_TALK -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.self_talk", tier, 5)).param("name", npc.name));
            }
            case HUMOR -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.humor", tier, 5)).param("name", npc.name));
            }
            case GRATITUDE -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.gratitude", tier, 5)).param("name", npc.name));
            }
            case INSULT_CHAT -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.insult_chat", tier, 5)).param("name", npc.name));
            }
            case HELP_REQUEST -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.help_request", tier, 3)).param("name", npc.name)
                    .param("prof_name", npc.profession != null ? npc.profession.ptName : "nada"));
            }
            case WHAT_CAN_YOU_DO -> {
                openConversation(npc, sender, currentTick);
                String jobList = npc.profession != null ? npc.profession.getJobListPt() : "nada no momento";
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.what_can_you_do", tier, 3))
                    .param("name", npc.name).param("prof_name", npc.profession != null ? npc.profession.ptName : "Desempregado")
                    .param("job_list", jobList));
            }
            default -> {
                openConversation(npc, sender, currentTick);
                sendReply(sender, Message.translation(getRandomVariant("npc-interactions.smalltalk", tier, 10)).param("name", npc.name));
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
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.start", tier, 3)).param("name", npc.name));
            sendNextMagicQuestion(sender, npc, tier);
        } else {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.reject", tier, 3)).param("name", npc.name));
        }
    }

    private void handleMagicGameCommand(PlayerRef sender, String message, SimNPCComponent npc, World world, FriendshipTier tier) {
        npc.conversationTimeoutTick = world.getTick() + CONVERSATION_TIMEOUT_TICKS * 5; // Refresh timeout

        if (message.contains("sair") || message.contains("stop") || message.contains("quit") || message.contains("parar") || message.contains("chega")) {
            npc.activeMagicGame = null;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.cancel", tier, 3)).param("name", npc.name));
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
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.invalid", tier, 3)).param("name", npc.name));
            return;
        }

        MagicEngine engine = npc.activeMagicGame;
        engine.answerQuestion(engine.getBestQuestion(), weight);

        Animal victoryAnimal = engine.checkVictory();
        if (victoryAnimal != null) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.win", tier, 3)).param("name", npc.name).param("animal_name", victoryAnimal.getName().getPt()));
            npc.activeMagicGame = null;
            return;
        }

        sendNextMagicQuestion(sender, npc, tier);
    }

    private void sendNextMagicQuestion(PlayerRef sender, SimNPCComponent npc, FriendshipTier tier) {
        MagicEngine engine = npc.activeMagicGame;
        String nextQuestionId = engine.getBestQuestion();

        if (nextQuestionId == null) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.lose", tier, 3)).param("name", npc.name));
            npc.activeMagicGame = null;
            return;
        }

        Question question = engine.getQuestions().stream()
                .filter(q -> q.getId().equals(nextQuestionId))
                .findFirst()
                .orElse(null);

        if (question != null) {
            int qNum = engine.getAskedQuestions().size() + 1;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.magic.question", tier, 3))
                    .param("name", npc.name).param("num", qNum).param("question", question.getText().getPt()));
        }
    }

    private void handleProfessionChange(PlayerRef sender, String message, SimNPCComponent npc, FriendshipTier tier) {
        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 20) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.prof.reject", tier, 3)).param("name", npc.name));
            return;
        }

        Profession newProf = getProfession(message);

        if (newProf != null) {
            npc.profession = newProf;
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.prof.accept", tier, 3)).param("name", npc.name).param("prof_name", newProf.ptName));
            npc.currentConversationPartner = null;
        } else {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.prof.invalid", tier, 3)).param("name", npc.name));
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
            // Find which profession CAN do this job, and suggest it
            String neededProf = findProfessionForJob(job);
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.job.wrong_prof", tier, 3))
                    .param("name", npc.name).param("prof_name", npc.profession.ptName)
                    .param("job_name", job.getPortugueseName()).param("needed_prof", neededProf));
            return;
        }

        int affinity = npc.getRelationship(sender.getUuid()).friendship;
        if (affinity <= 10) {
            sendReply(sender, Message.translation(getRandomVariant("npc-interactions.job.reject", tier, 3)).param("name", npc.name));
            return;
        }

        npc.currentJob = job;
        npc.jobDepartureTick = currentTick + 100L; // 5 seconds preparation phase
        npc.jobCompletionTick = npc.jobDepartureTick + (job.getDurationSeconds() * 20L);
        npc.jobEmployer = sender.getUuid();
        npc.isAway = false;
        npc.currentConversationPartner = null; // Unlock conversation now that intent is clear
        sendReply(sender, Message.translation(getRandomVariant("npc-interactions.job.accept", tier, 3)).param("name", npc.name).param("job_name", job.getPortugueseName()));
    }

    /** Returns the Portuguese name of a profession that can do the given job. */
    @Nonnull
    private String findProfessionForJob(JobType job) {
        for (Profession p : Profession.values()) {
            if (p.canDoJob(job)) {
                return p.ptName;
            }
        }
        return "outra profissão";
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
     * Intent classification with word-boundary-aware matching.
     * <p>
     * Key improvement: instead of simple contains(), we use "command matching" --
     * a message is only classified as a job/action intent if the keyword appears
     * as a standalone command or at the START of the message (not buried in a longer
     * sentence like "mas eu disse minerar"). This prevents false positives.
     * <p>
     * New conversational intents: COMPLIMENT, PERSONAL_QUESTION, SELF_TALK,
     * HUMOR, GRATITUDE, INSULT_CHAT, HELP_REQUEST, WHAT_CAN_YOU_DO.
     */
    private enum ChatIntent {
        CANCEL, CHANGE_PROFESSION, PLAY_MAGIC_GAME,
        MINE, FISH, FARM, GATHER, EXPLORE, COME,
        GREETING, COMPLIMENT, PERSONAL_QUESTION, SELF_TALK, HUMOR,
        GRATITUDE, INSULT_CHAT, HELP_REQUEST, WHAT_CAN_YOU_DO,
        SMALLTALK;

        /**
         * Checks if the message IS a command (the whole message or starts with the command).
         * This prevents "mas eu disse minerar" from matching MINE.
         */
        private static boolean isCommand(String message, String... keywords) {
            String trimmed = message.trim();
            for (String kw : keywords) {
                // Exact match: "minerar"
                if (trimmed.equals(kw)) return true;
                // Starts with command + space: "minerar agora" or "vai minerar"
                if (trimmed.startsWith(kw + " ") || trimmed.startsWith(kw + "!") || trimmed.startsWith(kw + ",")) return true;
                // Ends with command: "pode minerar" or "vai lá minerar"
                if (trimmed.endsWith(" " + kw) || trimmed.endsWith(" " + kw + "!") || trimmed.endsWith(" " + kw + "?")) return true;
            }
            return false;
        }

        /**
         * Checks if the message contains any of the given phrases.
         * Used for conversational intents where context doesn't matter as much.
         */
        private static boolean hasPhrase(String message, String... phrases) {
            for (String phrase : phrases) {
                if (message.contains(phrase)) return true;
            }
            return false;
        }

        /**
         * Checks if any of the keywords appear as standalone words in the message.
         */
        private static boolean hasWord(String message, String... words) {
            String[] messageWords = message.trim().split("\\s+");
            for (String target : words) {
                for (String mWord : messageWords) {
                    if (mWord.equals(target)) return true;
                }
            }
            return false;
        }

        static ChatIntent detect(String message, String npcName) {
            // --- CANCEL ---
            if (message.equals("tchau") || message.equals("adeus") || message.equals("sair")
                    || message.equals("cancelar") || hasPhrase(message, "deixa pra lá", "deixa pra la", "esquece", "falou")) {
                return CANCEL;
            }

            // --- PROFESSION CHANGE (must be before job commands) ---
            if (hasPhrase(message, "vire ", "seja ", "trabalhe como ", "mude pra ", "muda pra ", "vira ")) {
                return CHANGE_PROFESSION;
            }

            // --- MAGIC GAME ---
            if (hasPhrase(message, "jogar magic", "play magic", "akinator", "jogo do animal", "adivinha")) {
                return PLAY_MAGIC_GAME;
            }

            // --- JOB COMMANDS (word-boundary aware to avoid false positives) ---
            if (isCommand(message, "minerar", "mine", "vai minerar", "pode minerar", "va minerar", "vá minerar")) return MINE;
            if (isCommand(message, "pescar", "fish", "vai pescar", "pode pescar", "va pescar", "vá pescar")) return FISH;
            if (isCommand(message, "farmar", "farm", "plantar", "vai farmar", "pode farmar", "vai plantar")) return FARM;
            if (isCommand(message, "coletar", "gather", "catar", "vai coletar", "pode coletar")) return GATHER;
            if (isCommand(message, "explorar", "explore", "vai explorar", "pode explorar")) return EXPLORE;

            // --- COME HERE ---
            if (isCommand(message, "vem", "vem cá", "vem aqui", "vem pra cá", "come here")
                    || message.equals("aqui") || message.equals("volta")) {
                return COME;
            }

            // --- WHAT CAN YOU DO (before general conversation) ---
            if (hasPhrase(message, "o que você sabe", "o que voce sabe", "o que pode fazer",
                    "o que vc faz", "o que tu faz", "que trabalho", "qual seu trabalho",
                    "qual é seu trabalho", "qual sua profissão", "qual sua profissao",
                    "me ajuda com o que", "como posso te usar", "o que sabe fazer")) {
                return WHAT_CAN_YOU_DO;
            }

            // --- HELP REQUEST ---
            if (hasPhrase(message, "me ajuda", "ajuda eu", "preciso de ajuda", "pode me ajudar",
                    "ajuda aí", "ajuda ai", "help me", "uma mão", "uma mao", "socorro")) {
                return HELP_REQUEST;
            }

            // --- COMPLIMENT ---
            if (hasPhrase(message, "bonit", "lind", "gat", "massa", "top", "fod", "incrível", "incrivel",
                    "maravilhos", "demais", "show", "gostei de você", "gostei de vc",
                    "te adoro", "te admiro", "amo você", "amo voce", "te amo",
                    "você é legal", "voce é legal", "vc é legal", "gosto de vc",
                    "gosto de você", "gosto de voce")) {
                return COMPLIMENT;
            }

            // --- INSULT ---
            if (hasPhrase(message, "fei", "chato", "irritante", "idiota", "burro", "burra",
                    "odeio", "nojent", "ridícul", "ridicul", "inútil", "inutil",
                    "vai embora", "sai daqui", "some", "cala a boca", "cala boca",
                    "ninguém te quer", "ninguem te quer", "lixo", "ruim")) {
                return INSULT_CHAT;
            }

            // --- GRATITUDE ---
            if (hasPhrase(message, "obrigad", "valeu", "brigad", "thanks", "thank you",
                    "muito obrigad", "vlw", "tmj")) {
                return GRATITUDE;
            }

            // --- HUMOR / JOKE ---
            if (hasPhrase(message, "piada", "conta uma", "faz rir", "joke", "engraçad", "engracad",
                    "kk", "haha", "kkk", "risos", "lol", "hehe")) {
                return HUMOR;
            }

            // --- PERSONAL QUESTION ---
            if (hasPhrase(message, "como você tá", "como voce ta", "como vc ta", "tudo bem",
                    "como vai", "ta bem", "tá bem", "você gosta de", "voce gosta de",
                    "vc gosta de", "qual seu nome", "de onde", "onde mora",
                    "o que acha", "você é de", "vc é de", "como se sente",
                    "tá feliz", "ta feliz", "tá triste", "ta triste",
                    "o que aconteceu", "qual seu hobby", "o que você gosta")) {
                return PERSONAL_QUESTION;
            }

            // --- SELF TALK (player talking about themselves) ---
            if (hasPhrase(message, "eu tô", "eu to ", "eu sou", "eu fui", "eu quero",
                    "minha vida", "meu dia", "tô cansad", "to cansad",
                    "tô com fome", "to com fome", "tô triste", "to triste",
                    "tô feliz", "to feliz", "tô entediad", "to entediad")) {
                return SELF_TALK;
            }

            // --- GREETING ---
            boolean isGreeting = hasWord(message, "olá", "ola", "hello", "hi", "oi", "eae", "eai",
                    "fala", "salve", "bom dia", "boa tarde", "boa noite", "e aí", "e ai");
            boolean justCalledName = message.trim().equalsIgnoreCase(npcName) || message.trim().equalsIgnoreCase(npcName + "!");
            if (isGreeting || justCalledName) return GREETING;

            return SMALLTALK;
        }
    }
}
