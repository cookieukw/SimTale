package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class SimTaleCheckPregnancyInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    public static final BuilderCodec<SimTaleCheckPregnancyInteraction> CODEC =
            BuilderCodec.builder(
                    SimTaleCheckPregnancyInteraction.class,
                    SimTaleCheckPregnancyInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).build();

    public SimTaleCheckPregnancyInteraction() { super(); }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        LOGGER.atInfo().log("SimTale Debug: SimTaleCheckPregnancyInteraction firstRun executed!");
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Store<EntityStore> store = playerRef.getStore();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        SimPlayerComponent playerComp = store.getComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE);
        if (playerComp == null) {
            playerComp = new SimPlayerComponent(playerRefComponent.getUuid());
            commandBuffer.addComponent(playerRef, SimTale.SIM_PLAYER_COMPONENT_TYPE, playerComp);
        }

        Ref<EntityStore> targetRef = context.getTargetEntity();

        /* A pregnancy test reports a state; it does not decide who is allowed to be in one.

        Gender was checked first, so a pregnancy forced through /simtale forcepreg on a male
        subject was invisible to the very item meant to reveal it — the test denied a pregnancy
        that demonstrably existed. Actual state now wins, and gender is only the fallback for
        "you are not pregnant, and here is why you were never going to be".
        */
        if (targetRef != null) {
            SimNPCComponent targetNPC = store.getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (targetNPC == null) {
                playerRefComponent.sendMessage(Message.translation("general.pregtest.not_npc"));
                context.getState().state = InteractionState.Failed;
                return;
            }

            /* The Reaper is not a villager and is not a candidate for anything this item asks. She
            is also mid-ceremony whenever she exists, and every page here freezes its target and
            sets isInteractingViaUI — which is how a Reaper ended up standing in the world for
            good. RoutineAISystem no longer lets a page stall the ritual, but opening a pregnancy
            panel on Death is nonsense on its own terms.
            */
            if (targetNPC.isReaper) {
                playerRefComponent.sendMessage(Message.translation("general.pregtest.reaper"));
                context.getState().state = InteractionState.Failed;
                return;
            }

            /* Children are off limits regardless of anything else, and before any state check —
            the panel this opens is a pregnancy panel.
            */
            if (InteractionManager.isNpcAChild(targetNPC)) {
                playerRefComponent.sendMessage(Message.translation("general.pregtest.child").param("name", targetNPC.name));
                context.getState().state = InteractionState.Failed;
                return;
            }

            boolean npcIsPregnant = targetNPC.pregnancy != null && targetNPC.pregnancy.pregnant;
            if (!npcIsPregnant && targetNPC.gender != Gender.FEMALE) {
                playerRefComponent.sendMessage(Message.translation("general.pregtest.npc_not_female").param("name", targetNPC.name));
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Execute on main thread
            final SimNPCComponent finalNPC = targetNPC;
            store.getExternalData().getWorld().execute(() -> player.getPageManager().openCustomPage(playerRef, store, new NPCPregnancyPage(playerRefComponent, player, finalNPC)));

        } else {
            boolean playerIsPregnant = playerComp.pregnancy != null && playerComp.pregnancy.pregnant;

            if (!playerIsPregnant) {
                if (playerComp.gender == null) {
                    playerRefComponent.sendMessage(Message.translation("general.pregtest.no_gender"));
                    context.getState().state = InteractionState.Failed;
                    return;
                }
                if (playerComp.gender != Gender.FEMALE) {
                    playerRefComponent.sendMessage(Message.translation("general.pregtest.player_not_female"));
                    context.getState().state = InteractionState.Failed;
                    return;
                }
            }

            // Execute on main thread
            final SimPlayerComponent finalPlayerComp = playerComp;
            store.getExternalData().getWorld().execute(() -> player.getPageManager().openCustomPage(playerRef, store, new PlayerPregnancyPage(playerRefComponent, player, finalPlayerComp)));
        }

        // Consume item
        InventoryComponent.Hotbar hotbarComponent = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbarComponent != null && hotbarComponent.getActiveSlot() != -1) {
            ItemStack heldItem = InventoryComponent.getItemInHand(store, playerRef);
            if (heldItem != null) {
                CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
                combinedInventory.removeItemStackFromSlot(hotbarComponent.getActiveSlot(), heldItem, 1);
            }
        }
        
        context.getState().state = InteractionState.Finished;
    }
}
