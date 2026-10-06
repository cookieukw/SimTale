package com.cookieukw.SimTale.vehicles;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.ChairRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
                new SystemDependency<>(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class),
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
        World world = store.getExternalData().getWorld();

        // Bounded delta time to avoid large physics steps on lag spikes
        float clampedDt = Math.clamp(dt, 0.001f, 0.1f);

        Vector3d pos = transform.getPosition();
        Rotation3f rot = transform.getRotation();
        float carYaw = rot.yaw();
        final float startYaw = carYaw;

        float scale = CalhambequeManager.carScale(store, carRef);

        // 0. Tuning: /simtale carscale and /simtale carseat (also brings old 3.5x cars to the
        //    current size the first time they tick after a load)
        int tune = CalhambequeGeometry.version();
        if (car.appliedTuneVersion != tune) {
            car.appliedTuneVersion = tune;
            float target = CalhambequeGeometry.scale();
            if (Math.abs(target - scale) > 0.01f && Math.abs(car.requestedScale - target) > 0.01f) {
                car.requestedScale = target;
                // deferred by applyModel itself while the world is ticking
                SimNPCFactory.applyModel(store, carRef, CalhambequeGeometry.MODEL_ID, target, null);
            }
            CalhambequeManager.refreshRiders(store, carRef, car, target);
            scale = target;
            // cars placed before 1.2.0 carry a name tag; once per load is enough
            CalhambequeManager.hideNameplate(store, carRef);
        }

        // 1. Process Passenger
        if (car.passengerUuid != null) {
            Ref<EntityStore> passRef = store.getExternalData().getRefFromUUID(car.passengerUuid);
            if (passRef == null || !passRef.isValid()) {
                CalhambequeManager.forgetRider(car.passengerUuid);
                car.passengerUuid = null;
            } else {
                MovementStatesComponent passMsc = store.getComponent(passRef, MovementStatesComponent.getComponentType());
                MovementStates passMs = passMsc != null ? passMsc.getMovementStates() : null;
                if (passMs != null && (passMs.crouching || passMs.forcedCrouching)) {
                    PlayerRef passPlayerRef = store.getComponent(passRef, Universe.get().getPlayerRefComponentType());
                    CalhambequeManager.dismountPassenger(store, carRef, car, passRef, passPlayerRef);
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
                CalhambequeManager.forgetRider(car.driverUuid);
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
                        /* clamp(value, min, max). It was clamp(maxTurn, -maxTurn, diff), which throws
                        IllegalArgumentException whenever diff < -maxTurn (min > max) — turning one way
                        at speed crashed the whole world. */
                        float turn = Math.clamp(diff, -maxTurn, maxTurn);
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
                    if (playerInput != null) {
                        List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();
                        for (PlayerInput.InputUpdate update : queue) {
                            if (update instanceof PlayerInput.RelativeMovement rm) {
                                double dot = rm.getX() * dirX + rm.getZ() * dirZ;
                                if (dot > 0.02) throttle = 1f;
                                else if (dot < -0.02) throttle = -1f;
                            } else if (update instanceof PlayerInput.WishMovement wm) {
                                double dot = wm.getX() * dirX + wm.getZ() * dirZ;
                                if (dot > 0.02) throttle = 1f;
                                else if (dot < -0.02) throttle = -1f;
                                else if (wm.getZ() > 0.02) throttle = 1f;
                                else if (wm.getZ() < -0.02) throttle = -1f;
                            }
                        }
                    }

                    // Fallback to MovementStates if no explicit throttle was captured
                    if (throttle == 0f && ms != null && (ms.walking || ms.running || ms.sprinting)) {
                        if (car.speed < -0.2f) {
                            throttle = -1f;
                        } else if (car.speed > 0.2f) {
                            throttle = 1f;
                        } else {
                            float viewDiff = Math.abs(normalizeAngle(driverYaw - carYaw));
                            if (viewDiff > Math.PI * 0.6) {
                                throttle = -1f;
                            } else {
                                throttle = 1f;
                            }
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

        // 4. Translation with footprint collision.
        /* The whole footprint is tested (points every block or less around the hitbox-sized
        outline, turned with the car), from the step level up to the roof. The old check sampled
        three points of the bumper at foot level only: the sides and corners went through walls,
        a 1-block step lifted the car a full block from its bumper alone, the centre (still over
        the lower ground) fell back the next tick and the bumper hit the step again, so the car
        bounced against kerbs and house foundations, and the engine pushed the half-buried hitbox
        out of the blocks (the "teleport" next to houses). Now the car only climbs when nothing
        but the step is in the way, and it rests on the highest ground under any footprint point.
        */
        double curX = pos.x;
        double curY = pos.y;
        double curZ = pos.z;
        int footY = (int) Math.floor(curY + 0.1);
        boolean moving = Math.abs(car.speed) > 0.01f;

        if (moving) {
            double moveDist = car.speed * clampedDt;
            double nextX = curX - Math.sin(carYaw) * moveDist;
            double nextZ = curZ - Math.cos(carYaw) * moveDist;
            boolean loaded = footprintLoaded(world, nextX, nextZ, carYaw, scale);
            int fit = loaded ? footprintFit(world, nextX, nextZ, carYaw, footY, scale) : FIT_BLOCKED;
            // Blocked at full speed: close in on the obstacle in smaller steps before stopping, so the
            // car ends next to it instead of up to a block short.
            for (int i = 0; loaded && i < 3 && fit == FIT_BLOCKED; i++) {
                moveDist *= 0.5;
                double tx = curX - Math.sin(carYaw) * moveDist;
                double tz = curZ - Math.cos(carYaw) * moveDist;
                int f = footprintFit(world, tx, tz, carYaw, footY, scale);
                if (f == FIT_CLEAR) {
                    curX = tx;
                    curZ = tz;
                }
                if (f != FIT_BLOCKED) break;
            }
            if (fit == FIT_BLOCKED) {
                // Stop; keep the new heading only if turning on the spot is free.
                if (carYaw != startYaw && footprintFit(world, curX, curZ, carYaw, footY, scale) == FIT_BLOCKED) {
                    carYaw = startYaw;
                    rot.setYaw(carYaw);
                }
                car.speed = 0f;
            } else {
                curX = nextX;
                curZ = nextZ;
                if (fit == FIT_STEP) {
                    curY = footY + STEP_HEIGHT;
                    car.velocityY = 0f;
                }
            }
        } else if (carYaw != startYaw && footprintFit(world, curX, curZ, carYaw, footY, scale) == FIT_BLOCKED) {
            carYaw = startYaw;
            rot.setYaw(carYaw);
        }

        // 5. Gravity & ground: supported while any footprint point has ground right below.
        /* Unloaded ground counts as support. When the player left the world while driving, the
        chunks unloaded before the car stopped ticking, the ground "vanished" (an unloaded chunk
        reads as air) and the car fell out of the world: it was gone after rejoining. */
        int groundY = (int) Math.floor(curY - 0.2);
        if (!footprintLoaded(world, curX, curZ, carYaw, scale)) {
            car.velocityY = 0f;
        } else if (anyObstacleUnder(world, curX, curZ, carYaw, groundY, scale)) {
            car.velocityY = 0f;
            double groundTopY = groundY + 1.0;
            if (curY < groundTopY || curY - groundTopY < 0.25) {
                curY = groundTopY;
            }
        } else {
            car.velocityY -= GRAVITY * clampedDt;
            curY += car.velocityY * clampedDt;
            int fallY = (int) Math.floor(curY);
            if (anyObstacleUnder(world, curX, curZ, carYaw, fallY, scale)) {
                curY = fallY + 1.0;
                car.velocityY = 0f;
            }
        }
        updateMovementStates(store, carRef, car, commandBuffer);

        if (curY < 0) { // never below the bottom of the world
            curY = 0;
            car.velocityY = 0f;
        }

        // Apply updated Transform to Car
        transform.setPosition(new Vector3d(curX, curY, curZ));
        transform.setRotation(rot);

        // Keep the riders' server-side position on their seats (the client shows them attached
        // through MountNPC; this keeps chunk loading, interaction range etc. in step)
        if (hasActiveDriver) {
            syncRider(store, driverRef, curX, curY, curZ, carYaw, CalhambequeGeometry.driverSeat(scale));
        }
        if (car.passengerUuid != null) {
            Ref<EntityStore> passRef = store.getExternalData().getRefFromUUID(car.passengerUuid);
            if (passRef != null && passRef.isValid()) {
                syncRider(store, passRef, curX, curY, curZ, carYaw, CalhambequeGeometry.passengerSeat(scale));
            }
        }

        // 6. Animation State Transitions
        String targetAnim = null;
        if (car.speed < -0.2f) {
            targetAnim = "Reverse";
        } else if (car.speed > 0.2f) {
            targetAnim = "Drive";
        } else if (hasActiveDriver || car.engineRunning) {
            targetAnim = "Idle";
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

    private static final int FIT_CLEAR = 0, FIT_STEP = 1, FIT_BLOCKED = 2;
    /** Max spacing, in blocks, between the footprint points collision tests. */
    private static final double FOOTPRINT_SPACING = 0.9;

    /** Car-local points (x right, z forward) around the hitbox outline plus margin, and the centre. */
    private static double[][] footprint(float scale) {
        double m = CalhambequeGeometry.COLLISION_MARGIN;
        double hw = CalhambequeGeometry.HITBOX_HALF_WIDTH * scale + m;
        double front = CalhambequeGeometry.HITBOX_FRONT * scale + m;
        double back = CalhambequeGeometry.HITBOX_BACK * scale + m;
        int nx = Math.max(1, (int) Math.ceil(2 * hw / FOOTPRINT_SPACING));
        int nz = Math.max(1, (int) Math.ceil((front + back) / FOOTPRINT_SPACING));
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        pts.add(new double[]{0, 0});
        for (int i = 0; i <= nx; i++) {
            double lx = -hw + 2 * hw * i / nx;
            pts.add(new double[]{lx, -back});
            pts.add(new double[]{lx, front});
        }
        for (int j = 1; j < nz; j++) {
            double lz = -back + (front + back) * j / nz;
            pts.add(new double[]{-hw, lz});
            pts.add(new double[]{hw, lz});
        }
        return pts.toArray(new double[0][]);
    }

    /** Whether every chunk under the footprint is loaded (unknown ground is neither solid nor air). */
    private static boolean footprintLoaded(World world, double x, double z, float yaw, float scale) {
        for (double[] p : footprint(scale)) {
            int bx = (int) Math.floor(CalhambequeGeometry.toWorldX(x, yaw, p[0], p[1]));
            int bz = (int) Math.floor(CalhambequeGeometry.toWorldZ(z, yaw, p[0], p[1]));
            if (world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(bx, bz)) == null) return false;
        }
        return true;
    }

    /**
     * Whether the car fits at (x, z) with this heading and its floor at footY: CLEAR, STEP (a
     * climbable block at floor level and room for the car one block higher) or BLOCKED.
     */
    private static int footprintFit(World world, double x, double z, float yaw, int footY, float scale) {
        int bodyBlocks = Math.max(2, (int) Math.ceil(CalhambequeGeometry.height(scale)));
        double[][] pts = footprint(scale);
        boolean step = false;
        for (double[] p : pts) {
            int bx = (int) Math.floor(CalhambequeGeometry.toWorldX(x, yaw, p[0], p[1]));
            int bz = (int) Math.floor(CalhambequeGeometry.toWorldZ(z, yaw, p[0], p[1]));
            for (int dy = 1; dy <= bodyBlocks; dy++) {
                if (isObstacle(world, bx, footY + dy, bz)) return FIT_BLOCKED;
            }
            if (isObstacle(world, bx, footY, bz)) {
                if (!canStepUp(world, bx, footY, bz)) return FIT_BLOCKED;
                step = true;
            }
        }
        if (!step) return FIT_CLEAR;
        // one block higher, the roof needs one more block of air
        for (double[] p : pts) {
            int bx = (int) Math.floor(CalhambequeGeometry.toWorldX(x, yaw, p[0], p[1]));
            int bz = (int) Math.floor(CalhambequeGeometry.toWorldZ(z, yaw, p[0], p[1]));
            if (isObstacle(world, bx, footY + bodyBlocks + 1, bz)) return FIT_BLOCKED;
        }
        return FIT_STEP;
    }

    private static boolean anyObstacleUnder(World world, double x, double z, float yaw, int y, float scale) {
        for (double[] p : footprint(scale)) {
            int bx = (int) Math.floor(CalhambequeGeometry.toWorldX(x, yaw, p[0], p[1]));
            int bz = (int) Math.floor(CalhambequeGeometry.toWorldZ(z, yaw, p[0], p[1]));
            if (isObstacle(world, bx, y, bz)) return true;
        }
        return false;
    }

    /**
     * Tells the client whether the car is driving or standing. NPC movement animations (the
     * model's Walk/Idle sets) are picked on the client from these flags; the car's were never
     * updated, so the wheels spun while parked and stood still while driving.
     */
    private static void updateMovementStates(Store<EntityStore> store, Ref<EntityStore> carRef,
                                             CalhambequeComponent car, CommandBuffer<EntityStore> commandBuffer) {
        int state = car.speed > 0.2f ? 1 : car.speed < -0.2f ? 2 : 0;
        if (state == car.movementState) return;
        MovementStatesComponent msc = store.getComponent(carRef, MovementStatesComponent.getComponentType());
        if (msc == null || msc.getMovementStates() == null) return;
        car.movementState = state;
        MovementStates ms = msc.getMovementStates();
        boolean driving = state != 0;
        ms.idle = !driving;
        ms.horizontalIdle = !driving;
        ms.walking = driving;
        ms.running = false;
        ms.sprinting = false;
        ms.jumping = false;
        ms.falling = false;
        ms.fallingFar = false;
        ms.flying = false;
        ms.onGround = true;
        commandBuffer.replaceComponent(carRef, MovementStatesComponent.getComponentType(), msc);
    }

    static boolean isObstacle(World world, int x, int y, int z) {
        if (world == null) return false;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return false;
        BlockType bt = chunk.getBlockType(x, y, z);
        if (bt == null) return false;
        if (bt.getMaterial() == BlockMaterial.Solid) return true;
        if (bt.getBeds() != null || bt.getSeats() != null || bt.isDoor()) return true;
        String id = bt.getId();
        if (id != null) {
            if (BedRegistry.isBedId(id) || ChairRegistry.isChair(id)) return true;
            String lower = id.toLowerCase();
            if (lower.contains("bed") || lower.contains("chair") || lower.contains("chest")
                    || lower.contains("table") || lower.contains("bench") || lower.contains("sofa")
                    || lower.contains("couch") || lower.contains("door") || lower.contains("fence")
                    || lower.contains("gate") || lower.contains("wall") || lower.contains("tub")
                    || lower.contains("desk") || lower.contains("counter") || lower.contains("shelf")
                    || lower.contains("cabinet") || lower.contains("cupboard") || lower.contains("wardrobe")) {
                return true;
            }
        }
        return false;
    }

    private static boolean canStepUp(World world, int x, int footY, int z) {
        if (world == null) return false;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) return false;
        BlockType bt = chunk.getBlockType(x, footY, z);
        if (bt == null) return false;
        // Never step-climb onto beds, chairs, or furniture
        if (bt.getBeds() != null || bt.getSeats() != null || bt.isDoor()) return false;
        String id = bt.getId();
        if (id != null) {
            if (BedRegistry.isBedId(id) || ChairRegistry.isChair(id)) return false;
            String lower = id.toLowerCase();
            return !lower.contains("bed") && !lower.contains("chair") && !lower.contains("chest")
                    && !lower.contains("table") && !lower.contains("bench") && !lower.contains("sofa")
                    && !lower.contains("couch") && !lower.contains("door") && !lower.contains("fence")
                    && !lower.contains("gate") && !lower.contains("wall") && !lower.contains("tub")
                    && !lower.contains("desk") && !lower.contains("counter") && !lower.contains("shelf")
                    && !lower.contains("cabinet") && !lower.contains("cupboard") && !lower.contains("wardrobe");
        }
        return true;
    }

    private static void syncRider(Store<EntityStore> store, Ref<EntityStore> riderRef, double carX, double carY,
                                  double carZ, float carYaw, Vector3f seat) {
        TransformComponent t = store.getComponent(riderRef, TransformComponent.getComponentType());
        if (t == null) return;
        double wx = CalhambequeGeometry.toWorldX(carX, carYaw, seat.x, seat.z);
        double wz = CalhambequeGeometry.toWorldZ(carZ, carYaw, seat.x, seat.z);
        t.setPosition(new Vector3d(wx, carY + seat.y, wz));
    }

    private static float normalizeAngle(float angle) {
        while (angle > Math.PI) angle -= (float) (2 * Math.PI);
        while (angle < -Math.PI) angle += (float) (2 * Math.PI);
        return angle;
    }
}
