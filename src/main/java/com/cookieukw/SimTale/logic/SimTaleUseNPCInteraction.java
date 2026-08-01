package com.cookieukw.SimTale.logic;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.interaction.InteractionView;
import com.hypixel.hytale.server.npc.blackboard.view.interaction.ReservationStatus;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class SimTaleUseNPCInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public SimTaleUseNPCInteraction(String id) {
        super(id);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        // `assert` is disabled at runtime without -ea; fail the interaction explicitly instead.
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            HytaleLogger.getLogger().at(Level.INFO).log("UseNPCInteraction requires a Player but was used for: %s", ref);
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        NPCEntity npcComponent = commandBuffer.getComponent(targetRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcComponent == null) {
            HytaleLogger.getLogger().at(Level.INFO).log("UseNPCInteraction requires a target NPCEntity but was used for: %s", targetRef);
            context.getState().state = InteractionState.Failed;
        } else {
            // Log interaction!
            LOGGER.atInfo().log("SimTale [DEBUG]: Interacao com NPC via UseNPCInteraction (tecla F). Player: " + playerRefComponent.getReference() + ", NPC: " + targetRef);

            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            SimNPCComponent npc = targetRef.getStore().getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            
            if (npc == null) {
                UUIDComponent uuidComp = targetRef.getStore().getComponent(targetRef, UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    // "simtale" shell, not Caskara's "default" — see SimNPCPersistence.DB_SHELL.
                    SimNPCData data = SimNPCPersistence.loadData(uuidComp.getUuid());
                    String name = null;
                    if (data != null) {
                        name = data.name;
                    } else {
                        com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName displayName = targetRef.getStore().getComponent(targetRef, com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName.getComponentType());
                        if (displayName != null && displayName.getDisplayName() != null) {
                            name = displayName.getDisplayName().toString();
                        }
                        if (name == null || name.isEmpty()) {
                            name = com.cookieukw.SimTale.core.SimNPCNameGenerator.generate();
                        }
                    }
                    npc = new SimNPCComponent(uuidComp.getUuid(), name);
                    npc.entityRef = targetRef;
                    SimNPCPersistence.loadNPC(npc);
                    commandBuffer.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    
                    SimTale.trackNpc(npc);
                }
            }

            LOGGER.atInfo().log("SimTale [DEBUG]: player=" + (player != null) + ", npc=" + (npc != null));
            if (player != null && npc != null) {
                final Player finalPlayer = player;
                final SimNPCComponent finalNpc = npc;
                final PlayerRef finalPlayerRefComp = playerRefComponent;
                ref.getStore().getExternalData().getWorld().execute(() -> finalPlayer.getPageManager().openCustomPage(ref, ref.getStore(), new NPCInteractionPage(finalPlayerRefComp, finalPlayer, finalNpc)));
            }

            if (npcComponent == null) {
                LOGGER.atInfo().log("SimTale [DEBUG]: npcComponent is null!");
                context.getState().state = InteractionState.Failed;
                return;
            }
            if (npcComponent.getRole() == null) {
                LOGGER.atInfo().log("SimTale [DEBUG]: npcComponent.getRole() is null!");
                context.getState().state = InteractionState.Failed;
                return;
            }
            StateSupport stateSupport = StateSupport.get(targetRef, targetRef.getStore());
            if (stateSupport == null) {
                LOGGER.atInfo().log("SimTale [DEBUG]: stateSupport is null!");
                context.getState().state = InteractionState.Failed;
                return;
            }
            if (!stateSupport.willInteractWith(ref)) {
                LOGGER.atInfo().log("SimTale [DEBUG]: stateSupport.willInteractWith(ref) is false, but we will bypass and proceed.");
            }
            LOGGER.atInfo().log("SimTale [DEBUG]: All checks passed, opening UI page...");
            // stateSupport.addInteraction(Objects.requireNonNull(playerRefComponent.getReference()));
            InteractionView interactionView = commandBuffer.getResource(Blackboard.getResourceType()).getView(InteractionView.class, 0L);
            if (interactionView.getReservationStatus(targetRef, ref, commandBuffer) == ReservationStatus.RESERVED_OTHER) {
                playerRefComponent.sendMessage(Message.translation("server.npc.npc.isBusy").param("roleName", npcComponent.getRoleName()));
                context.getState().state = InteractionState.Failed;
                return;
            }
        }
    }
}
