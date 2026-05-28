package com.cookieukw.SimTale;

import com.cookie.runecore.commands.TestUICommand;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.cookieukw.SimTale.systems.SimTaleTickSystem;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.cookieukw.SimTale.systems.SimTaleChatHandler;
import com.hypixel.hytale.event.EventPriority;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Main entrypoint for the SimTale plugin.
 */
public class SimTale extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static SimTale instance;

    public static ComponentType<EntityStore, SimNPCComponent> SIM_NPC_COMPONENT_TYPE;
    public static final List<SimNPCComponent> ACTIVE_NPCS = new ArrayList<>();

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
        LOGGER.atInfo().log("Setting up SimTale registries...");

        // Register data components
        // registerComponent(Class, Supplier) is the available method in
        // ComponentRegistryProxy
        SIM_NPC_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(SimNPCComponent.class,
                SimNPCComponent::new);

        // Register tick systems
        this.getEntityStoreRegistry().registerSystem(new SimTaleTickSystem());
        this.getEntityStoreRegistry().registerSystem(new PlumbobSystem());

        // Register event handlers
        this.getEventRegistry().register(EventPriority.NORMAL.getValue(), PlayerMouseButtonEvent.class, null,
                new SimTaleEventHandler());
        
        // MobsAndMates: Register chat handler
        this.getEventRegistry().register(EventPriority.NORMAL.getValue(), PlayerChatEvent.class, null,
                new SimTaleChatHandler());

        // Register commands
        this.getCommandRegistry()
                .registerCommand(new SimTaleCommand(this.getName(), this.getManifest().getVersion().toString()));

                 
        this.getCommandRegistry().registerCommand(new TestUICommand());
    }
}
