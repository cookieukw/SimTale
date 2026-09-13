package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.logic.PlayerGenderPage;
import com.cookieukw.SimTale.systems.FarmPostRegistry;
import com.cookieukw.SimTale.systems.FarmlandRegistry;
import com.cookieukw.SimTale.systems.NPCSleepHelper;
import java.util.Set;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.cookieukw.SimTale.logic.PlayerPregnancyPage;
import com.cookieukw.SimTale.logic.SimBedDebugPage;
import com.cookieukw.SimTale.logic.SimChestDebugPage;
import com.cookieukw.SimTale.systems.BedWorldBootstrap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.cookie.caskara.Caskara;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.ArrayList;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.ai.AiConfig;
import com.cookieukw.SimTale.ai.AiConfigManager;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.FamilySystem;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.systems.HouseManager;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.ChestRegistry;
import com.cookieukw.SimTale.systems.ChairRegistry;
import com.cookieukw.SimTale.systems.FurnitureAnchorHelper;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
import com.cookieukw.SimTale.core.lifecycle.LifecycleState;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.systems.NPCMovementHelper;
import com.cookieukw.SimTale.systems.NPCWorkHelper;
import com.cookieukw.SimTale.systems.ConstructionPreviewManager;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.cookieukw.SimTale.systems.NPCSocialHelper;
import com.cookieukw.SimTale.systems.SimTaleJuiceHelper;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import java.util.Objects;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import java.util.LinkedHashMap;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.cookieukw.SimTale.systems.SeasonalCostumeHelper;

