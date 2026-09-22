package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.Rotation4;
import com.cookieukw.SimTale.systems.ConstructionHelper;
import com.cookieukw.SimTale.systems.ConstructionPreviewManager;

import org.joml.Vector3i;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rotation, prefab anchoring and position identity.
 *
 * <p>All pure maths, and all of it recently changed: the offset mapper stopped re-normalising the
 * rotated box to its minimum corner, which is what makes the blueprint marker land on the same
 * corner of the house at every facing.
 */
public final class GeometryTests {

    private GeometryTests() {
    }

    public static void run() {
        Assert.suite("Rotation4.fromYawDegrees", GeometryTests::yaw);
        Assert.suite("OffsetMapper rotates around the anchor", GeometryTests::offsets);
        Assert.suite("HouseBlockPos identity", GeometryTests::blockPos);
        Assert.suite("Preview site id is stable per position", GeometryTests::siteId);
    }

    private static void yaw() {
        Assert.equal(Rotation4.fromYawDegrees(0), Rotation4.SOUTH, "0 deg");
        Assert.equal(Rotation4.fromYawDegrees(90), Rotation4.WEST, "90 deg");
        Assert.equal(Rotation4.fromYawDegrees(180), Rotation4.NORTH, "180 deg");
        Assert.equal(Rotation4.fromYawDegrees(270), Rotation4.EAST, "270 deg");

        // The +45 offset means each cardinal owns the 90 degrees centred on it.
        Assert.equal(Rotation4.fromYawDegrees(44), Rotation4.SOUTH, "just inside the south arc");
        Assert.equal(Rotation4.fromYawDegrees(46), Rotation4.WEST, "just past the boundary");

        // Yaw arrives from a live transform and is not pre-wrapped.
        Assert.equal(Rotation4.fromYawDegrees(-90), Rotation4.EAST, "negative yaw wraps to 270");
        Assert.equal(Rotation4.fromYawDegrees(720), Rotation4.SOUTH, "yaw beyond a full turn wraps");
        Assert.equal(Rotation4.fromYawDegrees(-630), Rotation4.WEST,
                "several negative turns wrap: -630 + 720 = 90");
        Assert.equal(Rotation4.fromYawDegrees(-810), Rotation4.EAST,
                "-810 + 720 = -90, which is 270 — the same answer as -90");
    }

    private static void offsets() {
        /* The invariant the marker placement depends on: the prefab's own origin stays exactly on
        the block the player placed, whichever way the house is turned. Before the change the
        box was shifted so it always grew towards +X/+Z, so a different corner of the house
        touched the marker at every rotation.
        */
        for (Rotation4 facing : Rotation4.values()) {
            Vector3i origin = new ConstructionHelper.OffsetMapper(facing).offset(0, 0, 0);
            Assert.equal(origin, new Vector3i(0, 0, 0),
                    "local origin stays on the anchor facing " + facing);
        }

        Assert.equal(new ConstructionHelper.OffsetMapper(Rotation4.NORTH).offset(1, 2, 3),
                new Vector3i(1, 2, 3), "north is identity");
        Assert.equal(new ConstructionHelper.OffsetMapper(Rotation4.EAST).offset(1, 2, 3),
                new Vector3i(-3, 2, 1), "east");
        Assert.equal(new ConstructionHelper.OffsetMapper(Rotation4.SOUTH).offset(1, 2, 3),
                new Vector3i(-1, 2, -3), "south");
        Assert.equal(new ConstructionHelper.OffsetMapper(Rotation4.WEST).offset(1, 2, 3),
                new Vector3i(3, 2, -1), "west");

        // Height never rotates: only the XZ plane turns.
        for (Rotation4 facing : Rotation4.values()) {
            Assert.equal(new ConstructionHelper.OffsetMapper(facing).offset(5, 7, 9).y, 7,
                    "y is untouched facing " + facing);
        }

        // Four turns is the identity, which is what makes '/build rotate' cycle cleanly.
        Vector3i once = ConstructionHelper.rotate(new Vector3i(2, 0, 5), Rotation4.EAST);
        Vector3i twice = ConstructionHelper.rotate(once, Rotation4.EAST);
        Vector3i thrice = ConstructionHelper.rotate(twice, Rotation4.EAST);
        Vector3i fourth = ConstructionHelper.rotate(thrice, Rotation4.EAST);
        Assert.equal(fourth, new Vector3i(2, 0, 5), "four quarter turns return to the start");
    }

    private static void blockPos() {
        /* The registries are HashSets of these, and removeAt now removes by value instead of
        scanning — which only works because equals and hashCode agree.
        */
        HouseBlockPos a = new HouseBlockPos(10, -4, 7);
        HouseBlockPos b = new HouseBlockPos(10, -4, 7);
        HouseBlockPos c = new HouseBlockPos(10, -4, 8);

        Assert.isTrue(a.equals(b), "same coordinates are equal");
        Assert.equal(a.hashCode(), b.hashCode(), "equal objects agree on hashCode");
        Assert.isFalse(a.equals(c), "one differing axis is not equal");
        Assert.isFalse(a.equals(null), "never equal to null");

        Set<HouseBlockPos> set = new HashSet<>();
        set.add(a);
        Assert.isTrue(set.contains(b), "lookup by an equal instance finds it");
        Assert.isTrue(set.remove(new HouseBlockPos(10, -4, 7)), "removal by value works");
        Assert.isTrue(set.isEmpty(), "and actually removed it");
    }

    private static void siteId() {
        /* The key has to be derivable from the block alone: that is what lets a marker preview be
        rebuilt after a restart, with nothing persisted.
        */
        Vector3i pos = new Vector3i(-51, 80, -97);
        UUID first = ConstructionPreviewManager.idForBlock(pos);
        UUID again = ConstructionPreviewManager.idForBlock(new Vector3i(-51, 80, -97));
        Assert.equal(first, again, "same position yields the same id");

        Assert.isFalse(first.equals(ConstructionPreviewManager.idForBlock(new Vector3i(-51, 81, -97))),
                "one block up is a different site");
        Assert.isFalse(first.equals(ConstructionPreviewManager.idForBlock(new Vector3i(-50, 80, -97))),
                "one block east is a different site");
        Assert.isFalse(first.equals(ConstructionPreviewManager.idForBlock(new Vector3i(-51, 80, -96))),
                "one block south is a different site");
    }
}
