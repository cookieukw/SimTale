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
                && store.getComponent(npc.entityRef, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE) != null)) return;
        if (SimTale.ACTIVE_NPCS.size() < 3 || ThreadLocalRandom.current().nextDouble() > 0.25) return;

        PlayerRef player = world.getPlayerRefs().stream().findFirst().orElse(null);
        if (player == null || player.getReference() == null) return;
        TransformComponent transform = store.getComponent(player.getReference(), TransformComponent.getComponentType());
        if (transform == null) return;
        org.joml.Vector3d playerPos = transform.getPosition();
        Village village = VillageManager.nearest(world, playerPos.x, playerPos.z);
        if (village == null) return;

        org.joml.Vector3d arrival = findDistantVillagePoint(world, village, playerPos);
        if (arrival == null) return;
        world.execute(() -> MerchantCommand.spawnMerchant(store, world, arrival));
    }

    private static org.joml.Vector3d findDistantVillagePoint(World world, Village village,
                                                               org.joml.Vector3d playerPos) {
        double radius = Math.max(10.0, village.radius() - 5.0);
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2.0);
            double x = village.centerX() + Math.cos(angle) * radius;
            double z = village.centerZ() + Math.sin(angle) * radius;
            double dx = x - playerPos.x;
            double dz = z - playerPos.z;
            if (dx * dx + dz * dz < 20.0 * 20.0) continue;
            return new org.joml.Vector3d(x, playerPos.y, z);
        }
        return null;
    }
}
