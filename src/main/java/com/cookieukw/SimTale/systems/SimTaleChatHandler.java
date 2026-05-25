package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.logic.JobType;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Handles chat interactions for controlling SimTale NPCs.
 */
public class SimTaleChatHandler implements Consumer<PlayerChatEvent> {

    private static final int CONVERSATION_TIMEOUT_TICKS = 200; // 10 seconds

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void accept(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        String message = event.getContent();

        if (sender == null || message == null || message.trim().isEmpty()) {
            return;
        }

        message = message.toLowerCase();

        // Use the tracking list to find NPCs instead of broken reflection
        SimNPCComponent targetNpc = null;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            // Check if currently focused on this player
            if (sender.getUuid().equals(npc.currentConversationPartner)) {
                targetNpc = npc;
                break;
            }

            // Otherwise check if name is in the chat
            if (npc.name != null && message.contains(npc.name.toLowerCase())) {
                targetNpc = npc;
                break;
            }
        }

        if (targetNpc != null) {
            World world = null;
            for (World w : Universe.get().getWorlds().values()) {
                world = w;
                break;
            }
            if (world != null) {
                handleNpcCommand(sender, message, targetNpc, world);
            }
        }
    }

    private void handleNpcCommand(PlayerRef sender, String message, SimNPCComponent npc, World world) {
        boolean isGreeting = message.contains("olá") || message.contains("ola") || message.contains("hello") || message.contains("hi");
        boolean wantMine = message.contains("mine") || message.contains("minerar");
        boolean wantFish = message.contains("fish") || message.contains("pescar");
        boolean wantFarm = message.contains("farm") || message.contains("farmar") || message.contains("plantar");
        boolean wantGather = message.contains("gather") || message.contains("catar") || message.contains("coletar");
        boolean wantExplore = message.contains("explore") || message.contains("explorar");
        boolean wantCome = message.contains("vem") || message.contains("come");

        if (npc.currentJob != JobType.NONE && !wantCome) {
            sendReply(sender, "<" + npc.name + "> Já estou ocupado!");
            return;
        }

        // Engage conversation
        npc.currentConversationPartner = sender.getUuid();
        long currentTick = world.getTick();
        npc.conversationTimeoutTick = currentTick + CONVERSATION_TIMEOUT_TICKS;

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
        } else if (isGreeting) {
            sendReply(sender, "<" + npc.name + "> O que você quer que eu faça?");
        } else {
            sendReply(sender, "<" + npc.name + "> Não entendi. Fale 'pescar', 'minerar', 'farmar', 'coletar', 'explorar'.");
        }
    }

    private void assignJob(PlayerRef sender, SimNPCComponent npc, long currentTick, JobType job) {
        npc.currentJob = job;
        npc.jobCompletionTick = currentTick + (job.getDurationSeconds() * 20L);
        npc.jobEmployer = sender.getUuid();
        sendReply(sender, "<" + npc.name + "> Certo, indo " + job.getPortugueseName() + "!");
    }

    private void sendReply(PlayerRef sender, String text) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(150); // 150ms delay to ensure player's chat message is printed first
            } catch (InterruptedException e) {
                // Ignore
            }
            sender.sendMessage(Message.raw(text));
        });
    }
}
