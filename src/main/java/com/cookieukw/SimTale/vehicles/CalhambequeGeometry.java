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

    /** Default size (was 3.5x; asked to be a bit bigger). Tunable with /simtale carscale. */
    public static final float DEFAULT_SCALE = 4.5f;
    public static final float MIN_SCALE = 1.0f;
    public static final float MAX_SCALE = 10.0f;

    /** Body, from the box shapes: z -46.4..49.5, x within +-37.5 at the fenders, top at 46.3. */
    public static final float HALF_LENGTH_UNITS = 48f;
    public static final float HALF_WIDTH_UNITS = 30f;
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

    public static void reset() {
        scale = DEFAULT_SCALE;
        seatSide = DEFAULT_SEAT_SIDE;
        seatHeight = DEFAULT_SEAT_HEIGHT;
        seatBack = DEFAULT_SEAT_BACK;
        version++;
    }

    public static float blocks(float units, float carScale) {
        return units * carScale / UNITS_PER_BLOCK;
    }

    /** Driver seat in blocks, model axes (the anchor sent with MountNPC). */
    public static Vector3f driverSeat(float carScale) {
        return new Vector3f(blocks(-seatSide, carScale), blocks(seatHeight, carScale), blocks(-seatBack, carScale));
    }

    /** Passenger seat: the driver's, mirrored to the right-hand side. */
    public static Vector3f passengerSeat(float carScale) {
        return new Vector3f(blocks(seatSide, carScale), blocks(seatHeight, carScale), blocks(-seatBack, carScale));
    }

    public static double halfLength(float carScale) {
        return blocks(HALF_LENGTH_UNITS, carScale);
    }

    public static double halfWidth(float carScale) {
        return blocks(HALF_WIDTH_UNITS, carScale);
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
