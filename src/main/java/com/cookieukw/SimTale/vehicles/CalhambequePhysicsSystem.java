package com.cookieukw.SimTale.vehicles;

import com.cookieukw.SimTale.SimTale;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Physics and movement simulation system for the 1930s Vintage Calhambeque car.
 * Handles steering, acceleration, reverse, gravity, step climbing, solid collision, and animations.
 */
public class CalhambequePhysicsSystem extends EntityTickingSystem<EntityStore> {

    private static final float MAX_FORWARD_SPEED = 14.0f;
    private static final float MAX_REVERSE_SPEED = -5.0f;
    private static final float ACCELERATION = 9.0f;
    private static final float DECELERATION = 12.0f;
    private static final float STEER_SPEED = 2.4f;
    private static final float GRAVITY = 22.0f;
    private static final float STEP_HEIGHT = 1.0f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                SimTale.CALHAMBEQUE_COMPONENT_TYPE,
                TransformComponent.getComponentType()
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.BEFORE, PlayerProcessMovementSystem.class)
        );
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        CalhambequeComponent car = chunk.getComponent(index, SimTale.CALHAMBEQUE_COMPONENT_TYPE);
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (car == null || transform == null) return;

        Ref<EntityStore> carRef = chunk.getReferenceTo(index);
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null) return;

        // Bounded delta time to avoid large physics steps on lag spikes
        float clampedDt = Math.max(0.001f, Math.min(dt, 0.1f));

        Vector3d pos = transform.getPosition();
        Rotation3f rot = transform.getRotation();
        float carYaw = rot.yaw();

        // 1. Process Passenger
        if (car.passengerUuid != null) {
            Ref<EntityStore> passRef = store.getExternalData().getRefFromUUID(car.passengerUuid);
            if (passRef == null || !passRef.isValid()) {
                car.passengerUuid = null;
            } else {
                MovementStatesComponent passMsc = store.getComponent(passRef, MovementStatesComponent.getComponentType());
                MovementStates passMs = passMsc != null ? passMsc.getMovementStates() : null;
                if (passMs != null && (passMs.crouching || passMs.forcedCrouching)) {
                    // Passenger dismounts
                    int carNetId = 0;
                    NetworkId netId = store.getComponent(carRef, NetworkId.getComponentType());
                    if (netId != null) carNetId = netId.getId();

                    car.passengerUuid = null;

                    PlayerRef passPlayerRef = store.getComponent(passRef, Universe.get().getPlayerRefComponentType());
                    if (passPlayerRef != null && passPlayerRef.getPacketHandler() != null) {
                        passPlayerRef.getPacketHandler().write(new DismountNPC(carNetId));
                        passPlayerRef.sendMessage(Message.raw("§6[Calhambeque 1930s] §7Você desceu do banco do passageiro."));
                    }

                    TransformComponent passTransform = store.getComponent(passRef, TransformComponent.getComponentType());
                    if (passTransform != null) {
                        double sideX = Math.cos(carYaw) * 2.0;
                        double sideZ = -Math.sin(carYaw) * 2.0;
                        passTransform.setPosition(new Vector3d(pos.x + sideX, pos.y + 0.1, pos.z + sideZ));
                    }
                } else {
                    // Sync passenger transform
                    TransformComponent passTransform = store.getComponent(passRef, TransformComponent.getComponentType());
                    if (passTransform != null) {
                        double cos = Math.cos(carYaw);
                        double sin = Math.sin(carYaw);
                        double wx = pos.x + (car.passengerSeatOffset.x * cos - car.passengerSeatOffset.z * sin);
                        double wz = pos.z + (car.passengerSeatOffset.x * sin + car.passengerSeatOffset.z * cos);
                        passTransform.setPosition(new Vector3d(wx, pos.y + car.passengerSeatOffset.y, wz));
                    }
                }
            }
        }

        // 2. Process Driver
        float throttle = 0f;
        boolean hasActiveDriver = false;
        Ref<EntityStore> driverRef = null;

        if (car.driverUuid != null) {
            driverRef = store.getExternalData().getRefFromUUID(car.driverUuid);
            if (driverRef == null || !driverRef.isValid()) {
                car.driverUuid = null;
                car.speed = 0f;
                car.engineRunning = false;
            } else {
                hasActiveDriver = true;
                MovementStatesComponent msc = store.getComponent(driverRef, MovementStatesComponent.getComponentType());
                MovementStates ms = msc != null ? msc.getMovementStates() : null;
                TransformComponent driverTransform = store.getComponent(driverRef, TransformComponent.getComponentType());

                // Dismount driver on Crouch (Shift)
                if (ms != null && (ms.crouching || ms.forcedCrouching)) {
                    PlayerRef pRef = store.getComponent(driverRef, Universe.get().getPlayerRefComponentType());
                    CalhambequeManager.dismountDriver(store, carRef, car, driverRef, pRef);
                    hasActiveDriver = false;
                } else {
                    // Honk horn on Jump (Space)
                    if (ms != null && ms.jumping) {
                        long currentTick = world.getTick();
                        if (currentTick - car.lastHonkTick > 20) {
                            car.lastHonkTick = currentTick;
                            PlayerRef pRef = store.getComponent(driverRef, Universe.get().getPlayerRefComponentType());
                            if (pRef != null) {
                                pRef.sendMessage(Message.raw("§e[Calhambeque] §6* FON-FON! (Ahooga!) *"));
                            }
                        }
                    }

                    float driverYaw = driverTransform != null ? driverTransform.getRotation().yaw() : carYaw;

                    // Steer towards driver's view direction
                    if (Math.abs(car.speed) > 0.05f) {
                        float diff = normalizeAngle(driverYaw - carYaw);
                        float maxTurn = STEER_SPEED * clampedDt;
                        float turn = Math.max(-maxTurn, Math.min(maxTurn, diff));
                        if (car.speed < 0) {
                            turn = -turn;
                        }
                        carYaw += turn;
                        rot.setYaw(carYaw);
                    }

                    // Forward facing vector
                    double dirX = -Math.sin(carYaw);
                    double dirZ = -Math.cos(carYaw);

                    // Check player inputs for throttle
                    PlayerInput playerInput = store.getComponent(driverRef, PlayerInput.getComponentType());
                    if (playerInput != null && playerInput.getMovementUpdateQueue() != null) {
                        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();
                        for (PlayerInput.InputUpdate update : queue) {
                            if (update instanceof PlayerInput.RelativeMovement rm) {
                                double dot = rm.getX() * dirX + rm.getZ() * dirZ;
                                if (dot > 0.02) throttle = 1f;
                                else if (dot < -0.02) throttle = -1f;
                            }
                        }
                    }

                    // Fallback to MovementStates
                    if (throttle == 0f && ms != null && (ms.walking || ms.running || ms.sprinting)) {
                        float viewDiff = Math.abs(normalizeAngle(driverYaw - carYaw));
                        if (viewDiff < Math.PI / 2.0) {
                            throttle = 1f;
                        } else {
                            throttle = -1f;
                        }
                    }
                }
            }
        }

        // 3. Acceleration & Speed Update
        if (throttle > 0) {
            car.speed = Math.min(MAX_FORWARD_SPEED, car.speed + ACCELERATION * clampedDt);
        } else if (throttle < 0) {
            car.speed = Math.max(MAX_REVERSE_SPEED, car.speed - ACCELERATION * clampedDt);
        } else {
            if (car.speed > 0) {
                car.speed = Math.max(0f, car.speed - DECELERATION * clampedDt);
            } else if (car.speed < 0) {
                car.speed = Math.min(0f, car.speed + DECELERATION * clampedDt);
            }
        }

        // 4. Translation, Step Climbing & Solid Wall Collision
        double curX = pos.x;
        double curY = pos.y;
        double curZ = pos.z;

        if (Math.abs(car.speed) > 0.01f) {
            double dirX = -Math.sin(carYaw);
            double dirZ = -Math.cos(carYaw);
            double moveDist = car.speed * clampedDt;

            double nextX = curX + dirX * moveDist;
            double nextZ = curZ + dirZ * moveDist;

            int targetBlockX = (int) Math.floor(nextX);
            int targetBlockY = (int) Math.floor(curY + 0.1);
            int targetBlockZ = (int) Math.floor(nextZ);

            if (isSolid(world, targetBlockX, targetBlockY, targetBlockZ)) {
                // Check if 1-block step up is clear
                if (!isSolid(world, targetBlockX, targetBlockY + 1, targetBlockZ) &&
                    !isSolid(world, targetBlockX, targetBlockY + 2, targetBlockZ)) {
                    // Smoothly climb 1 block
                    curY += STEP_HEIGHT;
                    curX = nextX;
                    curZ = nextZ;
                } else {
                    // Solid wall -> stop car
                    car.speed = 0f;
                }
            } else {
                curX = nextX;
                curZ = nextZ;
            }
        }

        // 5. Gravity & Ground Alignment
        int groundBlockX = (int) Math.floor(curX);
        int groundBlockY = (int) Math.floor(curY - 0.2);
        int groundBlockZ = (int) Math.floor(curZ);

        if (isSolid(world, groundBlockX, groundBlockY, groundBlockZ)) {
            car.velocityY = 0f;
            double groundTopY = groundBlockY + 1.0;
            if (curY < groundTopY || curY - groundTopY < 0.25) {
                curY = groundTopY;
            }
        } else {
            car.velocityY -= GRAVITY * clampedDt;
            curY += car.velocityY * clampedDt;

            // Check floor penetration
            int fallBlockY = (int) Math.floor(curY);
            if (isSolid(world, groundBlockX, fallBlockY, groundBlockZ)) {
                curY = fallBlockY + 1.0;
                car.velocityY = 0f;
            }
        }

        // Apply updated Transform to Car
        transform.setPosition(new Vector3d(curX, curY, curZ));
        transform.setRotation(rot);

        // Keep Driver Transform synced
        if (hasActiveDriver && driverRef != null) {
            TransformComponent driverTrans = store.getComponent(driverRef, TransformComponent.getComponentType());
            if (driverTrans != null) {
                double cos = Math.cos(carYaw);
                double sin = Math.sin(carYaw);
                double wx = curX + (car.driverSeatOffset.x * cos - car.driverSeatOffset.z * sin);
                double wz = curZ + (car.driverSeatOffset.x * sin + car.driverSeatOffset.z * cos);
                driverTrans.setPosition(new Vector3d(wx, curY + car.driverSeatOffset.y, wz));
            }
        }

        // 6. Animation State Transitions
        String targetAnim = null;
        if (car.speed < -0.2f) {
            targetAnim = "Reverse";
        } else if (car.speed > 0.2f) {
            targetAnim = "Drive";
        } else if (hasActiveDriver || car.engineRunning) {
            targetAnim = "Drive";
        }

        if (targetAnim == null) {
            if (car.currentAnim != null) {
                AnimationUtils.stopAnimation(carRef, AnimationSlot.Movement, true, store);
                car.currentAnim = null;
            }
        } else if (!targetAnim.equals(car.currentAnim)) {
            AnimationUtils.playAnimation(carRef, AnimationSlot.Movement, targetAnim, store);
            car.currentAnim = targetAnim;
        }
    }

    private static boolean isSolid(World world, int x, int y, int z) {
        if (world == null) return false;
        BlockType bt = world.getBlockType(x, y, z);
        return bt != null && bt.getMaterial() == BlockMaterial.Solid;
    }

    private static float normalizeAngle(float angle) {
        while (angle > Math.PI) angle -= (float) (2 * Math.PI);
        while (angle < -Math.PI) angle += (float) (2 * Math.PI);
        return angle;
    }
}
