package com.cookieukw.SimTale;

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
import java.util.Map;
import java.util.ArrayList;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.ai.AiConfig;
import com.cookieukw.SimTale.ai.AiConfigManager;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.Gender;
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
import com.cookieukw.SimTale.systems.FurnitureAnchorHelper;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
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
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.Objects;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import java.util.LinkedHashMap;

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
        this.addSubCommand(new DebugLogSubCommand());
        this.addSubCommand(new ForcePregSubCommand());
        this.addSubCommand(new ForceBirthSubCommand());
        this.addSubCommand(new SetStageSubCommand());
        this.addSubCommand(new ForceMarrySubCommand());
        this.addSubCommand(new DebugCommands.DebugBedsSubCommand());
        this.addSubCommand(new DebugCommands.GraveyardSubCommand());
        this.addSubCommand(new DebugCommands.PutDownSubCommand());
        this.addSubCommand(new DebugCommands.DebugChestsSubCommand());
        this.addSubCommand(new DebugCommands.ForgetSubCommand());
        this.addSubCommand(new PregnancySubCommand());
        this.addSubCommand(new DebugCommands.DebugNearSubCommand());
        this.addSubCommand(new DebugCommands.VillageSubCommand());
        this.addSubCommand(new SetMoodSubCommand());
        this.addSubCommand(new SearchSubCommand());
        this.addSubCommand(new ToggleAiSubCommand());
        this.addSubCommand(new AiStatusSubCommand());
        this.addSubCommand(new HouseCheckSubCommand());
        this.addSubCommand(new ChestCheckSubCommand());
        this.addSubCommand(new ForceEatSubCommand());
        this.addSubCommand(new ForceWorkSubCommand());
        this.addSubCommand(new ForceKillSubCommand());
        this.addSubCommand(new SetProfessionSubCommand());
        this.addSubCommand(new ForcePlantSubCommand());
        this.addSubCommand(new SetGenderSubCommand());
        this.addSubCommand(new CamDebugSubCommand());
        this.addSubCommand(new UnstickSubCommand());
        this.addSubCommand(new RescanSubCommand());
        this.addSubCommand(new BabyStageSubCommand());
        this.addSubCommand(new ForcePlaceBabySubCommand());
        this.addSubCommand(new ForceConstructSubCommand());
        this.addSubCommand(new NpcStateSubCommand());
        this.addSubCommand(new ForceBabySwapSubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // If no subcommand is specified, show usage
        sendUsage(ctx);
    }

    private static void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Usage: /simtale <spawn|interact|tpall|clearall|forcespawn|forcesleep|forcepreg|forcebirth|setstage|marry|debugbeds|pregnancy|debugnear|setmood|search|toggleai|housecheck|chestcheck|forceeat|forcework|forceplant|setgender|camdebug|unstick|npcstate|forcebabyswap|forcekill|aistatus|setprofession|rescan|babystage|forceplacebaby|forceconstruct|graveyard|putdown>"));
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
            super("npcstate", "Shows the internal state of the nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) {
                ctx.sendMessage(Message.raw("[SimTale] No player transform."));
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
                ctx.sendMessage(Message.raw("[SimTale] No active NPCs nearby."));
                return;
            }

            Ref<EntityStore> nref = best.entityRef;
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(best.name).append(" (").append(String.format("%.1f", Math.sqrt(bestDist))).append("m) ===");

            StateSupport ss = StateSupport.get(nref, store);
            sb.append("\n  role state: ").append(ss != null ? ss.getStateName() : "<no StateSupport>");
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
                sb.append("\n  anim: <no ActiveAnimationComponent>");
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
              .append("  interactingUI=").append(best.isInteractingViaUI);

            NPCEntity ne = store.getComponent(nref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (ne != null) sb.append("\n  leash=").append(ne.getLeashPoint());

            RoutineAIComponent ai = store.getComponent(nref, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                sb.append("\n  ai task=").append(ai.currentTask)
                  .append("  target=").append(ai.targetBlockPosition)
                  .append("  lastLeash=").append(ai.lastLeashPos)
                  .append("\n  scheduledSleep=").append(ai.sleepingOnSchedule)
                  .append("  forcedByDebug=").append(ai.forcedByDebug)
                  .append("  sinceWake=")
                  .append(ai.lastWakeTick == 0 ? "never" : String.valueOf(world.getTick() - ai.lastWakeTick));

                // Ticks left on each search backoff, which is the difference between "idle because
                // it has nothing to do" and "idle because a need it cannot satisfy keeps pulling it
                // back". An NPC that stood still for an entire session was unreadable without
                // these: every branch involved fails silently.
                long tick = world.getTick();
                sb.append("\n  cooldowns: bed=").append(Math.max(0, ai.nextBedSearchTick - tick))
                  .append(" food=").append(Math.max(0, ai.nextFoodSearchTick - tick))
                  .append(" bath=").append(Math.max(0, ai.nextBathSearchTick - tick));
            }

            // The needs drive every IDLE decision, so without them the dump shows the outcome and
            // hides the reason.
            sb.append("\n  needs: hunger=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.HUNGER_ID)))
              .append(" energy=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.ENERGY_ID)))
              .append(" hygiene=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.HYGIENE_ID)))
              .append(" fun=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.FUN_ID)))
              .append(" social=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.SOCIAL_ID)));

            // Everything needed to tell "guard on the day shift" apart from "stuck in bed": the
            // profession, whether the world clock says this NPC's sleep window is open, and the
            // raw day progress behind that answer.
            sb.append("\n  profession=").append(best.profession)
              .append("  sleepWindow=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.isSleepPeriod(best, world))
              .append("  night=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.isNight(world))
              // currentHour returns 0..24, not the 0..1 progress it is derived from.
              .append("  hour=")
              .append(com.cookieukw.SimTale.systems.NPCSleepHelper.currentHour(world))
              .append("\n  bed=").append(best.bedLocation);

            ctx.sendMessage(Message.raw(sb.toString()));
            HytaleLogger.forEnclosingClass().atInfo().log(sb.toString());
        }

        private static String fmt(float value) {
            return String.format("%.0f", value);
        }
    }

    /** Toggles the verbose camera dump printed when the NPC interaction page opens. */
    private static class CamDebugSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> stateArg;

        public CamDebugSubCommand() {
            super("camdebug", "Toggles interaction camera debug");
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
            ctx.sendMessage(Message.raw("[SimTale] Camera debug: "
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
            super("unstick", "Unstucks frozen NPCs and the player stuck in bed");
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
                    // setSleepingState only resets the MovementStates flags (physics/hitbox).
                    // The Sleep clip itself plays on a separate Status animation slot
                    // (RoutineAISystem's SLEEPING entry) that this never stopped — she kept
                    // playing the lying-down animation while walking around. The natural WAKING
                    // completion (RoutineAISystem.java) stops it the same way; unstick needs to
                    // do the same rescue, not just clear the task state.
                    AnimationUtils.stopAnimation(npc.entityRef, AnimationSlot.Status, true, npc.entityRef.getStore());
                    NPCEntity npcEntityComponent = npc.entityRef.getStore().getComponent(npc.entityRef, Objects.requireNonNull(NPCEntity.getComponentType()));
                    if (npcEntityComponent != null) {
                        StateSupport stateSupport = StateSupport.get(npc.entityRef, npc.entityRef.getStore());
                        if (stateSupport != null) {
                            stateSupport.setState(npc.entityRef, "Idle", null, npc.entityRef.getStore());
                        }
                    }
                    NPCMovementHelper.playAnim(npc.entityRef, AnimationSlot.Status, "Characters/Animations/Default/Idle.blockyanim", "Idle", npc.entityRef.getStore());
                    touched = true;
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
            ctx.sendMessage(Message.raw("[SimTale] Unstuck " + fixed + " de "
                    + SimTale.ACTIVE_NPCS.size() + " NPCs ativos."));
        }
    }

    /**
     * Re-runs the bed/chest/work-post/farmland/crop world scan centered on wherever the player
     * is standing right now, instead of only wherever they were standing at world join. Testing
     * an area far from spawn otherwise means that area's blocks stay invisible to the registries
     * until the next full rejoin from right on top of them.
     */
    private static class RescanSubCommand extends AbstractPlayerCommand {
        public RescanSubCommand() {
            super("rescan", "Re-scans beds/chests/work posts/farmland/crops around your current position");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) {
                ctx.sendMessage(Message.raw("[SimTale] No player transform."));
                return;
            }
            com.cookieukw.SimTale.systems.BedWorldBootstrap.bootstrapLoadedRadius(world, pt.getPosition(), 32);
            ctx.sendMessage(Message.raw("[SimTale] Rescan done around your position. Check the server log for counts."));
        }
    }

    // --- SUBCOMMANDS ---

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
                SimNPCPersistence.deleteNPC(npc.entityId);
            }
            SimTale.clearActiveNpcs();

            // Sweep the database as well. Removing the entities is not enough: records for NPCs
            // that are not currently tracked (unloaded chunks, entities already gone, leftovers
            // from earlier sessions) would otherwise survive and be resurrected. This used to be
            // guaranteed to leak, because Caskara.delete() targeted the "default" shell while
            // the records live in "simtale".
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
     * Turns the mod's debug messages on and off.
     *
     * <p>Much of SimTale's diagnosis is in {@code LOGGER.debug} calls, which {@link SimLog} discards
     * by default — otherwise the server log would fill with per-tick scans (bed search, water,
     * chest, door). Without a way to turn them on in-game, investigating anything required recompiling.
     */
    private static class DebugLogSubCommand extends AbstractPlayerCommand {
        public DebugLogSubCommand() {
            super("debug", "Toggles SimTale debug messages in the log");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            SimLog.debugEnabled = !SimLog.debugEnabled;
            ctx.sendMessage(Message.raw("[SimTale] debug log "
                    + (SimLog.debugEnabled ? "ON" : "OFF")
                    + (SimLog.debugEnabled ? " — remember to turn it off later, it is verbose." : "")));
        }
    }

    private static class ForcePregSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> targetArg;

        public ForcePregSubCommand() {
            super("forcepreg", "Forces pregnancy on the executing player or nearest female NPC");
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
                // No real father to reference for a solo /simtale forcepreg --target=me — a
                // random UUID here used to silently fail every lookup that tried to resolve it
                // against a real NPC (the birth-time "add child to father's family" loop, any
                // future "who's the father" check), instead of the fatherId just being absent
                // like it legitimately is in this case.
                playerComp.pregnancy.start(null, world.getTick());
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
                    ctx.sendMessage(Message.raw("No female NPC found nearby."));
                    return;
                }

                if (nearestNPC.pregnancy != null && nearestNPC.pregnancy.pregnant) {
                    ctx.sendMessage(Message.raw(nearestNPC.name + " is already pregnant!"));
                    return;
                }

                nearestNPC.family.marry(playerRef.getUuid(), null);
                nearestNPC.getRelationship(playerRef.getUuid()).romance = 100;

                boolean success = LifecycleManager.startPregnancy(nearestNPC, playerRef.getUuid(), world.getTick());
                if (success) {
                    SimNPCPersistence.saveNPC(nearestNPC);
                    ctx.sendMessage(Message.raw("Pregnancy successfully forced for: " + nearestNPC.name));
                } else {
                    ctx.sendMessage(Message.raw("Failed to start pregnancy for: " + nearestNPC.name));
                }
            }
        }
    }

    private static class ForceBirthSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> targetArg;

        public ForceBirthSubCommand() {
            super("forcebirth", "Forces immediate birth on the executing player or nearest NPC");
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
                    ctx.sendMessage(Message.raw("You gave birth to your baby!"));
                } else {
                    ctx.sendMessage(Message.raw("You are not pregnant to force birth."));
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
                    ctx.sendMessage(Message.raw("No pregnant NPC found nearby."));
                    return;
                }

                nearestNPC.pregnancy.startTick = world.getTick() - nearestNPC.pregnancy.durationTicks - 1;
                
                GrowthComponent child = LifecycleManager.birthBaby(nearestNPC, store, world.getTick());
                if (child != null) {
                    SimNPCPersistence.saveNPC(nearestNPC);
                    ctx.sendMessage(Message.raw(nearestNPC.name + " deu a luz a " + child.getFullName() + "!"));
                } else {
                    ctx.sendMessage(Message.raw("Birth failed for " + nearestNPC.name));
                }
            }
        }
    }

    private static class SetStageSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> stageArg;

        public SetStageSubCommand() {
            super("setstage", "Sets the growth stage of the nearest child");
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
                ctx.sendMessage(Message.raw("Invalid stage. Choose from: BABY, TODDLER, CHILD, TEEN, ADULT"));
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
                // Says how many candidates there were: an empty ACTIVE_CHILDREN ("no children in
                // this world at all") and a full one whose entities are out of reach are the same
                // message otherwise, and they need opposite fixes.
                String miss = "[SimTale] setstage: nenhum filho ativo por perto (ACTIVE_CHILDREN="
                        + LifecycleManager.ACTIVE_CHILDREN.size() + ")";
                HytaleLogger.forEnclosingClass().atInfo().log(miss);
                ctx.sendMessage(Message.raw(miss));
                return;
            }

            nearestChild.stage = targetStage;
            nearestChild.birthTick = world.getTick() - (targetStage.getStartDay() * PregnancyComponent.TICKS_PER_DAY);

            // The scale comes from GrowthManager, not from GrowthStage.getScale().
            //
            // There were two different scale tables and this command used the wrong one. The enum
            // says 0.35/0.50/0.70/0.90/1.00; GrowthManager.calculateTargetScale interpolates inside
            // each stage and yields 0.45/0.55/0.75 at the start of TODDLER/CHILD/TEEN. Since
            // GrowthTickSystem recomputes with its own table every tick, whatever this command
            // wrote was overwritten within a frame — the command looked like it did nothing, or
            // like it resized by a bit and then refused to go back.
            //
            // Setting the age above and asking the growth code for the matching scale leaves one
            // source of truth, so the command and the passage of time can no longer disagree.
            nearestChild.currentScale = LifecycleManager.calculateTargetScale(nearestChild, world.getTick());

            Ref<EntityStore> childRef = world.getEntityStore().getRefFromUUID(nearestChild.childId);
            if (childRef != null && childRef.isValid()) {
                LifecycleManager.applyVisualScale(childRef, nearestChild.currentScale);
            }

            // Logged, not only sent to chat: seven setstage runs in one session left no trace in
            // the server log at all, so there was no way to tell a command that silently found no
            // child from one that ran and was undone a tick later.
            String report = "[SimTale] setstage: " + nearestChild.getFullName() + " -> " + targetStage.name()
                    + " (escala " + nearestChild.currentScale + ", idade " + nearestChild.getAgeDays(world.getTick()) + "d)";
            HytaleLogger.forEnclosingClass().atInfo().log(report);
            ctx.sendMessage(Message.raw(report));
        }
    }

    /**
     * Debug-only: {@code setstage} only finds a child already spawned as a live entity
     * ({@code LifecycleManager.ACTIVE_CHILDREN} entries resolve through
     * {@code world.getEntityStore().getRefFromUUID}), but a newborn "Baby" item held by the
     * player has no live entity at all — {@code birthPlayerBaby} spawns one only long enough to
     * mint a UUID, then removes it immediately, and {@code SimTaleEventHandler} additionally
     * refuses to place a `stage == BABY` item back down at all. There was no way to advance a
     * carried baby's stage without waiting for real time to pass. This edits the carried item's
     * backing {@link GrowthComponent} directly, by its {@code childId} metadata, with no entity
     * spawn involved.
     */
    private static class BabyStageSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> stageArg;

        public BabyStageSubCommand() {
            super("babystage", "Sets the growth stage of the Baby item held in your hand (no need to place it down first)");
            this.stageArg = this.withRequiredArg("stage", "BABY|TODDLER|CHILD|TEEN|ADULT", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            GrowthStage targetStage;
            try {
                targetStage = GrowthStage.valueOf(ctx.get(this.stageArg).toUpperCase());
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Invalid stage. Choose from: BABY, TODDLER, CHILD, TEEN, ADULT"));
                return;
            }

            ItemStack heldItem = InventoryComponent.getItemInHand(store, ref);
            if (heldItem == null || !heldItem.getItemId().equals("Baby")) {
                ctx.sendMessage(Message.raw("[SimTale] Segure o item 'Baby' na mao para usar este comando."));
                return;
            }

            String childIdStr = heldItem.getFromMetadataOrNull("childId", Codec.STRING);
            if (childIdStr == null) {
                ctx.sendMessage(Message.raw("[SimTale] Este item 'Baby' nao tem childId — provavelmente corrompido."));
                return;
            }
            UUID childId;
            try {
                childId = UUID.fromString(childIdStr);
            } catch (IllegalArgumentException badId) {
                ctx.sendMessage(Message.raw("[SimTale] childId invalido no item."));
                return;
            }

            GrowthComponent childComp = null;
            for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
                if (child.childId != null && child.childId.equals(childId)) {
                    childComp = child;
                    break;
                }
            }
            if (childComp == null) {
                childComp = Caskara.load("child_" + childId, GrowthComponent.class);
                if (childComp != null) {
                    LifecycleManager.ACTIVE_CHILDREN.add(childComp);
                }
            }
            if (childComp == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nao encontrei os dados desse bebe (childId=" + childId + ")."));
                return;
            }

            // No live entity to update — the point of this command is that one doesn't exist
            // yet. The stage/scale take effect the moment it's placed down (SimTaleEventHandler
            // reads childComp.currentScale/stage at that point) or picked up again.
            childComp.stage = targetStage;
            childComp.currentScale = targetStage.getScale();
            childComp.birthTick = world.getTick() - (targetStage.getStartDay() * PregnancyComponent.TICKS_PER_DAY);
            Caskara.save("child_" + childId, childComp);

            ctx.sendMessage(Message.raw("[SimTale] Stage do bebe carregado (" + childComp.getFullName() + ") definido para "
                    + targetStage.name() + " (escala: " + childComp.currentScale + "). Ja pode colocar no chao."));
        }
    }

    /**
     * Debug-only: places the "Baby" item held in the player's hand on the ground 2 blocks away,
     * without needing a right-click on a block.
     * <p>
     * The right-click flow ({@code SimTaleEventHandler}, listening for {@code PlayerMouseButtonEvent})
     * turned out to be unreliable enough during this session's testing that it needed a direct
     * alternative: the registration used {@code EventRegistry.register(...)} instead of
     * {@code .registerGlobal(...)} — the only listener in either this project or RuneCore doing
     * that for this event type, and the only one that never fired at all (confirmed with a log
     * at the very top of the handler that never printed once across a full testing session of
     * right-clicks). That registration bug is fixed now, but a direct command is still faster to
     * test with and doesn't depend on the click pipeline working at all, so it's kept — same
     * reasoning as every other {@code force*} debug command in this file.
     */
    private static class ForcePlaceBabySubCommand extends AbstractPlayerCommand {
        public ForcePlaceBabySubCommand() {
            super("forceplacebaby", "Places the Baby item you're holding on the ground, bypassing the right-click flow");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            ItemStack heldItem = InventoryComponent.getItemInHand(store, ref);
            if (heldItem == null || !"Baby".equals(heldItem.getItemId())) {
                ctx.sendMessage(Message.raw("[SimTale] Segure o item 'Baby' na mao para usar este comando."));
                return;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nao foi possivel obter sua posicao."));
                return;
            }
            // Copied first: joml's add mutates in place, so offsetting the live transform vector
            // teleports the player instead of picking a spot beside them.
            Vector3d spawnPos = new Vector3d(transform.getPosition()).add(2, 0, 2);

            boolean placed = SimTaleEventHandler.placeBabyFromHeldItem(store, ref, playerRef, heldItem, spawnPos);
            if (!placed) {
                ctx.sendMessage(Message.raw("[SimTale] Nao foi possivel colocar o bebe (childId invalido, dados nao encontrados, ou ainda no estagio BABY)."));
            }
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

    /**
     * Debug-only: skips the {@code BabyCareData.nextSwapAllowedTime} 4-hour real-time cooldown
     * instead of reimplementing the swap. The next {@code BabyCareTickSystem} tick (every 30
     * ticks) does the actual swap once the player stands within 4 blocks of the spouse NPC, so
     * this only unblocks the wait — it does not duplicate the swap logic itself.
     *
     * <p>A newborn baby has no world entity (it is removed on birth — only an inventory item plus
     * {@code BabyCareData} on disk), so this can't search by nearby entity like {@code setstage}
     * does. It looks first at the player's own inventory for the "Baby" item, then at nearby NPCs
     * currently carrying one via {@code BabyCareManager.getCarriedBabies}.
     */
    private static class ForceBabySwapSubCommand extends AbstractPlayerCommand {
        public ForceBabySwapSubCommand() {
            super("forcebabyswap", "Skips the custody swap cooldown for a baby you hold or a nearby NPC holds");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            UUID childId = null;
            String source = null;

            CombinedItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
            for (short slot = 0; slot < inventory.getCapacity(); slot++) {
                ItemStack item = inventory.getItemStack(slot);
                if (item != null && item.getItemId().equals("Baby")) {
                    String childIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                    if (childIdStr != null) {
                        childId = UUID.fromString(childIdStr);
                        source = "your inventory";
                        break;
                    }
                }
            }

            if (childId == null) {
                TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
                SimNPCComponent nearestCarrier = null;
                double minDistance = Double.MAX_VALUE;

                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityRef == null || !npc.entityRef.isValid()) continue;
                    if (BabyCareManager.getCarriedBabies(npc.entityId).isEmpty()) continue;
                    TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (playerTransform == null || npcTransform == null) continue;
                    double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                    if (distSq < minDistance) {
                        minDistance = distSq;
                        nearestCarrier = npc;
                    }
                }

                if (nearestCarrier != null) {
                    childId = BabyCareManager.getCarriedBabies(nearestCarrier.entityId).get(0);
                    source = nearestCarrier.name + "'s inventory";
                }
            }

            if (childId == null) {
                ctx.sendMessage(Message.raw("No baby found in your inventory or a nearby NPC's."));
                return;
            }

            BabyCareData care = BabyCareManager.load(childId);
            if (care == null) {
                ctx.sendMessage(Message.raw("Found the baby item but no matching BabyCareData for it."));
                return;
            }

            care.nextSwapAllowedTime = System.currentTimeMillis();
            BabyCareManager.save(care);

            ctx.sendMessage(Message.raw("Swap cooldown cleared for the baby currently in " + source
                + ". Stand within 4 blocks of the spouse NPC and wait a couple seconds for the next tick to swap it."));
        }
    }

    private static class ForceMarrySubCommand extends AbstractPlayerCommand {
        public ForceMarrySubCommand() {
            super("marry", "Forces marriage with the nearest NPC");
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
                ctx.sendMessage(Message.raw("No NPCs nearby to marry."));
                return;
            }

            if (nearestNPC.family.isMarried) {
                ctx.sendMessage(Message.raw(nearestNPC.name + " is already married!"));
                return;
            }

            // Set relationship status
            Relationship playerRel = nearestNPC.getRelationship(playerRef.getUuid());
            playerRel.status = RelationshipStatus.MARRIED;
            playerRel.romance = 100;
            playerRel.friendship = 100;

            nearestNPC.family.marry(playerRef.getUuid(), null);
            SimNPCPersistence.saveNPC(nearestNPC);

            ctx.sendMessage(Message.raw("You are now married to: " + nearestNPC.name + "!"));
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
            super("pregnancy", "Opens the player pregnancy info screen");
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

    // debugbeds, forget, debugchests and debugnear now live in DebugCommands.

    private static class SetMoodSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> moodArg;
        private final OptionalArg<String> intensityArg;

        public SetMoodSubCommand() {
            super("setmood", "Sets the mood/expression of the nearest NPC");
            this.moodArg = this.withRequiredArg("mood", "NEUTRAL|HAPPY|ANGRY|SAD|SCARED|SLEEPY|EXCITED|BORED", ArgTypes.STRING);
            // percent integer, not decimal.
            //
            // This was the only command in the project using ArgTypes.DOUBLE, and also the only that
            // failed. The Hytale parser rejects decimal point — the same problem that already had
            // torn down the old `bedtune`. Accepting 0 to 100 and converting here avoids the parser.
            this.intensityArg = this.withOptionalArg("intensity", "Intensidade em % (0 a 100)", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String moodName = ctx.get(this.moodArg).toUpperCase();
            Mood targetMood;
            try {
                targetMood = Mood.valueOf(moodName);
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Invalid mood. Choose from: NEUTRAL, HAPPY, ANGRY, SAD, SCARED, SLEEPY, EXCITED, BORED"));
                return;
            }

            String intensityRaw = ctx.get(this.intensityArg);
            float intensity = 1.0f;
            if (intensityRaw != null && !intensityRaw.isBlank()) {
                try {
                    int percent = Integer.parseInt(intensityRaw.trim());
                    intensity = Math.max(0f, Math.min(100f, percent)) / 100f;
                } catch (NumberFormatException e) {
                    ctx.sendMessage(Message.raw("Invalid intensity. Use an integer from 0 to 100 (e.g., 75)."));
                    return;
                }
            }

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
                ctx.sendMessage(Message.raw("No NPCs nearby."));
                return;
            }

            nearestNPC.setEmotion(targetMood, intensity, "command", world.getTick());
            SimNPCPersistence.saveNPC(nearestNPC);

            ctx.sendMessage(Message.raw("Mood of " + nearestNPC.name + " definido para " + targetMood.name() + " com intensidade " + intensity + "."));
        }
    }

    private static class SearchSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> nameArg;

        public SearchSubCommand() {
            super("search", "Searches for an NPC in the database by name");
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
            super("toggleai", "Enables or disables the use of AI for interactions");
            this.stateArg = this.withOptionalArg("state", "on|off", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            
            AiConfig config = AiConfigManager.getConfig();
            String stateStr = ctx.get(this.stateArg);

            if (stateStr == null) {
                // Toggle state
                config.enabled = !config.enabled;
            } else if (stateStr.equalsIgnoreCase("on") || stateStr.equalsIgnoreCase("true")) {
                config.enabled = true;
            } else if (stateStr.equalsIgnoreCase("off") || stateStr.equalsIgnoreCase("false")) {
                config.enabled = false;
            }

            AiConfigManager.save();
            String status = config.enabled ? "ATIVADO" : "DESATIVADO";
            ctx.sendMessage(Message.raw("The use of Generative AI for NPC conversations was: " + status));
        }
    }

    /**
     * Debug-only: shows exactly what {@code AiConfigManager}/{@code NpcAiManager} actually loaded
     * and initialized at boot, as opposed to what {@code simtale-ai.json} says on disk — the two
     * can disagree (e.g. a key present in the file but blank, or a provider requested in
     * {@code provider} that never got registered because its key was missing).
     */
    private static class AiStatusSubCommand extends AbstractPlayerCommand {
        public AiStatusSubCommand() {
            super("aistatus", "Shows the current AI config and which providers actually initialized");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            AiConfig config = AiConfigManager.getConfig();
            Message yes = Message.translation("general.yes");
            Message no = Message.translation("general.no");
            Message defined = Message.translation("general.cmd.aistatus.key_defined");
            Message blank = Message.translation("general.cmd.aistatus.key_blank");

            ctx.sendMessage(Message.translation("general.cmd.aistatus.header"));
            ctx.sendMessage(Message.translation(AiConfigManager.configFileExists()
                    ? "general.cmd.aistatus.config_file_found" : "general.cmd.aistatus.config_file_missing")
                .param("path", AiConfigManager.configFilePath()));
            ctx.sendMessage(Message.translation("general.cmd.aistatus.enabled").param("state", config.enabled ? yes : no));
            ctx.sendMessage(Message.translation("general.cmd.aistatus.provider_configured").param("provider", config.provider));
            ctx.sendMessage(Message.translation("general.cmd.aistatus.keys")
                .param("gemini", config.geminiKey != null && !config.geminiKey.isBlank() ? defined : blank)
                .param("openai", config.openaiKey != null && !config.openaiKey.isBlank() ? defined : blank)
                .param("openrouter", config.openrouterKey != null && !config.openrouterKey.isBlank() ? defined : blank));

            if (SimTale.aiManager == null) {
                ctx.sendMessage(Message.translation("general.cmd.aistatus.manager_null"));
                return;
            }

            java.util.Set<String> registered = SimTale.aiManager.registeredProviderIds();
            String defaultProviderId = SimTale.aiManager.defaultProviderId();
            ctx.sendMessage(Message.translation("general.cmd.aistatus.registered_providers")
                .param("providers", registered.isEmpty()
                    ? Message.translation("general.cmd.aistatus.registered_none")
                    : Message.raw(String.join(", ", registered))));
            ctx.sendMessage(Message.translation("general.cmd.aistatus.default_provider")
                .param("provider", defaultProviderId != null
                    ? Message.raw(defaultProviderId)
                    : Message.translation("general.cmd.aistatus.default_provider_none")));

            if (!config.enabled) {
                ctx.sendMessage(Message.translation("general.cmd.aistatus.diagnosis_disabled"));
            } else if (registered.isEmpty()) {
                ctx.sendMessage(Message.translation("general.cmd.aistatus.diagnosis_no_provider"));
            } else {
                ctx.sendMessage(Message.translation("general.cmd.aistatus.diagnosis_ok"));
            }
        }
    }

    private static class HouseCheckSubCommand extends AbstractPlayerCommand {
        public HouseCheckSubCommand() {
            super("housecheck", "Checks the structural validity and furniture of the nearest house");
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
            
            SimBedData.BedPos nearestBed = null;
            double minDist = Double.MAX_VALUE;
            synchronized (BedRegistry.BEDS) {
                for (SimBedData.BedPos bp : BedRegistry.BEDS) {
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
            super("chestcheck", "Checks the registry and ownership of the nearest chest");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.translation("general.cmd.chestcheck.no_transform"));
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
                ctx.sendMessage(Message.translation("general.cmd.chestcheck.none_registered"));
                return;
            }

            if (minDist > 16 * 16) {
                ctx.sendMessage(Message.translation("general.cmd.chestcheck.none_nearby"));
                return;
            }

            ctx.sendMessage(Message.translation("general.cmd.chestcheck.located")
                .param("x", nearestChest.x).param("y", nearestChest.y).param("z", nearestChest.z));

            UUID houseId = HouseManager.BLOCK_TO_HOUSE_ID.get(nearestChest);
            if (houseId != null) {
                HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
                if (house != null) {
                    ctx.sendMessage(Message.translation("general.cmd.chestcheck.house").param("houseId", houseId.toString()));
                    ctx.sendMessage(Message.translation("general.cmd.chestcheck.owners").param("owners", String.join(", ", house.owners)));
                } else {
                    ctx.sendMessage(Message.translation("general.cmd.chestcheck.house_data_missing").param("houseId", houseId.toString()));
                }
            } else {
                ctx.sendMessage(Message.translation("general.cmd.chestcheck.public_chest"));
            }
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
                        && (npc.profession == com.cookieukw.SimTale.core.Profession.FARMER
                            || npc.profession == com.cookieukw.SimTale.core.Profession.HUNTER
                            || npc.profession == com.cookieukw.SimTale.core.Profession.FISHERMAN
                            || npc.profession == com.cookieukw.SimTale.core.Profession.LUMBERJACK
                            || npc.profession == com.cookieukw.SimTale.core.Profession.MINER)) {
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
            com.cookieukw.SimTale.core.Profession profession;
            try {
                profession = com.cookieukw.SimTale.core.Profession.valueOf(profName.toUpperCase());
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
                com.cookieukw.SimTale.systems.FarmPostRegistry.FarmPost claimedPost =
                        com.cookieukw.SimTale.systems.FarmPostRegistry.claimNearest(npcPos.x, npcPos.y, npcPos.z, nearestNPC.entityId);
                Vector3d scanCenter = claimedPost != null
                        ? new Vector3d(claimedPost.postX() + 0.5, claimedPost.postY(), claimedPost.postZ() + 0.5)
                        : npcPos;

                Vector3i farmPos = NPCWorkHelper.scanForFarmland(scanCenter, world);
                SIM_LOGGER.debug("[SimTale] forceplant scan for {}: claimedPost={}, scanCenter=({},{},{}), farmPos={}, FarmlandRegistry.size={}",
                        nearestNPC.name, claimedPost, scanCenter.x, scanCenter.y, scanCenter.z, farmPos,
                        com.cookieukw.SimTale.systems.FarmlandRegistry.FARMLAND.size());
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
                        com.cookieukw.SimTale.systems.FarmPostRegistry.release(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ(), nearestNPC.entityId);
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

            player.getPageManager().openCustomPage(ref, store, new com.cookieukw.SimTale.logic.PlayerGenderPage(playerRef, player, simPlayer));
            ctx.sendMessage(Message.raw("Opening gender selection panel..."));
        }
    }
}
