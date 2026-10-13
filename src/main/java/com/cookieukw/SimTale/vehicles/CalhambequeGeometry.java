package com.cookieukw.SimTale.vehicles;

import org.joml.Vector3f;

/**
 * Sizes of the Calhambeque, measured from {@code Calhambeque.blockymodel}, and the two values that
 * can be tuned in game with {@code /simtale carscale} and {@code /simtale carseat}.
 *
 * <p><b>Units.</b> Everything here is in model units: 64 units are one block at scale 1, the same
 * ratio the seat height was derived from before (cushion at y = 20 units, 1.08 blocks at 3.5x).
 * Multiply by {@code scale / 64} to get blocks.
 *
 * <p><b>Axes</b> are the model's own: +Z is the front (front axle at z = +27), +X is the right-hand
 * side ({@code DoorRight} at x = +14), so the steering wheel at x = -7 is on the left. The
 * physics system maps them to the world the same way ({@link #toWorldX}/{@link #toWorldZ}):
 * local +Z is the driving direction, local +X is its right.
 *
 * <p><b>Why the hitbox lives in the model JSON unscaled.</b> {@code Model.createScaledModel}
 * multiplies the model asset's HitBox by the scale. The old asset declared a hitbox of
 * 3 x 2.4 x 5.2 blocks already, so at 3.5x the car was 10.5 x 8.4 x 18.2 blocks of invisible box:
 * a player standing next to the car was inside it, and the right-click ray only met the car from
 * above. The asset now holds the 1x box from {@link #HALF_WIDTH_UNITS} etc.
 */
public final class CalhambequeGeometry {

    public static final float UNITS_PER_BLOCK = 64f;
    public static final String MODEL_ID = "SimTale_Calhambeque";

    /** Default size: 3.5x, picked in game with /simtale carscale 350 (4.5x was too big). */
    public static final float DEFAULT_SCALE = 3.5f;
    public static final float MIN_SCALE = 1.0f;
    public static final float MAX_SCALE = 10.0f;

    /** Body, from the box shapes: z -46.4..49.5, x within +-37.5 at the fenders, top at 46.3. */
    public static final float HALF_LENGTH_UNITS = 48f;
    public static final float HALF_WIDTH_UNITS = 30f;
    /** Half width at the fenders, the hitbox's X (0.58 x 64). */
    public static final float FENDER_HALF_WIDTH_UNITS = 37f;

    /* The hitbox in the model JSON, at scale 1, in blocks: x +-0.58, z -0.73 (back) .. 0.77 (front).
    Collision tests this box plus a margin, so the car stops before its hitbox touches a block:
    when the hitbox ended up inside a wall the engine pushed the car out, which looked like the car
    teleporting backwards after a crash. */
    public static final float HITBOX_HALF_WIDTH = 0.58f;
    public static final float HITBOX_FRONT = 0.77f;
    public static final float HITBOX_BACK = 0.73f;
    public static final double COLLISION_MARGIN = 0.15;
    public static final float HEIGHT_UNITS = 46f;

    /**
     * Driver seat: on the bench cushion (centre y = 20, z = -15) behind the steering wheel
     * (x = -7). The passenger mirrors x. The previous offset (0.1, 1.08, -1.0 blocks at 3.5x)
     * put the driver in the middle of the bench, not behind the wheel.
     */
    public static final int DEFAULT_SEAT_SIDE = 7;     // units from the centre towards the wheel (-X)
    public static final int DEFAULT_SEAT_HEIGHT = 20;  // units above the car's origin
    public static final int DEFAULT_SEAT_BACK = 15;    // units behind the centre (-Z)

