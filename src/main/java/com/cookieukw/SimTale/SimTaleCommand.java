package com.cookieukw.SimTale;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public SimTaleCommand(String pluginName, String pluginVersion) {
        super("simtale", "Commands for SimTale.");
        this.setPermissionGroup(GameMode.Adventure);
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String input = ctx.getInputString();
        if (input != null && input.toLowerCase().contains("spawn")) {
            spawnNPC(ctx, "SimNPC");
            return;
        }

        ctx.sendMessage(
                Message.raw("SimTale v" + pluginVersion + " is active. Use /simtale spawn <name> to create an NPC."));
    }

    private void spawnNPC(CommandContext ctx, String name) {
        Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
        if (playerRef == null) {
            ctx.sendMessage(Message.raw("This command can only be used by players."));
            return;
        }

        ctx.sendMessage(Message.raw("Spawning NPC '" + name + "'... (Integration in progress)"));
    }
}
