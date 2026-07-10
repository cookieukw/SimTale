package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimNPCComponent;
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
import com.hypixel.hytale.server.core.entity.Frozen;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class NPCPregnancyPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;

    public NPCPregnancyPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (npc != null) {
            npc.isInteractingViaUI = true;
            if (npc.entityRef != null && npc.entityRef.isValid()) {
                store.ensureComponent(npc.entityRef, Frozen.getComponentType());
            }
        }

        commandBuilder.append("NPCPregnancy/NPCPregnancy.ui");
        commandBuilder.set("#BackButton.Visible", true);
        commandBuilder.set("#CloseButton.Visible", false);

        PregnancyComponent preg = npc.pregnancy;
        long currentTick = PregnancyDisplayUtil.getCurrentWorldTick();
        int totalChildren = npc.family.children != null ? npc.family.children.size() : 0;

        PregnancyDisplayUtil.populatePregnancyUI(commandBuilder, preg, currentTick, npc.name, totalChildren, false);

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", new EventData().append("button", "BackButton"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (eventData.contains("BackButton")) {
            player.getPageManager().openCustomPage(storeRef, store, new NPCInteractionPage(playerRefComp, player, npc));
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        super.onDismiss(playerRef, store);
        if (npc != null) {
            npc.isInteractingViaUI = false;
            if (npc.entityRef != null && npc.entityRef.isValid()) {
                store.tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
            }
        }
    }
}
