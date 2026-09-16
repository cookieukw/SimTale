package com.cookieukw.SimTale.pages;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public class TestPage extends BasicCustomUIPage {

    /**
     * Constructor.
     *
     * @param playerRef Reference to the player who will see this UI
     * @param message   The message to display
     */
    public TestPage(@Nonnull PlayerRef playerRef, String message) {
        /* BasicCustomUIPage constructor takes:
          - playerRef: Which player sees this UI
          - lifetime: When can the UI be closed (CanDismiss = ESC key works)
        */
        super(playerRef, CustomPageLifetime.CanDismiss);
        // Data passed to the page - will be displayed in the UI
    }


    @Override
    public void build(UICommandBuilder commandBuilder) {
          commandBuilder.append("NPCInteraction/NPCInteraction.ui");
       }
}