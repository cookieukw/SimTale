package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class NPCInteractionPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;

    public NPCInteractionPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, null);
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("NPCInteraction/NPCInteraction.ui");
        
        commandBuilder.set("#NpcName.Text", npc.name);
        
        Mood currentMood = npc.getMood();
        commandBuilder.set("#NpcMood.Text", "Humor: " + currentMood.ptName);

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF0000"); 
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChatButton", new com.hypixel.hytale.server.core.ui.builder.EventData().append("button", "ChatButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JokeButton", new com.hypixel.hytale.server.core.ui.builder.EventData().append("button", "JokeButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FlirtButton", new com.hypixel.hytale.server.core.ui.builder.EventData().append("button", "FlirtButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InsultButton", new com.hypixel.hytale.server.core.ui.builder.EventData().append("button", "InsultButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiftButton", new com.hypixel.hytale.server.core.ui.builder.EventData().append("button", "GiftButton"), false);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> storeRef, Store<EntityStore> store, String eventData) {
        com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG UI EVENT]: payload = " + eventData);
        
        // Fechar a pagina imediatamente para parar o "loading" no cliente
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (eventData == null) return;

        if (eventData.contains("ChatButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FRIENDLY);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("JokeButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FUNNY);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("FlirtButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ROMANTIC);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("InsultButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.MEAN);
            playerRefComp.sendMessage(Message.raw(resp));
        } else if (eventData.contains("GiftButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.GIFT);
            playerRefComp.sendMessage(Message.raw(resp));
        }
    }
}
