package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.vehicles.CalhambequeGeometry;
import org.joml.Vector3f;

/**
 * The Calhambeque's seat and size maths.
 *
 * <p>Pure functions only: the hitbox bug (an asset hitbox that the engine multiplied by the 3.5x
 * scale a second time) and the steering crash (Math.clamp with its bounds swapped) both came from
 * numbers nobody looked at, so these pin the numbers down.
 */
public final class CalhambequeTests {

    private CalhambequeTests() {
    }

    public static void run() {
        Assert.suite("Calhambeque local axes map to the driving direction", CalhambequeTests::axes);
        Assert.suite("Calhambeque seats follow the scale", CalhambequeTests::seats);
        Assert.suite("Calhambeque tuning is clamped and resettable", CalhambequeTests::tuning);
        Assert.suite("Steering clamp never throws", CalhambequeTests::steering);
    }

    private static void axes() {
        // Same forward/right as CalhambequePhysicsSystem: forward (-sin, -cos), right (cos, -sin)
        for (float yaw : new float[]{0f, 0.7f, (float) Math.PI / 2, 2.5f, (float) -Math.PI}) {
            double fx = CalhambequeGeometry.toWorldX(0, yaw, 0, 1);
            double fz = CalhambequeGeometry.toWorldZ(0, yaw, 0, 1);
            Assert.floatEqual((float) fx, (float) -Math.sin(yaw), "front x at yaw " + yaw);
            Assert.floatEqual((float) fz, (float) -Math.cos(yaw), "front z at yaw " + yaw);
            double rx = CalhambequeGeometry.toWorldX(0, yaw, 1, 0);
            double rz = CalhambequeGeometry.toWorldZ(0, yaw, 1, 0);
            Assert.floatEqual((float) rx, (float) Math.cos(yaw), "right x at yaw " + yaw);
            Assert.floatEqual((float) rz, (float) -Math.sin(yaw), "right z at yaw " + yaw);
        }
    }

    private static void seats() {
        CalhambequeGeometry.reset();
        Vector3f d35 = CalhambequeGeometry.driverSeat(3.5f);
        // the cushion height the old code used (1.08 blocks at 3.5x) is kept
        Assert.isTrue(Math.abs(d35.y - 1.08f) < 0.03f, "seat height at 3.5x");
        Assert.isTrue(d35.x < 0, "driver sits on the steering-wheel side (-X)");
        Assert.isTrue(d35.z < 0, "seat is behind the centre");
        Vector3f p45 = CalhambequeGeometry.passengerSeat(4.5f);
        Vector3f d45 = CalhambequeGeometry.driverSeat(4.5f);
        Assert.floatEqual(p45.x, -d45.x, "passenger mirrors the driver");
        Assert.floatEqual(d45.y / d35.y, 4.5f / 3.5f, "seat scales with the car");
        Assert.isTrue(CalhambequeGeometry.halfWidth(4.5f) < CalhambequeGeometry.halfLength(4.5f), "longer than wide");
        // a player (0.6 wide) standing beside the car must be outside its box: the old box was 5.25 wide
        Assert.isTrue(CalhambequeGeometry.halfWidth(4.5f) < 2.5, "box half width stays under 2.5 blocks");
    }

    private static void tuning() {
        CalhambequeGeometry.reset();
        int v = CalhambequeGeometry.version();
        CalhambequeGeometry.setScale(50f);
        Assert.floatEqual(CalhambequeGeometry.scale(), CalhambequeGeometry.MAX_SCALE, "scale clamped high");
        CalhambequeGeometry.setScale(0f);
        Assert.floatEqual(CalhambequeGeometry.scale(), CalhambequeGeometry.MIN_SCALE, "scale clamped low");
        CalhambequeGeometry.setSeat(9, 22, 12);
        Assert.equal(CalhambequeGeometry.seatSide(), 9, "seat side set");
        Assert.isTrue(CalhambequeGeometry.version() > v, "tuning bumps the version");
        CalhambequeGeometry.reset();
        Assert.floatEqual(CalhambequeGeometry.scale(), CalhambequeGeometry.DEFAULT_SCALE, "reset scale");
        Assert.equal(CalhambequeGeometry.seatBack(), CalhambequeGeometry.DEFAULT_SEAT_BACK, "reset seat");
    }

    private static void steering() {
        float maxTurn = 2.4f * 0.05f;
        for (float diff : new float[]{-3f, -maxTurn * 2, -maxTurn, 0f, maxTurn / 2, maxTurn * 3}) {
            float turn = Math.clamp(diff, -maxTurn, maxTurn);   // the fixed call in the physics system
            Assert.isTrue(Math.abs(turn) <= maxTurn + 1e-6f, "turn bounded for diff " + diff);
        }
        boolean threw = false;
        try {
            Math.clamp(maxTurn, -maxTurn, -3f);                  // the old argument order
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        Assert.isTrue(threw, "the old call really did throw when turning one way");
    }
}
