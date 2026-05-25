package com.cookieukw.SimTale.logic;

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
        
        String mood = "Feliz";
        if (npc.needs != null && npc.needs.isMiserable()) {
            mood = "Irritado";
        } else if (npc.needs != null && npc.needs.fun > 80 && npc.needs.social > 80) {
            mood = "Eufórico";
        }
        commandBuilder.set("#NpcMood.Text", "Humor: " + mood);

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
            InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.FRIENDLY);
            playerRefComp.sendMessage(Message.raw("Você bateu papo com " + npc.name + "!"));
            interactionHappened = true;
        } else if (eventData.contains("JokeButton")) {
            InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.FUNNY);
            playerRefComp.sendMessage(Message.raw("Você contou uma piada para " + npc.name + "!"));
            interactionHappened = true;
        } else if (eventData.contains("FlirtButton")) {
            InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.ROMANTIC);
            playerRefComp.sendMessage(Message.raw("Você paquerou o " + npc.name + "!"));
            interactionHappened = true;
        } else if (eventData.contains("InsultButton")) {
            InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.MEAN);
            playerRefComp.sendMessage(Message.raw("Você insultou o " + npc.name + "."));
            interactionHappened = true;
        } else if (eventData.contains("GiftButton")) {
            InteractionManager.performInteraction(npc, playerRefComp.getUuid(), InteractionType.FRIENDLY);
            playerRefComp.sendMessage(Message.raw("Você deu um presente pro " + npc.name + "!"));
            interactionHappened = true;
        }

        if (interactionHappened) {
            player.getPageManager().setPage(storeRef, store, null);
        }
    }
}
