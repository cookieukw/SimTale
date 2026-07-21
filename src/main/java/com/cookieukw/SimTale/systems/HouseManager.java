package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HouseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseManager.class);
    private static final int MAX_INTERIOR_BLOCKS = 512;

    public static final Map<UUID, HouseData> HOUSES_BY_ID = new ConcurrentHashMap<>();
    public static final Map<HouseBlockPos, UUID> BLOCK_TO_HOUSE_ID = new ConcurrentHashMap<>();
    public static final Map<UUID, UUID> OWNER_TO_HOUSE_ID = new ConcurrentHashMap<>();

    public enum ScanOutcome {
        NEW_HOUSE_SINGLE_OWNER,
        NEW_HOUSE_MULTI_OWNER,
        MERGED_INTO_EXISTING,
        CONFLICT_WITH_EXISTING,
        TOO_LARGE_OR_UNENCLOSED,
        NO_ENTRANCE
    }

    public static class HouseScanResult {
        public final Set<HouseBlockPos> interiorBlocks;
        public final Set<HouseBlockPos> doorBlocks;
        public final Set<HouseBlockPos> otherBeds;
        public final Set<HouseBlockPos> chestBlocks;
        public final boolean overflowed;

        public HouseScanResult(Set<HouseBlockPos> interiorBlocks, Set<HouseBlockPos> doorBlocks,
                               Set<HouseBlockPos> otherBeds, Set<HouseBlockPos> chestBlocks, boolean overflowed) {
            this.interiorBlocks = interiorBlocks;
            this.doorBlocks = doorBlocks;
            this.otherBeds = otherBeds;
            this.chestBlocks = chestBlocks;
            this.overflowed = overflowed;
        }
    }

    public static class ScanReport {
        public final ScanOutcome outcome;
        public final Set<UUID> freshBedOwners;
        public final UUID conflictingHouseId;
        public final HouseScanResult raw;

        public ScanReport(ScanOutcome outcome, Set<UUID> freshBedOwners, UUID conflictingHouseId, HouseScanResult raw) {
            this.outcome = outcome;
            this.freshBedOwners = freshBedOwners;
            this.conflictingHouseId = conflictingHouseId;
            this.raw = raw;
        }
    }

    public static void loadAllHouses() {
        HOUSES_BY_ID.clear();
        BLOCK_TO_HOUSE_ID.clear();
        OWNER_TO_HOUSE_ID.clear();

        try {
            List<HouseData> list = Caskara.list(HouseData.class);
            if (list != null) {
                for (HouseData house : list) {
                    if (house.houseId == null) continue;
                    UUID houseId = UUID.fromString(house.houseId);
                    HOUSES_BY_ID.put(houseId, house);

                    for (HouseBlockPos pos : house.interior) {
                        BLOCK_TO_HOUSE_ID.put(pos, houseId);
                    }
                    for (String ownerStr : house.owners) {
                        try {
                            UUID ownerId = UUID.fromString(ownerStr);
                            OWNER_TO_HOUSE_ID.put(ownerId, houseId);
                        } catch (Exception ignored) {}
                    }
                }
                LOGGER.info("[SimTale] Carregadas {} casas com sucesso da persistência Caskara", HOUSES_BY_ID.size());
            }
        } catch (Exception e) {
            LOGGER.error("[SimTale] Falha ao carregar as casas da persistência Caskara: ", e);
        }
    }

    public static void saveHouse(HouseData house) {
        if (house == null || house.houseId == null) return;
        Caskara.save("house_" + house.houseId, house);
    }

    public static void deleteHouse(UUID houseId) {
        HouseData house = HOUSES_BY_ID.remove(houseId);
        if (house != null) {
            for (HouseBlockPos pos : house.interior) {
                BLOCK_TO_HOUSE_ID.remove(pos);
            }
            for (String ownerStr : house.owners) {
                try {
                    UUID ownerId = UUID.fromString(ownerStr);
                    OWNER_TO_HOUSE_ID.remove(ownerId);
                } catch (Exception ignored) {}
            }
            Caskara.delete("house_" + houseId.toString(), HouseData.class);
            LOGGER.info("[SimTale] Casa {} deletada.", houseId);
        }
    }

    public static void registerHouse(HouseData house) {
        UUID houseId = UUID.fromString(house.houseId);
        HOUSES_BY_ID.put(houseId, house);
        for (HouseBlockPos pos : house.interior) {
            BLOCK_TO_HOUSE_ID.put(pos, houseId);
        }
        for (String ownerStr : house.owners) {
            try {
                UUID ownerId = UUID.fromString(ownerStr);
                OWNER_TO_HOUSE_ID.put(ownerId, houseId);
            } catch (Exception ignored) {}
        }
        saveHouse(house);
    }

    public static HouseScanResult scanHouseFromBed(World world, HouseBlockPos bedPos) {
        Set<HouseBlockPos> visited = new HashSet<>();
        Set<HouseBlockPos> doors = new HashSet<>();
        Set<HouseBlockPos> otherBeds = new HashSet<>();
        Set<HouseBlockPos> chestBlocks = new HashSet<>();
        Deque<HouseBlockPos> queue = new ArrayDeque<>();

        queue.add(bedPos);
        visited.add(bedPos);

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_INTERIOR_BLOCKS) {
                return new HouseScanResult(visited, doors, otherBeds, chestBlocks, true);
            }

            HouseBlockPos current = queue.poll();

            for (HouseBlockPos neighbor : get6Neighbors(current)) {
                if (visited.contains(neighbor)) continue;

                BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);

                if (isDoor(type)) {
                    doors.add(neighbor);
                    visited.add(neighbor);
                    continue; 
                }
                if (isBed(type)) {
                    if (!neighbor.equals(bedPos)) {
                        otherBeds.add(neighbor);
                    }
                    visited.add(neighbor);
                    continue; 
                }
                if (isChest(type)) {
                    chestBlocks.add(neighbor);
                    visited.add(neighbor);
                    continue; 
                }
                if (isSolid(type)) {
                    continue; 
                }

                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return new HouseScanResult(visited, doors, otherBeds, chestBlocks, false);
    }

    public static ScanReport scanAndClassify(World world, HouseBlockPos bedPos, UUID scanningOwner) {
        HouseScanResult raw = scanHouseFromBed(world, bedPos);

        if (raw.overflowed) return new ScanReport(ScanOutcome.TOO_LARGE_OR_UNENCLOSED, Set.of(), null, raw);
        if (raw.doorBlocks.isEmpty()) return new ScanReport(ScanOutcome.NO_ENTRANCE, Set.of(), null, raw);

        for (HouseBlockPos pos : raw.interiorBlocks) {
            UUID existingHouseId = BLOCK_TO_HOUSE_ID.get(pos);
            if (existingHouseId != null) {
                HouseData existing = HOUSES_BY_ID.get(existingHouseId);
                if (existing != null) {
                    boolean matchesOtherBed = false;
                    for (HouseBlockPos otherBed : raw.otherBeds) {
                        if (existing.bedPos.equals(otherBed)) {
                            matchesOtherBed = true;
                            break;
                        }
                    }
                    if (!matchesOtherBed && !existing.bedPos.equals(bedPos)) {
                        return new ScanReport(ScanOutcome.CONFLICT_WITH_EXISTING, Set.of(), existingHouseId, raw);
                    }
                }
            }
        }

        Set<UUID> freshOwners = new HashSet<>();
        for (HouseBlockPos otherBedPos : raw.otherBeds) {
            UUID existingHouseId = getHouseIdByBedPos(otherBedPos);

            if (existingHouseId == null) {
                UUID bedOwner = getBedOwnerUuid(otherBedPos);
                if (bedOwner != null) {
                    freshOwners.add(bedOwner);
                }
            } else {
                UUID myHouseId = getHouseIdForBed(bedPos);
                if (myHouseId == null || !existingHouseId.equals(myHouseId)) {
                    return new ScanReport(ScanOutcome.MERGED_INTO_EXISTING, Set.of(), existingHouseId, raw);
                }
            }
        }

        Set<UUID> allOwners = new HashSet<>(freshOwners);
        allOwners.add(scanningOwner);

        if (allOwners.size() > 1) {
            return new ScanReport(ScanOutcome.NEW_HOUSE_MULTI_OWNER, allOwners, null, raw);
        } else {
            return new ScanReport(ScanOutcome.NEW_HOUSE_SINGLE_OWNER, allOwners, null, raw);
        }
    }

    public static boolean canOpenChest(UUID npcId, HouseBlockPos chestPos) {
        UUID houseId = BLOCK_TO_HOUSE_ID.get(chestPos);
        if (houseId == null) {
            return true;
        }
        HouseData house = HOUSES_BY_ID.get(houseId);
        if (house == null) return true;
        
        return house.owners.contains(npcId.toString());
    }

    private static UUID getBedOwnerUuid(HouseBlockPos pos) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.bedLocation != null &&
                npc.bedLocation.x == pos.x &&
                npc.bedLocation.y == pos.y &&
                npc.bedLocation.z == pos.z) {
                return npc.entityId;
            }
        }
        return null;
    }

    private static UUID getHouseIdByBedPos(HouseBlockPos pos) {
        for (HouseData house : HOUSES_BY_ID.values()) {
            if (house.bedPos.equals(pos)) {
                return UUID.fromString(house.houseId);
            }
        }
        return null;
    }

    private static UUID getHouseIdForBed(HouseBlockPos pos) {
        return getHouseIdByBedPos(pos);
    }

    private static List<HouseBlockPos> get6Neighbors(HouseBlockPos pos) {
        return List.of(
            new HouseBlockPos(pos.x + 1, pos.y, pos.z),
            new HouseBlockPos(pos.x - 1, pos.y, pos.z),
            new HouseBlockPos(pos.x, pos.y + 1, pos.z),
            new HouseBlockPos(pos.x, pos.y - 1, pos.z),
            new HouseBlockPos(pos.x, pos.y, pos.z + 1),
            new HouseBlockPos(pos.x, pos.y, pos.z - 1)
        );
    }

    private static boolean isDoor(BlockType type) {
        if (type == null || type.getId() == null) return false;
        return type.getId().toLowerCase().contains("door");
    }

    private static boolean isBed(BlockType type) {
        if (type == null || type.getId() == null) return false;
        return com.cookieukw.SimTale.systems.BedRegistry.isBedId(type.getId());
    }

    private static boolean isChest(BlockType type) {
        if (type == null || type.getId() == null) return false;
        String id = type.getId().toLowerCase();
        return id.contains("chest") || id.contains("barrel") || id.contains("cupboard") || id.contains("cabinet");
    }

    private static boolean isSolid(BlockType type) {
        if (type == null || type.getId() == null) return false;
        String id = type.getId().toLowerCase();
        if (id.equalsIgnoreCase("empty") || id.equalsIgnoreCase("air")) return false;
        
        if (id.contains("torch") || id.contains("flower") || id.contains("grass") ||
            id.contains("carpet") || id.contains("banner") || id.contains("lantern") ||
            id.contains("chain") || id.contains("painting") || id.contains("mushroom") ||
            id.contains("water") || id.contains("lava") || id.contains("liquid") ||
            id.contains("vine") || id.contains("ladder")) {
            return false;
        }
        return true;
    }
}
