package com.cookieukw.SimTale;

import com.cookieukw.SimTale.systems.SimTaleMarkerProvider;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.cookieukw.SimTale.ai.AiConfig;
import com.cookieukw.SimTale.ai.AiConfigManager;
import com.cookieukw.SimTale.ai.NpcAiManager;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.providers.GeminiProvider;
import com.cookieukw.SimTale.ai.providers.OpenAIProvider;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.logic.SimTaleCheckPregnancyInteraction;
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
import com.cookieukw.SimTale.systems.BedEntityRegistrySystem;
import com.cookieukw.SimTale.systems.BedBlockEventSystem;
import com.cookieukw.SimTale.systems.BedPlaceBlockEventSystem;
import com.cookieukw.SimTale.systems.GrowthTickSystem;
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
import com.hypixel.hytale.server.npc.interactions.UseNPCInteraction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;


/**
 * Main entrypoint for the SimTale plugin.
 */
public class SimTale extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static ComponentType<EntityStore, SimNPCComponent> SIM_NPC_COMPONENT_TYPE;
    public static ComponentType<EntityStore, RoutineAIComponent> ROUTINE_AI_COMPONENT_TYPE;
    public static ComponentType<EntityStore, ConstructionSiteComponent> CONSTRUCTION_COMPONENT_TYPE;
    public static ComponentType<EntityStore, SimPlayerComponent> SIM_PLAYER_COMPONENT_TYPE;
    /**
     * Iteration view of the tracked NPCs. Mutate it only through {@link #trackNpc},
     * {@link #untrackNpc}, {@link #untrackNpcById} and {@link #clearActiveNpcs} — direct
     * add/remove desynchronizes {@link #NPCS_BY_ID}.
     */
    public static final List<SimNPCComponent> ACTIVE_NPCS = new CopyOnWriteArrayList<>();

    /**
     * UUID index over {@link #ACTIVE_NPCS}. Several tick systems needed "find the NPC with
     * this id" once per NPC per tick, which was a linear scan of the whole roster — O(n²)
     * every tick. This makes those lookups O(1).
     */
    private static final Map<UUID, SimNPCComponent> NPCS_BY_ID =
            new ConcurrentHashMap<>();

    public static final List<ConstructionSiteComponent> ACTIVE_SITES = new CopyOnWriteArrayList<>();
    public static boolean debugForceSpawning = false;
    public static NpcAiManager aiManager;

    /**
     * Starts tracking an NPC, replacing any previously tracked instance with the same id.
     * Replaces the {@code removeIf(...)} + {@code add(...)} pair that was copy-pasted across
     * four call sites.
     */
    public static void trackNpc(SimNPCComponent npc) {
        if (npc == null) return;
        if (npc.entityId != null) {
            SimNPCComponent previous = NPCS_BY_ID.put(npc.entityId, npc);
            if (previous != null && previous != npc) {
                ACTIVE_NPCS.remove(previous);
            }
        }
        if (!ACTIVE_NPCS.contains(npc)) {
            ACTIVE_NPCS.add(npc);
        }
    }

    public static void untrackNpc(SimNPCComponent npc) {
        if (npc == null) return;
        ACTIVE_NPCS.remove(npc);
        if (npc.entityId != null) {
            NPCS_BY_ID.remove(npc.entityId, npc);
        }
    }

    public static void untrackNpcById(UUID entityId) {
        if (entityId == null) return;
        SimNPCComponent existing = NPCS_BY_ID.remove(entityId);
        if (existing != null) {
            ACTIVE_NPCS.remove(existing);
        }
    }

    /** O(1) replacement for scanning {@link #ACTIVE_NPCS} looking for a matching entityId. */
    public static SimNPCComponent findNpc(UUID entityId) {
        return entityId != null ? NPCS_BY_ID.get(entityId) : null;
    }

    public static void clearActiveNpcs() {
        ACTIVE_NPCS.clear();
        NPCS_BY_ID.clear();
    }

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

        // customModel/customUrl override only the provider actually selected in the config.
        // Applying them to every provider meant that setting, say, an OpenRouter model also
        // sent that model id to Gemini and OpenAI, breaking both.
        String selected = config.provider != null ? config.provider.trim().toLowerCase(Locale.ROOT) : "";

        // 1. Setup Gemini
        String geminiKey = firstNonBlank(config.geminiKey, System.getenv("GEMINI_API_KEY"));
        if (geminiKey != null) {
            String model = overrideFor(selected, "gemini", config.customModel, "gemini-2.5-flash");
            aiManager.register(new GeminiProvider(geminiKey, model));
            LOGGER.atInfo().log("Registered Gemini AI Provider.");
        }

        // 2. Setup OpenAI
        String openAiKey = firstNonBlank(config.openaiKey, System.getenv("OPENAI_API_KEY"));
        if (openAiKey != null) {
            String model = overrideFor(selected, "openai", config.customModel, "gpt-4o-mini");
            String url = overrideFor(selected, "openai", config.customUrl, "https://api.openai.com");
            aiManager.register(new OpenAIProvider(url, openAiKey, model));
            LOGGER.atInfo().log("Registered OpenAI Provider.");
        }

        // 3. Setup OpenRouter (OpenAI-compatible, registered under its own id)
        String openRouterKey = firstNonBlank(config.openrouterKey, System.getenv("OPENROUTER_API_KEY"));
        if (openRouterKey != null) {
            String model = overrideFor(selected, "openrouter", config.customModel, "google/gemini-2.5-flash");
            String url = overrideFor(selected, "openrouter", config.customUrl, "https://openrouter.ai");
            aiManager.register(new OpenAIProvider("openrouter", url, openRouterKey, model));
            LOGGER.atInfo().log("Registered OpenRouter AI Provider.");
        }

        // Set default active provider according to config selection
        if (config.provider != null && !config.provider.isBlank()) {
            if (!aiManager.setDefaultProvider(config.provider)) {
                LOGGER.atWarning().log("Configured AI provider '" + config.provider
                        + "' is not registered (missing API key?). Keeping the first available provider.");
            }
        }

        // Register data components
        // registerComponent(Class, Supplier) is the available method in
        // ComponentRegistryProxy
        // Registered WITH a persistence id and codec so the component survives entity reloads.
        // Previously it used the codec-less overload, which made it runtime-only: NPCs came
        // back from a reload with no SimNPCComponent and had to be re-attached by
        // SimTaleTickSystem, which only runs while the entity is ticking.
        SIM_NPC_COMPONENT_TYPE = this.getEntityStoreRegistry().registerComponent(SimNPCComponent.class,
                "simtale:npc", SimNPCComponent.CODEC);
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

        // Map markers are per-world, so the provider is registered once per world rather than as a
        // system. Registering it under a stable id lets a reload replace it instead of stacking.
        for (World mapWorld : Universe.get().getWorlds().values()) {
            mapWorld.getWorldMapManager().addMarkerProvider(
                    SimTaleMarkerProvider.PROVIDER_ID, new SimTaleMarkerProvider());
        }

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

        // Register custom interaction codecs
        Interaction.CODEC.register(
                "SimTale_CheckPregnancy",
                SimTaleCheckPregnancyInteraction.class,
                SimTaleCheckPregnancyInteraction.CODEC
        );
    }

    /** @return the first non-blank value, or {@code null} when both are blank/absent. */
    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return null;
    }

    /** Applies {@code custom} only when {@code providerId} is the provider chosen in the config. */
    private static String overrideFor(String selectedProvider, String providerId, String custom, String defaultValue) {
        if (providerId.equals(selectedProvider) && custom != null && !custom.isBlank()) {
            return custom;
        }
        return defaultValue;
    }
}