/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends AbstractPlayerCommand {

    private static final SimLog SIM_LOGGER = SimLog.forClass(SimTaleCommand.class);

    public SimTaleCommand() {
        super("simtale", "SimTale plugin commands");
        this.setPermissionGroups("Adventure");
        
        // Register Hytale native subcommands
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new InteractSubCommand());
        this.addSubCommand(new TpAllSubCommand());
        this.addSubCommand(new ClearAllSubCommand());
        this.addSubCommand(new ForceSpawnSubCommand());
        this.addSubCommand(new ForceSleepSubCommand());
        this.addSubCommand(new DiagnosticsCommands.DebugLogSubCommand());
        this.addSubCommand(new LifecycleCommands.ForcePregSubCommand());
        this.addSubCommand(new LifecycleCommands.ForceBirthSubCommand());
        this.addSubCommand(new LifecycleCommands.SetStageSubCommand());
        this.addSubCommand(new LifecycleCommands.ForceMarrySubCommand());
        this.addSubCommand(new DebugCommands.DebugBedsSubCommand());
        this.addSubCommand(new DebugCommands.GraveyardSubCommand());
        this.addSubCommand(new DebugCommands.PutDownSubCommand());
        this.addSubCommand(new DebugCommands.DebugChestsSubCommand());
        this.addSubCommand(new DebugCommands.ForgetSubCommand());
        this.addSubCommand(new LifecycleCommands.PregnancySubCommand());
        this.addSubCommand(new DebugCommands.DebugNearSubCommand());
        this.addSubCommand(new DebugCommands.VillageSubCommand());
        this.addSubCommand(new SocialTestCommands.SetMoodSubCommand());
        this.addSubCommand(new DiagnosticsCommands.SearchSubCommand());
        this.addSubCommand(new DiagnosticsCommands.ToggleAiSubCommand());
        this.addSubCommand(new DiagnosticsCommands.AiStatusSubCommand());
        this.addSubCommand(new DiagnosticsCommands.HouseCheckSubCommand());
        this.addSubCommand(new DiagnosticsCommands.ChestCheckSubCommand());
        this.addSubCommand(new DiagnosticsCommands.ChairCheckSubCommand());
        this.addSubCommand(new DespawnNearestSubCommand());
        this.addSubCommand(new ForceEatSubCommand());
        this.addSubCommand(new ForceWorkSubCommand());
        this.addSubCommand(new ForceKillSubCommand());
        this.addSubCommand(new SetProfessionSubCommand());
        this.addSubCommand(new ForcePlantSubCommand());
        this.addSubCommand(new SetGenderSubCommand());
        this.addSubCommand(new DiagnosticsCommands.CamDebugSubCommand());
        this.addSubCommand(new DiagnosticsCommands.UnstickSubCommand());
        this.addSubCommand(new DiagnosticsCommands.RescanSubCommand());
        this.addSubCommand(new LifecycleCommands.GrowBabySubCommand());
        this.addSubCommand(new LifecycleCommands.ForcePlaceBabySubCommand());
        this.addSubCommand(new ForceConstructSubCommand());
        this.addSubCommand(new DiagnosticsCommands.NpcStateSubCommand());
        this.addSubCommand(new LifecycleCommands.ForceBabySwapSubCommand());
        this.addSubCommand(new SocialTestCommands.ForceSocialSubCommand());
        this.addSubCommand(new SocialTestCommands.TestFlirtSubCommand());
        this.addSubCommand(new SocialTestCommands.TestShoveSubCommand());
        this.addSubCommand(new SocialTestCommands.TestGreetSubCommand());
        this.addSubCommand(new CostumeSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // If no subcommand is specified, show usage
        sendUsage(ctx);
    }

    private static void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Usage: /simtale <spawn|interact|tpall|clearall|forcespawn|forcesleep|forcesocial|testflirt|testshove|testgreet|forcepreg|forcebirth|setstage|marry|debugbeds|pregnancy|debugnear|setmood|search|toggleai|housecheck|chestcheck|chaircheck|despawnnearest|forceeat|forcework|forceplant|setgender|camdebug|unstick|npcstate|forcebabyswap|forcekill|aistatus|setprofession|rescan|growbaby|forceplacebaby|forceconstruct|graveyard|putdown|costume>"));
    }

    // --- SUBCOMMANDS ---

    /**
     * PROTOTIPO (13/09, com gatilho automatico adicionado depois na mesma sessao): fantasia de
     * evento sazonal (Natal/Halloween), manual, na NPC mais proxima. Ver
     * {@link SeasonalCostumeHelper} pro gatilho automatico por calendario e pro codigo
     * compartilhado de aplicar/remover -- este comando so acha a NPC mais proxima e delega.
     * <p>
     * Usa o mesmo mecanismo que o Reaper ja usa pra trocar de modelo em tempo real
     * (SimNPCFactory.applyModel -> ModelAsset -> Model.createScaledModel -> ModelComponent), so
     * que apontando pra um ModelAsset gerado por scripts/generate_costume_assets.py
     * (src/main/resources/Server/Models/Events/Generated/*.json) cujo "Parent" e o proprio id
     * de modelo ESPECIFICO daquela NPC (nao o modelo base generico -- isso e o que faz a NPC
     * manter a propria cara enquanto fantasiada, ver docs/experimentos.md) e cujo unico
     * DefaultAttachment extra e um item de cosmetico que o proprio jogo ja usa pra isso
     * (Cosmetics/Head/SantaHat.blockymodel pro Natal -- e literalmente a mesma combinacao de
     * modelo/textura/GradientSet/GradientId do Server/Models/Christmas/Trork_Christmas.json
     * que o jogo ja envia --, StrawHat.blockymodel com a textura de bruxa pro Halloween).
     * <p>
     * Nada disso foi confirmado rodando numa partida real ainda -- ver testing_checklist.md.
     */
    private static class CostumeSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> eventArg;

        public CostumeSubCommand() {
            super("costume", "Testa uma fantasia de evento sazonal (christmas|halloween|off) na NPC mais proxima");
            this.eventArg = this.withRequiredArg("evento", "christmas|halloween|off", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String evento = ctx.get(this.eventArg).toLowerCase();

            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef == null || !npc.entityRef.isValid() || npc.isReaper) continue;
                TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (playerTransform == null || npcTransform == null) continue;
                double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                if (distSq < minDistance) {
                    minDistance = distSq;
                    nearestNPC = npc;
                }
            }
            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("Nenhuma NPC por perto."));
                return;
            }

            Ref<EntityStore> npcRef = nearestNPC.entityRef;
            PersistentModel pm = store.getComponent(npcRef, PersistentModel.getComponentType());
            if (pm == null) {
                ctx.sendMessage(Message.raw("[SimTale] NPC sem PersistentModel; nao foi possivel trocar a fantasia."));
                return;
            }
            if (evento.equals("off")) {
                boolean ok = SeasonalCostumeHelper.removeCostume(store, npcRef, nearestNPC);
                ctx.sendMessage(Message.raw(ok
                        ? "[SimTale] Fantasia removida de " + nearestNPC.name + "."
                        : "[SimTale] " + nearestNPC.name + " nao esta com fantasia de evento (ou o modelo original nao foi encontrado)."));
                return;
            }

            String suffix;
            if (evento.equals("christmas") || evento.equals("natal")) {
                suffix = "Christmas";
            } else if (evento.equals("halloween")) {
                suffix = "Halloween";
            } else {
                ctx.sendMessage(Message.raw("[SimTale] Evento desconhecido. Use: christmas, halloween ou off."));
                return;
            }

            // O asset de fantasia aponta "Parent" para o id ESPECIFICO desta NPC (nao para uma
            // base generica), gerado por scripts/generate_costume_assets.py em
            // Server/Models/Events/Generated/<currentId>_<evento>.json -- assim a NPC mantem a
            // propria cara (cabelo/rosto/roupa unicos dela) em vez de virar visualmente
            // identica a qualquer outra NPC fantasiada. Ver docs/experimentos.md, secao "a
            // limitacao de 'trocar o modelo inteiro'" (13/09). Mesma logica de aplicar/reverter
            // usada pelo gatilho automatico de calendario em SeasonalCostumeHelper, entao os
            // dois nunca ficam com um estado diferente do que a NPC realmente esta vestindo.
            boolean ok = SeasonalCostumeHelper.applyCostume(store, npcRef, nearestNPC, suffix);
            ctx.sendMessage(Message.raw(ok
                    ? "[SimTale] " + nearestNPC.name + " vestida pro evento '" + evento + "'. Use '/simtale costume off' pra desfazer."
                    : "[SimTale] Falhou -- asset nao encontrado pra essa NPC/evento. Rode scripts/generate_costume_assets.py."));
        }
    }


    private static class SpawnSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> npcTypeArg;

        public SpawnSubCommand() {
            super("spawn", "Spawns a SimTale NPC");
            this.npcTypeArg = this.withRequiredArg("type", "SLOTHIAN|TRORK|HUMAN_MALE|HUMAN_FEMALE|CHILD_MALE|CHILD_FEMALE", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String typeName = ctx.get(this.npcTypeArg);
            SimNPCFactory.NPCType type;
            try {
                type = SimNPCFactory.NPCType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.translation("general.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE/CHILD_MALE/CHILD_FEMALE"));
                return;
            }
            // The Reaper is ephemeral now — spawned automatically for a specific death and
            // removed once the ritual finishes (RoutineAISystem's DYING->DEAD transition), not a
            // standing NPC the player summons ahead of time.
            if (type == SimNPCFactory.NPCType.REAPER) {
                ctx.sendMessage(Message.raw("[SimTale] O Ceifador nao pode mais ser invocado manualmente — ele aparece sozinho quando uma NPC morre."));
                return;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            // `assert` is stripped at runtime without -ea, so these were not real checks.
            if (transform == null) {
                ctx.sendMessage(Message.raw("Could not get your position."));
                return;
            }
            // Copied first: joml's add mutates in place, so offsetting the live transform vector
            // teleports the player instead of picking a spot beside them.
            Vector3d pos = new Vector3d(transform.getPosition()).add(2, 0, 2);

            Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
            SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

            // Save initial state to DB
            if (comp != null) {
                SimNPCPersistence.saveNPC(comp);
            }

            ctx.sendMessage(Message.translation("general.cmd.spawn.success").param("type", type.name()));
        }
    }

    private static class InteractSubCommand extends AbstractPlayerCommand {
        public InteractSubCommand() {
            super("interact", "Opens the interaction screen with the nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            // If no NPCs tracked (e.g., after world reload), try to reassemble from database
            if (SimTale.ACTIVE_NPCS.isEmpty()) {
                ctx.sendMessage(Message.translation("general.cmd.reload.db"));
                SimNPCPersistence.reassembleActiveNPCs(world);
            }
            
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        Vector3d pPos = playerTransform.getPosition();
                        Vector3d nPos = npcTransform.getPosition();
                        double distSq = pPos.distanceSquared(nPos);
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.translation("general.cmd.interact.none"));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Player component unavailable."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new NPCInteractionPage(playerRef, player, nearestNPC));
            ctx.sendMessage(Message.translation("general.cmd.interact.success").param("name", nearestNPC.name));
        }
    }

    private static class TpAllSubCommand extends AbstractPlayerCommand {
        public TpAllSubCommand() {
            super("tpall", "Teleports all active NPCs to your position");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            if (playerTransform == null) return;
            Vector3d pPos = playerTransform.getPosition();
            int count = 0;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (npcTransform != null) {
                        npcTransform.setPosition(new Vector3d(pPos.x + (Math.random() - 0.5) * 4, pPos.y, pPos.z + (Math.random() - 0.5) * 4));
                        npc.entityRef.getStore().putComponent(npc.entityRef, TransformComponent.getComponentType(), npcTransform);
                        count++;
                    }
                }
            }
            ctx.sendMessage(Message.raw("Teleported " + count + " SimTale NPCs to your position."));
        }
    }

    private static class ClearAllSubCommand extends AbstractPlayerCommand {
        public ClearAllSubCommand() {
            super("clearall", "Removes all SimTale NPCs from the world and database");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            int count = 0;
            List<SimNPCComponent> toRemove = new ArrayList<>(SimTale.ACTIVE_NPCS);
            for (SimNPCComponent npc : toRemove) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    store.removeEntity(npc.entityRef, RemoveReason.REMOVE);
                    count++;
                }
                PlumbobSystem.removePlumbob(npc.entityId);
                // Buried, not deleted. Everything the Reaper collects ends up in the graveyard and
                // can be brought back; clearing the village by command used to be the one way to
                // destroy an NPC outright, which made it a trap — one command and a whole village
                // of histories was gone with no way back.
                SimNPCPersistence.archiveToGraveyard(npc.entityId);
            }
            SimTale.clearActiveNpcs();

            // Sweep the database as well. Removing the entities is not enough: records for NPCs
            // that are not currently tracked (unloaded chunks, entities already gone, leftovers
            // from earlier sessions) would otherwise survive and be resurrected. This used to be
            // guaranteed to leak, because Caskara.delete() targeted the "default" shell while
            // the records live in "simtale".
            //
            // Archived first, one by one, so the untracked ones reach the graveyard too instead of
            // being the only NPCs the command can still destroy for good.
            for (SimNPCData data : SimNPCPersistence.listAll()) {
                if (data.id == null) continue;
                try {
                    SimNPCPersistence.archiveToGraveyard(UUID.fromString(data.id));
                } catch (IllegalArgumentException ignored) {
                    // Malformed id: nothing to archive, deleteAll below still clears it.
                }
            }
            int purged = SimNPCPersistence.deleteAll();

            ctx.sendMessage(Message.raw("Removed " + count + " NPCs from the world and "
                    + purged + " bank records."));
        }
    }

    private static class ForceSpawnSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> npcTypeArg;

        public ForceSpawnSubCommand() {
            super("forcespawn", "Forces the immediate spawn of an NPC for debugging");
            this.npcTypeArg = this.withOptionalArg("type", "SLOTHIAN|TRORK|HUMAN_MALE|HUMAN_FEMALE|CHILD_MALE|CHILD_FEMALE", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String typeName = ctx.get(this.npcTypeArg);
            SimNPCFactory.NPCType type;
            if (typeName != null) {
                try {
                    type = SimNPCFactory.NPCType.valueOf(typeName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.sendMessage(Message.translation("general.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE/CHILD_MALE/CHILD_FEMALE"));
                    return;
                }
            } else {
                type = Math.random() > 0.5 ? SimNPCFactory.NPCType.HUMAN_MALE : SimNPCFactory.NPCType.HUMAN_FEMALE;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sendMessage(Message.raw("Could not get your position."));
                return;
            }
            // Copied first: joml's add mutates in place, so offsetting the live transform vector
            // teleports the player instead of picking a spot beside them.
            Vector3d pos = new Vector3d(transform.getPosition()).add(2, 0, 2);

            Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
            SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

            if (comp != null) {
                SimNPCPersistence.saveNPC(comp);
            }

            ctx.sendMessage(Message.raw("Forced spawn of debug NPC of type: " + type.name()));
        }
    }

    private static class ForceSleepSubCommand extends AbstractPlayerCommand {
        public ForceSleepSubCommand() {
            super("forcesleep", "Forces the nearest NPC to find a bed and sleep");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        Vector3d pPos = playerTransform.getPosition();
                        Vector3d nPos = npcTransform.getPosition();
                        double distSq = pPos.distanceSquared(nPos);
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            NeedsHelper.setNeed(null, nearestNPC.entityRef, NeedsHelper.ENERGY_ID, 0f);
            nearestNPC.forceSleep = true;
            
            ctx.sendMessage(Message.raw("Forcing " + nearestNPC.name + " to go sleep! Energy set to 0."));
        }
    }

    /**
     * Debug-only: starts building a TavernHouse at the player's position immediately, skipping
     * the Blueprint_TavernHouse item's right-click preview/confirm flow entirely — same
     * reasoning as {@code ForcePlaceBabySubCommand} above (the click pipeline this depends on,
     * {@code SimTaleEventHandler}, was unreliable to test against). Goes straight to
     * {@code ConstructionPreviewManager.commit}, which does not check {@code isClear} itself
     * (only the click-handler's caller did) — so unlike the normal flow, this does not refuse an
     * obstructed site. That is intentional for a debug command; the normal blueprint flow still
     * enforces it.
     */
    private static class ForceConstructSubCommand extends AbstractPlayerCommand {
        public ForceConstructSubCommand() {
            super("forceconstruct", "Starts building a TavernHouse at your position, bypassing the blueprint item's click flow");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nao foi possivel obter sua posicao."));
                return;
            }
            Vector3d pos = transform.getPosition();
            Vector3i anchor = new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);

            ConstructionPreviewManager.start(playerRef.getUuid(), "TavernHouse", anchor);
            ConstructionSiteComponent committed = ConstructionPreviewManager.commit(playerRef.getUuid(), world);
            if (committed != null) {
                committed.facing = Rotation4.NORTH;
                committed.roofFacing = Rotation4.NORTH;
                committed.isBuilding = true;
                ctx.sendMessage(Message.raw("[SimTale] Construcao de TavernHouse iniciada na sua posicao."));
            } else {
                ctx.sendMessage(Message.raw("[SimTale] Falha ao iniciar a construcao."));
            }
        }
    }

    // debugbeds, forget, debugchests and debugnear now live in DebugCommands.

    /**
     * Removes the nearest tracked NPC outright: no death flow, no Reaper, no grief for the
     * family — just gone, as if she had never been tracked at all.
     *
     * <p>Exists specifically to clean up after the duplicate-body bug documented on
     * {@code GrowthManager.onStageChanged}'s TODDLER branch (testing_checklist.md, 12/09): a
     * child whose BABY->TODDLER transition re-ran left 2-3 fully live, independently-ticking
     * bodies sharing one name in the world, all still tracked in {@code ACTIVE_NPCS}. {@code
     * forcekill} is the wrong tool for that — it is a real in-fiction death, complete with a
     * Reaper spawning to collect the soul, and would tell every relative of a *technical
     * duplicate* that she died. This command skips all of that: it removes the live entity, drops
     * it from {@code ACTIVE_NPCS}, and deletes its saved {@code SimNPCData} record, so a stray
     * copy can be erased without leaving a death behind that never actually happened in the
     * story.
     *
     * <p>Deliberately does not touch {@code GrowthComponent}/{@code ACTIVE_CHILDREN} or the
     * family bond records — those already collapsed back down to one entry on their own (every
     * re-run of the buggy branch rewrote the same {@code GrowthComponent} object and the same
     * Caskara key), so the only actual duplication to clean up is the extra live bodies.
     */
    private static class DespawnNearestSubCommand extends AbstractPlayerCommand {
        public DespawnNearestSubCommand() {
            super("despawnnearest", "Removes the nearest NPC with no death flow (cleanup for duplicate-body bugs)");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            if (playerTransform == null) {
                ctx.sendMessage(Message.raw("No player transform."));
                return;
            }

            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef == null || !npc.entityRef.isValid() || npc.isReaper) continue;
                TransformComponent npcTransform =
                        npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (npcTransform == null) continue;
                double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                if (distSq < minDistance) {
                    minDistance = distSq;
                    nearestNPC = npc;
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            String name = nearestNPC.name;
            UUID entityId = nearestNPC.entityId;
            Ref<EntityStore> npcRef = nearestNPC.entityRef;

            SimTale.untrackNpc(nearestNPC);
            if (entityId != null) {
                try {
                    SimNPCPersistence.deleteNPC(entityId);
                } catch (Exception e) {
                    HytaleLogger.forEnclosingClass().atWarning()
                            .log("SimTale: falha ao apagar registro de '" + name + "': " + e);
                }
            }
            if (npcRef.isValid()) {
                store.removeEntity(npcRef, RemoveReason.REMOVE);
            }

            ctx.sendMessage(Message.raw("[SimTale] '" + name + "' (id=" + entityId + ") removida sem passar pelo fluxo de morte."));
            HytaleLogger.forEnclosingClass().atInfo()
                    .log("SimTale: '" + name + "' (id=" + entityId + ") despawned via /simtale despawnnearest");
        }
    }

    private static class ForceEatSubCommand extends AbstractPlayerCommand {
        public ForceEatSubCommand() {
            super("forceeat", "Forces the nearest NPC to eat from a chest");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        Vector3d pPos = playerTransform.getPosition();
                        Vector3d nPos = npcTransform.getPosition();
                        double distSq = pPos.distanceSquared(nPos);
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            NeedsHelper.setNeed(null, nearestNPC.entityRef, NeedsHelper.HUNGER_ID, 0f);
            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                ai.currentTask = RoutineAIComponent.TaskType.FINDING_FOOD;
                ai.taskStartTime = world.getTick();
                store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
            }
            
            ctx.sendMessage(Message.raw("Forçando " + nearestNPC.name + " a ir comer! Fome definida para 0."));
        }
    }

    private static class ForceWorkSubCommand extends AbstractPlayerCommand {
        public ForceWorkSubCommand() {
            super("forcework", "Forces the nearest NPC to work (farm or hunt)");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            // NPCWorkHelper.handleWorkLogic only ever reacts to this trigger for Farmer/Hunter/
            // Fisherman (systems/NPCWorkHelper.java:121) — every other profession picked here
            // would set the debug flag on an NPC nothing ever reads it from, and silently do nothing.
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()
                        && (npc.profession == Profession.FARMER
                            || npc.profession == Profession.HUNTER
                            || npc.profession == Profession.FISHERMAN
                            || npc.profession == Profession.LUMBERJACK
                            || npc.profession == Profession.MINER)) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        Vector3d pPos = playerTransform.getPosition();
                        Vector3d nPos = npcTransform.getPosition();
                        double distSq = pPos.distanceSquared(nPos);
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No Farmer, Hunter, Fisherman or Lumberjack NPC nearby."));
                return;
            }

            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                ai.currentTask = RoutineAIComponent.TaskType.IDLE;
                ai.forcedByDebug = true;
                ai.taskStartTime = world.getTick();
                store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
                // The actual scan (deposit-if-carrying, then harvest-or-plant / hunt) runs on
                // NPCWorkHelper's next tick, not synchronously here, so this can't promise it found
                // something — only that the check will run immediately instead of on its normal
                // 100-tick stagger.
                ctx.sendMessage(Message.raw(nearestNPC.name + " (" + nearestNPC.profession.ptName
                    + ") vai verificar trabalho no próximo tick — só terá efeito visível se houver "
                    + "colheita/plantio/caça/pesca/corte de árvore disponível por perto."));
            } else {
                ctx.sendMessage(Message.raw("NPC AI not active."));
            }
        }
    }

    /**
     * Debug-only: there is currently no in-game path into the death flow at all (old age/disease
     * are aspirational per the comment in {@code RoutineAISystem}) — the only way to test the
     * Grim Reaper soul-collection pipeline (DYING -> DEAD -> REAPING) is to force it directly.
     * Requires a reaper NPC to already exist in the world ({@code /simtale spawn reaper}) —
     * {@code RoutineAISystem} only dispatches an idle reaper it finds in {@code ACTIVE_NPCS}.
     */
    /**
     * Debug-only: assigns a profession directly, bypassing chat (needs friendship &gt; 20, see
     * {@code SimTaleChatHandler.handleProfessionChange}) and the item-based UI assignment (needs
     * the right tool in hand and isn't guaranteed to land on the NPC you're aiming at). Useful
     * for testing a specific profession's work cycle without first winning the NPC over.
     */
    private static class SetProfessionSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> profArg;

        public SetProfessionSubCommand() {
            super("setprofession", "Forces the nearest NPC's profession, no affinity or item needed");
            this.profArg = this.withRequiredArg("profession",
                    "UNEMPLOYED|MINER|FARMER|FISHERMAN|LUMBERJACK|GUARD|EXPLORER|BUILDER|HUNTER", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String profName = ctx.get(this.profArg);
            Profession profession;
            try {
                profession = Profession.valueOf(profName.toUpperCase());
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Invalid profession. Choose from: UNEMPLOYED/MINER/FARMER/FISHERMAN/LUMBERJACK/GUARD/EXPLORER/BUILDER/HUNTER"));
                return;
            }

            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            nearestNPC.profession = profession;
            ctx.sendMessage(Message.raw(nearestNPC.name + " agora é " + profession.ptName + "."));
        }
    }

    private static class ForceKillSubCommand extends AbstractPlayerCommand {
        public ForceKillSubCommand() {
            super("forcekill", "Forces the nearest NPC into the death flow (a Reaper spawns automatically to collect the soul)");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid() && !npc.isReaper) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai == null) {
                ctx.sendMessage(Message.raw("NPC AI not active."));
                return;
            }

            ai.currentTask = RoutineAIComponent.TaskType.DYING;
            ai.forcedByDebug = true;
            ai.taskStartTime = world.getTick();
            store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);

            ctx.sendMessage(Message.raw("Forçando " + nearestNPC.name + " a morrer. Em ~200 ticks um Ceifador vai aparecer sozinho pra coletar a alma."));
        }
    }

    private static class ForcePlantSubCommand extends AbstractPlayerCommand {
        public ForcePlantSubCommand() {
            super("forceplant", "Forces the nearest Farmer NPC to plant in nearby plowed land");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid() && npc.profession == Profession.FARMER) {
                    TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform != null && npcTransform != null) {
                        Vector3d pPos = playerTransform.getPosition();
                        Vector3d nPos = npcTransform.getPosition();
                        double distSq = pPos.distanceSquared(nPos);
                        if (distSq < minDistance) {
                            minDistance = distSq;
                            nearestNPC = npc;
                        }
                    }
                }
            }

            if (nearestNPC == null) {
                ctx.sendMessage(Message.translation("general.cmd.forceplant.not_farmer"));
                return;
            }

            // Ensure they have seeds. Goes through NPCWorkHelper.getInventory rather than reading
            // the Storage component directly — it self-heals NPCs still carrying the engine's
            // default zero-capacity EmptyItemContainer (see NPCWorkHelper.getInventory).
            ItemContainer inv = NPCWorkHelper.getInventory(store, nearestNPC.entityRef);
            if (inv != null) {
                String seed = NPCWorkHelper.findSeedInInventory(inv);
                if (seed == null) {
                    // Give them 5 carrot seeds to start
                    ItemStack seedStack = new ItemStack("Plant_Seeds_Carrot", 5);
                    if (inv.canAddItemStack(seedStack)) {
                        inv.addItemStack(seedStack);
                        SIM_LOGGER.debug("[SimTale] forceplant gave {} 5x Plant_Seeds_Carrot; findSeedInInventory now returns '{}'",
                                nearestNPC.name, NPCWorkHelper.findSeedInInventory(inv));
                        ctx.sendMessage(Message.translation("general.cmd.forceplant.seeds_added").param("name", nearestNPC.name));
                    } else {
                        StringBuilder contents = new StringBuilder();
                        for (short slot = 0; slot < inv.getCapacity(); slot++) {
                            ItemStack it = inv.getItemStack(slot);
                            if (it != null && !it.isEmpty()) {
                                contents.append(it.getItemId()).append("x").append(it.getQuantity()).append(", ");
                            }
                        }
                        SIM_LOGGER.debug("[SimTale] forceplant could not give {} seeds — inventory full (capacity={}). Contents: {}",
                                nearestNPC.name, inv.getCapacity(), contents.toString());
                        ctx.sendMessage(Message.raw("[SimTale] " + nearestNPC.name + "'s inventory is full — could not give seeds."));
                    }
                } else {
                    SIM_LOGGER.debug("[SimTale] forceplant: {} already has seed '{}', not adding more", nearestNPC.name, seed);
                }
            }

            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                TransformComponent npcTransform = store.getComponent(nearestNPC.entityRef, TransformComponent.getComponentType());
                Vector3d npcPos = npcTransform.getPosition();

                // Same registered-plot lookup handleWorkLogic uses: a Deco_Scarecrow lets her
                // find farmland from anywhere, not just within scanning range of where she's
                // standing right now.
                FarmPostRegistry.FarmPost claimedPost =
                        FarmPostRegistry.claimNearest(npcPos.x, npcPos.y, npcPos.z, nearestNPC.entityId);
                Vector3d scanCenter = claimedPost != null
                        ? new Vector3d(claimedPost.postX() + 0.5, claimedPost.postY(), claimedPost.postZ() + 0.5)
                        : npcPos;

                Vector3i farmPos = NPCWorkHelper.scanForFarmland(scanCenter, world);
                SIM_LOGGER.debug("[SimTale] forceplant scan for {}: claimedPost={}, scanCenter=({},{},{}), farmPos={}, FarmlandRegistry.size={}",
                        nearestNPC.name, claimedPost, scanCenter.x, scanCenter.y, scanCenter.z, farmPos,
                        FarmlandRegistry.FARMLAND.size());
                if (farmPos != null) {
                    ai.targetBlockPosition = farmPos;
                    if (claimedPost != null) {
                        ai.claimedWorkPost = new Vector3i(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ());
                    }
                    ai.currentTask = RoutineAIComponent.TaskType.MOVING_TO_WORK;
                    ai.forcedByDebug = true;
                    ai.taskStartTime = world.getTick();
                    store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
                    ctx.sendMessage(Message.translation("general.cmd.forceplant.success").param("name", nearestNPC.name).param("pos", farmPos.toString()));
                } else {
                    if (claimedPost != null) {
                        FarmPostRegistry.release(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ(), nearestNPC.entityId);
                    }
                    ctx.sendMessage(Message.translation("general.cmd.forceplant.farmland_not_found").param("name", nearestNPC.name));
                }
            } else {
                ctx.sendMessage(Message.translation("general.cmd.forceplant.ai_inactive"));
            }
        }
    }

    private static class SetGenderSubCommand extends AbstractPlayerCommand {
        public SetGenderSubCommand() {
            super("setgender", "Opens the gender selection screen for the player");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Error: Player not found."));
                return;
            }

            SimPlayerComponent simPlayer = store.getComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE);
            if (simPlayer == null) {
                simPlayer = SimPlayerPersistence.loadPlayer(playerRef.getUuid());
                if (simPlayer == null) {
                    simPlayer = new SimPlayerComponent(playerRef.getUuid());
                }
                store.addComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            }

            player.getPageManager().openCustomPage(ref, store, new PlayerGenderPage(playerRef, player, simPlayer));
            ctx.sendMessage(Message.raw("Opening gender selection panel..."));
        }
    }
}