    private static volatile float scale = DEFAULT_SCALE;
    /** Top speed and acceleration, in percent of the physics defaults. /simtale carspeed. */
    public static final int DEFAULT_SPEED_PERCENT = 100;
    public static final int MIN_SPEED_PERCENT = 25;
    public static final int MAX_SPEED_PERCENT = 400;
    private static volatile int speedPercent = DEFAULT_SPEED_PERCENT;
    private static volatile int seatSide = DEFAULT_SEAT_SIDE;
    private static volatile int seatHeight = DEFAULT_SEAT_HEIGHT;
    private static volatile int seatBack = DEFAULT_SEAT_BACK;
    /** Bumped on every tuning change, so the physics system can refresh cars and riders. */
    private static volatile int version = 0;

    private CalhambequeGeometry() {
    }

    public static float scale() {
        return scale;
    }

    public static int version() {
        return version;
    }

    public static int seatSide() {
        return seatSide;
    }

    public static int seatHeight() {
        return seatHeight;
    }

    public static int seatBack() {
        return seatBack;
    }

    public static void setScale(float newScale) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        version++;
    }

    public static void setSeat(int side, int height, int back) {
        seatSide = side;
        seatHeight = height;
        seatBack = back;
        version++;
    }

    public static int speedPercent() {
        return speedPercent;
    }

    /** Multiplier for top speed and acceleration (1.0 = defaults). */
    public static float speedFactor() {
        return speedPercent / 100f;
    }

    public static void setSpeedPercent(int percent) {
        speedPercent = Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT, percent));
    }

    public static void reset() {
        scale = DEFAULT_SCALE;
        speedPercent = DEFAULT_SPEED_PERCENT;
        seatSide = DEFAULT_SEAT_SIDE;
        seatHeight = DEFAULT_SEAT_HEIGHT;
        seatBack = DEFAULT_SEAT_BACK;
        version++;
    }

    public static float blocks(float units, float carScale) {
        return units * carScale / UNITS_PER_BLOCK;
    }

    /**
     * Driver seat in blocks, in the car's local frame used by the physics (x to the right of the
     * driving direction, z forward; see {@link #toWorldX}).
     *
     * <p>In game the model shows up turned half a circle from its own axes: its front (+Z) faces
     * the driving direction and the steering wheel (model x = -7) ends up on the right. So the
     * driver sits at local x = +side, and z = -back is behind the centre, on the bench. The first
     * version used the model axes directly and put the rider on the hood, on the passenger side.
     */
    public static Vector3f driverSeat(float carScale) {
        return new Vector3f(blocks(seatSide, carScale), blocks(seatHeight, carScale), blocks(-seatBack, carScale));
    }

    /** Passenger seat: the driver's, mirrored to the other side. */
    public static Vector3f passengerSeat(float carScale) {
        return new Vector3f(blocks(-seatSide, carScale), blocks(seatHeight, carScale), blocks(-seatBack, carScale));
    }

    /**
     * The MountNPC anchor for a seat. The client reads the anchor in the entity's frame, where
     * forward is -Z (Hytale's facing at yaw 0), so z is negated; x already matches.
     */
    public static Vector3f mountAnchor(Vector3f seat) {
        return new Vector3f(seat.x, seat.y, -seat.z);
    }

    public static double halfLength(float carScale) {
        return blocks(HALF_LENGTH_UNITS, carScale);
    }

    public static double halfWidth(float carScale) {
        return blocks(HALF_WIDTH_UNITS, carScale);
    }

    public static double fenderHalfWidth(float carScale) {
        return blocks(FENDER_HALF_WIDTH_UNITS, carScale);
    }

    public static double height(float carScale) {
        return blocks(HEIGHT_UNITS, carScale);
    }

    /** World x of a point given in car-local blocks (x right, z front) for a car at yaw. */
    public static double toWorldX(double carX, float yaw, double localX, double localZ) {
        return carX + localX * Math.cos(yaw) - localZ * Math.sin(yaw);
    }

    /** World z of a point given in car-local blocks (x right, z front) for a car at yaw. */
    public static double toWorldZ(double carZ, float yaw, double localX, double localZ) {
        return carZ - (localX * Math.sin(yaw) + localZ * Math.cos(yaw));
    }
}
