package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.MerchantVisitData;
import com.cookieukw.SimTale.db.MerchantVisitPersistence;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Restores a persisted travelling merchant once when a world is first entered. */
public final class MerchantVisitorPersistenceManager {
    private static final Set<String> LOADED_WORLDS = ConcurrentHashMap.newKeySet();

    private MerchantVisitorPersistenceManager() {}

    public static void restore(World world, Store<EntityStore> store) {
        if (world == null || store == null || !LOADED_WORLDS.add(world.getName())) return;
        long now = world.getTick();
        for (MerchantVisitData data : MerchantVisitPersistence.list(world)) {
            if (data == null || data.merchantName == null || data.departureTick <= now) continue;
            PlayerRef player = world.getPlayerRefs().stream().findFirst().orElse(null);
            if (player == null || player.getReference() == null) continue;
            TransformComponent transform = store.getComponent(player.getReference(), TransformComponent.getComponentType());
            if (transform == null) continue;
            Vector3d pos = new Vector3d(transform.getPosition()).add(2, 0, 2);
            world.execute(() -> {
                var ref = SimNPCFactory.spawnNPC(store, pos, SimNPCFactory.NPCType.HUMAN_MALE);
                SimNPCComponent npc = store.getComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE);
                if (npc == null) return;
                npc.profession = Profession.MERCHANT;
                npc.name = data.merchantName;
                SimNPCFactory.refreshNameplate(store, ref, npc.name);
                store.putComponent(ref, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE,
                        new MerchantVisitorComponent(data.merchantName, data.seed, data.merchantName,
                                data.departureTick, data.toStock()));
            });
        }
    }
}
