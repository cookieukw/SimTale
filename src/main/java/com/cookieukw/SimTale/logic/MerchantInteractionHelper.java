package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Routes a merchant interaction straight to the trade screen.
 *
 * <p>Merchants are not residents: they have no needs panel, no flirt/joke/insult actions and no
 * schedule. Opening the ordinary NPC page for them showed a full villager sheet with hunger and
 * traits, and that page also pins the Movement animation slot while it is open. The merchant drops
 * that page entirely and behaves like a villager counter: one click, one trade window.
 */
public final class MerchantInteractionHelper {
    private MerchantInteractionHelper() {
    }

    public static boolean openIfMerchant(Player player, PlayerRef playerRefComp,
                                         Ref<EntityStore> playerEntityRef, SimNPCComponent npc,
                                         Store<EntityStore> store) {
        if (player == null || playerRefComp == null || playerEntityRef == null || npc == null) {
            return false;
        }
        Ref<EntityStore> merchantRef = npc.entityRef;
        if (merchantRef == null || !merchantRef.isValid()
                || store == null
                || store.getComponent(merchantRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE) == null) {
            return false;
        }

        World world = store.getExternalData().getWorld();
        Runnable open = () -> {
            if (!merchantRef.isValid() || !playerEntityRef.isValid()) return;
            player.getPageManager().openCustomPage(playerEntityRef, merchantRef.getStore(),
                    new MerchantTradePage(playerRefComp, merchantRef));
        };
        if (world != null && world.isTicking()) {
            world.execute(open);
        } else {
            open.run();
        }
        return true;
    }
}
