package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
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
            
            // Guards for the re-attach path, which used to adopt ANY entity into the mod.
            //
            // They deliberately do NOT touch context.getState() and do NOT return early. This class
            // is registered over the engine's own UseNPCInteraction.DEFAULT_ID, so failing the
            // interaction here does not merely decline to open our screen — it breaks the shared
            // interaction pipeline, and with it doors, blocks and everything else. Declining is
            // done by simply leaving `npc` null, which the page condition below already handles.
            boolean isPlayerTarget = targetRef.getStore()
                    .getComponent(targetRef, Player.getComponentType()) != null;

            if (npc == null && !isPlayerTarget) {
                UUIDComponent uuidComp = targetRef.getStore().getComponent(targetRef, UUIDComponent.getComponentType());
                // "simtale" shell, not Caskara's "default" — see SimNPCPersistence.DB_SHELL.
                // No record means this is not one of ours and there is nothing to re-attach.
                SimNPCData data = uuidComp != null
                        ? SimNPCPersistence.loadData(uuidComp.getUuid())
                        : null;
                if (uuidComp != null && data != null) {
                    String name = data.name;
                    if (name == null || name.isEmpty()) {
                        com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName displayName = targetRef.getStore().getComponent(targetRef, com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName.getComponentType());
                        if (displayName != null && displayName.getDisplayName() != null) {
                            name = displayName.getDisplayName().toString();
                        }
                    }
                    if (name == null || name.isEmpty()) {
                        name = com.cookieukw.SimTale.core.SimNPCNameGenerator.generate();
                    }

                    npc = new SimNPCComponent(uuidComp.getUuid(), name);
                    npc.entityRef = targetRef;
                    SimNPCPersistence.loadNPC(npc);
                    commandBuffer.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);

                    SimTale.trackNpc(npc);
                }
            }

            // Entities adopted before these guards still carry the component and a saved record.
            // Gender is the tell: spawnNPC always sets it, adoption never did. Same rule as
            // /simtale forget, so what refuses to open here is exactly what that command clears.
            boolean wronglyAdopted = npc != null && npc.gender == null;

            // Block interaction if NPC is currently sleeping or heading to bed
            RoutineAIComponent ai = targetRef.getStore().getComponent(targetRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null && (ai.currentTask == RoutineAIComponent.TaskType.SLEEPING
                    || ai.currentTask == RoutineAIComponent.TaskType.ENTERING_BED
                    || ai.currentTask == RoutineAIComponent.TaskType.WAKING)) {
                playerRefComponent.sendMessage(Message.translation("general.npc.sleeping").param("name", npc != null ? npc.name : "NPC"));
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Pleading for a life, mid-collection: interacting with the Reaper while she's
            // actively REAPING short-circuits the normal interaction panel entirely — no
            // friendly chat, no gifts, no marriage proposal to Death. Holding an
            // Ingredient_Voidheart buys the NPC back; anything else (or nothing) just gets
            // turned away.
            if (npc != null && npc.isReaper && ai != null && ai.currentTask == RoutineAIComponent.TaskType.REAPING) {
                World world = targetRef.getStore().getExternalData().getWorld();
                ItemStack heldItem = InventoryComponent.getItemInHand(ref.getStore(), ref);

                if (heldItem != null && heldItem.getItemId().equals("Ingredient_Voidheart")
                        && ai.dyingEntityId != null && world != null) {
                    Ref<EntityStore> dyingRef = world.getEntityStore().getRefFromUUID(ai.dyingEntityId);
                    RoutineAIComponent dyingAi = dyingRef != null
                            ? dyingRef.getStore().getComponent(dyingRef, SimTale.ROUTINE_AI_COMPONENT_TYPE) : null;
                    SimNPCComponent dyingNpc = dyingRef != null
                            ? dyingRef.getStore().getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE) : null;

                    if (dyingAi != null && dyingNpc != null) {
                        dyingAi.currentTask = RoutineAIComponent.TaskType.IDLE;
                        dyingAi.taskStartTime = 0;

                        // Same hotbar-removal pattern as the Baby item drop in SimTaleEventHandler.
                        InventoryComponent.Hotbar hotbarComponent = ref.getStore().getComponent(ref, InventoryComponent.Hotbar.getComponentType());
                        if (hotbarComponent != null && hotbarComponent.getActiveSlot() != -1) {
                            CombinedItemContainer combinedInventory =
                                    InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_FIRST);
                            combinedInventory.removeItemStackFromSlot(hotbarComponent.getActiveSlot(), heldItem, 1);
                        }

                        playerRefComponent.sendMessage(Message.raw("[SimTale] A Morte aceita o Voidheart e poupa " + dyingNpc.name + "."));

                        SimTale.untrackNpc(npc);
                        commandBuffer.removeEntity(targetRef, RemoveReason.REMOVE);
                    } else {
                        playerRefComponent.sendMessage(Message.raw("[SimTale] Tarde demais — a alma ja foi."));
                    }
                } else {
                    playerRefComponent.sendMessage(Message.raw("[SimTale] A Morte nao aceita nada alem de um Ingredient_Voidheart em troca de uma vida."));
                }

                context.getState().state = InteractionState.Failed;
                return;
            }

            LOGGER.atInfo().log("SimTale [DEBUG]: player=" + (player != null) + ", npc=" + (npc != null));
            if (player != null && npc != null && !wronglyAdopted) {
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
                playerRefComponent.sendMessage(Message.translation("general.npc.busy").param("roleName", npcComponent.getRoleName()));
                context.getState().state = InteractionState.Failed;
                return;
            }
        }
    }
}
