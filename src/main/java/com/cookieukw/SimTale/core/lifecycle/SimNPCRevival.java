package com.cookieukw.SimTale.core.lifecycle;


import com.cookieukw.SimTale.core.FamilySystem;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimTaleLifecyclePersistence;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.HouseManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brings an NPC back out of the graveyard.
 *
 * <p><b>Why the entity UUID changes.</b> The obvious implementation is to spawn a body and force
 * the dead NPC's original UUID back onto it, so that every existing reference keeps working. The
 * engine does not allow it. {@code EntityStore$UUIDSystem} is a RefSystem that fills
 * {@code entitiesByUuid} from the UUIDComponent at the moment an entity is <em>added</em>, using
 * {@code putIfAbsent}, and it logs "Removing duplicate entity with UUID" and deletes the newcomer
 * when the key is taken. Two things follow: replacing the component after the spawn never reaches
 * the index (so {@code getRefFromUUID} would keep pointing at the wrong ref forever), and colliding
 * on purpose gets the revived body silently destroyed.
 *
 * <p>So the body gets a new id and every reference to the old one is rewritten instead. That work
 * is what {@link #remapReferences} does, and it is the fragile part of this feature: anything that
 * stores an NPC UUID and is not listed there will silently keep pointing at a grave.
 */
public final class SimNPCRevival {

    private static final SimLog LOGGER = SimLog.forClass(SimNPCRevival.class);

    private SimNPCRevival() {
    }

    /** Why a revival did not happen, so the caller can say something useful instead of "failed". */
    public enum Failure {
        NO_RECORD,
        SPAWN_FAILED
    }

    /** Either the revived component, or the reason there isn't one. */
    public record Result(SimNPCComponent npc, Failure failure, boolean reclaimedBed) {
        public boolean ok() {
            return npc != null;
        }
    }

    /**
     * Rebuilds a buried NPC at {@code position}.
     *
     * @param graveId the id the NPC had when it died — the key it is filed under in the graveyard
     */
    public static Result revive(Store<EntityStore> store, World world, Vector3d position, UUID graveId) {
        SimNPCData data = SimNPCPersistence.loadFromGraveyard(graveId);
        if (data == null) {
            return new Result(null, Failure.NO_RECORD, false);
        }

        /* A child has a separate lifecycle record with her life stage, child/adult body choice,
        scale and pinned visual variant. The old revival path ignored it and always spawned a new
        adult with a random appearance, so a dead daughter returned as a different grown stranger. */
        GrowthComponent growth = SimTaleLifecyclePersistence.load("child_" + graveId, GrowthComponent.class);
        Gender savedGender = growth != null && growth.gender != null ? growth.gender : data.gender;
        boolean childBody = growth != null && growth.stage != null
                && growth.stage.ordinal() <= GrowthStage.CHILD.ordinal();
        SimNPCFactory.NPCType type = childBody
                ? (savedGender == Gender.MALE ? SimNPCFactory.NPCType.CHILD_MALE : SimNPCFactory.NPCType.CHILD_FEMALE)
                : savedGender == Gender.MALE
                ? SimNPCFactory.NPCType.HUMAN_MALE
                : SimNPCFactory.NPCType.HUMAN_FEMALE;
        float restoredScale = growth != null
                ? LifecycleManager.calculateTargetScale(growth, WorldUtil.tick()) : 1.0f;
        if (growth != null && growth.variant < 0) {
            growth.variant = 1 + (int) (Math.random() * 200);
        }

        Ref<EntityStore> ref;
        try {
            ref = growth != null
                    ? SimNPCFactory.spawnNPC(store, new Vector3d(position), type, restoredScale, growth.variant)
                    : SimNPCFactory.spawnNPC(store, new Vector3d(position), type);
        } catch (RuntimeException e) {
            LOGGER.warn("[SimTale] Revival spawn failed for {}: {}", graveId, e.getMessage());
            return new Result(null, Failure.SPAWN_FAILED, false);
        }

        SimNPCComponent npc = store.getComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) {
            return new Result(null, Failure.SPAWN_FAILED, false);
        }

        UUID newId = npc.entityId;

        if (growth != null) {
            growth.childId = newId;
            growth.isBeingCarried = false;
            growth.carriedBy = null;
            growth.currentScale = restoredScale;
            SimTaleLifecyclePersistence.delete("child_" + graveId, GrowthComponent.class);
            SimTaleLifecyclePersistence.save("child_" + newId, growth);
            LifecycleManager.ACTIVE_CHILDREN.removeIf(existing -> existing != null
                    && (graveId.equals(existing.childId) || newId.equals(existing.childId)));
            LifecycleManager.ACTIVE_CHILDREN.add(growth);
        }

        /* The bed is handled separately below, so the restore must not quietly re-register the old
        one as if it were still hers. Everything else about her comes back untouched.
        */
        SimBedData.BedPos oldBed = data.bedLocation;
        data.bedLocation = null;
        SimNPCPersistence.applyArchivedData(npc, data);
        data.bedLocation = oldBed;

        if (growth != null) {
            FamilyBonds.linkToFamily(npc, growth);
            LifecycleManager.applyVisualScale(ref, growth.currentScale);
        }

        /* The nameplate was stamped with the random name the factory rolled, before the real name
        was restored over it.
        */
        SimNPCFactory.refreshNameplate(store, ref, npc.name);

        remapReferences(store.getExternalData().getWorld(), graveId, newId);

        boolean reclaimed = tryReclaimBed(world, npc, oldBed);

        SimNPCPersistence.saveNPC(npc);
        SimNPCPersistence.removeFromGraveyard(graveId);

        LOGGER.info("[SimTale] Revived '{}' ({} -> {}), bed reclaimed: {}",
                npc.name, graveId, newId, reclaimed);
        return new Result(npc, null, reclaimed);
    }

    /**
     * Gives the bed back only if it is still there and still free.
     * <p>
     * Taking it by force would evict whoever moved in while she was dead, which trades one
     * homeless NPC for another and looks like a bug from the inside of the house.
     */
    private static boolean tryReclaimBed(World world, SimNPCComponent npc,
                                         SimBedData.BedPos oldBed) {
        if (world == null || oldBed == null) return false;
        if (!BedRegistry.exists(oldBed.x, oldBed.y, oldBed.z)) return false;
        return HouseManager.validateAndClaimBed(world, oldBed, npc);
    }

    /**
     * Rewrites every stored reference from the dead id to the living one.
     *
     * <p>Covers, in order: the relationship maps and family trees of every live NPC, the same two
     * on every record still in the database (an NPC that is not loaded right now must not wake up
     * with a broken spouse), the growth records of children, and house ownership.
     *
     * <p>Deliberately not covered: the revived NPC's own outgoing references. Those point at other
     * people, whose ids did not change.
     */
    private static void remapReferences(World world, UUID oldId, UUID newId) {
        if (oldId == null || newId == null || oldId.equals(newId)) return;

        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other == null || oldId.equals(other.entityId)) continue;
            remapRelationships(other.relationships, oldId, newId);
            remapFamily(other.family, oldId, newId);
        }

        for (SimNPCData record : SimNPCPersistence.listAll()) {
            if (record == null || record.id == null || record.id.equals(oldId.toString())) continue;
            boolean touched = remapStoredRelationships(record.relationships, oldId, newId);
            touched |= remapFamily(record.family, oldId, newId);
            if (touched) {
                SimNPCPersistence.saveData(record);
            }
        }

        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (child == null) continue;
            if (oldId.equals(child.childId)) child.childId = newId;
            if (oldId.equals(child.motherId)) child.motherId = newId;
            if (oldId.equals(child.fatherId)) child.fatherId = newId;
            if (oldId.equals(child.carriedBy)) child.carriedBy = newId;
        }

        remapHouseOwnership(world, oldId, newId);
    }

    private static void remapRelationships(Map<UUID, Relationship> relationships, UUID oldId, UUID newId) {
        if (relationships == null) return;
        Relationship existing = relationships.remove(oldId);
        if (existing != null) {
            relationships.put(newId, existing);
        }
    }

    private static boolean remapStoredRelationships(Map<String, Relationship> relationships,
                                                    UUID oldId, UUID newId) {
        if (relationships == null) return false;
        Relationship existing = relationships.remove(oldId.toString());
        if (existing == null) return false;
        relationships.put(newId.toString(), existing);
        return true;
    }

    private static boolean remapFamily(FamilySystem family,
                                       UUID oldId, UUID newId) {
        if (family == null) return false;
        boolean touched = false;
        if (oldId.equals(family.spouseId)) {
            family.spouseId = newId;
            touched = true;
        }
        if (family.children != null) {
            for (Child child : family.children) {
                if (child != null && oldId.equals(child.id)) {
                    child.id = newId;
                    touched = true;
                }
            }
        }
        return touched;
    }

    /**
     * House ownership is stored twice — as a string set on the house and as an index by owner —
     * and both have to move together or {@code OWNER_TO_HOUSE_ID} starts answering for a UUID that
     * no house admits to owning.
     */
    private static void remapHouseOwnership(World world, UUID oldId, UUID newId) {
        List<HouseData> changed = new ArrayList<>();
        HouseManager.HouseIndexes indexes = HouseManager.indexes(world);
        for (HouseData house : indexes.housesById.values()) {
            if (house == null || house.owners == null) continue;
            if (house.owners.remove(oldId.toString())) {
                house.owners.add(newId.toString());
                changed.add(house);
            }
        }

        UUID houseId = indexes.ownerToHouseId.remove(oldId);
        if (houseId != null) {
            indexes.ownerToHouseId.put(newId, houseId);
        }

        for (HouseData house : changed) {
            HouseManager.saveHouse(world, house);
        }
    }

    /** Buried NPCs sorted newest-death-first; legacy records without a timestamp remain last. */
    public static List<SimNPCData> listBuried() {
        List<SimNPCData> all = new ArrayList<>(SimNPCPersistence.listGraveyard());
        Set<String> seen = new HashSet<>();
        all.removeIf(d -> d == null || d.id == null || !seen.add(d.id));
        all.sort((a, b) -> {
            int byDeathDate = Long.compare(b.deathTimestamp, a.deathTimestamp);
            if (byDeathDate != 0) return byDeathDate;
            String left = a.name != null ? a.name : "";
            String right = b.name != null ? b.name : "";
            int byName = left.compareToIgnoreCase(right);
            return byName != 0 ? byName : a.id.compareTo(b.id);
        });
        return all;
    }
}
