package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Tick system that monitors pregnant Players and triggers birth
 * when the pregnancy duration expires. Also updates active construction previews in real-time.
 */
public class PlayerPregnancyTickSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null) return;

        // Get current world
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        long worldTick = world.getTick();

        // Tick real-time ghost preview if player has an active session
        UUIDComponent uuidComp = store.getComponent(playerRef, UUIDComponent.getComponentType());
        UUID playerUuid = uuidComp != null ? uuidComp.getUuid() : null;

        if (playerUuid != null) {
            ConstructionSiteComponent preview = ConstructionPreviewManager.get(playerUuid);
            if (preview != null) {
                TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    double yawRad = Math.toRadians(Math.toDegrees(transform.getRotation().yaw()));
                    double forwardX = -Math.sin(yawRad);
                    double forwardZ = Math.cos(yawRad);
                    int tx = (int) Math.floor(pos.x + forwardX * 6.0);
                    int ty = (int) Math.floor(pos.y);
                    int tz = (int) Math.floor(pos.z + forwardZ * 6.0);

                    Rotation4 facing = Rotation4.fromYawDegrees(Math.toDegrees(transform.getRotation().yaw()));
                    ConstructionPreviewManager.update(playerUuid, world, new Vector3i(tx, ty, tz), facing);
                }
            }
        }

        // --- Pregnancy logic ---
        SimPlayerComponent playerComp = chunk.getComponent(index, SimTale.SIM_PLAYER_COMPONENT_TYPE);
        if (playerComp == null) return;

        PregnancyComponent pregnancy = playerComp.pregnancy;
        if (pregnancy == null || !pregnancy.pregnant) return;

        // Update trimester
        boolean trimesterChanged = pregnancy.updateTrimester(worldTick);
        if (trimesterChanged || worldTick % 100 == 0) {
            LifecycleManager.applyPregnancySpeedDebuff(playerRef, pregnancy);
        }

        // Apply pregnancy behavior
        LifecycleManager.applyPlayerPregnancyBehavior(playerRef, playerComp);

        // Check if it's time to give birth
        if (pregnancy.isReadyToBirth(worldTick)) {
            LOGGER.atInfo().log("SimTale: Player " + playerComp.playerUuid + " is giving birth!");
            LifecycleManager.birthPlayerBaby(playerRef, playerComp, store, worldTick);
        }
    }
}
