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
import com.cookieukw.SimTale.systems.ChildPlayHelper;
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
 * Debug/GM subcommands for testing NPC social interactions on demand: forcing a mood, forcing a
 * social interaction between the two nearest NPCs, and the three single-NPC test gestures
 * (flirt, shove, greet) used to check a specific animation/dialogue path without waiting for the
 * routine AI to trigger it on its own.
 *
 * <p>Split out of {@code SimTaleCommand} for the same reason as {@link LifecycleCommands} (see
 * its javadoc) -- part of the same 13/09 pass that cut the file back down after it regrew past
 * its previous 2053-line split.
 *
 * <p>Package-private on purpose -- nothing outside the command layer should be constructing
 * these.
 */
final class SocialTestCommands {

    private SocialTestCommands() {
    }

    static class SetMoodSubCommand extends AbstractPlayerCommand {
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

            // force, not setEmotion: an explicit debug command must not be silently denied by
            // the priority/hold guard that protects organic mood changes from ambient triggers —
            // see SimNPCComponent.forceEmotion's own javadoc for the exact failure this replaced
            // (command printed success while a still-protected ANGRY/SAD quietly won anyway).
            nearestNPC.forceEmotion(targetMood, intensity, "command", world.getTick());
            SimNPCPersistence.saveNPC(nearestNPC);

