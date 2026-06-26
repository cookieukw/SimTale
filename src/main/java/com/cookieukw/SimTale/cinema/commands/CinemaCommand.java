package com.cookieukw.SimTale.cinema.commands;

import com.cookieukw.SimTale.cinema.CinemaUIPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command that opens the Cinema UI.
 */
public class CinemaCommand extends AbstractPlayerCommand {

    public CinemaCommand() {
        super("cinema", "Abre a interface do Cinema");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        playerRef.sendMessage(Message.raw("[Cinema] Abrindo a interface de video..."));

        CinemaUIPage page = new CinemaUIPage(playerRef, player);
        player.getPageManager().setPage(ref, store, page);
    }
}
