package com.cookieukw.SimTale;

import com.cookieukw.SimTale.ai.AiConfig;
import com.cookieukw.SimTale.ai.AiConfigManager;
import com.cookieukw.SimTale.ai.NpcAiManager;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.providers.GeminiProvider;
import com.cookieukw.SimTale.ai.providers.OpenAIProvider;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.logic.SimTaleUseNPCInteraction;
import com.cookieukw.SimTale.systems.BabyCareTickSystem;
import com.cookieukw.SimTale.systems.ConstructionSystem;
import com.cookieukw.SimTale.systems.MoodAnimationSystem;
import com.cookieukw.SimTale.systems.PlayerJoinHandler;
import com.cookieukw.SimTale.systems.PlayerPregnancyTickSystem;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.systems.PregnancyTickSystem;
import com.cookieukw.SimTale.systems.RoutineAISystem;
import com.cookieukw.SimTale.systems.SimNPCSpawnSystem;
import com.cookieukw.SimTale.systems.SimTaleChatHandler;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.cookieukw.SimTale.systems.SimTaleTickSystem;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.BedEntityRegistrySystem;
import com.cookieukw.SimTale.systems.BedBlockEventSystem;
import com.cookieukw.SimTale.systems.BedPlaceBlockEventSystem;
import com.cookieukw.SimTale.systems.GrowthTickSystem;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.npc.interactions.UseNPCInteraction;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entrypoint for the SimTale plugin.
 */
public class SimTale extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static ComponentType<EntityStore, SimNPCComponent> SIM_NPC_COMPONENT_TYPE;
    public static ComponentType<EntityStore, RoutineAIComponent> ROUTINE_AI_COMPONENT_TYPE;
    public static ComponentType<EntityStore, ConstructionSiteComponent> CONSTRUCTION_COMPONENT_TYPE;
    public static ComponentType<EntityStore, SimPlayerComponent> SIM_PLAYER_COMPONENT_TYPE;
    public static final List<SimNPCComponent> ACTIVE_NPCS = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final List<ConstructionSiteComponent> ACTIVE_SITES = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static boolean debugForceSpawning = false;
    public static NpcAiManager aiManager;

    public SimTale(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("SimTale v" + this.getManifest().getVersion().toString() + " is loading...");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up SimTale registries...");

        // Load local JSON configuration
        AiConfigManager.load();
        AiConfig config = AiConfigManager.getConfig();

        // Initialize AI manager
        aiManager = new NpcAiManager();

        // 1. Setup Gemini
        String geminiKey = !config.geminiKey.isBlank() ? config.geminiKey : System.getenv("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank()) {
            String model = !config.customModel.isBlank() ? config.customModel : "gemini-2.5-flash";
            aiManager.register(new GeminiProvider(geminiKey, model));
            LOGGER.atInfo().log("Registered Gemini AI Provider.");
        }

        // 2. Setup OpenAI
        String openAiKey = !config.openaiKey.isBlank() ? config.openaiKey : System.getenv("OPENAI_API_KEY");
        if (openAiKey != null && !openAiKey.isBlank()) {
            String model = !config.customModel.isBlank() ? config.customModel : "gpt-4o-mini";
            String url = !config.customUrl.isBlank() ? config.customUrl : "https://api.openai.com";
            aiManager.register(new OpenAIProvider(url, openAiKey, model));
            LOGGER.atInfo().log("Registered OpenAI Provider.");
        }

        // 3. Setup OpenRouter (Fallback to custom if configured, else default)
        String openRouterKey = !config.openrouterKey.isBlank() ? config.openrouterKey : System.getenv("OPENROUTER_API_KEY");
        if (openRouterKey != null && !openRouterKey.isBlank()) {
            String model = !config.customModel.isBlank() ? config.customModel : "google/gemini-2.5-flash";
            String url = !config.customUrl.isBlank() ? config.customUrl : "https://openrouter.ai";
            aiManager.register(new OpenAIProvider(url, openRouterKey, model));
            LOGGER.atInfo().log("Registered OpenRouter AI Provider.");
        }

        // Set default active provider according to config selection
        if (config.provider != null && !config.provider.isBlank()) {
            aiManager.setDefaultProvider(config.provider);
        }

        // Register data components
        // registerComponent(Class, Supplier) is the available method in
        // ComponentRegistryProxy
        SIM_NPC_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(SimNPCComponent.class,
                SimNPCComponent::new);
        ROUTINE_AI_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(RoutineAIComponent.class, 
                RoutineAIComponent::new);
        CONSTRUCTION_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(ConstructionSiteComponent.class, 
                "simtale:construction_site", ConstructionSiteComponent.CODEC);
        SIM_PLAYER_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(SimPlayerComponent.class,
                SimPlayerComponent::new);

        // Register tick systems
        this.getEntityStoreRegistry().registerSystem(new SimTaleTickSystem());
        this.getEntityStoreRegistry().registerSystem(new RoutineAISystem());
        this.getEntityStoreRegistry().registerSystem(new BedEntityRegistrySystem());
        this.getEntityStoreRegistry().registerSystem(new BedBlockEventSystem());
        this.getEntityStoreRegistry().registerSystem(new BedPlaceBlockEventSystem());
        this.getEntityStoreRegistry().registerSystem(new PlumbobSystem());
        this.getEntityStoreRegistry().registerSystem(new MoodAnimationSystem());
        this.getEntityStoreRegistry().registerSystem(new ConstructionSystem());
        this.getEntityStoreRegistry().registerSystem(new SimNPCSpawnSystem());
        this.getEntityStoreRegistry().registerSystem(new PregnancyTickSystem());
        this.getEntityStoreRegistry().registerSystem(new PlayerPregnancyTickSystem());
        this.getEntityStoreRegistry().registerSystem(new BabyCareTickSystem());
        this.getEntityStoreRegistry().registerSystem(new GrowthTickSystem());

        // Register event handlers
        this.getEventRegistry().register(EventPriority.NORMAL.getValue(), PlayerMouseButtonEvent.class,
                new SimTaleEventHandler());
        
        // MobsAndMates: Register chat handler
        this.getEventRegistry().registerGlobal(EventPriority.NORMAL.getValue(), PlayerChatEvent.class,
                new SimTaleChatHandler());
        
        // SimTale: Register player join handler
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, new PlayerJoinHandler());

        // Register commands
        this.getCommandRegistry()
                .registerCommand(new SimTaleCommand());
        this.getCommandRegistry().registerCommand(new BuildCommand());
        this.getCommandRegistry().registerCommand(new SimDebugCommand());

        // Register custom UseNPCInteraction to hook interactions
        Interaction.getAssetStore().loadAssets(DefaultAssetMap.DEFAULT_PACK_KEY, List.of(
            new SimTaleUseNPCInteraction(UseNPCInteraction.DEFAULT_ID)
        ));
    }
}
