package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import javax.annotation.Nonnull;

public class PlayerGenderPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private final SimPlayerComponent simPlayer;

    public PlayerGenderPage(@Nonnull PlayerRef playerRefComp, Player player, SimPlayerComponent simPlayer) {
        super(playerRefComp, CustomPageLifetime.CantClose, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.simPlayer = simPlayer;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("PlayerGender/PlayerGender.ui");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MaleButton", new EventData().append("button", "MaleButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FemaleButton", new EventData().append("button", "FemaleButton"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        if (eventData.contains("MaleButton")) {
            simPlayer.gender = Gender.MALE;
            SimPlayerPersistence.savePlayer(simPlayer);
            player.getPageManager().setPage(storeRef, store, Page.None);
            playerRefComp.sendMessage(Message.translation("simtale.gender.success.male"));
        } else if (eventData.contains("FemaleButton")) {
            simPlayer.gender = Gender.FEMALE;
            SimPlayerPersistence.savePlayer(simPlayer);
            player.getPageManager().setPage(storeRef, store, Page.None);
            playerRefComp.sendMessage(Message.translation("simtale.gender.success.female"));
        }
    }
}