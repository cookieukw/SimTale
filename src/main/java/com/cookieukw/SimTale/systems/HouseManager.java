package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.lifecycle.FamilyBonds;
import com.hypixel.hytale.server.core.Message;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HouseManager {
    private static final SimLog LOGGER = SimLog.forClass(HouseManager.class);
    private static final int MAX_INTERIOR_BLOCKS = 512;
    public static final int MIN_INTERIOR_BLOCKS = 50;

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

    /**
     * @param furnitureBlocks solid blocks touching the interior — chairs, tables, lamps on a post.
     *        Separate from {@code interiorBlocks} because the fill must not walk through them, but
     *        the furniture check has to be able to see them. Without this set the mandatory
     *        requirements were unsatisfiable: a chair and a table are solid, the fill skipped them
     *        as walls without recording anything, and the check then looked for them in a set that
     *        by construction only ever held air, beds and chests.
     */
    public record HouseScanResult(Set<HouseBlockPos> interiorBlocks, Set<HouseBlockPos> doorBlocks,
                                  Set<HouseBlockPos> otherBeds, Set<HouseBlockPos> chestBlocks,
                                  Set<HouseBlockPos> furnitureBlocks, boolean overflowed,
                                  boolean hitUnloaded) {
    }

    public record ScanReport(ScanOutcome outcome, Set<UUID> freshBedOwners, UUID conflictingHouseId,
                             HouseScanResult raw) {
    }

    public static void loadAllHouses() {
        HOUSES_BY_ID.clear();
        BLOCK_TO_HOUSE_ID.clear();
        OWNER_TO_HOUSE_ID.clear();

        try {
            List<HouseData> list = SimNPCPersistence.worldShell().core(HouseData.class).extractAll();
            if (list != null) {
                for (HouseData house : list) {
                    if (house.houseId == null) continue;
                    house.syncBeds();
                    HOUSES_BY_ID.put(UUID.fromString(house.houseId), house);
                    indexHouse(house);
                }
                LOGGER.info("[SimTale] Carregadas {} casas com sucesso da persistência Caskara", HOUSES_BY_ID.size());
                pruneOrphanOwners();
                dedupeHousesByOverlap();
                VillageManager.markDirty();
            }
        } catch (Exception e) {
            LOGGER.error("[SimTale] Falha ao carregar as casas da persistência Caskara: ", e);
        }
    }

    /**
     * Drops owners whose NPC record no longer exists.
     *
     * <p>Houses never released an owner: registration deliberately carries the previous list over
     * so an update does not evict the current residents, and nothing removes an entry on
     * {@code /simtale forget}, {@code clearall} or death. The list therefore only grew, and the
     * chest debug screen ended up showing bare UUID fragments for NPCs that had not existed for
     * hours.
     *
     * <p>The check is deliberately against the database and not {@code ACTIVE_NPCS}. An NPC in an
     * unloaded chunk is absent from that list and is very much alive — pruning by it would evict
     * real residents from their own homes, which is a far worse bug than the one being fixed.
     *
     * <p>Runs once at load, where the record set is already being read, rather than on a tick.
     */
    private static void pruneOrphanOwners() {
        int removed = 0;
        int housesTouched = 0;

        for (HouseData house : HOUSES_BY_ID.values()) {
            if (house.owners == null || house.owners.isEmpty()) continue;

            List<String> orphans = new ArrayList<>();
            for (String ownerStr : house.owners) {
                try {
                    if (SimNPCPersistence.loadData(UUID.fromString(ownerStr)) == null) {
                        orphans.add(ownerStr);
                    }
                } catch (IllegalArgumentException notAUuid) {
                    orphans.add(ownerStr);
                }
            }

            if (orphans.isEmpty()) continue;

            /* Leaving a house with zero owners would make its chests unusable by everyone, which
            is worse than a stale name. The bed claim path will adopt it again.
            */
            unindexHouse(house);
            house.owners.removeAll(orphans);
            indexHouse(house);
            saveHouse(house);

            removed += orphans.size();
            housesTouched++;
        }

        if (removed > 0) {
            LOGGER.info("[SimTale] Removidos {} dono(s) orfao(s) de {} casa(s)", removed, housesTouched);
        }
    }

    public static void saveHouse(HouseData house) {
        if (house == null || house.houseId == null) return;
        SimNPCPersistence.worldShell().core(HouseData.class).preserve("house_" + house.houseId, house);
    }

    /**
     * Removes the house whose bed sits at {@code bedPos}, if any.
     *
     * <p>A house is identified by its bed, so breaking the bed ends the house. Nothing did this
     * before: {@code deleteHouse} was reachable only from the duplicate sweep, which meant a house
     * record outlived its own demolition — the bed gone, the walls gone, and the registry still
     * insisting the place was a home. That is exactly the stale-village problem this project set
     * out not to have.
     *
     * @return true when a house was removed
    /**
     * Handles the removal of a bed block.
     * Unlike legacy behavior, breaking a bed does NOT delete the house.
     * It unassigns any NPC currently sleeping in that bed, removes the bed from the house's beds set,
     * and saves the updated house. The house and its chests, interior and residents remain intact.
     *
     * @return true if a house containing this bed was updated
     */
    public static boolean handleBedBroken(HouseBlockPos bedPos) {
        if (bedPos == null) return false;

        HouseData targetHouse = null;
        for (HouseData house : HOUSES_BY_ID.values()) {
            if (house == null) continue;
            if ((house.beds != null && house.beds.contains(bedPos)) || (house.bedPos != null && house.bedPos.equals(bedPos))) {
                targetHouse = house;
                break;
            }
        }
        if (targetHouse == null) {
            UUID houseId = BLOCK_TO_HOUSE_ID.get(bedPos);
            if (houseId != null) {
                targetHouse = HOUSES_BY_ID.get(houseId);
            }
        }

        if (targetHouse == null) return false;

        targetHouse.removeBed(bedPos);
        saveHouse(targetHouse);

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.bedLocation != null
                    && npc.bedLocation.x == bedPos.x
                    && npc.bedLocation.y == bedPos.y
                    && npc.bedLocation.z == bedPos.z) {
                npc.bedLocation = null;
                SimBedData.BedPos alternate = findUnclaimedBedInHouse(targetHouse, npc);
                if (alternate != null) {
                    npc.bedLocation = alternate;
                    LOGGER.info("[SimTale] NPC '{}' teve a cama quebrada, mas mudou para outra cama livre na mesma casa ({},{},{}).",
                            npc.name, alternate.x, alternate.y, alternate.z);
                } else {
                    LOGGER.info("[SimTale] NPC '{}' perdeu a cama e vai procurar outra (casa {} mantida).",
                            npc.name, targetHouse.houseId);
                }
                SimNPCPersistence.saveNPC(npc);
            }
        }

        VillageManager.markDirty();
        return true;
    }

    public static boolean deleteHouseByBed(HouseBlockPos bedPos) {
        return handleBedBroken(bedPos);
    }

    public static boolean handleDoorBroken(HouseBlockPos doorPos) {
        if (doorPos == null) return false;
        for (HouseData house : HOUSES_BY_ID.values()) {
            if (house == null || house.doors == null) continue;
            if (house.doors.remove(doorPos)) {
                saveHouse(house);
                LOGGER.info("[SimTale] Porta removida da casa {}. Portas restantes: {}", house.houseId, house.doors.size());
                return true;
            }
        }
        return false;
    }

    public static boolean registerBedInHouse(HouseBlockPos bedPos) {
        if (bedPos == null) return false;
        UUID houseId = findHouseIdForRoom(bedPos, null);
        if (houseId == null) {
            houseId = findHouseIdForChest(bedPos);
        }
        if (houseId != null) {
            HouseData house = HOUSES_BY_ID.get(houseId);
            if (house != null) {
                house.addBed(bedPos);
                saveHouse(house);
                VillageManager.markDirty();
                LOGGER.info("[SimTale] Nova cama em ({},{},{}) adicionada à casa existente {}.",
                        bedPos.x, bedPos.y, bedPos.z, house.houseId);
                return true;
            }
        }
        return false;
    }

    public static SimBedData.BedPos findUnclaimedBedInHouse(HouseData house, SimNPCComponent self) {
        if (house == null || house.beds == null || house.beds.isEmpty()) return null;
        for (HouseBlockPos pos : house.beds) {
            SimBedData.BedPos bp = new SimBedData.BedPos(pos.x, pos.y, pos.z, 0f);
            if (!isBedTakenByAnotherNpc(bp, self)) {
                return bp;
            }
        }
        return null;
    }

    public static void deleteHouse(UUID houseId) {
        HouseData house = HOUSES_BY_ID.remove(houseId);
        if (house != null) {
            unindexHouse(house);
            SimNPCPersistence.worldShell().core(HouseData.class).discard("house_" + houseId.toString());
            VillageManager.markDirty();
            LOGGER.info("[SimTale] Casa {} deletada.", houseId);
        }
    }

    public static void registerHouse(HouseData house) {
        UUID houseId = UUID.fromString(house.houseId);
        /* Un-index whatever was registered under this id first; re-registering a house whose
        footprint shrank used to leave the dropped blocks pointing at it in
        BLOCK_TO_HOUSE_ID forever, so chest permissions kept honouring walls that no longer
        existed.
        */
        HouseData previous = HOUSES_BY_ID.put(houseId, house);
        if (previous != null) {
            unindexHouse(previous);
        }
        indexHouse(house);
        saveHouse(house);
        VillageManager.markDirty();
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
        Set<HouseBlockPos> furniture = new HashSet<>();
        Set<HouseBlockPos> otherBeds = new HashSet<>();
        Set<HouseBlockPos> chestBlocks = new HashSet<>();
        Deque<HouseBlockPos> queue = new ArrayDeque<>();

        queue.add(bedPos);
        visited.add(bedPos);
        boolean hitUnloaded = false;

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_INTERIOR_BLOCKS) {
                return new HouseScanResult(visited, doors, otherBeds, chestBlocks, furniture, true, hitUnloaded);
            }

            HouseBlockPos current = queue.poll();

            for (HouseBlockPos neighbor : get6Neighbors(current)) {
                if (visited.contains(neighbor)) continue;

                BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);

                /* A null type means "not loaded", not "air". isSolid() reported false for it,
                so the fill poured out through unloaded chunks until it hit the 512-block cap
                and the house was rejected as unenclosed. Treat it as a boundary and flag the
                scan as incomplete instead of burning the whole budget.
                */
                if (type == null) {
                    hitUnloaded = true;
                    continue;
                }

                if (isDoor(type)) {
                    // Only register the bottom block of the door as the door coordinate to avoid double counting
                    BlockType below = world.getBlockType(neighbor.x, neighbor.y - 1, neighbor.z);
                    if (!isDoor(below)) {
                        doors.add(neighbor);
                    }
                    visited.add(neighbor);
                    continue; 
                }
                /* Furniture is recorded AND traversed. It used to be recorded and treated as a
                wall, which made the fill stop at it — and a bed spans six blocks, so the bed the
                scan starts from walled its own scan in. One house passed and the identical one
                next door failed purely because of where the bed sat relative to free space; a
                real case reported four interior blocks visited.

                Doors stay non-traversable on purpose: a door is the way out, and walking the
                fill through it would leak the scan into the world.
                */
                if (isBed(type)) {
                    if (!neighbor.equals(bedPos)) {
                        otherBeds.add(neighbor);
                    }
                    visited.add(neighbor);
                    queue.add(neighbor);
                    continue;
                }
                if (isChest(world, neighbor, type)) {
                    chestBlocks.add(neighbor);
                    visited.add(neighbor);
                    queue.add(neighbor);
                    continue;
                }
                if (isSolid(type)) {
                    /* Still a wall for the fill — but if it is recognisable furniture, remember
                    where it was. Chairs, tables and most lamps are solid, so skipping them
                    silently is what made "needs a chair, a table" impossible to clear even in a
                    room that had both.
                    */
                    if (type.getId() != null
                            && FurnitureRequirement.classify(type.getId().toLowerCase()).isPresent()) {
                        furniture.add(neighbor);
                    }
                    continue;
                }

                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return new HouseScanResult(visited, doors, otherBeds, chestBlocks, furniture, false, hitUnloaded);
    }

    public static ScanReport scanAndClassify(World world, HouseBlockPos bedPos, UUID scanningOwner) {
        HouseScanResult raw = scanHouseFromBed(world, bedPos);

        if (raw.overflowed || raw.hitUnloaded) {
            return new ScanReport(ScanOutcome.TOO_LARGE_OR_UNENCLOSED, Set.of(), null, raw);
        }
        if (raw.interiorBlocks.size() < MIN_INTERIOR_BLOCKS) return new ScanReport(ScanOutcome.TOO_SMALL, Set.of(), null, raw);
        if (raw.doorBlocks.isEmpty()) return new ScanReport(ScanOutcome.NO_ENTRANCE, Set.of(), null, raw);

        for (HouseBlockPos pos : raw.interiorBlocks) {
            UUID existingHouseId = BLOCK_TO_HOUSE_ID.get(pos);
            if (existingHouseId != null) {
                HouseData existing = HOUSES_BY_ID.get(existingHouseId);
                if (existing != null) {
                    boolean matchesOtherBed = false;
                    for (HouseBlockPos otherBed : raw.otherBeds) {
                        if ((existing.beds != null && existing.beds.contains(otherBed))
                                || (existing.bedPos != null && existing.bedPos.equals(otherBed))) {
                            matchesOtherBed = true;
                            break;
                        }
                    }
                    boolean matchesMyBed = (existing.beds != null && existing.beds.contains(bedPos))
                            || (existing.bedPos != null && existing.bedPos.equals(bedPos));
                    if (!matchesOtherBed && !matchesMyBed) {
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

    /**
     * Whether this NPC may take from the chest.
     *
     * <p>A chest that belongs to no house is off limits. That is what keeps NPCs away from the
     * loot chests the world generator scatters around: the registry scan picks up every storage
     * block in range, and without this rule a villager would happily raid a dungeon for dinner.
     * The side effect is that a chest the player drops in an open field is also ignored until it
     * is part of a recognised house, which is the intended trade.
     */
    public static boolean canOpenChest(UUID npcId, HouseBlockPos chestPos) {
        if (chestPos == null || npcId == null) return false;

        // Communal/shared chests in a village can be opened by villagers
        if (ChestRegistry.isShared(chestPos)) {
            VillageManager.Village village = VillageManager.nearest(chestPos.x, chestPos.z);
            if (village != null && village.contains(chestPos.x, chestPos.z)) {
                return true;
            }
            return true;
        }

        UUID houseId = findHouseIdForChest(chestPos);
        if (houseId == null) {
            return false;
        }
        HouseData house = HOUSES_BY_ID.get(houseId);
        if (house == null) return false;

        return house.owners.contains(npcId.toString());
    }

    /**
     * Resolves the house a storage block belongs to, accepting a chest that only touches it.
     *
     * <p>The exact-position lookup alone was wrong for any chest placed after the house was
     * registered. {@code house.interior} is a snapshot taken by the flood fill at claim time, and
     * nothing rescans a room afterwards — so a chest put down later was never in
     * {@code BLOCK_TO_HOUSE_ID}, {@code canOpenChest} refused it forever, and the chest panel
     * reported "no house" for a chest standing in the middle of a registered bedroom.
     *
     * <p>Touching the interior is a sound substitute: the fill records every free block of the
     * room, so a chest inside one necessarily has a face against a recorded block, and a chest
     * outside has none — the loot chests in the world stay excluded.
     */
    public static UUID findHouseIdForChest(HouseBlockPos chestPos) {
        if (chestPos == null) return null;

        UUID direct = BLOCK_TO_HOUSE_ID.get(chestPos);
        if (direct != null) return direct;

        for (HouseBlockPos neighbor : get6Neighbors(chestPos)) {
            UUID houseId = BLOCK_TO_HOUSE_ID.get(neighbor);
            if (houseId != null) return houseId;
        }
        return null;
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
            if (house == null) continue;
            if ((house.beds != null && house.beds.contains(pos))
                    || (house.bedPos != null && house.bedPos.equals(pos))) {
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

    /**
     * Asks the engine whether the block is storage, with the old name guess as a fallback.
     *
     * <p>The name-only version was why NPCs stopped fetching food: chests this world uses do not
     * carry any of the expected keywords, so the house scan filed them as plain walls. They never
     * entered {@code interior}, so {@code BLOCK_TO_HOUSE_ID} had no entry for them, so
     * {@code canOpenChest} — which now requires a house — refused every one of them.
     */
    private static boolean isChest(World world, HouseBlockPos pos, BlockType type) {
        if (ChestRegistry.isContainerAt(world, pos.x, pos.y, pos.z)) return true;
        return type != null && ChestRegistry.isChestId(type.getId());
    }

    private static boolean isSolid(BlockType type) {
        if (type == null || type.getId() == null) return false;
        String id = type.getId().toLowerCase();
        if (id.equalsIgnoreCase("empty") || id.equalsIgnoreCase("air")) return false;

        return !id.contains("torch") && !id.contains("flower") && 
                (!id.contains("grass") || id.contains("soil_grass") || id.contains("grass_block")) &&
                !id.contains("carpet") && !id.contains("banner") && !id.contains("lantern") &&
                !id.contains("chain") && !id.contains("painting") && !id.contains("mushroom") &&
                !id.contains("water") && !id.contains("lava") && !id.contains("liquid") &&
                !id.contains("vine") && !id.contains("ladder");
    }

    public enum FurnitureRequirement {
        LIGHT_SOURCE("torch", "lantern", "candle", "campfire", "glow", "lamp", "chandelier"),
        SEATING("chair", "stool", "bench", "seat", "sofa", "couch"),
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

        /* Interior plus the furniture found in the walls of that interior. Passing only the
        interior is what made "needs a chair, a table" permanent.
        */
        Set<HouseBlockPos> scanArea = new HashSet<>(structural.raw.interiorBlocks);
        scanArea.addAll(structural.raw.furnitureBlocks);

        FurnitureScanResult furniture = scanFurniture(world, scanArea, HouseRequirementSet.DEFAULT);
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

        /* The separating space is raw(), not the end of the .lang value.

        "Precisa de: " ended with a space in both language files and it rendered as
        "Precisa de:uma cadeira" — the .lang parser trims trailing whitespace, so a value can
        never carry its own spacing. Any string that has to butt up against another one has to
        put the gap here, on the Java side.
        */
        Message missingList = Message.raw(" ");
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
            
            /* Reuse the id of whoever already occupies this room instead of picking a new one.

            Here was the origin of record accumulation: it was always UUID.randomUUID(), and
            saveHouse writes to "house_<uuid>". Since the id changed on every claim, the SAME
            room became a new record every time an NPC claimed a bed in it. A world with ONE
            physical house reached 109 records in Caskara.

            The damage was not just database bloat: during loadAllHouses all are indexed, and
            BLOCK_TO_HOUSE_ID ended up pointing to an older record whose bedPos was a different
            bed. Then scanAndClassify returned CONFLICT_WITH_EXISTING for the entire room and
            no NPC could claim any bed there anymore.
            */
            UUID reusedId = findHouseIdForRoom(houseBed, report.raw.interiorBlocks);
            Set<UUID> owners = new HashSet<>(report.freshBedOwners);

            if (reusedId != null) {
                HouseData previous = HOUSES_BY_ID.get(reusedId);
                if (previous != null && previous.owners != null) {
                    // Don't evict existing occupants when updating the record.
                    for (String ownerStr : previous.owners) {
                        try {
                            owners.add(UUID.fromString(ownerStr));
                        } catch (Exception ignored) {}
                    }
                }
            }

            HouseData house = new HouseData(
                reusedId != null ? reusedId : UUID.randomUUID(),
                owners,
                houseBed,
                report.raw.interiorBlocks,
                report.raw.doorBlocks,
                report.raw.chestBlocks
            );
            if (report.raw.otherBeds != null) {
                for (HouseBlockPos other : report.raw.otherBeds) {
                    house.addBed(other);
                }
            }
            house.addBed(houseBed);
            registerHouse(house);
            
            npc.bedLocation = bestBed;
            npc.family.homeX = bestBed.x;
            npc.family.homeY = bestBed.y;
            npc.family.homeZ = bestBed.z;
            npc.family.hasSharedHome = true;
            SimNPCPersistence.saveNPC(npc);
            LOGGER.info("[SimTale] NPC '{}' successfully registered and validated home at bed ({},{},{})!", npc.name, bestBed.x, bestBed.y, bestBed.z);
            return true;
        } else if (report.outcome == ScanOutcome.CONFLICT_WITH_EXISTING
                || report.outcome == ScanOutcome.MERGED_INTO_EXISTING) {

            /* The room already belongs to a registered house. That is no reason to reject: the NPC
            simply moves in.

            Previously, either of these outcomes returned false, causing a total lockup.
            A session log showed the cycle repeating 30x per second:

              is tired (energy=0.0), interrupting task to find bed immediately
              found unclaimed bed at (44,80,30)
              Bed (44,80,30) rejected: candidate house is invalid (CONFLICT_WITH_EXISTING)

            3447 rejections in a few seconds, always for the same bed. The NPC stayed exhausted
            and frozen in place without ever walking to bed, because FINDING_BED fell into IDLE
            and the tiredness interrupt restarted everything on the next tick.

            The root cause was the world accumulating 109 registered houses over test runs,
            so practically every room already belonged to one. Rejecting all of them amounted to
            forbidding any NPC from sleeping.
            */
            HouseData existing = report.conflictingHouseId != null
                    ? HOUSES_BY_ID.get(report.conflictingHouseId)
                    : null;

            if (existing == null) {
                /* The id pointed to a house that no longer exists: orphan record. There is no owner
                to respect, so normal creation flow can proceed on the next attempt.
                */
                LOGGER.warn("[SimTale] Bed ({},{},{}): conflict with non-existent house {}. Orphan record ignored.",
                        bestBed.x, bestBed.y, bestBed.z, report.conflictingHouseId);
                return false;
            }

            if (isBedTakenByAnotherNpc(bestBed, npc)) {
                LOGGER.warn("[SimTale] Bed ({},{},{}) already claimed; NPC '{}' will look for another.",
                        bestBed.x, bestBed.y, bestBed.z, npc.name);
                return false;
            }

            existing.owners.add(npc.entityId.toString());
            existing.addBed(houseBed);
            registerHouse(existing);

            npc.bedLocation = bestBed;
            npc.family.homeX = bestBed.x;
            npc.family.homeY = bestBed.y;
            npc.family.homeZ = bestBed.z;
            npc.family.hasSharedHome = true;
            SimNPCPersistence.saveNPC(npc);

            LOGGER.info("[SimTale] NPC '{}' moved into existing house {} using bed ({},{},{}).",
                    npc.name, existing.houseId, bestBed.x, bestBed.y, bestBed.z);
            return true;

        } else {
            LOGGER.warn("[SimTale] Bed ({},{},{}) for NPC '{}' was rejected: candidate house is invalid ({})",
                        bestBed.x, bestBed.y, bestBed.z, npc.name, report.outcome);
            return false;
        }
    }

    /**
     * Finds the id of the house that already covers this room, if any.
     * <p>
     * The bed is checked first because it is the scan starting point; then any interior block
     * will do, since having just one belonging to a house means it's the same room.
     */
    private static UUID findHouseIdForRoom(HouseBlockPos bedPos, Set<HouseBlockPos> interior) {
        UUID byBed = BLOCK_TO_HOUSE_ID.get(bedPos);
        if (byBed != null && HOUSES_BY_ID.containsKey(byBed)) return byBed;

        if (interior != null) {
            for (HouseBlockPos pos : interior) {
                UUID id = BLOCK_TO_HOUSE_ID.get(pos);
                if (id != null && HOUSES_BY_ID.containsKey(id)) return id;
            }
        }
        return null;
    }

    /**
     * Removes duplicate records of the same physical house.
     * Checks for significant interior block overlap between records.
     */
    private static void dedupeHousesByOverlap() {
        List<UUID> toDelete = new ArrayList<>();
        List<HouseData> allHouses = new ArrayList<>(HOUSES_BY_ID.values());

        for (int i = 0; i < allHouses.size(); i++) {
            HouseData h1 = allHouses.get(i);
            if (h1 == null || h1.houseId == null || toDelete.contains(UUID.fromString(h1.houseId))) continue;

            for (int j = i + 1; j < allHouses.size(); j++) {
                HouseData h2 = allHouses.get(j);
                if (h2 == null || h2.houseId == null || toDelete.contains(UUID.fromString(h2.houseId))) continue;

                boolean sameBed = (h1.bedPos != null && h2.bedPos != null && h1.bedPos.equals(h2.bedPos));
                boolean interiorOverlap = false;

                if (!sameBed && h1.interior != null && h2.interior != null && !h1.interior.isEmpty() && !h2.interior.isEmpty()) {
                    int overlapCount = 0;
                    for (HouseBlockPos pos : h2.interior) {
                        if (h1.interior.contains(pos)) {
                            overlapCount++;
                            if (overlapCount >= 10) {
                                interiorOverlap = true;
                                break;
                            }
                        }
                    }
                }

                if (sameBed || interiorOverlap) {
                    if (h2.owners != null) h1.owners.addAll(h2.owners);
                    if (h2.beds != null) h1.beds.addAll(h2.beds);
                    if (h2.doors != null) h1.doors.addAll(h2.doors);
                    if (h2.chests != null) h1.chests.addAll(h2.chests);
                    toDelete.add(UUID.fromString(h2.houseId));
                }
            }
        }

        if (toDelete.isEmpty()) return;

        for (UUID dup : toDelete) {
            deleteHouse(dup);
        }
        LOGGER.warn("[SimTale] {} duplicate house records removed ({} remaining).",
                toDelete.size(), HOUSES_BY_ID.size());

        BLOCK_TO_HOUSE_ID.clear();
        OWNER_TO_HOUSE_ID.clear();
        for (HouseData house : HOUSES_BY_ID.values()) {
            indexHouse(house);
            saveHouse(house);
        }
    }

    /**
     * Indicates whether the bed is already used by another NPC.
     * <p>
     * Moving into an existing house is allowed for anyone with a place to lie down. A house can
     * have multiple residents, so what matters is the bed being free, not the room.
     */
    private static boolean isBedTakenByAnotherNpc(SimBedData.BedPos bed, SimNPCComponent self) {
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other == self) continue;
            if (other.entityId != null && self.entityId != null && other.entityId.equals(self.entityId)) continue;

            SimBedData.BedPos b = other.bedLocation;
            if (b != null && b.x == bed.x && b.y == bed.y && b.z == bed.z) {
                if (FamilyBonds.isChildOf(self, other)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
}