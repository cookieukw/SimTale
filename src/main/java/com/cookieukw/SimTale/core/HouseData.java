package com.cookieukw.SimTale.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HouseData {
    public String houseId;
    public Set<String> owners = new HashSet<>();
    public HouseBlockPos bedPos;
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
        this.interior = interior;
        this.doors = doors;
        this.chests = chests;
    }
}
