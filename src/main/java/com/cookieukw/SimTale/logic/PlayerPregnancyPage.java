package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleState;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class PlayerPregnancyPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private final SimPlayerComponent playerComp;

    public PlayerPregnancyPage(@Nonnull PlayerRef playerRefComp, Player player, SimPlayerComponent playerComp) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.playerComp = playerComp;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("NPCPregnancy/NPCPregnancy.ui");
        commandBuilder.set("#BackButton.Visible", false);
        commandBuilder.set("#CloseButton.Visible", true);

        PregnancyComponent preg = playerComp.pregnancy;
        long currentTick = PregnancyDisplayUtil.getCurrentWorldTick();
        int totalChildren = LifecycleState.findChildrenOfMother(playerRefComp.getUuid()).size();

        PregnancyDisplayUtil.populatePregnancyUI(commandBuilder, preg, currentTick, playerRefComp.getUsername(), totalChildren, true);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData().append("button", "CloseButton"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        player.getPageManager().setPage(storeRef, store, Page.None);
    }
}
