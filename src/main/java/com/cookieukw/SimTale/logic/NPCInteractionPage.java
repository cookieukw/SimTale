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
        commandBuilder.append("Custom/NPCInteraction/NPCInteraction.ui");
        
        commandBuilder.set("#NpcName.Text", npc.name);
        
        Mood currentMood = npc.getMood();
        commandBuilder.set("#NpcMood.Text", "Humor: " + currentMood.ptName);

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF0000"); 
            commandBuilder.set("#NpcName.Classes", "ShakeAnimation");
        }

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChatButton");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JokeButton");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FlirtButton");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InsultButton");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiftButton");
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> storeRef, Store<EntityStore> store, String eventData) {
        if (eventData == null) return;
        
        boolean interactionHappened = false;

        if (eventData.contains("ChatButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.FRIENDLY);
            playerRefComp.sendMessage(Message.raw(resp));
            interactionHappened = true;
        } else if (eventData.contains("JokeButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.FUNNY);
            playerRefComp.sendMessage(Message.raw(resp));
            interactionHappened = true;
        } else if (eventData.contains("FlirtButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.ROMANTIC);
            playerRefComp.sendMessage(Message.raw(resp));
            interactionHappened = true;
        } else if (eventData.contains("InsultButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.MEAN);
            playerRefComp.sendMessage(Message.raw(resp));
            interactionHappened = true;
        } else if (eventData.contains("GiftButton")) {
            String resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.GIFT);
            playerRefComp.sendMessage(Message.raw(resp));
            interactionHappened = true;
        }

        if (interactionHappened) {
            player.getPageManager().setPage(storeRef, store, null);
        }
    }
}
