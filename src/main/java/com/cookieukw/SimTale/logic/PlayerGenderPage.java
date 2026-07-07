package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import javax.annotation.Nonnull;

public class PlayerGenderPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private final SimPlayerComponent simPlayer;

    public PlayerGenderPage(@Nonnull PlayerRef playerRefComp, Player player, SimPlayerComponent simPlayer) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.simPlayer = simPlayer;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> playerRef, @NonNullDecl UICommandBuilder commandBuilder, @NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl Store<EntityStore> store) {
        commandBuilder.append("PlayerGender/PlayerGender.ui");

        // Bind events for buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MaleButton", new EventData().append("button", "MaleButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FemaleButton", new EventData().append("button", "FemaleButton"), false);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> storeRef, @NonNullDecl Store<EntityStore> store, @NonNullDecl String eventData) {
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (eventData.contains("MaleButton")) {
            simPlayer.gender = Gender.MALE;
            SimPlayerPersistence.savePlayer(simPlayer);
            playerRefComp.sendMessage(Message.translation("simtale.gender.success.male"));
        } else if (eventData.contains("FemaleButton")) {
            simPlayer.gender = Gender.FEMALE;
            SimPlayerPersistence.savePlayer(simPlayer);
            playerRefComp.sendMessage(Message.translation("simtale.gender.success.female"));
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        super.onDismiss(playerRef, store);
        // Force player to choose a gender; do not allow closing without selection
        if (simPlayer.gender == null) {
            store.getExternalData().getWorld().execute(() -> player.getPageManager().openCustomPage(playerRef, store, new PlayerGenderPage(playerRefComp, player, simPlayer)));
        }
    }
}
