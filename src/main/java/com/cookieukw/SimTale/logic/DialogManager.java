package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import java.util.List;
import java.util.ArrayList;

public class DialogManager {

    public static void openMainDialog(PlayerRef playerRef, Player player, Ref<EntityStore> storeRef, Store<EntityStore> store, SimNPCComponent npc) {
        List<SimDialogOption> options = new ArrayList<>();
        
        options.add(new SimDialogOption(
            Message.translation("ui.button.chat"),
            () -> performAndShowResult(playerRef, player, storeRef, store, npc, InteractionType.FRIENDLY)
        ));

        options.add(new SimDialogOption(
            Message.translation("ui.button.joke"),
            () -> performAndShowResult(playerRef, player, storeRef, store, npc, InteractionType.FUNNY)
        ));

        options.add(new SimDialogOption(
            Message.translation("ui.button.flirt"),
            () -> performAndShowResult(playerRef, player, storeRef, store, npc, InteractionType.ROMANTIC)
        ));

        options.add(new SimDialogOption(
            Message.translation("ui.button.gift"),
            () -> performAndShowResult(playerRef, player, storeRef, store, npc, InteractionType.GIFT)
        ));

        options.add(new SimDialogOption(
            Message.translation("ui.button.insult"),
            () -> performAndShowResult(playerRef, player, storeRef, store, npc, InteractionType.MEAN)
        ));

        options.add(new SimDialogOption(
            Message.translation("general.npc.bye"),
            () -> player.getPageManager().setPage(storeRef, store, com.hypixel.hytale.protocol.packets.interface_.Page.None)
        ));

        SimDialogPage mainDialog = new SimDialogPage(
            playerRef,
            player,
            Message.translation("ui.dialog.main.greeting").param("npc", npc.name),
            options
        );

        player.getPageManager().openCustomPage(storeRef, store, mainDialog);
    }

    private static void performAndShowResult(PlayerRef playerRef, Player player, Ref<EntityStore> storeRef, Store<EntityStore> store, SimNPCComponent npc, InteractionType type) {
        Message responseMsg = InteractionManager.performInteraction(npc, playerRef.getUuid(), playerRef, type);

        List<SimDialogOption> options = new ArrayList<>();
        options.add(new SimDialogOption(
            Message.translation("general.npc.back"),
            () -> openMainDialog(playerRef, player, storeRef, store, npc)
        ));
        options.add(new SimDialogOption(
            Message.translation("general.npc.bye"),
            () -> player.getPageManager().setPage(storeRef, store, com.hypixel.hytale.protocol.packets.interface_.Page.None)
        ));

        SimDialogPage resultDialog = new SimDialogPage(playerRef, player, responseMsg, options);
        player.getPageManager().openCustomPage(storeRef, store, resultDialog);
    }
}
