package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;

import javax.annotation.Nonnull;
import java.util.List;

public class SimDialogPage extends InteractiveCustomUIPage<String> {

    private final Message text;
    private final List<SimDialogOption> options;
    private final Player player;

    public SimDialogPage(@Nonnull PlayerRef playerRef, Player player, Message text, List<SimDialogOption> options) {
        super(playerRef, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.text = text;
        this.options = options;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> storeRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        commandBuilder.append("SimDialogPage/SimDialogPage.ui");
        commandBuilder.set("#DialogText.TextSpans", text);

        for (int i = 0; i < options.size(); i++) {
            SimDialogOption option = options.get(i);
            String optionId = "#Option_" + i;
            
            commandBuilder.set(optionId + ".Visible", true);
            commandBuilder.set("#OptionText_" + i + ".TextSpans", option.text);
            
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, optionId, new EventData().append("option", String.valueOf(i)), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        if (eventData.contains("option")) {
            // Extract the option index. The eventData looks like {"option":"0"} roughly.
            // A simple parsing for this specific case:
            try {
                String indexStr = eventData.split("\"option\":\"")[1].split("\"")[0];
                int index = Integer.parseInt(indexStr);
                
                if (index >= 0 && index < options.size()) {
                    SimDialogOption selected = options.get(index);
                    if (selected.action != null) {
                        selected.action.run();
                    } else {
                        player.getPageManager().setPage(storeRef, store, Page.None);
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
    }
}
