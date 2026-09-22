package com.cookieukw.SimTale.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HouseData {
    public String houseId;
    public Set<String> owners = new HashSet<>();
    public HouseBlockPos bedPos;
    public Set<HouseBlockPos> beds = new HashSet<>();
    public HouseBlockPos anchorPos;
    public Set<HouseBlockPos> interior = new HashSet<>();
    public Set<HouseBlockPos> doors = new HashSet<>();
    public Set<HouseBlockPos> chests = new HashSet<>();

    public HouseData() {
    }

    public HouseData(UUID houseId, Set<UUID> owners, HouseBlockPos bedPos, Set<HouseBlockPos> interior, Set<HouseBlockPos> doors, Set<HouseBlockPos> chests) {
        this.houseId = houseId.toString();
        for (UUID owner : owners) {
            this.owners.add(owner.toString());
        }
        this.bedPos = bedPos;
        if (bedPos != null) {
            this.beds.add(bedPos);
        }
        this.interior = interior != null ? new HashSet<>(interior) : new HashSet<>();
        this.doors = doors != null ? new HashSet<>(doors) : new HashSet<>();
        this.chests = chests != null ? new HashSet<>(chests) : new HashSet<>();
        this.anchorPos = computeAnchor();
    }

    public HouseBlockPos getAnchor() {
        if (anchorPos != null) {
            return anchorPos;
        }
        anchorPos = computeAnchor();
        return anchorPos;
    }

    public HouseBlockPos computeAnchor() {
        if (bedPos != null) {
            return bedPos;
        }
        if (beds != null && !beds.isEmpty()) {
            return beds.iterator().next();
        }
        if (doors != null && !doors.isEmpty()) {
            return doors.iterator().next();
        }
        if (interior != null && !interior.isEmpty()) {
            long sumX = 0, sumY = 0, sumZ = 0;
            for (HouseBlockPos pos : interior) {
                sumX += pos.x;
                sumY += pos.y;
                sumZ += pos.z;
            }
            int count = interior.size();
            return new HouseBlockPos((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count));
        }
        return null;
    }

    public void syncBeds() {
        if (beds == null) {
            beds = new HashSet<>();
        }
        if (beds.isEmpty() && bedPos != null) {
            beds.add(bedPos);
        } else if (bedPos == null && !beds.isEmpty()) {
            bedPos = beds.iterator().next();
        }
        if (anchorPos == null) {
            anchorPos = computeAnchor();
        }
    }

    public void addBed(HouseBlockPos bed) {
        if (bed == null) return;
        if (beds == null) beds = new HashSet<>();
        beds.add(bed);
        if (bedPos == null) {
            bedPos = bed;
        }
    }

    public void removeBed(HouseBlockPos bed) {
        if (bed == null || beds == null) return;
        beds.remove(bed);
        if (bedPos != null && bedPos.equals(bed)) {
            bedPos = beds.isEmpty() ? null : beds.iterator().next();
        }
    }
}
