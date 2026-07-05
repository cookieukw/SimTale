package com.cookieukw.SimTale.db;

import java.util.ArrayList;
import java.util.List;

public class SimBedData {
    public List<BedPos> beds = new ArrayList<>();

    public static class BedPos {
        public int x;
        public int y;
        public int z;
        public float yaw;

        public BedPos() {}

        public BedPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BedPos(int x, int y, int z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BedPos bedPos = (BedPos) o;
            return x == bedPos.x && y == bedPos.y && z == bedPos.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
