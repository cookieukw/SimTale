package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class SimTaleGenericItemInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    // Codec needed for registration
    public static final BuilderCodec<SimTaleGenericItemInteraction> CODEC =
            BuilderCodec.builder(
                    SimTaleGenericItemInteraction.class,
                    SimTaleGenericItemInteraction::new,
                    SimpleInstantInteraction.CODEC
            ).build();

    public SimTaleGenericItemInteraction() { super(); }
    public SimTaleGenericItemInteraction(String id) { super(id); }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        LOGGER.atInfo().log("SimTale Debug: SimTaleGenericItemInteraction firstRun executed!");
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Player player = playerRef.getStore().getComponent(playerRef, Player.getComponentType());
        PlayerRef playerRefComponent = commandBuffer.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || playerRefComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack heldItem = InventoryComponent.getItemInHand(playerRef.getStore(), playerRef);
        if (heldItem == null || heldItem.getItemId() == null) {
            LOGGER.atInfo().log("SimTale Debug: Held item is null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        LOGGER.atInfo().log("SimTale Debug: Processing custom interaction for item: " + heldItem.getItemId());
        
        // Pass to the manager!
        boolean handled = SimTaleItemManager.handleItemUse(heldItem.getItemId(), player, playerRefComponent);
        
        if (handled) {
            context.getState().state = InteractionState.Finished;
        } else {
            context.getState().state = InteractionState.Failed;
            LOGGER.atInfo().log("SimTale Debug: No handler registered for item: " + heldItem.getItemId());
        }
    }

}
