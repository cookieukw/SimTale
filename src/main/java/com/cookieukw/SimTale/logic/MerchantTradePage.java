package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** First functional merchant screen: displays the visit stock and executes safe coin trades. */
public final class MerchantTradePage extends InteractiveCustomUIPage<String> {
    private final PlayerRef playerRef;
    private final Ref<EntityStore> merchantRef;

    public MerchantTradePage(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> merchantRef) {
        super(playerRef, CustomPageLifetime.CanDismiss,
                BuilderCodec.builder(String.class, String::new).build());
        this.playerRef = playerRef;
        this.merchantRef = merchantRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("MerchantTrade/MerchantTrade.ui");
        MerchantVisitorComponent visitor = store.getComponent(merchantRef,
                SimTale.MERCHANT_VISITOR_COMPONENT_TYPE);
        if (visitor == null || visitor.stock == null) {
            cmd.set("#Status.TextSpans", Message.raw("Merchant stock unavailable."));
            return;
        }
        int row = 0;
        for (MerchantStock.Offer offer : visitor.stock.offers()) {
            if (row >= 5) break;
            String selector = "#Row" + row;
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #ItemName.TextSpans", Message.raw(offer.itemId()));
            cmd.set(selector + " #ItemMeta.TextSpans", Message.raw("In stock: " + offer.quantity()));
            cmd.set(selector + " #ItemPrice.TextSpans", Message.raw(offer.buyPrice() + " coins"));
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #BtnBuy",
                    new EventData().append("button", "Buy" + row), false);
            row++;
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnClose",
                new EventData().append("button", "Close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store,
                                @Nonnull String data) {
        if (data.contains("Close")) {
            playerRef.getStore().getComponent(playerRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                    .getPageManager().setPage(playerRef, store, Page.None);
            return;
        }
        if (!data.contains("Buy")) return;
        int row;
        try { row = Integer.parseInt(data.replaceAll("\\D", "")); }
        catch (NumberFormatException e) { return; }
        MerchantVisitorComponent visitor = store.getComponent(merchantRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE);
        if (visitor == null || visitor.stock == null || row >= visitor.stock.offers().size()) return;
        MerchantStock.Offer offer = visitor.stock.offers().get(row);
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, playerRef,
                InventoryComponent.HOTBAR_FIRST);
        if (inventory == null) return;
        int coins = count(inventory, "SimTale_Coin");
        if (coins < offer.buyPrice()) {
            this.playerRef.sendMessage(Message.raw("Not enough coins."));
            return;
        }
        ItemStack item = new ItemStack(offer.itemId(), 1);
        if (!inventory.canAddItemStack(item)) {
            this.playerRef.sendMessage(Message.raw("Your inventory is full."));
            return;
        }
        if (!remove(inventory, "SimTale_Coin", (int) offer.buyPrice())) return;
        visitor.stock.buy(offer.itemId(), 1, offer.buyPrice());
        inventory.addItemStack(item);
        this.playerRef.sendMessage(Message.raw("Bought " + offer.itemId() + "."));
        this.rebuild();
    }

    private static int count(CombinedItemContainer inventory, String id) {
        int total = 0;
        for (short i = 0; i < inventory.getCapacity(); i++) {
            ItemStack item = inventory.getItemStack(i);
            if (item != null && id.equals(item.getItemId())) total += item.getQuantity();
        }
        return total;
    }

    private static boolean remove(CombinedItemContainer inventory, String id, int amount) {
        int remaining = amount;
        for (short i = 0; i < inventory.getCapacity() && remaining > 0; i++) {
            ItemStack item = inventory.getItemStack(i);
            if (item == null || !id.equals(item.getItemId())) continue;
            int take = Math.min(remaining, item.getQuantity());
            inventory.removeItemStackFromSlot(i, take);
            remaining -= take;
        }
        return remaining == 0;
    }
}
