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
import com.cookieukw.SimTale.systems.ChildCarryHelper;
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
import com.cookieukw.SimTale.systems.ChairRegistry;
import com.cookieukw.SimTale.systems.FurnitureAnchorHelper;
import com.cookieukw.SimTale.systems.VillageManager;
import com.cookieukw.SimTale.systems.VillageStockManager;
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

/**
 * Debug/GM subcommands for diagnosing a stuck, frozen or misbehaving NPC, and for checking what
 * the house/chest/chair/AI registries currently see near the player: dumping runtime state,
 * toggling the camera-interaction debug dump, forcing an unstick or a re-scan, and the various
 * {@code *check}/{@code toggleai}/{@code aistatus}/{@code search} inspection commands.
 *
 * <p>Split out of {@code SimTaleCommand} for the same reason as {@link LifecycleCommands} (see
 * its javadoc) -- part of the same 13/09 pass that cut the file back down after it regrew past
 * its previous 2053-line split.
 *
 * <p>Package-private on purpose -- nothing outside the command layer should be constructing
 * these.
 */
final class DiagnosticsCommands {

    private DiagnosticsCommands() {
    }

    /**
     * Dumps the runtime state of the nearest NPC: role state, animation slots, movement flags,
     * Frozen, leash and AI task.
     * <p>
     * Built for the "sliding on ice after interacting" bug. Run it on a fresh NPC and on one
     * that has been talked to, and diff the two — whatever differs is the culprit, instead of
     * guessing which subsystem is stuck.
     */
    static class NpcStateSubCommand extends AbstractPlayerCommand {

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

                /* Ticks left on each search backoff, which is the difference between "idle because
                it has nothing to do" and "idle because a need it cannot satisfy keeps pulling it
                back". An NPC that stood still for an entire session was unreadable without
                these: every branch involved fails silently.
                */
                long tick = world.getTick();
                sb.append("\n  cooldowns: bed=").append(Math.max(0, ai.nextBedSearchTick - tick))
                  .append(" food=").append(Math.max(0, ai.nextFoodSearchTick - tick))
                  .append(" bath=").append(Math.max(0, ai.nextBathSearchTick - tick));
            }

