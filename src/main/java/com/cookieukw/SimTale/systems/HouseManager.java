package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypixel.hytale.server.core.Message;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        TOO_SMALL,
        NO_ENTRANCE
    }

    public record HouseScanResult(Set<HouseBlockPos> interiorBlocks, Set<HouseBlockPos> doorBlocks,
                                  Set<HouseBlockPos> otherBeds, Set<HouseBlockPos> chestBlocks, boolean overflowed) {
    }

    public record ScanReport(ScanOutcome outcome, Set<UUID> freshBedOwners, UUID conflictingHouseId,
                             HouseScanResult raw) {
    }

    public static void loadAllHouses() {
        HOUSES_BY_ID.clear();
        BLOCK_TO_HOUSE_ID.clear();
        OWNER_TO_HOUSE_ID.clear();

        try {
            List<HouseData> list = SimNPCPersistence.DB_SHELL.core(HouseData.class).extractAll();
            if (list != null) {
                for (HouseData house : list) {
                    if (house.houseId == null) continue;
                    HOUSES_BY_ID.put(UUID.fromString(house.houseId), house);
                    indexHouse(house);
                }
                LOGGER.info("[SimTale] Carregadas {} casas com sucesso da persistência Caskara", HOUSES_BY_ID.size());
            }
        } catch (Exception e) {
            LOGGER.error("[SimTale] Falha ao carregar as casas da persistência Caskara: ", e);
        }
    }

    public static void saveHouse(HouseData house) {
        if (house == null || house.houseId == null) return;
        SimNPCPersistence.DB_SHELL.core(HouseData.class).preserve("house_" + house.houseId, house);
    }

    public static void deleteHouse(UUID houseId) {
        HouseData house = HOUSES_BY_ID.remove(houseId);
        if (house != null) {
            unindexHouse(house);
            SimNPCPersistence.DB_SHELL.core(HouseData.class).discard("house_" + houseId.toString());
            LOGGER.info("[SimTale] Casa {} deletada.", houseId);
        }
    }

    public static void registerHouse(HouseData house) {
        UUID houseId = UUID.fromString(house.houseId);
        HOUSES_BY_ID.put(houseId, house);
        indexHouse(house);
        saveHouse(house);
    }

    /** Populates BLOCK_TO_HOUSE_ID and OWNER_TO_HOUSE_ID from a house's interior/owners. */
    private static void indexHouse(HouseData house) {
        UUID houseId = UUID.fromString(house.houseId);
        for (HouseBlockPos pos : house.interior) {
            BLOCK_TO_HOUSE_ID.put(pos, houseId);
        }
        for (String ownerStr : house.owners) {
            try {
                OWNER_TO_HOUSE_ID.put(UUID.fromString(ownerStr), houseId);
            } catch (Exception ignored) {}
        }
    }

    /** Reverses {@link #indexHouse}: removes a house's interior/owners from the lookup maps. */
    private static void unindexHouse(HouseData house) {
        for (HouseBlockPos pos : house.interior) {
            BLOCK_TO_HOUSE_ID.remove(pos);
        }
        for (String ownerStr : house.owners) {
            try {
                OWNER_TO_HOUSE_ID.remove(UUID.fromString(ownerStr));
            } catch (Exception ignored) {}
        }
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
        if (raw.interiorBlocks.size() < 15) return new ScanReport(ScanOutcome.TOO_SMALL, Set.of(), null, raw);
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
                UUID myHouseId = getHouseIdByBedPos(bedPos);
                if (!existingHouseId.equals(myHouseId)) {
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
        return BedRegistry.isBedId(type.getId());
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

        return !id.contains("torch") && !id.contains("flower") && !id.contains("grass") &&
                !id.contains("carpet") && !id.contains("banner") && !id.contains("lantern") &&
                !id.contains("chain") && !id.contains("painting") && !id.contains("mushroom") &&
                !id.contains("water") && !id.contains("lava") && !id.contains("liquid") &&
                !id.contains("vine") && !id.contains("ladder");
    }

    public enum FurnitureRequirement {
        LIGHT_SOURCE("torch", "lantern", "candle", "campfire", "glow"),
        SEATING("chair", "stool", "bench", "seat"),
        SURFACE("table", "workbench", "desk", "counter"),
        STORAGE_OPTIONAL("chest", "barrel", "cupboard", "cabinet");

        private final List<String> idKeywords;

        FurnitureRequirement(String... idKeywords) {
            this.idKeywords = List.of(idKeywords);
        }

        public boolean matches(String blockIdLower) {
            return idKeywords.stream().anyMatch(blockIdLower::contains);
        }

        public static Optional<FurnitureRequirement> classify(String blockIdLower) {
            return Arrays.stream(values()).filter(r -> r.matches(blockIdLower)).findFirst();
        }
    }

    public record HouseRequirementSet(Set<FurnitureRequirement> mandatory) {

        public static final HouseRequirementSet DEFAULT = new HouseRequirementSet(
                Set.of(FurnitureRequirement.LIGHT_SOURCE, FurnitureRequirement.SEATING, FurnitureRequirement.SURFACE)
            );
        }

    public record FurnitureScanResult(Map<FurnitureRequirement, Integer> foundCounts,
                                      Set<FurnitureRequirement> missingMandatory, boolean compatible) {
    }

    public record HouseCompatibilityResult(ScanOutcome structuralOutcome, FurnitureScanResult furniture,
                                           boolean fullyCompatible) {
    }

    public static FurnitureScanResult scanFurniture(World world, Set<HouseBlockPos> interiorBlocks, HouseRequirementSet requirements) {
        Map<FurnitureRequirement, Integer> counts = new EnumMap<>(FurnitureRequirement.class);

        for (HouseBlockPos pos : interiorBlocks) {
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null || type.getId() == null) continue;

            FurnitureRequirement.classify(type.getId().toLowerCase())
                .ifPresent(req -> counts.merge(req, 1, Integer::sum));
        }

        Set<FurnitureRequirement> missing = requirements.mandatory().stream()
            .filter(req -> counts.getOrDefault(req, 0) == 0)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(FurnitureRequirement.class)));

        return new FurnitureScanResult(counts, missing, missing.isEmpty());
    }

    public static HouseCompatibilityResult checkFullCompatibility(World world, HouseBlockPos bedPos, UUID scanningOwner) {
        ScanReport structural = scanAndClassify(world, bedPos, scanningOwner);

        boolean structuralOk = structural.outcome == ScanOutcome.NEW_HOUSE_SINGLE_OWNER
            || structural.outcome == ScanOutcome.NEW_HOUSE_MULTI_OWNER;

        if (!structuralOk) {
            return new HouseCompatibilityResult(structural.outcome, null, false);
        }

        FurnitureScanResult furniture = scanFurniture(world, structural.raw.interiorBlocks, HouseRequirementSet.DEFAULT);
        return new HouseCompatibilityResult(structural.outcome, furniture, furniture.compatible());
    }

    public static Message buildCompatibilityReport(HouseCompatibilityResult result) {
        if (result.furniture() == null) {
            return switch (result.structuralOutcome()) {
                case TOO_LARGE_OR_UNENCLOSED -> Message.translation("general.house.check.unenclosed");
                case TOO_SMALL -> Message.translation("general.house.check.too_small");
                case NO_ENTRANCE -> Message.translation("general.house.check.no_entrance");
                case MERGED_INTO_EXISTING, CONFLICT_WITH_EXISTING -> Message.translation("general.house.check.conflict");
                default -> Message.translation("general.house.check.unknown_error");
            };
        }

        if (result.fullyCompatible()) {
            return Message.translation("general.house.check.valid");
        }

        Message missingList = Message.raw("");
        boolean first = true;
        for (FurnitureRequirement missing : result.furniture().missingMandatory()) {
            if (!first) {
                missingList = missingList.insert(Message.raw(", "));
            }
            missingList = missingList.insert(Message.translation("general.house.requirement." + missing.name().toLowerCase()));
            first = false;
        }

        return Message.translation("general.house.check.incomplete").insert(missingList);
    }

    public static boolean validateAndClaimBed(World world, SimBedData.BedPos bestBed, SimNPCComponent npc) {
        HouseBlockPos houseBed = new HouseBlockPos(bestBed.x, bestBed.y, bestBed.z);
        ScanReport report = scanAndClassify(world, houseBed, npc.entityId);
        if (report.outcome == ScanOutcome.NEW_HOUSE_SINGLE_OWNER || 
            report.outcome == ScanOutcome.NEW_HOUSE_MULTI_OWNER) {
            
            HouseData house = new HouseData(
                UUID.randomUUID(), 
                report.freshBedOwners, 
                houseBed, 
                report.raw.interiorBlocks, 
                report.raw.doorBlocks, 
                report.raw.chestBlocks
            );
            registerHouse(house);
            
            npc.bedLocation = bestBed;
            npc.family.homeX = bestBed.x;
            npc.family.homeY = bestBed.y;
            npc.family.homeZ = bestBed.z;
            npc.family.hasSharedHome = true;
            SimNPCPersistence.saveNPC(npc);
            LOGGER.info("[SimTale] NPC '{}' registrou e validou com sucesso sua casa na cama ({},{},{})!", npc.name, bestBed.x, bestBed.y, bestBed.z);
            return true;
        } else {
            LOGGER.warn("[SimTale] Cama ({},{},{}) para o NPC '{}' foi rejeitada: a casa candidata e invalida ({})", 
                        bestBed.x, bestBed.y, bestBed.z, npc.name, report.outcome);
            return false;
        }
    }
}