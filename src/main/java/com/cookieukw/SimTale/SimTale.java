package com.cookieukw.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.cookieukw.SimTale.systems.SimTaleTickSystem;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.systems.ConstructionSystem;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.systems.RoutineAISystem;
import com.cookieukw.SimTale.systems.SimNPCSpawnSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.cookieukw.SimTale.systems.SimTaleChatHandler;
import com.hypixel.hytale.event.EventPriority;
import com.cookieukw.SimTale.systems.MoodAnimationSystem;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Main entrypoint for the SimTale plugin.
 */
public class SimTale extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static ComponentType<EntityStore, SimNPCComponent> SIM_NPC_COMPONENT_TYPE;
    public static ComponentType<EntityStore, RoutineAIComponent> ROUTINE_AI_COMPONENT_TYPE;
    public static ComponentType<EntityStore, ConstructionSiteComponent> CONSTRUCTION_COMPONENT_TYPE;
    public static final List<SimNPCComponent> ACTIVE_NPCS = new ArrayList<>();
    public static final List<ConstructionSiteComponent> ACTIVE_SITES = new ArrayList<>();

    public SimTale(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("SimTale v" + this.getManifest().getVersion().toString() + " is loading...");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up SimTale registries...");

        // Register data components
        // registerComponent(Class, Supplier) is the available method in
        // ComponentRegistryProxy
        SIM_NPC_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(SimNPCComponent.class,
                SimNPCComponent::new);
        ROUTINE_AI_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(RoutineAIComponent.class, 
                RoutineAIComponent::new);
        CONSTRUCTION_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(ConstructionSiteComponent.class, 
                "simtale:construction_site", ConstructionSiteComponent.CODEC);

        // Register tick systems
        this.getEntityStoreRegistry().registerSystem(new SimTaleTickSystem());
        this.getEntityStoreRegistry().registerSystem(new RoutineAISystem());
        this.getEntityStoreRegistry().registerSystem(new PlumbobSystem());
        this.getEntityStoreRegistry().registerSystem(new MoodAnimationSystem());
        this.getEntityStoreRegistry().registerSystem(new ConstructionSystem());
        this.getEntityStoreRegistry().registerSystem(new SimNPCSpawnSystem());

        // Register event handlers
        this.getEventRegistry().register(EventPriority.NORMAL.getValue(), PlayerMouseButtonEvent.class,
                new SimTaleEventHandler());
        
        // MobsAndMates: Register chat handler
        this.getEventRegistry().register(EventPriority.NORMAL.getValue(), PlayerChatEvent.class, "chat",
                new SimTaleChatHandler());

        // Register commands
        this.getCommandRegistry()
                .registerCommand(new SimTaleCommand());
        this.getCommandRegistry().registerCommand(new BuildCommand());
        this.getCommandRegistry().registerCommand(new SimDebugCommand());
    }
}