            /* The needs drive every IDLE decision, so without them the dump shows the outcome and
            hides the reason.
            */
            sb.append("\n  needs: hunger=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.HUNGER_ID)))
              .append(" energy=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.ENERGY_ID)))
              .append(" hygiene=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.HYGIENE_ID)))
              .append(" fun=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.FUN_ID)))
              .append(" social=").append(fmt(NeedsHelper.getNeed(store, nref, NeedsHelper.SOCIAL_ID)));

            /* Everything needed to tell "guard on the day shift" apart from "stuck in bed": the
            profession, whether the world clock says this NPC's sleep window is open, and the
            raw day progress behind that answer.
            */
            sb.append("\n  profession=").append(best.profession)
              .append("  sleepWindow=")
              .append(NPCSleepHelper.isSleepPeriod(best, world))
              .append("  night=")
              .append(NPCSleepHelper.isNight(world))
              // currentHour returns 0..24, not the 0..1 progress it is derived from.
              .append("  hour=")
              .append(NPCSleepHelper.currentHour(world))
              .append("\n  bed=").append(best.bedLocation);

            ctx.sendMessage(Message.raw(sb.toString()));
            HytaleLogger.forEnclosingClass().atInfo().log(sb.toString());
        }

        private static String fmt(float value) {
            return String.format("%.0f", value);
        }
    }

    /** Toggles the verbose camera dump printed when the NPC interaction page opens. */
    static class CamDebugSubCommand extends AbstractPlayerCommand {
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
    static class UnstickSubCommand extends AbstractPlayerCommand {

        public UnstickSubCommand() {
            super("unstick", "Unstucks frozen NPCs and the player stuck in bed");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            /* Free the player first: getting stuck in a bed with no way out, not even in creative,
            leaves no other escape from inside the game. Nothing in SimTale mounts the player, so
            this is a rescue hatch rather than a fix — but the components are the same ones the
            NPC path clears, and clearing them when they are absent is harmless.
            */
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
                    ? "[SimTale] You were released from the bed/mount."
                    : "[SimTale] You were neither mounted nor frozen."));

            int fixed = 0;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                /* A carried child has a MountedComponent (points to the carrier) and is
                deliberately Frozen at all times (see ChildCarryHelper.pickUp) -- precisely
                the two states that this command exists to clear from truly stuck NPCs.
                Without this guard, /simtale unstick swept through ALL ACTIVE_NPCS and stripped the
                MountedComponent from every carried child, without going through the restoration
                path (BoundingBox back via PARKED_BOXES, NpcFreezeUtil.unfreeze) that
                ChildCarryHelper.putDown performs -- she was left frozen, with a near-zero hitbox, stuck
                at the old position from when she was picked up (her TransformComponent stops
                updating as soon as she is mounted), and without any MountedComponent -- exactly what
                findCarriedBy/putDown use to find who is being carried, so "/simtale putdown"
                afterwards could no longer find anyone. And this happened to the entire stack at once,
                not just the top one, because the loop does not stop at the first.
                */
                if (npc.entityRef != null && npc.entityRef.isValid()
                        && ChildCarryHelper.isBeingCarried(npc.entityRef.getStore(), npc)) {
                    continue;
                }

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

                    /* Also release anyone who was stuck in bed.

                    Without this, unstick reset the task to IDLE but left the NPC mounted and with
                    MovementStates.sleeping enabled. On the next attempt to sleep,
                    mountOnBlock returned ALREADY_MOUNTED and the NPC never went back to bed —
                    which also made it impossible to reproduce the sleep cycle for testing.
                    */
                    if (npc.entityRef.getStore().getComponent(
                            npc.entityRef, MountedComponent.getComponentType()) != null) {
                        npc.entityRef.getStore().tryRemoveComponent(
                                npc.entityRef, MountedComponent.getComponentType());
                        touched = true;
                    }
                    NPCMovementHelper.setSleepingState(npc.entityRef, npc.entityRef.getStore(), false);
                    /* setSleepingState only resets the MovementStates flags (physics/hitbox).
                    The Sleep clip itself plays on a separate Status animation slot
                    (RoutineAISystem's SLEEPING entry) that this never stopped — she kept
                    playing the lying-down animation while walking around. The natural WAKING
                    completion (RoutineAISystem.java) stops it the same way; unstick needs to
                    do the same rescue, not just clear the task state.
                    */
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
                        /* Drops the stale leash so the next moveTo re-issues the "Moving" state
                        and the walk animation comes back.
                        */
                        NPCMovementHelper.clearMoveTarget(npc.entityRef, ai);
                        ai.currentTask = RoutineAIComponent.TaskType.IDLE;
                        ai.targetBlockPosition = null;
                        ai.socializeTargetId = null;
                        ai.socializeHost = false;
                        ai.wanderTimer = 0;
                        /* Without clearing this, an NPC freed from bed still counts as a scheduled
                        sleeper, and the SLEEPING branch would wait for a window that is not open.
                        */
                        ai.sleepingOnSchedule = false;
                        /* Push both searches out so the interrupts do not drag her straight back
                        to the bed the command just freed her from.
                        */
                        ai.nextBedSearchTick = world.getTick() + 200;
                        ai.nextFoodSearchTick = world.getTick() + 200;
                    }
                }

                if (touched) fixed++;
            }
            ctx.sendMessage(Message.raw("[SimTale] Unstuck " + fixed + " of "
                    + SimTale.ACTIVE_NPCS.size() + " active NPCs."));
        }
    }

    /**
     * Re-runs the bed/chest/work-post/farmland/crop world scan centered on wherever the player
     * is standing right now, instead of only wherever they were standing at world join. Testing
     * an area far from spawn otherwise means that area's blocks stay invisible to the registries
     * until the next full rejoin from right on top of them.
     */
    static class RescanSubCommand extends AbstractPlayerCommand {
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
            BedWorldBootstrap.bootstrapLoadedRadius(world, pt.getPosition(), 32);
            ctx.sendMessage(Message.raw("[SimTale] Rescan done around your position. Check the server log for counts."));
        }
    }

    /**
     * Turns the mod's debug messages on and off.
     *
     * <p>Much of SimTale's diagnosis is in {@code LOGGER.debug} calls, which {@link SimLog} discards
     * by default — otherwise the server log would fill with per-tick scans (bed search, water,
     * chest, door). Without a way to turn them on in-game, investigating anything required recompiling.
     */
    static class DebugLogSubCommand extends AbstractPlayerCommand {
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

    static class HouseCheckSubCommand extends AbstractPlayerCommand {
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
                .filter(h -> (h.beds != null && h.beds.contains(houseBed)) || (h.bedPos != null && h.bedPos.equals(houseBed)))
                .findFirst()
                .orElse(null);
            if (existingHouse != null) {
                existingHouse.interior = rawScan.interiorBlocks();
                existingHouse.doors = rawScan.doorBlocks();
                existingHouse.chests = rawScan.chestBlocks();
                existingHouse.addBed(houseBed);
                HouseManager.registerHouse(existingHouse);
                ctx.sendMessage(Message.raw("[House Debug] Registered house updated in persistence with " 
                    + rawScan.doorBlocks().size() + " door(s) and " + rawScan.chestBlocks().size() + " chest(s).").color("green"));
            }
        }
    }

    static class ChestCheckSubCommand extends AbstractPlayerCommand {
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

            /* Scan first, like housecheck already did. Otherwise this command reports "nothing
            registered" for a chest that simply had not been picked up yet.
            */
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

            boolean isShared = ChestRegistry.isShared(nearestChest);
            ctx.sendMessage(Message.translation(isShared ? "general.cmd.chestcheck.type_shared" : "general.cmd.chestcheck.type_private"));

            UUID houseId = HouseManager.findHouseIdForChest(nearestChest);
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

    static class ChestShareSubCommand extends AbstractPlayerCommand {
        public ChestShareSubCommand() {
            super("chestshare", "Toggles whether the nearest chest is private or shared village storage");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) return;
            Vector3d pos = tc.getPosition();
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

            if (nearestChest == null || minDist > 16 * 16) {
                ctx.sendMessage(Message.translation("general.cmd.chestshare.none_nearby"));
                return;
            }

            boolean nowShared = !ChestRegistry.isShared(nearestChest);
            ChestRegistry.setShared(nearestChest.x, nearestChest.y, nearestChest.z, nowShared);
            ctx.sendMessage(Message.translation(nowShared ? "general.cmd.chestshare.now_shared" : "general.cmd.chestshare.now_private")
                    .param("x", nearestChest.x)
                    .param("y", nearestChest.y)
                    .param("z", nearestChest.z));
        }
    }

    static class VillageStockSubCommand extends AbstractPlayerCommand {
        public VillageStockSubCommand() {
            super("villagestock", "Shows the communal inventory summary of the nearest village");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) return;
            Vector3d pos = tc.getPosition();

            VillageManager.Village village = VillageManager.nearest(pos.x, pos.z);
            if (village == null) {
                ctx.sendMessage(Message.translation("general.cmd.villagestock.no_village"));
                return;
            }

            List<HouseBlockPos> sharedChests = VillageStockManager.getSharedChestsInVillage(village);
            Map<String, Integer> stock = VillageStockManager.getVillageStockSummary(village, world);

            ctx.sendMessage(Message.translation("general.cmd.villagestock.header"));
            ctx.sendMessage(Message.translation("general.cmd.villagestock.info")
                    .param("x", (int) village.centerX())
                    .param("z", (int) village.centerZ())
                    .param("radius", (int) village.radius())
                    .param("houses", village.houses()));
            ctx.sendMessage(Message.translation("general.cmd.villagestock.shared_chests")
                    .param("count", sharedChests.size()));

            if (stock.isEmpty()) {
                ctx.sendMessage(Message.translation("general.cmd.villagestock.empty"));
            } else {
                ctx.sendMessage(Message.translation("general.cmd.villagestock.stocked_items"));
                for (Map.Entry<String, Integer> entry : stock.entrySet()) {
                    ctx.sendMessage(Message.raw("  §f- " + entry.getKey() + ": §e" + entry.getValue()));
                }
            }
        }
    }

    /**
     * Diagnostic twin of {@code chestcheck}, for chairs: reports the nearest registered chair,
     * its distance, and whether it is currently occupied. Chairs had no self-service inspector at
     * all before this — the only way to know whether {@link ChairRegistry} actually held anything
     * was to add temporary logging, which is exactly the blind spot {@code chestcheck} and
     * {@code debugbeds} already closed for chests and beds.
     */
    static class ChairCheckSubCommand extends AbstractPlayerCommand {
        public ChairCheckSubCommand() {
            super("chaircheck", "Checks the registry and occupancy of the nearest chair");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.translation("general.cmd.chaircheck.no_transform"));
                return;
            }
            Vector3d pos = tc.getPosition();

            /* Scan first, same reasoning as chestcheck: otherwise this reports "nothing
            registered" for a chair the world simply had not rescanned yet.
            */
            BedWorldBootstrap.bootstrapLoadedRadius(world, pos, 16);

            Vector3i nearestChair = null;
            double minDist = Double.MAX_VALUE;

            synchronized (ChairRegistry.CHAIRS) {
                for (Vector3i cp : ChairRegistry.CHAIRS) {
                    double dx = (cp.x + 0.5) - pos.x;
                    double dy = cp.y - pos.y;
                    double dz = (cp.z + 0.5) - pos.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < minDist) {
                        minDist = distSq;
                        nearestChair = cp;
                    }
                }
            }

            if (nearestChair == null) {
                ctx.sendMessage(Message.translation("general.cmd.chaircheck.none_registered"));
                return;
            }

            if (minDist > 16 * 16) {
                ctx.sendMessage(Message.translation("general.cmd.chaircheck.none_nearby"));
                return;
            }

            ctx.sendMessage(Message.translation("general.cmd.chaircheck.located")
                .param("x", nearestChair.x).param("y", nearestChair.y).param("z", nearestChair.z));

            boolean occupied = ChairRegistry.isOccupied(nearestChair);
            ctx.sendMessage(Message.translation(occupied
                    ? "general.cmd.chaircheck.occupied" : "general.cmd.chaircheck.free"));
        }
    }

    static class ToggleAiSubCommand extends AbstractPlayerCommand {
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
    static class AiStatusSubCommand extends AbstractPlayerCommand {
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

            Set<String> registered = SimTale.aiManager.registeredProviderIds();
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

    static class SearchSubCommand extends AbstractPlayerCommand {
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
}
