package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.cookieukw.SimTale.logic.PlayerPregnancyPage;
import com.cookieukw.SimTale.logic.SimBedDebugPage;
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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.joml.Vector3i;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.ArrayList;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.systems.HouseManager;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.ChestRegistry;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.systems.NPCMovementHelper;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.Objects;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import java.util.HashMap;

/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends AbstractPlayerCommand {

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
        this.addSubCommand(new DebugLogSubCommand());
        this.addSubCommand(new ForcePregSubCommand());
        this.addSubCommand(new ForceBirthSubCommand());
        this.addSubCommand(new SetStageSubCommand());
        this.addSubCommand(new ForceMarrySubCommand());
        this.addSubCommand(new DebugBedsSubCommand());
        this.addSubCommand(new DebugChestsSubCommand());
        this.addSubCommand(new ForgetSubCommand());
        this.addSubCommand(new PregnancySubCommand());
        this.addSubCommand(new DebugNearSubCommand());
        this.addSubCommand(new SetMoodSubCommand());
        this.addSubCommand(new SearchSubCommand());
        this.addSubCommand(new ToggleAiSubCommand());
        this.addSubCommand(new HouseCheckSubCommand());
        this.addSubCommand(new ChestCheckSubCommand());
        this.addSubCommand(new ForceEatSubCommand());
        this.addSubCommand(new ForceWorkSubCommand());
        this.addSubCommand(new ForcePlantSubCommand());
        this.addSubCommand(new SetGenderSubCommand());
        this.addSubCommand(new CamDebugSubCommand());
        this.addSubCommand(new UnstickSubCommand());
        this.addSubCommand(new NpcStateSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // If no subcommand is specified, show usage
        sendUsage(ctx);
    }

    private static void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Uso: /simtale <spawn|interact|tpall|clearall|forcespawn|forcesleep|forcepreg|forcebirth|setstage|marry|debugbeds|pregnancy|debugnear|setmood|search|toggleai|housecheck|chestcheck|forceeat|forcework|forceplant|setgender|camdebug|unstick|npcstate>"));
    }

    /**
     * Dumps the runtime state of the nearest NPC: role state, animation slots, movement flags,
     * Frozen, leash and AI task.
     * <p>
     * Built for the "sliding on ice after interacting" bug. Run it on a fresh NPC and on one
     * that has been talked to, and diff the two — whatever differs is the culprit, instead of
     * guessing which subsystem is stuck.
     */
    private static class NpcStateSubCommand extends AbstractPlayerCommand {

        public NpcStateSubCommand() {
            super("npcstate", "Mostra o estado interno do NPC mais proximo");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) {
                ctx.sendMessage(Message.raw("[SimTale] Sem transform do jogador."));
                return;
            }

            SimNPCComponent best = null;
            double bestDist = Double.MAX_VALUE;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef == null || !npc.entityRef.isValid()) continue;
                TransformComponent nt = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (nt == null) continue;
                double d = pt.getPosition().distanceSquared(nt.getPosition());
                if (d < bestDist) { bestDist = d; best = npc; }
            }

            if (best == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC ativo por perto."));
                return;
            }

            Ref<EntityStore> nref = best.entityRef;
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(best.name).append(" (").append(String.format("%.1f", Math.sqrt(bestDist))).append("m) ===");

            StateSupport ss = StateSupport.get(nref, store);
            sb.append("\n  role state: ").append(ss != null ? ss.getStateName() : "<sem StateSupport>");
            if (ss != null) sb.append("  busy=").append(ss.isInBusyState());

            ActiveAnimationComponent anim = store.getComponent(nref, ActiveAnimationComponent.getComponentType());
            if (anim != null) {
                String[] slots = anim.getActiveAnimations();
                sb.append("\n  anim slots:");
                for (AnimationSlot s : AnimationSlot.values()) {
                    int i = s.ordinal();
                    if (i < slots.length && slots[i] != null) sb.append(" ").append(s).append("=").append(slots[i]);
                }
            } else {
                sb.append("\n  anim: <sem ActiveAnimationComponent>");
            }

            MovementStatesComponent msc = store.getComponent(nref, MovementStatesComponent.getComponentType());
            if (msc != null) {
                MovementStates m = msc.getMovementStates();
                sb.append("\n  movement: idle=").append(m.idle)
                  .append(" walking=").append(m.walking)
                  .append(" running=").append(m.running)
                  .append(" onGround=").append(m.onGround)
                  .append(" sleeping=").append(m.sleeping)
                  .append(" mounting=").append(m.mounting);
            }

            sb.append("\n  frozen=").append(store.getComponent(nref, Frozen.getComponentType()) != null)
              .append("  interagindoUI=").append(best.isInteractingViaUI);

            NPCEntity ne = store.getComponent(nref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (ne != null) sb.append("\n  leash=").append(ne.getLeashPoint());

            RoutineAIComponent ai = store.getComponent(nref, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                sb.append("\n  ai task=").append(ai.currentTask)
                  .append("  alvo=").append(ai.targetBlockPosition)
                  .append("  lastLeash=").append(ai.lastLeashPos)
                  .append("\n  sonoAgendado=").append(ai.sleepingOnSchedule);
            }

            // Everything needed to tell "guard on the day shift" apart from "stuck in bed": the
            // profession, whether the world clock says this NPC's sleep window is open, and the
            // raw day progress behind that answer.
            sb.append("\n  profissao=").append(best.profession)
              .append("  janelaDeSono=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.isSleepPeriod(best, world))
              .append("  noite=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.isNight(world))
              .append("  hora=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.currentHour(world));

            ctx.sendMessage(Message.raw(sb.toString()));
            HytaleLogger.forEnclosingClass().atInfo().log(sb.toString());
        }
    }

    /** Toggles the verbose camera dump printed when the NPC interaction page opens. */
    private static class CamDebugSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> stateArg;

        public CamDebugSubCommand() {
            super("camdebug", "Liga/desliga o debug da camera de interacao");
            this.stateArg = this.withOptionalArg("state", "on|off", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String state = ctx.get(this.stateArg);
            if (state == null || state.isBlank()) {
                NPCInteractionPage.CAMERA_DEBUG = !NPCInteractionPage.CAMERA_DEBUG;
            } else {
                NPCInteractionPage.CAMERA_DEBUG = state.equalsIgnoreCase("on") || state.equalsIgnoreCase("true");
            }
            ctx.sendMessage(Message.raw("[SimTale] Debug de camera: "
                    + (NPCInteractionPage.CAMERA_DEBUG ? "LIGADO" : "DESLIGADO")
                    + ". Abra o menu de um NPC para ver o dump."));
        }
    }

    /**
     * Releases NPCs stuck in the "interacting via UI" state.
     * <p>
     * If the interaction page ever fails to run its cleanup, the NPC keeps {@code Frozen} and
     * {@code isInteractingViaUI = true}. RoutineAISystem's self-heal only strips Frozen when
     * that flag is false, so the NPC can never recover on its own — it just glides around
     * frozen. This is the manual escape hatch.
     */
    private static class UnstickSubCommand extends AbstractPlayerCommand {

        public UnstickSubCommand() {
            super("unstick", "Destrava NPCs presos e o proprio jogador preso na cama");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            // Free the player first: getting stuck in a bed with no way out, not even in creative,
            // leaves no other escape from inside the game. Nothing in SimTale mounts the player, so
            // this is a rescue hatch rather than a fix — but the components are the same ones the
            // NPC path clears, and clearing them when they are absent is harmless.
            boolean playerFreed = false;
            if (store.getComponent(ref, MountedComponent.getComponentType()) != null) {
                store.tryRemoveComponent(ref, MountedComponent.getComponentType());
                playerFreed = true;
            }
            if (store.getComponent(ref, Frozen.getComponentType()) != null) {
                store.tryRemoveComponent(ref, Frozen.getComponentType());
                playerFreed = true;
            }
            NPCMovementHelper.setSleepingState(ref, store, false);
            ctx.sendMessage(Message.raw(playerFreed
                    ? "[SimTale] Voce foi solto da cama/montaria."
                    : "[SimTale] Voce nao estava montado nem congelado."));

            int fixed = 0;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                boolean touched = false;

                if (npc.isInteractingViaUI) {
                    npc.isInteractingViaUI = false;
                    touched = true;
                }
                npc.uiInteractionPlayer = null;
                npc.currentConversationPartner = null;

                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    if (npc.entityRef.getStore().getComponent(npc.entityRef, Frozen.getComponentType()) != null) {
                        npc.entityRef.getStore().tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
                        touched = true;
                    }

                    // Solta tambem quem ficou preso na cama.
                    //
                    // Sem isto o unstick zerava a task para IDLE mas deixava a NPC montada e com
                    // MovementStates.sleeping ligado. Na tentativa seguinte de dormir o
                    // mountOnBlock respondia ALREADY_MOUNTED e a NPC nunca voltava para a cama —
                    // o que tambem tornava impossivel reproduzir o ciclo de sono para testar.
                    if (npc.entityRef.getStore().getComponent(
                            npc.entityRef, MountedComponent.getComponentType()) != null) {
                        npc.entityRef.getStore().tryRemoveComponent(
                                npc.entityRef, MountedComponent.getComponentType());
                        touched = true;
                    }
                    NPCMovementHelper.setSleepingState(npc.entityRef, npc.entityRef.getStore(), false);
                    RoutineAIComponent ai = npc.entityRef.getStore().getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    if (ai != null) {
                        // Drops the stale leash so the next moveTo re-issues the "Moving" state
                        // and the walk animation comes back.
                        NPCMovementHelper.clearMoveTarget(npc.entityRef, ai);
                        ai.currentTask = RoutineAIComponent.TaskType.IDLE;
                        ai.targetBlockPosition = null;
                        ai.socializeTargetId = null;
                        ai.socializeHost = false;
                        ai.wanderTimer = 0;
                        // Without clearing this, an NPC freed from bed still counts as a scheduled
                        // sleeper, and the SLEEPING branch would wait for a window that is not open.
                        ai.sleepingOnSchedule = false;
                        // Push both searches out so the interrupts do not drag her straight back
                        // to the bed the command just freed her from.
                        ai.nextBedSearchTick = world.getTick() + 200;
                        ai.nextFoodSearchTick = world.getTick() + 200;
                    }
                }

                if (touched) fixed++;
            }
            ctx.sendMessage(Message.raw("[SimTale] Destravados " + fixed + " de "
                    + SimTale.ACTIVE_NPCS.size() + " NPCs ativos."));
        }
    }

    // --- SUBCOMMANDS ---

    private static class SpawnSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> npcTypeArg;

        public SpawnSubCommand() {
            super("spawn", "Cria um NPC do SimTale");
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

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            // `assert` is stripped at runtime without -ea, so these were not real checks.
            if (transform == null) {
                ctx.sendMessage(Message.raw("Nao foi possivel obter sua posicao."));
                return;
            }
            Vector3d pos = transform.getPosition().add(2, 0, 2);

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
            super("interact", "Abre a tela de interacao com o NPC mais proximo");
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
                ctx.sendMessage(Message.raw("Componente de jogador indisponivel."));
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new NPCInteractionPage(playerRef, player, nearestNPC));
            ctx.sendMessage(Message.translation("general.cmd.interact.success").param("name", nearestNPC.name));
        }
    }

    private static class TpAllSubCommand extends AbstractPlayerCommand {
        public TpAllSubCommand() {
            super("tpall", "Teleporta todos os NPCs ativos para sua posicao");
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
            ctx.sendMessage(Message.raw("Teleportados " + count + " NPCs do SimTale para sua posicao."));
        }
    }

    private static class ClearAllSubCommand extends AbstractPlayerCommand {
        public ClearAllSubCommand() {
            super("clearall", "Remove todos os NPCs do SimTale do mundo e do banco");
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
                SimNPCPersistence.deleteNPC(npc.entityId);
            }
            SimTale.clearActiveNpcs();

            // Sweep the database as well. Removing the entities is not enough: records for NPCs
            // that are not currently tracked (unloaded chunks, entities already gone, leftovers
            // from earlier sessions) would otherwise survive and be resurrected. This used to be
            // guaranteed to leak, because Caskara.delete() targeted the "default" shell while
            // the records live in "simtale".
            int purged = SimNPCPersistence.deleteAll();

            ctx.sendMessage(Message.raw("Removidos " + count + " NPCs do mundo e "
                    + purged + " registros do banco."));
        }
    }

    private static class ForceSpawnSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> npcTypeArg;

        public ForceSpawnSubCommand() {
            super("forcespawn", "Forca o spawn imediato de um NPC para debug");
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
                ctx.sendMessage(Message.raw("Nao foi possivel obter sua posicao."));
                return;
            }
            Vector3d pos = transform.getPosition().add(2, 0, 2);

            Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
            SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

            if (comp != null) {
                SimNPCPersistence.saveNPC(comp);
            }

            ctx.sendMessage(Message.raw("Forcado spawn de NPC de debug do tipo: " + type.name()));
        }
    }

    private static class ForceSleepSubCommand extends AbstractPlayerCommand {
        public ForceSleepSubCommand() {
            super("forcesleep", "Forca o NPC mais proximo a procurar uma cama e ir dormir");
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
                ctx.sendMessage(Message.raw("Nenhum NPC por perto."));
                return;
            }

            nearestNPC.needs.energy = 0f;
            nearestNPC.forceSleep = true;
            
            ctx.sendMessage(Message.raw("Forcando " + nearestNPC.name + " a ir dormir! Energia definida para 0."));
        }
    }

    /**
     * Liga e desliga as mensagens de depuracao do mod.
     *
     * <p>Boa parte do diagnostico do SimTale esta em chamadas {@code LOGGER.debug}, que o
     * {@link SimLog} descarta por padrao — do contrario o log do servidor encheria com varreduras
     * por tick (busca de cama, de agua, de bau, de porta). Sem um jeito de liga-las em jogo,
     * investigar qualquer coisa exigia recompilar.
     */
    private static class DebugLogSubCommand extends AbstractPlayerCommand {
        public DebugLogSubCommand() {
            super("debug", "Liga/desliga as mensagens de depuracao do SimTale no log");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            SimLog.debugEnabled = !SimLog.debugEnabled;
            ctx.sendMessage(Message.raw("[SimTale] log de depuracao "
                    + (SimLog.debugEnabled ? "LIGADO" : "desligado")
                    + (SimLog.debugEnabled ? " — lembre de desligar depois, ele e verboso." : "")));
        }
    }

    private static class ForcePregSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> targetArg;

        public ForcePregSubCommand() {
            super("forcepreg", "Forca a gravidez no player executor ou na NPC feminina mais proxima");
            this.targetArg = this.withOptionalArg("target", "me|npc", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String target = ctx.get(this.targetArg);
            if (target == null) target = "me";

            if (target.equalsIgnoreCase("me")) {
                SimPlayerComponent playerComp = store.getComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE);
                if (playerComp == null) {
                    playerComp = new SimPlayerComponent(playerRef.getUuid());
                    store.addComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE, playerComp);
                }
                if (playerComp.pregnancy == null) {
                    playerComp.pregnancy = new PregnancyComponent();
                }
                playerComp.pregnancy.start(UUID.randomUUID(), world.getTick());
                SimPlayerPersistence.savePlayer(playerComp);
                ctx.sendMessage(Message.translation("general.cmd.forcepreg.success"));
                openPlayerPregnancyPage(ref, store, playerRef, playerComp);
            } else {
                TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
                SimNPCComponent nearestNPC = null;
                double minDistance = Double.MAX_VALUE;

                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityRef != null && npc.entityRef.isValid() && npc.gender == Gender.FEMALE) {
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
                    ctx.sendMessage(Message.raw("Nenhuma NPC feminina encontrada por perto."));
                    return;
                }

                if (nearestNPC.pregnancy != null && nearestNPC.pregnancy.pregnant) {
                    ctx.sendMessage(Message.raw(nearestNPC.name + " ja esta gravida!"));
                    return;
                }

                nearestNPC.family.marry(playerRef.getUuid(), null);
                nearestNPC.getRelationship(playerRef.getUuid()).romance = 100;

                boolean success = LifecycleManager.startPregnancy(nearestNPC, playerRef.getUuid(), world.getTick());
                if (success) {
                    SimNPCPersistence.saveNPC(nearestNPC);
                    ctx.sendMessage(Message.raw("Gravidez forcada com sucesso para: " + nearestNPC.name));
                } else {
                    ctx.sendMessage(Message.raw("Falha ao iniciar gravidez para: " + nearestNPC.name));
                }
            }
        }
    }

    private static class ForceBirthSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> targetArg;

        public ForceBirthSubCommand() {
            super("forcebirth", "Forca o parto imediato do player executor ou da NPC mais proxima");
            this.targetArg = this.withOptionalArg("target", "me|npc", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String target = ctx.get(this.targetArg);
            if (target == null) target = "me";

            if (target.equalsIgnoreCase("me")) {
                SimPlayerComponent playerComp = store.getComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE);
                if (playerComp != null && playerComp.pregnancy != null && playerComp.pregnancy.pregnant) {
                    playerComp.pregnancy.startTick = world.getTick() - playerComp.pregnancy.durationTicks - 1;
                    LifecycleManager.birthPlayerBaby(ref, playerComp, store, world.getTick());
                    ctx.sendMessage(Message.raw("Voce deu a luz ao seu bebe!"));
                } else {
                    ctx.sendMessage(Message.raw("Voce nao esta gravida para forcar o parto."));
                }
            } else {
                TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
                SimNPCComponent nearestNPC = null;
                double minDistance = Double.MAX_VALUE;

                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityRef != null && npc.entityRef.isValid() && npc.pregnancy != null && npc.pregnancy.pregnant) {
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
                    ctx.sendMessage(Message.raw("Nenhuma NPC gravida encontrada por perto."));
                    return;
                }

                nearestNPC.pregnancy.startTick = world.getTick() - nearestNPC.pregnancy.durationTicks - 1;
                
                GrowthComponent child = LifecycleManager.birthBaby(nearestNPC, store, world.getTick());
                if (child != null) {
                    SimNPCPersistence.saveNPC(nearestNPC);
                    ctx.sendMessage(Message.raw(nearestNPC.name + " deu a luz a " + child.getFullName() + "!"));
                } else {
                    ctx.sendMessage(Message.raw("Falha no parto de " + nearestNPC.name));
                }
            }
        }
    }

    private static class SetStageSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> stageArg;

        public SetStageSubCommand() {
            super("setstage", "Define o estagio de crescimento do filho mais proximo");
            this.stageArg = this.withRequiredArg("stage", "BABY|TODDLER|CHILD|TEEN|ADULT", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String stageName = ctx.get(this.stageArg).toUpperCase();
            GrowthStage targetStage;
            try {
                targetStage = GrowthStage.valueOf(stageName);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Estagio invalido. Escolha entre: BABY, TODDLER, CHILD, TEEN, ADULT"));
                return;
            }

            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            GrowthComponent nearestChild = null;
            double minDistance = Double.MAX_VALUE;

            for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
                if (child.childId != null) {
                    Ref<EntityStore> childRef = world.getEntityStore().getRefFromUUID(child.childId);
                    if (childRef != null) {
                        TransformComponent childTransform = store.getComponent(childRef, TransformComponent.getComponentType());
                        if (playerTransform != null && childTransform != null) {
                            Vector3d pPos = playerTransform.getPosition();
                            Vector3d cPos = childTransform.getPosition();
                            double distSq = pPos.distanceSquared(cPos);
                            if (distSq < minDistance) {
                                minDistance = distSq;
                                nearestChild = child;
                            }
                        }
                    }
                }
            }

            if (nearestChild == null) {
                ctx.sendMessage(Message.raw("Nenhum filho ativo encontrado por perto."));
                return;
            }

            nearestChild.stage = targetStage;
            nearestChild.currentScale = targetStage.getScale();
            nearestChild.birthTick = world.getTick() - (targetStage.getStartDay() * PregnancyComponent.TICKS_PER_DAY);

            // Atualiza a escala visual do modelo da entidade filho
            Ref<EntityStore> childRef = world.getEntityStore().getRefFromUUID(nearestChild.childId);
            if (childRef != null) {
                PersistentModel pm = store.getComponent(childRef, PersistentModel.getComponentType());
                if (pm != null) {
                    ModelReference oldRef = pm.getModelReference();
                    ModelReference newRef = new ModelReference(oldRef.getModelAssetId(), nearestChild.currentScale, new HashMap<>());
                    store.replaceComponent(childRef, PersistentModel.getComponentType(), new PersistentModel(newRef));
                }
            }

            ctx.sendMessage(Message.raw("Estagio de " + nearestChild.getFullName() + " definido para " + targetStage.name() + " (escala: " + nearestChild.currentScale + ")."));
        }
    }

    private static class ForceMarrySubCommand extends AbstractPlayerCommand {
        public ForceMarrySubCommand() {
            super("marry", "Forca o casamento com o NPC mais proximo");
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
                ctx.sendMessage(Message.raw("Nenhum NPC por perto para casar."));
                return;
            }

            if (nearestNPC.family.isMarried) {
                ctx.sendMessage(Message.raw(nearestNPC.name + " ja esta casado(a)!"));
                return;
            }

            // Set relationship status
            Relationship playerRel = nearestNPC.getRelationship(playerRef.getUuid());
            playerRel.status = RelationshipStatus.MARRIED;
            playerRel.romance = 100;
            playerRel.friendship = 100;

            nearestNPC.family.marry(playerRef.getUuid(), null);
            SimNPCPersistence.saveNPC(nearestNPC);

            ctx.sendMessage(Message.raw("Voce agora esta casado com: " + nearestNPC.name + "!"));
        }
    }

    private static void openPlayerPregnancyPage(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, SimPlayerComponent playerComp) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new PlayerPregnancyPage(playerRef, player, playerComp));
        }
    }

    private static class PregnancySubCommand extends AbstractPlayerCommand {
        public PregnancySubCommand() {
            super("pregnancy", "Abre a tela de informacoes da gravidez do jogador");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            SimPlayerComponent playerComp = store.getComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE);
            if (playerComp == null) {
                playerComp = new SimPlayerComponent(playerRef.getUuid());
                store.addComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE, playerComp);
            }
            openPlayerPregnancyPage(ref, store, playerRef, playerComp);
        }
    }

    private static class DebugBedsSubCommand extends AbstractPlayerCommand {
        public DebugBedsSubCommand() {
            super("debugbeds", "Abre a tela de debug de camas");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            // Scan before opening, like housecheck and the SimDebug button already do. Without it
            // this command showed an empty list for any bed outside the radius swept when the
            // player joined, which reads as "nothing is registered" rather than "nothing here yet".
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) {
                BedWorldBootstrap.bootstrapLoadedRadius(world, tc.getPosition(), 32);
            }

            player.getPageManager().openCustomPage(ref, store, new SimBedDebugPage(playerRef, player));
        }
    }

    /**
     * Removes the SimTale NPC component from entities that were adopted by mistake.
     *
     * <p>Before the guard in SimTaleEventHandler, right-clicking any entity attached
     * SIM_NPC_COMPONENT_TYPE to it — cows included. The guard stops new cases but does nothing
     * about the ones already carrying the component, and those keep opening the villager panel and
     * running the villager routine.
     *
     * <p>The tell is {@code gender}: {@code SimNPCFactory.spawnNPC} always sets it, while the
     * adoption path built the component with the bare constructor, which leaves it null.
     */
    private static class ForgetSubCommand extends AbstractPlayerCommand {
        public ForgetSubCommand() {
            super("forget", "Remove o componente de NPC de entidades adotadas por engano (vacas, mobs)");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            List<SimNPCComponent> adopted = new ArrayList<>();
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.gender == null) {
                    adopted.add(npc);
                }
            }

            if (adopted.isEmpty()) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhuma entidade adotada por engano encontrada."));
                return;
            }

            int cleaned = 0;
            for (SimNPCComponent npc : adopted) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    Store<EntityStore> npcStore = npc.entityRef.getStore();
                    npcStore.tryRemoveComponent(npc.entityRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    npcStore.tryRemoveComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    // Leave the entity free to move: the routine may have parked it in a bed.
                    npcStore.tryRemoveComponent(npc.entityRef, MountedComponent.getComponentType());
                    npcStore.tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
                    NPCMovementHelper.setSleepingState(npc.entityRef, npcStore, false);
                }

                // Deleting the saved record is what makes this stick. SimTaleTickSystem re-attaches
                // any entity that still has one when its chunk loads, so stripping the component
                // alone would hand the cow straight back on the next reload.
                if (npc.entityId != null) {
                    com.cookieukw.SimTale.db.SimNPCPersistence.deleteNPC(npc.entityId);
                }

                SimTale.untrackNpc(npc);
                cleaned++;
            }

            ctx.sendMessage(Message.raw("[SimTale] " + cleaned
                    + " entidade(s) adotada(s) liberadas e removidas do banco. "
                    + "Entidades ainda nao carregadas so serao limpas quando aparecerem."));
        }
    }

    private static class DebugChestsSubCommand extends AbstractPlayerCommand {
        public DebugChestsSubCommand() {
            super("debugchests", "Abre a tela de debug de baus registrados");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            // Same scan debugbeds does. Without it this screen only ever showed chests that
            // happened to be within the radius swept when the player joined the world, which
            // reads as "nothing is registered" — the exact ambiguity this page exists to remove.
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null) {
                BedWorldBootstrap.bootstrapLoadedRadius(world, tc.getPosition(), 32);
            }

            player.getPageManager().openCustomPage(ref, store,
                    new com.cookieukw.SimTale.logic.SimChestDebugPage(playerRef, player));
        }
    }

    private static class DebugNearSubCommand extends AbstractPlayerCommand {
        public DebugNearSubCommand() {
            super("debugnear", "Mostra detalhes de blocos e entidades proximas");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.raw("Erro: TransformComponent nulo."));
                return;
            }

            Vector3d pos = tc.getPosition();
            int px = (int) Math.floor(pos.x);
            int py = (int) Math.floor(pos.y);
            int pz = (int) Math.floor(pos.z);

            ctx.sendMessage(Message.raw("--- DIAGNOSTICO PROXIMO (Sua Pos: " + px + "," + py + "," + pz + ") ---"));

            // 1. Scan blocks in 3x3x3
            ctx.sendMessage(Message.raw("Blocos proximos:"));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType type = world.getBlockType(px + dx, py + dy, pz + dz);
                        if (type != null && type.getId() != null && !type.getId().equalsIgnoreCase("Empty")) {
                            ctx.sendMessage(Message.raw("  Block (" + dx + "," + dy + "," + dz + "): ID='" + type.getId() + "'"));
                        }
                    }
                }
            }

            // 2. Scan all ACTIVE_NPCS near the player
            ctx.sendMessage(Message.raw("NPCs Ativos proximos:"));
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null) {
                    TransformComponent npcTc = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (npcTc != null) {
                        double dist = pos.distance(npcTc.getPosition());
                        if (dist <= 15.0) {
                            ctx.sendMessage(Message.raw("  NPC: Name='" + npc.name + "' Dist=" + String.format("%.2f", dist) + " (id=" + npc.entityId + ")"));
                        }
                    }
                }
            }
        }
    }

    private static class SetMoodSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> moodArg;
        private final OptionalArg<Double> intensityArg;

        public SetMoodSubCommand() {
            super("setmood", "Define o humor/expressao do NPC mais proximo");
            this.moodArg = this.withRequiredArg("mood", "NEUTRAL|HAPPY|ANGRY|SAD|SCARED|SLEEPY|EXCITED|BORED", ArgTypes.STRING);
            this.intensityArg = this.withOptionalArg("intensity", "Intensidade da emocao (0.0 a 1.0)", ArgTypes.DOUBLE);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String moodName = ctx.get(this.moodArg).toUpperCase();
            Mood targetMood;
            try {
                targetMood = Mood.valueOf(moodName);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Humor invalido. Escolha entre: NEUTRAL, HAPPY, ANGRY, SAD, SCARED, SLEEPY, EXCITED, BORED"));
                return;
            }

            Double intensityVal = ctx.get(this.intensityArg);
            float intensity = intensityVal != null ? intensityVal.floatValue() : 1.0f;

            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null) {
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
                ctx.sendMessage(Message.raw("Nenhum NPC por perto."));
                return;
            }

            nearestNPC.setEmotion(targetMood, intensity, "command", world.getTick());
            SimNPCPersistence.saveNPC(nearestNPC);

            ctx.sendMessage(Message.raw("Humor de " + nearestNPC.name + " definido para " + targetMood.name() + " com intensidade " + intensity + "."));
        }
    }

    private static class SearchSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        public SearchSubCommand() {
            super("search", "Procura um NPC no banco de dados pelo nome");
            this.nameArg = this.withRequiredArg("name", "Nome do NPC", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String query = ctx.get(this.nameArg).toLowerCase();
            playerRef.sendMessage(Message.translation("general.cmd.search.searching").param("query", query));
            
            CompletableFuture.runAsync(() -> {
                try {
                    // "simtale" shell, not Caskara's "default" — this always returned empty.
                    List<SimNPCData> allData = SimNPCPersistence.listAll();
                    if (allData == null || allData.isEmpty()) {
                        playerRef.sendMessage(Message.translation("general.cmd.search.empty"));
                        return;
                    }
                    int count = 0;
                    for (SimNPCData data : allData) {
                        if (data.name != null && data.name.toLowerCase().contains(query)) {
                            playerRef.sendMessage(Message.translation("general.cmd.search.found")
                                .param("name", data.name.toLowerCase())
                                .param("id", data.id.toLowerCase())
                                .param("profession", data.profession != null 
                                    ? Message.raw(data.profession.name().toLowerCase()) 
                                    : Message.translation("general.profession.none")));
                            count++;
                        }
                    }
                    playerRef.sendMessage(Message.translation("general.cmd.search.finished").param("count", count));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.translation("general.cmd.search.error").param("error", e.getMessage()));
                }
            });
        }
    }

    private static class ToggleAiSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> stateArg;

        public ToggleAiSubCommand() {
            super("toggleai", "Ativa ou desativa o uso de Inteligencia Artificial para interacoes");
            this.stateArg = this.withOptionalArg("state", "on|off", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            
            com.cookieukw.SimTale.ai.AiConfig config = com.cookieukw.SimTale.ai.AiConfigManager.getConfig();
            String stateStr = ctx.get(this.stateArg);

            if (stateStr == null) {
                // Toggle state
                config.enabled = !config.enabled;
            } else if (stateStr.equalsIgnoreCase("on") || stateStr.equalsIgnoreCase("true")) {
                config.enabled = true;
            } else if (stateStr.equalsIgnoreCase("off") || stateStr.equalsIgnoreCase("false")) {
                config.enabled = false;
            }

            com.cookieukw.SimTale.ai.AiConfigManager.save();
            String status = config.enabled ? "ATIVADO" : "DESATIVADO";
            ctx.sendMessage(Message.raw("O uso de IA Generativa para conversas com NPCs foi: " + status));
        }
    }

    private static class HouseCheckSubCommand extends AbstractPlayerCommand {
        public HouseCheckSubCommand() {
            super("housecheck", "Verifica a validade estrutural e a mobilia da casa mais proxima");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.raw("Error: TransformComponent is null."));
                return;
            }
            Vector3d pos = tc.getPosition();
            
            // Scan and register loaded beds in a 12-block radius to ensure BedRegistry.BEDS is populated instantly
            BedWorldBootstrap.bootstrapLoadedRadius(world, pos, 12);
            
            com.cookieukw.SimTale.db.SimBedData.BedPos nearestBed = null;
            double minDist = Double.MAX_VALUE;
            synchronized (BedRegistry.BEDS) {
                for (com.cookieukw.SimTale.db.SimBedData.BedPos bp : BedRegistry.BEDS) {
                    double dx = bp.x - pos.x;
                    double dy = bp.y - pos.y;
                    double dz = bp.z - pos.z;
                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq < minDist) {
                        minDist = distSq;
                        nearestBed = bp;
                    }
                }
            }
            
            if (nearestBed == null) {
                ctx.sendMessage(Message.raw("No registered beds found nearby!"));
                return;
            }
            
            if (minDist > 16 * 16) {
                ctx.sendMessage(Message.raw("No registered beds within a 16-block radius!"));
                return;
            }
            
            HouseBlockPos houseBed = new HouseBlockPos(nearestBed.x, nearestBed.y, nearestBed.z);
            HouseManager.HouseScanResult rawScan = HouseManager.scanHouseFromBed(world, houseBed);
            
            Message yesMsg = Message.translation("general.yes");
            Message noMsg = Message.translation("general.no");
            
            Message debugMsg = Message.translation("general.house.debug.status")
                .param("visited", rawScan.interiorBlocks().size())
                .param("doors", rawScan.doorBlocks().size())
                .param("chests", rawScan.chestBlocks().size())
                .param("overflowed", rawScan.overflowed() ? yesMsg : noMsg)
                .param("unloaded", rawScan.hitUnloaded() ? yesMsg : noMsg)
                .color("yellow");
            ctx.sendMessage(debugMsg);

            HouseManager.HouseCompatibilityResult result = HouseManager.checkFullCompatibility(world, houseBed, playerRef.getUuid());
            ctx.sendMessage(HouseManager.buildCompatibilityReport(result));

            // Update existing house's structure dynamically if the house is registered
            HouseData existingHouse = HouseManager.HOUSES_BY_ID.values().stream()
                .filter(h -> h.bedPos != null && h.bedPos.equals(houseBed))
                .findFirst()
                .orElse(null);
            if (existingHouse != null) {
                existingHouse.interior = rawScan.interiorBlocks();
                existingHouse.doors = rawScan.doorBlocks();
                existingHouse.chests = rawScan.chestBlocks();
                HouseManager.registerHouse(existingHouse);
                ctx.sendMessage(Message.raw("[House Debug] Registered house updated in persistence with " 
                    + rawScan.doorBlocks().size() + " door(s) and " + rawScan.chestBlocks().size() + " chest(s).").color("green"));
            }
        }
    }

    private static class ChestCheckSubCommand extends AbstractPlayerCommand {
        public ChestCheckSubCommand() {
            super("chestcheck", "Verifica o registro e a posse do bau mais proximo");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.raw("Erro: TransformComponent nulo."));
                return;
            }
            Vector3d pos = tc.getPosition();

            // Scan first, like housecheck already did. Otherwise this command reports "nothing
            // registered" for a chest that simply had not been picked up yet.
            BedWorldBootstrap.bootstrapLoadedRadius(world, pos, 16);

            HouseBlockPos nearestChest = null;
            double minDist = Double.MAX_VALUE;

            synchronized (ChestRegistry.CHESTS) {
                for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                    double dx = cp.x - pos.x;
                    double dy = cp.y - pos.y;
                    double dz = cp.z - pos.z;
                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq < minDist) {
                        minDist = distSq;
                        nearestChest = cp;
                    }
                }
            }

            if (nearestChest == null) {
                ctx.sendMessage(Message.raw("Nenhum bau registrado no ChestRegistry."));
                return;
            }

            if (minDist > 16 * 16) {
                ctx.sendMessage(Message.raw("Nenhum bau registrado proximo (raio de 16 blocos)!"));
                return;
            }

            String msg = "[ChestCheck] Baú localizado em (" + nearestChest.x + ", " + nearestChest.y + ", " + nearestChest.z + ")\n";

            UUID houseId = HouseManager.BLOCK_TO_HOUSE_ID.get(nearestChest);
            if (houseId != null) {
                HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
                if (house != null) {
                    msg += "Residência: " + houseId + "\n";
                    msg += "Proprietários: " + String.join(", ", house.owners);
                } else {
                    msg += "Erro: Vinculado à casa " + houseId + " mas dados da casa não encontrados.";
                }
            } else {
                msg += "Tipo: Baú Público (Não pertence a nenhuma casa cadastrada)";
            }

            ctx.sendMessage(Message.raw(msg));
        }
    }

    private static class ForceEatSubCommand extends AbstractPlayerCommand {
        public ForceEatSubCommand() {
            super("forceeat", "Força o NPC mais próximo a ir comer de um baú");
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
                ctx.sendMessage(Message.raw("Nenhum NPC por perto."));
                return;
            }

            nearestNPC.needs.hunger = 0f;
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
            super("forcework", "Força o NPC mais próximo a ir trabalhar (colher ou caçar)");
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
                ctx.sendMessage(Message.raw("Nenhum NPC por perto."));
                return;
            }

            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                ai.currentTask = RoutineAIComponent.TaskType.IDLE;
                ai.forcedByDebug = true;
                ai.taskStartTime = world.getTick();
                store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
                ctx.sendMessage(Message.raw("Forçando " + nearestNPC.name + " a ir trabalhar! Profissão: " + nearestNPC.profession.ptName));
            } else {
                ctx.sendMessage(Message.raw("IA do NPC não ativa."));
            }
        }
    }

    private static class ForcePlantSubCommand extends AbstractPlayerCommand {
        public ForcePlantSubCommand() {
            super("forceplant", "Força o NPC Fazendeiro mais próximo a plantar em terras aradas próximas");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid() && npc.profession == com.cookieukw.SimTale.core.Profession.FARMER) {
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

            // Ensure they have seeds
            InventoryComponent.Storage storage = store.getComponent(nearestNPC.entityRef, InventoryComponent.Storage.getComponentType());
            if (storage != null && storage.getInventory() != null) {
                ItemContainer inv = storage.getInventory();
                String seed = com.cookieukw.SimTale.systems.NPCWorkHelper.findSeedInInventory(inv);
                if (seed == null) {
                    // Give them 5 carrot seeds to start
                    inv.addItemStack(new ItemStack("Plant_Seeds_Carrot", 5));
                    ctx.sendMessage(Message.translation("general.cmd.forceplant.seeds_added").param("name", nearestNPC.name));
                }
            }

            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                TransformComponent npcTransform = store.getComponent(nearestNPC.entityRef, TransformComponent.getComponentType());
                Vector3i farmPos = com.cookieukw.SimTale.systems.NPCWorkHelper.scanForFarmland(npcTransform.getPosition(), world);
                if (farmPos != null) {
                    ai.targetBlockPosition = farmPos;
                    ai.currentTask = RoutineAIComponent.TaskType.MOVING_TO_WORK;
                    ai.forcedByDebug = true;
                    ai.taskStartTime = world.getTick();
                    store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
                    ctx.sendMessage(Message.translation("general.cmd.forceplant.success").param("name", nearestNPC.name).param("pos", farmPos.toString()));
                } else {
                    ctx.sendMessage(Message.translation("general.cmd.forceplant.farmland_not_found").param("name", nearestNPC.name));
                }
            } else {
                ctx.sendMessage(Message.translation("general.cmd.forceplant.ai_inactive"));
            }
        }
    }

    private static class SetGenderSubCommand extends AbstractPlayerCommand {
        public SetGenderSubCommand() {
            super("setgender", "Abre a tela de seleção de gênero para o jogador");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(Message.raw("Erro: Jogador não encontrado."));
                return;
            }

            SimPlayerComponent simPlayer = store.getComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE);
            if (simPlayer == null) {
                simPlayer = com.cookieukw.SimTale.db.SimPlayerPersistence.loadPlayer(playerRef.getUuid());
                if (simPlayer == null) {
                    simPlayer = new SimPlayerComponent(playerRef.getUuid());
                }
                store.addComponent(ref, SimTale.SIM_PLAYER_COMPONENT_TYPE, simPlayer);
            }

            player.getPageManager().openCustomPage(ref, store, new com.cookieukw.SimTale.logic.PlayerGenderPage(playerRef, player, simPlayer));
            ctx.sendMessage(Message.raw("Abrindo painel de seleção de gênero..."));
        }
    }
}
