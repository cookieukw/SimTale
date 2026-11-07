package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.MerchantCommand;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.systems.VillageManager.Village;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.system.tick.TickingSystem;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Terraria-style arrival check for temporary travelling merchants. */
public final class MerchantArrivalSystem extends TickingSystem<EntityStore> {
    private static final long CHECK_INTERVAL_TICKS = 20L * 60L * 15L;
    private static final Map<World, Long> LAST_CHECK = new ConcurrentHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) return;
        long tick = world.getTick();
        long last = LAST_CHECK.getOrDefault(world, Long.MIN_VALUE);
        if (tick - last < CHECK_INTERVAL_TICKS) return;
        LAST_CHECK.put(world, tick);

        if (SimTale.ACTIVE_NPCS.stream().anyMatch(npc -> npc.entityRef != null
                && npc.entityRef.isValid()
                && npc.entityRef.getStore() == store
                && store.getComponent(npc.entityRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE) != null)) return;
        if (SimTale.ACTIVE_NPCS.size() < 3 || ThreadLocalRandom.current().nextDouble() > 0.25) return;

        PlayerRef player = world.getPlayerRefs().stream().findFirst().orElse(null);
        if (player == null || player.getReference() == null) return;
        TransformComponent transform = store.getComponent(player.getReference(), TransformComponent.getComponentType());
        if (transform == null) return;
        org.joml.Vector3d playerPos = transform.getPosition();
        Village village = VillageManager.nearest(world, playerPos.x, playerPos.z);
        if (village == null) return;

        org.joml.Vector3d arrival = findDistantVillagePoint(store, village, playerPos);
        if (arrival == null) return;
        world.execute(() -> MerchantCommand.spawnMerchant(store, world, arrival));
    }

    /** Prefers ground a villager already stands on, so the merchant never arrives inside a hill. */
    private static org.joml.Vector3d findDistantVillagePoint(Store<EntityStore> store, Village village,
                                                              org.joml.Vector3d playerPos) {
        java.util.List<org.joml.Vector3d> candidates = new java.util.ArrayList<>();
        double villageLimit = (village.radius() + 16.0) * (village.radius() + 16.0);
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef == null || !npc.entityRef.isValid() || npc.entityRef.getStore() != store) continue;
            TransformComponent transform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
            if (transform == null) continue;
            org.joml.Vector3d pos = transform.getPosition();
            double dx = pos.x - playerPos.x;
            double dz = pos.z - playerPos.z;
            if (dx * dx + dz * dz < 28.0 * 28.0) continue;
            double vx = pos.x - village.centerX();
            double vz = pos.z - village.centerZ();
            if (vx * vx + vz * vz > villageLimit) continue;
            candidates.add(new org.joml.Vector3d(pos));
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!candidates.isEmpty()) {
            org.joml.Vector3d base = candidates.get(random.nextInt(candidates.size()));
            double angle = random.nextDouble(0, Math.PI * 2.0);
            return new org.joml.Vector3d(base.x + Math.cos(angle) * 1.5, base.y, base.z + Math.sin(angle) * 1.5);
        }
        return null;
    }
}
