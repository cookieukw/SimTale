package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.TradingCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/** Development command for spawning a travelling merchant while the shop UI is built. */
public final class MerchantCommand extends AbstractPlayerCommand {
    public MerchantCommand() {
        super("merchant", "Spawns a temporary travelling merchant");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d position = new Vector3d(transform.getPosition()).add(2, 0, 2);
        Ref<EntityStore> merchantRef = SimNPCFactory.spawnNPC(store, position,
                Math.random() < 0.5 ? SimNPCFactory.NPCType.HUMAN_MALE : SimNPCFactory.NPCType.HUMAN_FEMALE);
        SimNPCComponent merchant = store.getComponent(merchantRef, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (merchant == null) return;
        merchant.profession = Profession.MERCHANT;
        merchant.name = "Viajante " + merchant.name;
        SimNPCFactory.refreshNameplate(store, merchantRef, merchant.name);
        TradingCatalog catalog = new TradingCatalog(java.util.List.of(
                TradingCatalog.item("Food_Bread", 3, 12, 4, 2),
                TradingCatalog.item("Plant_Seeds_Carrot", 4, 16, 3, 1),
                TradingCatalog.item("Ingredient_Iron_Bar", 1, 4, 12, 6)));
        long seed = world.getTick() ^ merchant.entityId.getMostSignificantBits();
        MerchantStock stock = catalog.roll(seed);
        store.putComponent(merchantRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE,
                new MerchantVisitorComponent(world.getTick() + 20L * 60L * 10L, stock));
        playerRef.sendMessage(Message.raw("Merchant spawned: " + merchant.name));
    }
}
