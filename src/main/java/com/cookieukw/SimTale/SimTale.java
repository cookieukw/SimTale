package com.cookieukw.SimTale;


import java.util.Set;
import com.cookieukw.SimTale.systems.SimNpcPlayerListHelper;
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
import com.cookieukw.SimTale.logic.SimTaleItemRegistry;
import com.cookieukw.SimTale.systems.BabyCareTickSystem;
import com.cookieukw.SimTale.systems.ConstructionSystem;
import com.cookieukw.SimTale.systems.MoodAnimationSystem;
import com.cookieukw.SimTale.systems.PlayerJoinHandler;
import com.cookieukw.SimTale.systems.PlayerPregnancyTickSystem;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.systems.PregnancyTickSystem;
import com.cookieukw.SimTale.systems.RoutineAISystem;
import com.cookieukw.SimTale.systems.SimTaleChatHandler;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.cookieukw.SimTale.systems.SimTaleTickSystem;
import com.cookieukw.SimTale.systems.BedEntityRegistrySystem;
import com.cookieukw.SimTale.systems.ChildPutDownSystem;
import com.cookieukw.SimTale.systems.BedBlockEventSystem;
import com.cookieukw.SimTale.systems.BedPlaceBlockEventSystem;
import com.cookieukw.SimTale.systems.BabyBabbleSystem;
import com.cookieukw.SimTale.systems.GrowthTickSystem;
import com.cookieukw.SimTale.systems.ConstructionPreviewSweepSystem;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
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
        SimNpcPlayerListHelper.broadcastAdd(npc);
    }

    public static void untrackNpc(SimNPCComponent npc) {
        if (npc == null) return;
        ACTIVE_NPCS.remove(npc);
        if (npc.entityId != null) {
            NPCS_BY_ID.remove(npc.entityId, npc);
            SimNpcPlayerListHelper.broadcastRemove(npc.entityId);
        }
    }

    public static void untrackNpcById(UUID entityId) {
        if (entityId == null) return;
        SimNPCComponent existing = NPCS_BY_ID.remove(entityId);
        if (existing != null) {
            ACTIVE_NPCS.remove(existing);
        }
        SimNpcPlayerListHelper.broadcastRemove(entityId);
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
            aiManager.setDefaultProvider(config.provider);
        }

        logAiStartupState(config);

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
        this.getEntityStoreRegistry().registerSystem(new ConstructionPreviewSweepSystem());
        this.getEntityStoreRegistry().registerSystem(new PregnancyTickSystem());
        this.getEntityStoreRegistry().registerSystem(new PlayerPregnancyTickSystem());
        this.getEntityStoreRegistry().registerSystem(new BabyCareTickSystem());
        this.getEntityStoreRegistry().registerSystem(new GrowthTickSystem());
        this.getEntityStoreRegistry().registerSystem(new BabyBabbleSystem());
        // Crouch + use a block to put a carried child down. An EntityEventSystem rather than an
        // event-registry listener because UseBlockEvent is an EcsEvent — see the class doc for why
        // the PlayerMouseButtonEvent version had to be abandoned.
        this.getEntityStoreRegistry().registerSystem(new ChildPutDownSystem());

        // Map markers are NOT registered here.
        //
        // This was a loop over Universe.get().getWorlds().values(), and it ran during registry
        // setup — about a minute before the first world is loaded, as the server log shows. The
        // collection was empty, the loop body never executed, and no provider was ever attached,
        // so the NPC markers could not appear no matter what the provider did.
        //
        // Registration moved to PlayerJoinHandler, which by definition has a world in hand. See
        // SimTaleMarkerProvider.ensureRegistered.

        // Register event handlers
        //
        // Was .register(...) instead of .registerGlobal(...) — the only PlayerMouseButtonEvent
        // registration in either this project or RuneCore using that method, and the only one
        // that never fired. Every other listener for this event (SimTaleChatHandler's own
        // PlayerChatEvent registration right below, and all four of RuneCore's
        // PlayerMouseButtonEvent listeners) uses registerGlobal. This is why the baby/blueprint
        // block-placement logic in SimTaleEventHandler never ran, no matter what item was held —
        // confirmed via a log line at the very top of accept() that never printed once across an
        // entire testing session full of right-clicks.
        this.getEventRegistry().registerGlobal(EventPriority.NORMAL.getValue(), PlayerMouseButtonEvent.class,
                new SimTaleEventHandler());
        
        this.getEventRegistry().registerGlobal(EventPriority.NORMAL.getValue(), PlayerChatEvent.class,
                new SimTaleChatHandler());
        
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, new PlayerJoinHandler());

        this.getEventRegistry().registerGlobal(EntityRemoveEvent.class, event -> {
            if (event == null || event.getEntity() == null) return;
            UUID entityUuid = event.getEntity().getUuid();
            if (entityUuid != null) {
                SimTale.untrackNpcById(entityUuid);
            }
        });

        this.getCommandRegistry()
                .registerCommand(new SimTaleCommand());
        this.getCommandRegistry().registerCommand(new BuildCommand());
        this.getCommandRegistry().registerCommand(new SimDebugCommand());

        Interaction.getAssetStore().loadAssets(DefaultAssetMap.DEFAULT_PACK_KEY, List.of(
            new SimTaleUseNPCInteraction(UseNPCInteraction.DEFAULT_ID)
        ));

        Interaction.CODEC.register(
                "SimTale_CheckPregnancy",
                SimTaleCheckPregnancyInteraction.class,
                SimTaleCheckPregnancyInteraction.CODEC
        );

        SimTaleItemRegistry.init();
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

    /**
     * Reports, in one place, everything needed to tell why the AI is or is not answering.
     *
     * <p>The old single line — "Configured AI provider 'gemini' is not registered (missing API
     * key?)" — fired on a completely untouched install, because the default config ships with
     * {@code provider = "gemini"}, {@code enabled = false} and an empty key. So the message that
     * was supposed to flag a misconfiguration appeared on every boot of every server that had
     * never opted into AI, and became noise. It also never said where {@code simtale-ai.json}
     * actually is: the path is relative, so it resolves against the server's working directory,
     * which is not the world folder and not something the player can guess.
     */
    private static void logAiStartupState(AiConfig config) {
        Set<String> registered = aiManager.registeredProviderIds();
        boolean selectedIsUp = aiManager.defaultProviderId() != null
                && aiManager.defaultProviderId().equalsIgnoreCase(config.provider);

        if (!config.enabled) {
            LOGGER.atInfo().log("[SimTale] IA generativa desligada (enabled=false em "
                    + AiConfigManager.configFilePath() + "). As NPCs usam so as respostas prontas.");
            return;
        }

        if (registered.isEmpty()) {
            LOGGER.atWarning().log("[SimTale] IA generativa LIGADA mas nenhum provedor subiu:"
                    + " nenhuma chave de API foi encontrada. Preencha geminiKey/openaiKey/openrouterKey em "
                    + AiConfigManager.configFilePath()
                    + " ou exporte GEMINI_API_KEY/OPENAI_API_KEY/OPENROUTER_API_KEY."
                    + " Enquanto isso as NPCs respondem so com as falas prontas.");
            return;
        }

        if (!selectedIsUp) {
            LOGGER.atWarning().log("[SimTale] Provedor escolhido '" + config.provider
                    + "' nao subiu (chave ausente?); usando '" + aiManager.defaultProviderId()
                    + "'. Provedores ativos: " + String.join(", ", registered)
                    + ". Config: " + AiConfigManager.configFilePath());
            return;
        }

        LOGGER.atInfo().log("[SimTale] IA generativa pronta com '" + aiManager.defaultProviderId()
                + "'. Provedores ativos: " + String.join(", ", registered));
    }
}