            ctx.sendMessage(Message.raw("Mood of " + nearestNPC.name + " definido para " + targetMood.name() + " com intensidade " + intensity + "."));
        }
    }

    static class ForceSocialSubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> topicArg;

        public ForceSocialSubCommand() {
            super("forcesocial", "Forces the two nearest NPCs to talk to each other");
            this.topicArg = this.withOptionalArg("topic", "hostile|romantic|hunger|fatigue|farm|wood|guard|fish|happy|sad|night|weather|village", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) {
                ctx.sendMessage(Message.raw("[SimTale] No player transform."));
                return;
            }
            Vector3d playerPos = pt.getPosition();

            List<SimNPCComponent> npcs = new ArrayList<>();
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    npcs.add(npc);
                }
            }
            npcs.sort((a, b) -> {
                TransformComponent ta = a.entityRef.getStore().getComponent(a.entityRef, TransformComponent.getComponentType());
                TransformComponent tb = b.entityRef.getStore().getComponent(b.entityRef, TransformComponent.getComponentType());
                if (ta == null) return 1;
                if (tb == null) return -1;
                return Double.compare(playerPos.distanceSquared(ta.getPosition()), playerPos.distanceSquared(tb.getPosition()));
            });

            if (npcs.size() < 2) {
                ctx.sendMessage(Message.raw("[SimTale] Precisa de pelo menos 2 NPCs carregados por perto para testar."));
                return;
            }

            SimNPCComponent npc1 = npcs.get(0);
            SimNPCComponent npc2 = npcs.get(1);
            Ref<EntityStore> ref1 = npc1.entityRef;
            Ref<EntityStore> ref2 = npc2.entityRef;
            RoutineAIComponent ai1 = store.getComponent(ref1, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            RoutineAIComponent ai2 = store.getComponent(ref2, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            TransformComponent trans1 = store.getComponent(ref1, TransformComponent.getComponentType());
            TransformComponent trans2 = store.getComponent(ref2, TransformComponent.getComponentType());

            if (ai1 == null || ai2 == null || trans1 == null || trans2 == null) {
                ctx.sendMessage(Message.raw("[SimTale] Componentes de IA ou Transform inválidos nos NPCs."));
                return;
            }

            String topicStr = ctx.get(this.topicArg);
            int topicId;
            if (topicStr != null && !topicStr.isBlank()) {
                topicId = switch (topicStr.toLowerCase()) {
                    case "hostile" -> NPCSocialHelper.TOPIC_HOSTILE;
                    case "romantic" -> NPCSocialHelper.TOPIC_ROMANTIC;
                    case "hunger" -> NPCSocialHelper.TOPIC_HUNGER;
                    case "fatigue" -> NPCSocialHelper.TOPIC_FATIGUE;
                    case "farm" -> NPCSocialHelper.TOPIC_WORK_FARM;
                    case "wood" -> NPCSocialHelper.TOPIC_WORK_WOOD;
                    case "guard" -> NPCSocialHelper.TOPIC_WORK_GUARD;
                    case "fish" -> NPCSocialHelper.TOPIC_WORK_FISH;
                    case "happy" -> NPCSocialHelper.TOPIC_MOOD_HAPPY;
                    case "sad" -> NPCSocialHelper.TOPIC_MOOD_SAD;
                    case "night" -> NPCSocialHelper.TOPIC_TIME_NIGHT;
                    case "village" -> NPCSocialHelper.TOPIC_VILLAGE;
                    default -> NPCSocialHelper.TOPIC_WEATHER;
                };
            } else {
                topicId = NPCSocialHelper.evaluateSocialTopic(npc1, npc2, world) / 10;
            }

            int variant = (int) (Math.random() * 2) + 1;
            int encodedTopic = topicId * 10 + variant;

            Vector3d p1 = trans1.getPosition();
            Vector3d p2 = trans2.getPosition();
            double dx = p2.x - p1.x;
            double dz = p2.z - p1.z;
            double distSq = dx * dx + dz * dz;

            if (distSq > 9.0 || distSq < 0.25) {
                p2 = new Vector3d(p1.x + 1.5, p1.y, p1.z);
                trans2.teleportPosition(p2);
                dx = p2.x - p1.x;
                dz = p2.z - p1.z;
            }

            trans1.teleportRotation(new Rotation3f(0f, (float) Math.atan2(-dx, -dz), 0f));
            trans2.teleportRotation(new Rotation3f(0f, (float) Math.atan2(dx, dz), 0f));

            NPCMovementHelper.clearMoveTarget(ref1, ai1);
            NPCMovementHelper.clearMoveTarget(ref2, ai2);

            ai1.currentTask = RoutineAIComponent.TaskType.SOCIALIZING;
            ai1.socializeHost = true;
            ai1.socializeTargetId = npc2.entityId;
            ai1.taskStartTime = world.getTick();
            ai1.socialTalkTimer = 0;
            ai1.socialTopic = encodedTopic;

            ai2.currentTask = RoutineAIComponent.TaskType.SOCIALIZING;
            ai2.socializeHost = false;
            ai2.socializeTargetId = npc1.entityId;
            ai2.taskStartTime = world.getTick();
            ai2.socialTalkTimer = 0;
            ai2.socialTopic = encodedTopic;

            NPCEntity e1 = store.getComponent(ref1, NPCEntity.getComponentType());
            if (e1 != null) e1.setLeashPoint(new Vector3d(p1.x, p1.y, p1.z));
            NPCEntity e2 = store.getComponent(ref2, NPCEntity.getComponentType());
            if (e2 != null) e2.setLeashPoint(new Vector3d(p2.x, p2.y, p2.z));

            ctx.sendMessage(Message.raw("[SimTale] Iniciando bate-papo entre " + npc1.name + " e " + npc2.name + " (tópico: " + (topicStr != null ? topicStr : "auto") + ")"));
        }
    }

    static class ForcePlaySubCommand extends AbstractPlayerCommand {
        private final OptionalArg<String> gameArg;

        public ForcePlaySubCommand() {
            super("forceplay", "Forces the two nearest children to start a game of tag or hide-and-seek");
            this.gameArg = this.withOptionalArg("jogo", "tag|escondeesconde", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) {
                ctx.sendMessage(Message.raw("[SimTale] No player transform."));
                return;
            }
            Vector3d playerPos = pt.getPosition();

            List<SimNPCComponent> children = new ArrayList<>();
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()
                        && com.cookieukw.SimTale.logic.InteractionManager.isNpcAChild(npc)) {
                    children.add(npc);
                }
            }
            children.sort((a, b) -> {
                TransformComponent ta = a.entityRef.getStore().getComponent(a.entityRef, TransformComponent.getComponentType());
                TransformComponent tb = b.entityRef.getStore().getComponent(b.entityRef, TransformComponent.getComponentType());
                if (ta == null) return 1;
                if (tb == null) return -1;
                return Double.compare(playerPos.distanceSquared(ta.getPosition()), playerPos.distanceSquared(tb.getPosition()));
            });

            if (children.size() < 2) {
                ctx.sendMessage(Message.raw("[SimTale] Precisa de pelo menos 2 criancas carregadas por perto para testar."));
                return;
            }

            SimNPCComponent npc1 = children.get(0);
            SimNPCComponent npc2 = children.get(1);
            Ref<EntityStore> ref1 = npc1.entityRef;
            Ref<EntityStore> ref2 = npc2.entityRef;
            RoutineAIComponent ai1 = store.getComponent(ref1, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            RoutineAIComponent ai2 = store.getComponent(ref2, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            TransformComponent trans1 = store.getComponent(ref1, TransformComponent.getComponentType());
            TransformComponent trans2 = store.getComponent(ref2, TransformComponent.getComponentType());

            if (ai1 == null || ai2 == null || trans1 == null || trans2 == null) {
                ctx.sendMessage(Message.raw("[SimTale] Componentes de IA ou Transform invalidos nas criancas."));
                return;
            }

            // Bring them together first, same fallback ForceSocialSubCommand uses, so the game
            // doesn't open with an instant chase-timeout because they spawned out of range.
            Vector3d p1 = trans1.getPosition();
            Vector3d p2 = trans2.getPosition();
            double distSq = p1.distanceSquared(p2);
            if (distSq > 100.0) {
                p2 = new Vector3d(p1.x + 2.0, p1.y, p1.z);
                trans2.teleportPosition(p2);
            }

            String gameStr = ctx.get(this.gameArg);
            Boolean forcedTag = null;
            if (gameStr != null && !gameStr.isBlank()) {
                String norm = gameStr.toLowerCase().replace("-", "").replace(" ", "");
                if (norm.contains("esconde") || norm.contains("hide") || norm.contains("seek")) {
                    forcedTag = Boolean.FALSE;
                } else if (norm.contains("tag") || norm.contains("pega")) {
                    forcedTag = Boolean.TRUE;
                }
            }

            ChildPlayHelper.startGame(ref1, npc1, ai1, ref2, npc2, ai2, world, store, forcedTag);

            ctx.sendMessage(Message.raw("[SimTale] " + npc1.name + " e " + npc2.name + " comecaram a brincar ("
                    + (forcedTag == null ? "aleatorio" : (forcedTag ? "pega-pega" : "esconde-esconde")) + ")."));
        }
    }

    static class TestFlirtSubCommand extends AbstractPlayerCommand {
        public TestFlirtSubCommand() {
            super("testflirt", "Tests the flirt blush and heart reaction on nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) return;
            SimNPCComponent nearest = findNearestNpc(pt.getPosition());
            if (nearest == null || nearest.entityRef == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC por perto."));
                return;
            }
            SimTaleJuiceHelper.playFlirtSuccess(nearest.entityRef, nearest, playerRef, store, world.getTick());
            ctx.sendMessage(Message.raw("[SimTale] Testando flerte com sucesso em " + nearest.name));
        }
    }

    static class TestShoveSubCommand extends AbstractPlayerCommand {
        public TestShoveSubCommand() {
            super("testshove", "Tests the angry shove with knockback from nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) return;
            SimNPCComponent nearest = findNearestNpc(pt.getPosition());
            if (nearest == null || nearest.entityRef == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC por perto."));
                return;
            }
            SimTaleJuiceHelper.playShove(nearest.entityRef, nearest, ref, playerRef, store, 5.0f, world.getTick());
            ctx.sendMessage(Message.raw("[SimTale] " + nearest.name + " empurrou o jogador com raiva!"));
        }
    }

    static class TestGreetSubCommand extends AbstractPlayerCommand {
        public TestGreetSubCommand() {
            super("testgreet", "Tests proximity wave and greeting on nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) return;
            SimNPCComponent nearest = findNearestNpc(pt.getPosition());
            if (nearest == null || nearest.entityRef == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC por perto."));
                return;
            }
            SimTaleJuiceHelper.playGreeting(nearest.entityRef, store);
            playerRef.sendMessage(Message.raw("[Vila] " + nearest.name + " acenou para você!"));
        }
    }

    static class TestKissSubCommand extends AbstractPlayerCommand {
        public TestKissSubCommand() {
            super("testkiss", "Tests the Kiss_1/Kiss_2 romance duo animation with the nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) return;
            SimNPCComponent nearest = findNearestNpc(pt.getPosition());
            if (nearest == null || nearest.entityRef == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC por perto."));
                return;
            }
            if (com.cookieukw.SimTale.logic.InteractionManager.isNpcAChild(nearest)) {
                ctx.sendMessage(Message.raw("[SimTale] " + nearest.name + " e uma crianca -- comando bloqueado."));
                return;
            }
            SimTaleJuiceHelper.playKiss(ref, nearest.entityRef, store);
            ctx.sendMessage(Message.raw("[SimTale] Testando beijo com " + nearest.name
                    + " (bypassa o status de relacionamento -- so pra ver a animacao)."));
        }
    }

    static class TestProposeSubCommand extends AbstractPlayerCommand {
        public TestProposeSubCommand() {
            super("testpropose", "Tests the Propose_Kneel/Propose_React marriage-proposal duo animation with the nearest NPC");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent pt = store.getComponent(ref, TransformComponent.getComponentType());
            if (pt == null) return;
            SimNPCComponent nearest = findNearestNpc(pt.getPosition());
            if (nearest == null || nearest.entityRef == null) {
                ctx.sendMessage(Message.raw("[SimTale] Nenhum NPC por perto."));
                return;
            }
            if (com.cookieukw.SimTale.logic.InteractionManager.isNpcAChild(nearest)) {
                ctx.sendMessage(Message.raw("[SimTale] " + nearest.name + " e uma crianca -- comando bloqueado."));
                return;
            }
            SimTaleJuiceHelper.playMarriageProposal(ref, nearest.entityRef, store);
            ctx.sendMessage(Message.raw("[SimTale] Testando pedido de casamento com " + nearest.name
                    + " (bypassa a logica de aceitar/recusar -- so pra ver a animacao)."));
        }
    }

    private static SimNPCComponent findNearestNpc(Vector3d playerPos) {
        if (playerPos == null) return null;
        SimNPCComponent best = null;
        double bestDist = Double.MAX_VALUE;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef == null || !npc.entityRef.isValid()) continue;
            TransformComponent nt = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
            if (nt == null) continue;
            double d = playerPos.distanceSquared(nt.getPosition());
            if (d < bestDist) {
                bestDist = d;
                best = npc;
            }
        }
        return best;
    }
}
