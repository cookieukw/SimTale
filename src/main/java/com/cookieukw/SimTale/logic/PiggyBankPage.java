package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/** Deposit and withdraw screen opened by the piggy bank item. */
public final class PiggyBankPage extends InteractiveCustomUIPage<String> {
    private static final long WITHDRAW_SMALL = 100L;
    private static final long WITHDRAW_LARGE = 1_000L;

    private final PlayerRef playerRef;
    private final Player player;

    public PiggyBankPage(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        super(playerRef, CustomPageLifetime.CanDismiss,
                BuilderCodec.builder(String.class, String::new).build());
        this.playerRef = playerRef;
        this.player = player;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("PiggyBank/PiggyBank.ui");
        World world = store.getExternalData().getWorld();
        UUID playerId = this.playerRef.getUuid();
        long balance = PiggyBankService.balance(world, playerId);

        cmd.set("#Title.TextSpans", Message.translation("ui.piggy.title"));
        cmd.set("#Subtitle.TextSpans", Message.translation("ui.piggy.subtitle"));
        cmd.set("#Balance.TextSpans",
                Message.translation("ui.piggy.balance").param("coins", Long.toString(balance)));
        cmd.set("#Hint.TextSpans", Message.translation("ui.piggy.hint"));
        cmd.set("#DepositLabel.TextSpans", Message.translation("ui.piggy.deposit"));
        cmd.set("#Withdraw100Label.TextSpans", Message.translation("ui.piggy.withdraw100"));
        cmd.set("#Withdraw1000Label.TextSpans", Message.translation("ui.piggy.withdraw1000"));
        cmd.set("#WithdrawAllLabel.TextSpans", Message.translation("ui.piggy.withdrawAll"));
        cmd.set("#CloseLabel.TextSpans", Message.translation("ui.piggy.close"));
        cmd.set("#Status.TextSpans", Message.raw(""));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDepositAll",
                new EventData().append("button", "DepositAll"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnWithdraw100",
                new EventData().append("button", "Withdraw100"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnWithdraw1000",
                new EventData().append("button", "Withdraw1000"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnWithdrawAll",
                new EventData().append("button", "WithdrawAll"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnClose",
                new EventData().append("button", "Close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store,
                                @Nonnull String data) {
        if (data.contains("Close")) {
            player.getPageManager().setPage(playerRef, store, Page.None);
            return;
        }

        World world = store.getExternalData().getWorld();
        UUID playerId = this.playerRef.getUuid();
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, playerRef,
                InventoryComponent.HOTBAR_FIRST);
        if (inventory == null) return;

        if (data.contains("DepositAll")) {
            long deposited = PiggyBankService.depositAll(inventory, world, playerId);
            if (deposited <= 0L) {
                this.playerRef.sendMessage(Message.translation("ui.piggy.status.no_coins"));
            } else {
                this.playerRef.sendMessage(Message.translation("ui.piggy.status.deposited")
                        .param("amount", Long.toString(deposited)));
            }
            this.rebuild();
            return;
        }

        long requested = data.contains("Withdraw1000") ? WITHDRAW_LARGE
                : data.contains("Withdraw100") ? WITHDRAW_SMALL
                : 0L; // WithdrawAll
        long withdrawn = PiggyBankService.withdraw(inventory, world, playerId, requested);
        if (withdrawn == PiggyBankService.INVENTORY_FULL) {
            this.playerRef.sendMessage(Message.translation("ui.piggy.status.inventory_full"));
        } else if (withdrawn <= 0L) {
            this.playerRef.sendMessage(Message.translation("ui.piggy.status.empty"));
        } else {
            this.playerRef.sendMessage(Message.translation("ui.piggy.status.withdrawn")
                    .param("amount", Long.toString(withdrawn)));
        }
        this.rebuild();
    }
}
