package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.interaction.InteractionView;
import com.hypixel.hytale.server.npc.blackboard.view.interaction.ReservationStatus;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import java.util.UUID;
import com.hypixel.hytale.server.core.entity.UUIDComponent;

public class SimTaleUseNPCInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public SimTaleUseNPCInteraction(String id) {
        super(id);
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        PlayerRef playerRefComponent = (PlayerRef) commandBuffer.getComponent(ref, PlayerRef.getComponentType());
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
        NPCEntity npcComponent = (NPCEntity) commandBuffer.getComponent(targetRef, NPCEntity.getComponentType());
        if (npcComponent == null) {
            HytaleLogger.getLogger().at(Level.INFO).log("UseNPCInteraction requires a target NPCEntity but was used for: %s", targetRef);
            context.getState().state = InteractionState.Failed;
        } else {
            // Log interaction!
            LOGGER.atInfo().log("SimTale [DEBUG]: Interacao com NPC via UseNPCInteraction (tecla F). Player: " + playerRefComponent.getReference() + ", NPC: " + targetRef);

            Player player = (Player) ref.getStore().getComponent(ref, Player.getComponentType());
            SimNPCComponent npc = targetRef.getStore().getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            
            if (npc == null) {
                UUIDComponent uuidComp = targetRef.getStore().getComponent(targetRef, UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    SimNPCData data = Caskara.load(uuidComp.getUuid().toString(), SimNPCData.class);
                    if (data != null) {
                        npc = new SimNPCComponent(uuidComp.getUuid(), data.name);
                        npc.entityRef = targetRef;
                        SimNPCPersistence.loadNPC(npc);
                        targetRef.getStore().addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                        
                        final UUID targetId = uuidComp.getUuid();
                        SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId));
                        SimTale.ACTIVE_NPCS.add(npc);
                    }
                }
            }

            if (player != null && npc != null) {
                final Player finalPlayer = player;
                final SimNPCComponent finalNpc = npc;
                final PlayerRef finalPlayerRefComp = playerRefComponent;
                ref.getStore().getExternalData().getWorld().execute(() -> {
                    finalPlayer.getPageManager().openCustomPage(ref, ref.getStore(), new NPCInteractionPage(finalPlayerRefComp, finalPlayer, finalNpc));
                });
            }

            if (!npcComponent.getRole().getStateSupport().willInteractWith(ref)) {
                context.getState().state = InteractionState.Failed;
                return;
            }
            InteractionView interactionView = (InteractionView) ((Blackboard) commandBuffer.getResource(Blackboard.getResourceType())).getView(InteractionView.class, 0L);
            if (interactionView.getReservationStatus(targetRef, ref, commandBuffer) == ReservationStatus.RESERVED_OTHER) {
                playerRefComponent.sendMessage(Message.translation("server.npc.npc.isBusy").param("roleName", npcComponent.getRoleName()));
                context.getState().state = InteractionState.Failed;
                return;
            }
            npcComponent.getRole().getStateSupport().addInteraction(playerRefComponent.getReference());
        }
    }
}
