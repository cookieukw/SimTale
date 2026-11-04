package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Currency;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.TradeResult;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.db.MerchantVisitData;
import com.cookieukw.SimTale.db.MerchantVisitPersistence;
import com.cookieukw.SimTale.db.PiggyBankPersistence;
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
import com.hypixel.hytale.server.core.universe.world.World;
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
        cmd.set("#BtnClose #CloseLabel.TextSpans", Message.translation("ui.merchant.close"));
        MerchantVisitorComponent visitor = store.getComponent(merchantRef,
                SimTale.MERCHANT_VISITOR_COMPONENT_TYPE);
        if (visitor == null || visitor.stock == null) {
            cmd.set("#Status.TextSpans", Message.translation("ui.merchant.status.unavailable"));
            return;
        }
        if (visitor.merchantName != null && !visitor.merchantName.isBlank()) {
            cmd.set("#Title.TextSpans",
                    Message.translation("ui.merchant.title").param("name", visitor.merchantName));
        }
        String specialtyKey = visitor.specialty != null && !visitor.specialty.isBlank()
                ? visitor.specialty
                : "general";
        cmd.set("#Subtitle.TextSpans", Message.translation("ui.merchant.subtitle")
                .insert(Message.raw(" · "))
                .insert(Message.translation("ui.merchant.specialty." + specialtyKey)));
        CombinedItemContainer wallet = InventoryComponent.getCombined(store, playerRef,
                InventoryComponent.HOTBAR_FIRST);
        long walletValue = Currency.inventoryValue(wallet);
        long piggyBalance = PiggyBankService.balance(store.getExternalData().getWorld(),
                this.playerRef.getUuid());
        cmd.set("#Balance.TextSpans",
                Message.translation("ui.merchant.balance")
                        .param("coins", Long.toString(walletValue))
                        .param("bank", Long.toString(piggyBalance)));
        cmd.set("#Status.TextSpans", Message.translation("ui.merchant.status.idle"));
        // The mockup ships five rows; hide the ones this visit did not roll so no placeholder text
        // in English ("Item name", "12 coins") leaks into an otherwise localized screen.
        cmd.set("#CategoryTabs.Visible", false);
        java.util.List<MerchantStock.Offer> offers = visitor.stock.offers();
        for (int row = 0; row < 5; row++) {
            String selector = "#Row" + row;
            if (row >= offers.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            MerchantStock.Offer offer = offers.get(row);
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #ItemName.TextSpans", itemName(offer.itemId()));
            cmd.set(selector + " #ItemMeta.TextSpans",
                    Message.translation("ui.merchant.meta")
                            .param("quantity", Integer.toString(offer.quantity()))
                            .param("sellPrice", Long.toString(offer.sellPrice())));
            cmd.set(selector + " #ItemPrice.TextSpans",
                    Message.translation("ui.merchant.price").param("price", Long.toString(offer.buyPrice())));
            cmd.set(selector + " #BtnBuy #BuyLabel.TextSpans", Message.translation("ui.merchant.buy"));
            cmd.set(selector + " #BtnSell #SellLabel.TextSpans", Message.translation("ui.merchant.sell"));
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #BtnBuy",
                    new EventData().append("button", "Buy" + row), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #BtnSell",
                    new EventData().append("button", "Sell" + row), false);
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
        boolean selling = data.contains("Sell");
        if (!selling && !data.contains("Buy")) return;
        int row;
        try { row = Integer.parseInt(data.replaceAll("\\D", "")); }
        catch (NumberFormatException e) { return; }
        MerchantVisitorComponent visitor = store.getComponent(merchantRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE);
        if (visitor == null || visitor.stock == null || row >= visitor.stock.offers().size()) return;
        MerchantStock.Offer offer = visitor.stock.offers().get(row);
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, playerRef,
                InventoryComponent.HOTBAR_FIRST);
        if (inventory == null) return;
        if (selling) {
            sell(store, inventory, visitor, offer);
            return;
        }
        if (offer.buyPrice() > Integer.MAX_VALUE) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.invalid_price"));
            return;
        }
        World world = store.getExternalData().getWorld();
        long walletValue = Currency.inventoryValue(inventory);
        long piggyBalance = PiggyBankService.balance(world, this.playerRef.getUuid());
        long totalFunds = walletValue + piggyBalance;
        if (totalFunds < offer.buyPrice()) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.no_coins"));
            return;
        }
        ItemStack item = new ItemStack(offer.itemId(), 1);
        if (!inventory.canAddItemStack(item)) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.inventory_full"));
            return;
        }
        TradeResult trade = visitor.stock.buy(offer.itemId(), 1, totalFunds);
        if (!trade.accepted()) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.sold_out"));
            return;
        }
        if (!PiggyBankService.pay(inventory, world, this.playerRef.getUuid(), trade.amount())) {
            visitor.stock.restore(offer.itemId(), 1);
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.no_coins"));
            return;
        }
        if (!inventory.canAddItemStack(item)) {
            /* Paying re-denominates the wallet and can fill the last free slot with change. Refund
            the purchase to savings instead of deleting the item or the money. */
            PiggyBankPersistence.add(world, this.playerRef.getUuid(), trade.amount());
            visitor.stock.restore(offer.itemId(), 1);
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.inventory_full"));
            return;
        }
        inventory.addItemStack(item);
        MerchantVisitPersistence.save(store.getExternalData().getWorld(), visitor.visitId,
                MerchantVisitData.of(visitor, store.getExternalData().getWorld()));
        this.playerRef.sendMessage(Message.translation("ui.merchant.status.bought")
                .param("item", itemName(offer.itemId())));
        this.rebuild();
    }

    /** Sells one unit of an offer back to the merchant, paying the player in coins. */
    private void sell(Store<EntityStore> store, CombinedItemContainer inventory,
                      MerchantVisitorComponent visitor, MerchantStock.Offer offer) {
        if (offer.sellPrice() > Integer.MAX_VALUE) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.invalid_price"));
            return;
        }
        if (count(inventory, offer.itemId()) <= 0) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.no_item")
                    .param("item", itemName(offer.itemId())));
            return;
        }
        TradeResult trade = visitor.stock.sell(offer.itemId(), 1);
        if (!trade.accepted()) {
            this.playerRef.sendMessage(Message.translation("ui.merchant.status.trade_failed"));
            return;
        }
        if (!remove(inventory, offer.itemId(), 1)) {
            visitor.stock.restore(offer.itemId(), 1);
            return;
        }
        World world = store.getExternalData().getWorld();
        PiggyBankService.credit(inventory, world, this.playerRef.getUuid(), trade.amount());
        MerchantVisitPersistence.save(store.getExternalData().getWorld(), visitor.visitId,
                MerchantVisitData.of(visitor, store.getExternalData().getWorld()));
        this.playerRef.sendMessage(Message.translation("ui.merchant.status.sold")
                .param("item", itemName(offer.itemId()))
                .param("price", Long.toString(trade.amount())));
        this.rebuild();
    }

    private static long count(CombinedItemContainer inventory, String id) {
        long total = 0L;
        for (short i = 0; i < inventory.getCapacity(); i++) {
            ItemStack item = inventory.getItemStack(i);
            if (item != null && id.equals(item.getItemId())) total += item.getQuantity();
        }
        return total;
    }

    /** Localized item name with a safe fallback to the raw item id. */
    private static Message itemName(String itemId) {
        try {
            Message name = new ItemStack(itemId, 1).getDisplayName();
            if (name != null) return name;
        } catch (Exception ignored) {
        }
        return Message.raw(itemId);
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
