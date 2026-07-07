package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
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
import javax.annotation.Nonnull;
import java.util.List;
import java.util.ArrayList;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import java.util.UUID;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
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
        this.addSubCommand(new ForcePregSubCommand());
        this.addSubCommand(new ForceBirthSubCommand());
        this.addSubCommand(new SetStageSubCommand());
        this.addSubCommand(new ForceMarrySubCommand());
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // If no subcommand is specified, show usage
        sendUsage(ctx);
    }

    private static void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Uso: /simtale <spawn|interact|tpall|clearall|forcespawn|forcesleep|forcepreg|forcebirth|setstage|marry>"));
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
                ctx.sendMessage(Message.translation("simtale.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE/CHILD_MALE/CHILD_FEMALE"));
                return;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            assert transform != null;
            Vector3d pos = transform.getPosition().add(2, 0, 2);

            Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
            SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

            // Save initial state to DB
            assert comp != null;
            SimNPCPersistence.saveNPC(comp);

            ctx.sendMessage(Message.translation("simtale.cmd.spawn.success").param("type", type.name()));
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
                ctx.sendMessage(Message.translation("simtale.cmd.reload.db"));
                SimNPCPersistence.reassembleActiveNPCs(world);
            }
            
            TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
            SimNPCComponent nearestNPC = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null) {
                    TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
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
                ctx.sendMessage(Message.translation("simtale.cmd.interact.none"));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            assert player != null;
            player.getPageManager().openCustomPage(ref, store, new NPCInteractionPage(playerRef, player, nearestNPC));
            ctx.sendMessage(Message.translation("simtale.cmd.interact.success").param("name", nearestNPC.name));
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
                    TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (npcTransform != null) {
                        npcTransform.setPosition(new Vector3d(pPos.x + (Math.random() - 0.5) * 4, pPos.y, pPos.z + (Math.random() - 0.5) * 4));
                        store.putComponent(npc.entityRef, TransformComponent.getComponentType(), npcTransform);
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
                Caskara.delete(npc.entityId.toString(), SimNPCData.class);
            }
            SimTale.ACTIVE_NPCS.clear();
            ctx.sendMessage(Message.raw("Removidos permanentemente " + count + " NPCs do Hytale e banco de dados."));
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
                    ctx.sendMessage(Message.translation("simtale.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE/CHILD_MALE/CHILD_FEMALE"));
                    return;
                }
            } else {
                type = Math.random() > 0.5 ? SimNPCFactory.NPCType.HUMAN_MALE : SimNPCFactory.NPCType.HUMAN_FEMALE;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            assert transform != null;
            Vector3d pos = transform.getPosition().add(2, 0, 2);

            Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
            SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

            assert comp != null;
            SimNPCPersistence.saveNPC(comp);

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
                if (npc.entityRef != null) {
                    TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
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
            
            RoutineAIComponent ai = store.getComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                ai.currentTask = RoutineAIComponent.TaskType.FINDING_BED;
                ai.targetBlockPosition = null;
                ai.taskStartTime = world.getTick();
                store.putComponent(nearestNPC.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, ai);
            }
            
            ctx.sendMessage(Message.raw("Forcando " + nearestNPC.name + " a ir dormir! Energia definida para 0."));
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
                ctx.sendMessage(Message.raw("Gravidez forcada com sucesso em voce (ignoring gender)!"));
            } else {
                TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
                SimNPCComponent nearestNPC = null;
                double minDistance = Double.MAX_VALUE;

                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityRef != null && npc.gender == Gender.FEMALE) {
                        TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
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
                    if (npc.entityRef != null && npc.pregnancy != null && npc.pregnancy.pregnant) {
                        TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
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
                if (npc.entityRef != null) {
                    TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
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
}
