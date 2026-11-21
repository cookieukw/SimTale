package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import javax.annotation.Nonnull;

/** Interactive in-game SimTale manual. */
public final class SimTaleGuidePage extends InteractiveCustomUIPage<String> {
    private static final int LAST_PAGE = 5;

    private final Player player;
    private int page;
    private boolean playedOpenSound;

    public SimTaleGuidePage(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        super(playerRef, CustomPageLifetime.CanDismiss,
                BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("SimTaleGuide.ui");
        String prefix = "ui.guide.page." + page + ".";
        cmd.set("#LeftTitle.TextSpans", Message.translation(prefix + "left_title"));
        cmd.set("#LeftText.TextSpans", Message.translation(prefix + "left_text"));
        cmd.set("#RightTitle.TextSpans", Message.translation(prefix + "right_title"));
        cmd.set("#RightText.TextSpans", Message.translation(prefix + "right_text"));
        if (!playedOpenSound) {
            playedOpenSound = true;
            playSound("SimTale/GuideBookOpen", playerRef, store);
        }
        cmd.set("#Previous.Visible", page > 0);
        cmd.set("#Next.Visible", page < LAST_PAGE);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Previous",
                new EventData().append("action", "previous"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Next",
                new EventData().append("action", "next"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#Close",
                new EventData().append("action", "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> playerRef,
                                @Nonnull Store<EntityStore> store, @Nonnull String data) {
        if (data.contains("close")) {
            playSound("SimTale/GuideBookClose", playerRef, store);
            player.getPageManager().setPage(playerRef, store, Page.None);
            return;
        }
        if (data.contains("previous")) { page = Math.max(0, page - 1); playSound("SimTale/GuidePageTurn", playerRef, store); }
        if (data.contains("next")) { page = Math.min(LAST_PAGE, page + 1); playSound("SimTale/GuidePageTurn", playerRef, store); }
        rebuild();
    }

    private void playSound(String id, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;
            int index = SoundEvent.getAssetMap().getIndex(id);
            if (index >= 0) SoundUtil.playSoundEvent3dToPlayer(ref, index, SoundCategory.UI,
                    transform.getPosition(), store);
        } catch (RuntimeException ignored) {
            // Audio must never prevent the guide page from opening or closing.
        }
    }
}
