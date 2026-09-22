package com.cookieukw.SimTale.core;

public enum Rotation4 {
    NORTH, EAST, SOUTH, WEST;

    public static Rotation4 fromYawDegrees(double yawDegrees) {
        double y = (yawDegrees % 360.0 + 360.0) % 360.0;
        int idx = (int) Math.floor((y + 45.0) / 90.0) & 3;
        return switch (idx) {
            case 0 -> SOUTH;
            case 1 -> WEST;
            case 2 -> NORTH;
            default -> EAST;
        };
    }
}
