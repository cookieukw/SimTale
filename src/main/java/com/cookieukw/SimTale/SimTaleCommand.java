package com.cookieukw.SimTale;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * A simple command for the SimTale plugin.
 */
public class SimTaleCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public SimTaleCommand(String pluginName, String pluginVersion) {
        // Command name is "simtale", description includes plugin info
        super("simtale", "Prints a test message from the " + pluginName + " plugin.");

        // Allows the command to be used by anyone, not just OP
        this.setPermissionGroup(GameMode.Adventure);

        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Hello from " + pluginName + " v" + pluginVersion + "! SimTale is running."));
    }
}
