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
                    HOUSES_BY_ID.put(UUID.fromString(house.houseId), house);
                    indexHouse(house);
                }
                LOGGER.info("[SimTale] Carregadas {} casas com sucesso da persistência Caskara", HOUSES_BY_ID.size());
                pruneOrphanOwners();
                dedupeHousesByBed();
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

            // Leaving a house with zero owners would make its chests unusable by everyone, which
            // is worse than a stale name. The bed claim path will adopt it again.
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
     */
    public static boolean deleteHouseByBed(HouseBlockPos bedPos) {
        if (bedPos == null) return false;

        UUID target = null;
        for (Map.Entry<UUID, HouseData> entry : HOUSES_BY_ID.entrySet()) {
            HouseData house = entry.getValue();
            if (house.bedPos != null && house.bedPos.equals(bedPos)) {
                target = entry.getKey();
                break;
            }
        }
        if (target == null) return false;

        // The residents lose their claim: leaving bedLocation pointing at a bed that no longer
        // exists would send them walking to it every night.
        HouseData house = HOUSES_BY_ID.get(target);
        if (house != null) {
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.bedLocation != null
                        && npc.bedLocation.x == bedPos.x
                        && npc.bedLocation.y == bedPos.y
                        && npc.bedLocation.z == bedPos.z) {
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    SimNPCPersistence.saveNPC(npc);
                    LOGGER.info("[SimTale] NPC '{}' perdeu a cama; vai procurar outra.", npc.name);
                }
            }
        }

        deleteHouse(target);
        return true;
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
        // Un-index whatever was registered under this id first; re-registering a house whose
        // footprint shrank used to leave the dropped blocks pointing at it in
        // BLOCK_TO_HOUSE_ID forever, so chest permissions kept honouring walls that no longer
        // existed.
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

                // A null type means "not loaded", not "air". isSolid() reported false for it,
                // so the fill poured out through unloaded chunks until it hit the 512-block cap
                // and the house was rejected as unenclosed. Treat it as a boundary and flag the
                // scan as incomplete instead of burning the whole budget.
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
                // Furniture is recorded AND traversed. It used to be recorded and treated as a
                // wall, which made the fill stop at it — and a bed spans six blocks, so the bed the
                // scan starts from walled its own scan in. One house passed and the identical one
                // next door failed purely because of where the bed sat relative to free space; a
                // real case reported four interior blocks visited.
                //
                // Doors stay non-traversable on purpose: a door is the way out, and walking the
                // fill through it would leak the scan into the world.
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
                    // Still a wall for the fill — but if it is recognisable furniture, remember
                    // where it was. Chairs, tables and most lamps are solid, so skipping them
                    // silently is what made "needs a chair, a table" impossible to clear even in a
                    // room that had both.
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
        UUID houseId = findHouseIdForChest(chestPos);
        if (houseId == null) {
            return false;
        }
        HouseData house = HOUSES_BY_ID.get(houseId);
        if (house == null) return false;

        // npcId is null for NPCs that have not been persisted yet; it used to NPE here,
        // aborting the whole hunger/deposit scan for that NPC.
        if (npcId == null) return false;
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

        // Interior plus the furniture found in the walls of that interior. Passing only the
        // interior is what made "needs a chair, a table" permanent.
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

        // The separating space is raw(), not the end of the .lang value.
        //
        // "Precisa de: " ended with a space in both language files and it rendered as
        // "Precisa de:uma cadeira" — the .lang parser trims trailing whitespace, so a value can
        // never carry its own spacing. Any string that has to butt up against another one has to
        // put the gap here, on the Java side.
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
            
            // Reaproveita o id de quem ja ocupa este comodo, em vez de sortear um novo.
            //
            // Aqui estava a origem do acumulo de registros: era sempre UUID.randomUUID(), e o
            // saveHouse grava em "house_<uuid>". Como o id mudava a cada reivindicacao, o MESMO
            // comodo virava um registro novo toda vez que uma NPC pegava uma cama nele. Um mundo
            // com UMA casa fisica chegou a 109 registros no Caskara.
            //
            // O estrago nao era so lixo no banco: no loadAllHouses todos sao indexados, e o
            // BLOCK_TO_HOUSE_ID acabava apontando para um registro antigo cuja bedPos era outra
            // cama. Dai o scanAndClassify devolvia CONFLICT_WITH_EXISTING para o comodo inteiro e
            // nenhuma NPC conseguia mais reivindicar cama nenhuma ali.
            UUID reusedId = findHouseIdForRoom(houseBed, report.raw.interiorBlocks);
            Set<UUID> owners = new HashSet<>(report.freshBedOwners);

            if (reusedId != null) {
                HouseData previous = HOUSES_BY_ID.get(reusedId);
                if (previous != null && previous.owners != null) {
                    // Nao expulsa quem ja morava ali ao atualizar o registro.
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
            registerHouse(house);
            
            npc.bedLocation = bestBed;
            npc.family.homeX = bestBed.x;
            npc.family.homeY = bestBed.y;
            npc.family.homeZ = bestBed.z;
            npc.family.hasSharedHome = true;
            SimNPCPersistence.saveNPC(npc);
            LOGGER.info("[SimTale] NPC '{}' registrou e validou com sucesso sua casa na cama ({},{},{})!", npc.name, bestBed.x, bestBed.y, bestBed.z);
            return true;
        } else if (report.outcome == ScanOutcome.CONFLICT_WITH_EXISTING
                || report.outcome == ScanOutcome.MERGED_INTO_EXISTING) {

            // O comodo ja pertence a uma casa registrada. Isso nao e motivo para recusar: a NPC
            // simplesmente se muda para la.
            //
            // Antes, qualquer um desses dois desfechos devolvia false, e o efeito era um
            // travamento total. O log de uma sessao mostrou o ciclo se repetindo 30x por segundo:
            //
            //   is tired (energy=0.0), interrupting task to find bed immediately
            //   found unclaimed bed at (44,80,30)
            //   Cama (44,80,30) rejeitada: a casa candidata e invalida (CONFLICT_WITH_EXISTING)
            //
            // 3447 rejeicoes em poucos segundos, sempre da mesma cama. A NPC ficava exausta e
            // parada, sem nunca andar ate a cama, porque FINDING_BED caia em IDLE e a interrupcao
            // de cansaco reiniciava tudo no tick seguinte.
            //
            // A causa de fundo e que o mundo acumulou 109 casas registradas ao longo dos testes,
            // entao praticamente todo comodo ja pertence a alguma. Recusar todas equivale a
            // proibir qualquer NPC de dormir.
            HouseData existing = report.conflictingHouseId != null
                    ? HOUSES_BY_ID.get(report.conflictingHouseId)
                    : null;

            if (existing == null) {
                // O id apontava para uma casa que nao existe mais: registro orfao. Nao ha dono a
                // respeitar, entao o caminho normal de criacao pode seguir na proxima tentativa.
                LOGGER.warn("[SimTale] Cama ({},{},{}): conflito com casa inexistente {}. Registro orfao ignorado.",
                        bestBed.x, bestBed.y, bestBed.z, report.conflictingHouseId);
                return false;
            }

            if (isBedTakenByAnotherNpc(bestBed, npc)) {
                LOGGER.warn("[SimTale] Cama ({},{},{}) ja tem dono; NPC '{}' vai procurar outra.",
                        bestBed.x, bestBed.y, bestBed.z, npc.name);
                return false;
            }

            existing.owners.add(npc.entityId.toString());
            registerHouse(existing);

            npc.bedLocation = bestBed;
            npc.family.homeX = bestBed.x;
            npc.family.homeY = bestBed.y;
            npc.family.homeZ = bestBed.z;
            npc.family.hasSharedHome = true;
            SimNPCPersistence.saveNPC(npc);

            LOGGER.info("[SimTale] NPC '{}' mudou-se para a casa existente {} usando a cama ({},{},{}).",
                    npc.name, existing.houseId, bestBed.x, bestBed.y, bestBed.z);
            return true;

        } else {
            LOGGER.warn("[SimTale] Cama ({},{},{}) para o NPC '{}' foi rejeitada: a casa candidata e invalida ({})",
                        bestBed.x, bestBed.y, bestBed.z, npc.name, report.outcome);
            return false;
        }
    }

    /**
     * Procura o id da casa que ja cobre este comodo, se houver.
     * <p>
     * A cama e consultada primeiro porque e o ponto de partida do scan; depois qualquer bloco do
     * interior serve, ja que basta um deles pertencer a uma casa para o comodo ser o mesmo.
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
     * Remove registros duplicados da mesma cama, mantendo um.
     * <p>
     * Limpa o passivo deixado pelo bug do id aleatorio (ver {@link #validateAndClaimBed}). So
     * agrupa por {@code bedPos} identico: dois registros para a MESMA cama sao necessariamente o
     * mesmo lugar, entao nao ha julgamento a fazer. Casas com camas diferentes ficam intactas.
     */
    private static void dedupeHousesByBed() {
        Map<HouseBlockPos, UUID> keptByBed = new HashMap<>();
        List<UUID> duplicates = new ArrayList<>();

        for (Map.Entry<UUID, HouseData> entry : HOUSES_BY_ID.entrySet()) {
            HouseData house = entry.getValue();
            if (house == null || house.bedPos == null) continue;

            UUID kept = keptByBed.putIfAbsent(house.bedPos, entry.getKey());
            if (kept != null) {
                duplicates.add(entry.getKey());
            }
        }

        if (duplicates.isEmpty()) return;

        for (UUID dup : duplicates) {
            deleteHouse(dup);
        }
        LOGGER.warn("[SimTale] {} duplicate house records removed ({} remaining). "
                        + "Eram sobras do id aleatorio gerado a cada reivindicacao de cama.",
                duplicates.size(), HOUSES_BY_ID.size());

        // Reindexa: os registros apagados podem ter sobrescrito entradas do que ficou.
        BLOCK_TO_HOUSE_ID.clear();
        OWNER_TO_HOUSE_ID.clear();
        for (HouseData house : HOUSES_BY_ID.values()) {
            indexHouse(house);
        }
    }

    /**
     * Diz se a cama ja e usada por outra NPC.
     * <p>
     * Se muda para uma casa existente quem tem onde deitar. Uma casa pode ter varios moradores,
     * entao o que importa e a cama estar livre, nao o comodo.
     */
    private static boolean isBedTakenByAnotherNpc(SimBedData.BedPos bed, SimNPCComponent self) {
        for (SimNPCComponent other : com.cookieukw.SimTale.SimTale.ACTIVE_NPCS) {
            if (other == self) continue;
            if (other.entityId != null && self.entityId != null && other.entityId.equals(self.entityId)) continue;

            SimBedData.BedPos b = other.bedLocation;
            if (b != null && b.x == bed.x && b.y == bed.y && b.z == bed.z) {
                return true;
            }
        }
        return false;
    }
}