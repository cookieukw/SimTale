package com.cookieukw.SimTale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * This class serves as the entrypoint for the SimTale plugin.
 */
public class SimTale extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static SimTale instance;

    public SimTale(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("SimTale v" + this.getManifest().getVersion().toString() + " is loading...");
    }

    public static SimTale getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up SimTale...");

        // Register commands
        this.getCommandRegistry()
                .registerCommand(new SimTaleCommand(this.getName(), this.getManifest().getVersion().toString()));
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("SimTale has been enabled!");
    }

    @Override
    public void shutdown() {
        LOGGER.atInfo().log("SimTale is shutting down...");
    }
}
