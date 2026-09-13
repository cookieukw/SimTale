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
 * Debug/GM subcommands for the pregnancy -> birth -> growth -> marriage pipeline: forcing a
 * pregnancy or birth on demand, jumping straight to a growth stage, growing/placing/swapping a
 * carried baby item, and forcing a marriage, plus the {@code /simtale pregnancy} status page.
 *
 * <p>Split out of {@code SimTaleCommand}, which had grown back to 2346 lines / 38 nested
 * subcommands after the previous split (see {@link DebugCommands}'s own javadoc, from when it
 * was last cut down to 2053 lines) -- new commands kept landing back in the one file. Grouped by
 * purpose, same as that split: these eight are read together when testing anything about a
 * child's life stage.
 *
 * <p>Package-private on purpose -- nothing outside the command layer should be constructing
 * these.
 */
final class LifecycleCommands {

    private LifecycleCommands() {
    }

    static class ForcePregSubCommand extends AbstractPlayerCommand {
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

    static class ForceBirthSubCommand extends AbstractPlayerCommand {
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

    static class SetStageSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> stageArg;

        public SetStageSubCommand() {
            super("setstage", "Sets the growth stage of the nearest child");
            this.stageArg = this.withRequiredArg("stage", "BABY|TODDLER|CHILD|TEEN|ADULT", ArgTypes.STRING);
        }

        /**
         * Entity for a growth record, with the mod's own roster as a fallback.
         *
         * <p>{@code getRefFromUUID} reads {@code EntityStore}'s UUID index, which is filled when an
         * entity is <em>added</em> to the store. A world restored from disk repopulates it as
         * chunks load, so there is a window — and, for anything that was re-registered rather than
         * re-added, a permanent gap — where an NPC is perfectly alive in the world and absent from
         * that map. SimTale keeps its own reference on {@code SimNPCComponent.entityRef}, updated
         * by the systems that spawn and track NPCs, so it answers when the index does not.
         */
        private static Ref<EntityStore> resolveChildRef(World world, UUID childId) {
            Ref<EntityStore> byIndex = world.getEntityStore().getRefFromUUID(childId);
            if (byIndex != null && byIndex.isValid()) return byIndex;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc != null && childId.equals(npc.entityId)
                        && npc.entityRef != null && npc.entityRef.isValid()) {
                    return npc.entityRef;
                }
            }
            return null;
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

            // Self-heal before searching.
            //
            // The list is rebuilt from disk in PlayerJoinHandler, and anything that stops that from
            // running — an early return, a join that fired before the world was ready, a reload of
            // the mod without a rejoin — leaves it empty for the rest of the session with no way to
            // recover short of relogging. Refilling here is idempotent and costs one database read
            // on a command nobody spams.
            LifecycleState.ensureLoaded();

            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            GrowthComponent nearestChild = null;
            double minDistance = Double.MAX_VALUE;
            int unresolved = 0;

            for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
                if (child.childId == null) continue;

                Ref<EntityStore> childRef = resolveChildRef(world, child.childId);
                if (childRef == null || !childRef.isValid()) {
                    unresolved++;
                    continue;
                }

                TransformComponent childTransform =
                        store.getComponent(childRef, TransformComponent.getComponentType());
                if (playerTransform == null || childTransform == null) {
                    unresolved++;
                    continue;
                }

                double distSq = playerTransform.getPosition().distanceSquared(childTransform.getPosition());
                if (distSq < minDistance) {
                    minDistance = distSq;
                    nearestChild = child;
                }
            }

            if (nearestChild == null) {
                // Three failures wore the same message and need opposite fixes: no growth records
                // at all, records whose entities are not in the world, and a player with no
                // transform. Saying which one it is turns a guess into a lookup.
                int total = LifecycleManager.ACTIVE_CHILDREN.size();
                String miss;
                if (total == 0) {
                    miss = "[SimTale] setstage: nenhum registro de crescimento neste mundo. "
                            + "Filhos que ja viraram ADULT saem da lista de proposito.";
                } else {
                    miss = "[SimTale] setstage: " + total + " registro(s) de filho, mas "
                            + unresolved + " sem entidade carregada no mundo. "
                            + "Chegue perto do filho ou confira se ele ainda existe.";
                }
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

            Ref<EntityStore> childRef = resolveChildRef(world, nearestChild.childId);
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
    /**
     * Ages the Baby item in your hand so it can be placed.
     *
     * <p>Not a variant of {@code setstage}: the two never see the same subject. {@code setstage}
     * searches for the nearest child <em>entity</em>, and a baby in your hand has none — it is
     * metadata on an item until someone puts it down. This is the only way to reach a child at that
     * point in its life.
     *
     * <p>No argument, on purpose. The command has exactly one job — skip the four days a newborn
     * has to wait before {@code placeBabyFromHeldItem} will accept it — and every attempt to also
     * expose the later stages here produced a command that read as nonsense, because the subject is
     * a baby item. Aging a child further is what {@code setstage} is for, and it works the moment
     * this one has put a body in the world.
     */
    static class GrowBabySubCommand extends AbstractPlayerCommand {

        /** The first stage a held baby is allowed to be placed at. */
        private static final GrowthStage PLACEABLE = GrowthStage.TODDLER;

        public GrowBabySubCommand() {
            super("growbaby", "Skips the newborn wait on the Baby item in your hand so it can be placed");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            GrowthStage targetStage = PLACEABLE;

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
            childComp.birthTick = world.getTick() - (targetStage.getStartDay() * PregnancyComponent.TICKS_PER_DAY);
            // Age first, then ask the growth code for the matching scale — the same correction
            // setstage already got. GrowthStage.getScale() is a second, coarser table
            // (0.35/0.50/0.70/0.90/1.00) that disagrees with calculateTargetScale's interpolation,
            // and since GrowthTickSystem recomputes with the latter every tick, anything written
            // from the enum table was overwritten within a frame.
            childComp.currentScale = LifecycleManager.calculateTargetScale(childComp, world.getTick());
            Caskara.save("child_" + childId, childComp);

            // No BABY case any more: none of the accepted values map to it, precisely because
            // placeBabyFromHeldItem refuses a newborn and the command would be reporting success on
            // something that still cannot be put down.
            ctx.sendMessage(Message.raw("[SimTale] " + childComp.getFullName() + " cresceu para "
                    + targetStage.getDisplayName() + " (escala: " + childComp.currentScale
                    + "). Ja pode colocar no chao."));
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
    static class ForcePlaceBabySubCommand extends AbstractPlayerCommand {
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
    static class ForceBabySwapSubCommand extends AbstractPlayerCommand {
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

    static class ForceMarrySubCommand extends AbstractPlayerCommand {
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

    static class PregnancySubCommand extends AbstractPlayerCommand {
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

    private static void openPlayerPregnancyPage(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, SimPlayerComponent playerComp) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new PlayerPregnancyPage(playerRef, player, playerComp));
        }
    }
}
