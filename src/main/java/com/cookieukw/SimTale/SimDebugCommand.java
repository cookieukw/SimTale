package com.cookieukw.SimTale;

import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.SimDebugPage;
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
 * Debug command to inspect and control NPC behaviors.
 * Usage: /simdebug
 */
public class SimDebugCommand extends AbstractPlayerCommand {

    public SimDebugCommand() {
        super("simdebug", "Opens the SimTale debug panel");
        this.setPermissionGroups("Adventure");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // Auto-reassemble NPCs if needed
        if (SimTale.ACTIVE_NPCS.isEmpty()) {
            ctx.sendMessage(Message.raw("[SimDebug] Reloading NPCs from database..."));
            SimNPCPersistence.reassembleActiveNPCs(world);
        }

        if (SimTale.ACTIVE_NPCS.isEmpty()) {
            ctx.sendMessage(Message.raw("[SimDebug] No active NPCs found. Spawn one with /simtale spawn"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        /* `assert` is stripped at runtime unless the JVM is started with -ea, so this was
        effectively no check at all — a null here just became an NPE inside the command.
        */
        if (player == null) {
            ctx.sendMessage(Message.raw("[SimDebug] Player component unavailable."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new SimDebugPage(playerRef, player));
        ctx.sendMessage(Message.raw("[SimDebug] Panel opened! (" + SimTale.ACTIVE_NPCS.size() + " active NPCs)"));
    }
}
