package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.MerchantIdentity;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.core.MerchantStock;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.MerchantVisitData;
import com.cookieukw.SimTale.db.MerchantVisitPersistence;
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
        SimNPCComponent merchant = spawnMerchant(store, world, position);
        if (merchant == null) return;
        playerRef.sendMessage(Message.raw("Merchant spawned: " + merchant.name));
    }

    public static SimNPCComponent spawnMerchant(Store<EntityStore> store, World world, Vector3d position) {
        long seed = world.getTick() ^ java.util.concurrent.ThreadLocalRandom.current().nextLong();
        MerchantIdentity visitIdentity = MerchantIdentity.roll(seed);
        Ref<EntityStore> merchantRef = SimNPCFactory.spawnNPC(store, position,
                visitIdentity.npcType(), 1.0f, visitIdentity.variant());
        SimNPCComponent merchant = store.getComponent(merchantRef, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (merchant == null) return null;
        merchant.profession = Profession.MERCHANT;
        merchant.gender = visitIdentity.gender();
        merchant.name = visitIdentity.name();
        SimNPCFactory.refreshNameplate(store, merchantRef, merchant.name);
        MerchantStock stock = visitIdentity.specialty().catalog().roll(seed);
        long departureTick = world.getTick() + 20L * 60L * 30L;
        String visitId = merchant.entityId.toString();
        MerchantVisitorComponent visitor = new MerchantVisitorComponent(visitId, seed, merchant.name,
                visitIdentity.specialty().key(), visitIdentity.gender().name(),
                visitIdentity.variant(), departureTick, stock);
        store.putComponent(merchantRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE, visitor);
        // spawnNPC writes a general SimNPCData row before this visitor tag exists. Remove that
        // provisional permanent-roster entry; visitor save/expiry is handled by the visit shell.
        com.cookieukw.SimTale.db.SimNPCPersistence.deleteNPC(merchant.entityId);
        MerchantVisitPersistence.save(world, visitId, MerchantVisitData.of(visitor, world));
        return merchant;
    }
}
