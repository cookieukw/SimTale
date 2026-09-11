package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.HouseManager;

import java.util.UUID;

/**
 * Wires a newborn to the people who made it.
 *
 * <p>Nothing did this. A child became an entity with a name, a model and a growth stage, and an
 * entirely empty relationship map — so its own parents read as {@code STRANGER}, gifts from them
 * scored like gifts from a passer-by, and the map tinted them the same grey as anyone else. The
 * family data existed the whole time; it just lived in {@link GrowthComponent} and was never
 * projected onto the social system that actually drives behaviour.
 *
 * <p>A parent is not merely a friend, so the bond starts well above what conversation can reach:
 * conversations move friendship a few points at a time, and a child that had to chat its way up to
 * liking its mother would be stranger than one that never did.
 */
public final class FamilyBonds {

    private static final SimLog LOGGER = SimLog.forClass(FamilyBonds.class);

    /**
     * Starting bond between a child and a parent.
     *
     * <p>High enough to land on {@code BEST_FRIEND} through {@link Relationship}'s own thresholds
     * rather than by forcing a status: family is a strong bond, not a special case in the status
     * machine. Trust is maxed because a parent is who a child trusts by default.
     */
    private static final int PARENT_FRIENDSHIP = 85;
    private static final int PARENT_TRUST = 90;
    private static final int PARENT_AFFINITY = 120;

    /** Siblings start close, but below parents. */
    private static final int SIBLING_FRIENDSHIP = 60;
    private static final int SIBLING_TRUST = 55;
    private static final int SIBLING_AFFINITY = 70;

    private FamilyBonds() {
    }

    /**
     * Links {@code child} to its parents in both directions, and to its siblings.
     *
     * <p>Both directions matter: the child liking its mother while the mother treats it as a
     * stranger is exactly half a bug, and the half the player notices is whichever one they happen
     * to interact with first.
     *
     * <p>Safe to call more than once — the values are set, not accumulated, so re-running on load
     * cannot inflate a bond past its intended strength.
     *
     * @param child     the newly embodied child's NPC component
     * @param growth    the growth record carrying the parent ids
     */
    public static void linkToFamily(SimNPCComponent child, GrowthComponent growth) {
        if (child == null || growth == null || child.entityId == null) return;

        bondWithParent(child, growth.motherId);
        bondWithParent(child, growth.fatherId);

        if (child.bedLocation == null) {
            BedPos parentBed = findParentBed(child);
            if (parentBed != null) {
                child.bedLocation = parentBed;
                child.family.homeX = parentBed.x;
                child.family.homeY = parentBed.y;
                child.family.homeZ = parentBed.z;
                child.family.hasSharedHome = true;
                SimNPCPersistence.saveNPC(child);
                LOGGER.info("[SimTale] '{}' vinculada à cama dos pais ({},{},{})",
                        child.name, parentBed.x, parentBed.y, parentBed.z);
            }
        }

        LOGGER.info("[SimTale] '{}' vinculada aos pais (mae={}, pai={})",
                child.name, growth.motherId, growth.fatherId);
    }

    /**
     * Applies the parent bond in both directions.
     *
     * <p>The parent may be a player rather than an NPC. That side simply has no
     * {@code SimNPCComponent} to write to, and the child's own relationship carries the bond —
     * which is the side the interaction panel, gift scoring and map tint all read for a player.
     */
    private static void bondWithParent(SimNPCComponent child, UUID parentId) {
        if (parentId == null) return;

        setFamilyBond(child.getRelationship(parentId),
                PARENT_FRIENDSHIP, PARENT_TRUST, PARENT_AFFINITY);

        SimNPCComponent parent = LifecycleUtils.findNPCById(parentId);
        if (parent == null) return;

        setFamilyBond(parent.getRelationship(child.entityId),
                PARENT_FRIENDSHIP, PARENT_TRUST, PARENT_AFFINITY);

        // Siblings: everyone already on the parent's child list, before this one joins it.
        for (Child sibling : parent.family.children) {
            if (sibling.id == null || sibling.id.equals(child.entityId)) continue;

            setFamilyBond(child.getRelationship(sibling.id),
                    SIBLING_FRIENDSHIP, SIBLING_TRUST, SIBLING_AFFINITY);

            SimNPCComponent siblingNpc = LifecycleUtils.findNPCById(sibling.id);
            if (siblingNpc != null) {
                setFamilyBond(siblingNpc.getRelationship(child.entityId),
                        SIBLING_FRIENDSHIP, SIBLING_TRUST, SIBLING_AFFINITY);
            }
        }

        // Record the child on the parent's list if the birth path has not already.
        boolean known = false;
        for (Child existing : parent.family.children) {
            if (child.entityId.equals(existing.id)) {
                known = true;
                break;
            }
        }
        if (!known) {
            parent.family.children.add(new Child(child.entityId, child.name));
        }

        SimNPCPersistence.saveNPC(parent);
    }

    /**
     * Sets a bond to fixed values instead of adding to whatever was there.
     *
     * <p>{@code addFriendship} and friends are for interactions that accumulate. Being someone's
     * child is a fact, not an accumulation, and using the adders here would mean a second call —
     * on reload, say — pushing the bond past its ceiling.
     */
    private static void setFamilyBond(Relationship rel, int friendship, int trust, int affinity) {
        if (rel == null) return;

        rel.friendship = Math.max(rel.friendship, friendship);
        rel.trust = Math.max(rel.trust, trust);
        rel.affinity = Math.max(rel.affinity, affinity);

        // Never downgrade a status the players earned themselves, and never let a family bond
        // overwrite a romance the status machine is tracking.
        if (rel.status == RelationshipStatus.UNKNOWN
                || rel.status == RelationshipStatus.STRANGER
                || rel.status == RelationshipStatus.ACQUAINTANCE
                || rel.status == RelationshipStatus.FRIEND
                || rel.status == RelationshipStatus.GOOD_FRIEND) {
            rel.status = RelationshipStatus.BEST_FRIEND;
        }
    }

    /**
     * Looks up the bed of a child's parents, from active NPCs, registered houses or growth data.
     */
    public static BedPos findParentBed(SimNPCComponent child) {
        if (child == null) return null;

        GrowthComponent growth = null;
        for (GrowthComponent c : LifecycleManager.ACTIVE_CHILDREN) {
            if (child.entityId != null && child.entityId.equals(c.childId)) {
                growth = c;
                break;
            }
        }
        if (growth == null && child.entityId != null) {
            growth = Caskara.load("child_" + child.entityId, GrowthComponent.class);
        }

        if (growth != null) {
            BedPos bed = getBedOfParent(growth.motherId);
            if (bed != null) return bed;
            bed = getBedOfParent(growth.fatherId);
            if (bed != null) return bed;
        }

        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other.family != null && other.family.children != null) {
                for (Child c : other.family.children) {
                    if (child.entityId != null && child.entityId.equals(c.id)) {
                        if (other.bedLocation != null) return other.bedLocation;
                    }
                }
            }
        }

        if (child.family != null && child.family.hasSharedHome) {
            return new BedPos(
                    (int) Math.floor(child.family.homeX),
                    (int) Math.floor(child.family.homeY),
                    (int) Math.floor(child.family.homeZ),
                    0f);
        }

        return null;
    }

    private static BedPos getBedOfParent(UUID parentId) {
        if (parentId == null) return null;

        SimNPCComponent parentNpc = LifecycleUtils.findNPCById(parentId);
        if (parentNpc != null && parentNpc.bedLocation != null) {
            return parentNpc.bedLocation;
        }

        UUID houseId = HouseManager.OWNER_TO_HOUSE_ID.get(parentId);
        if (houseId != null) {
            HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
            if (house != null && house.bedPos != null) {
                return new BedPos(house.bedPos.x, house.bedPos.y, house.bedPos.z, 0f);
            }
        }
        return null;
    }

    public static boolean isChildOf(SimNPCComponent child, SimNPCComponent parent) {
        if (child == null || parent == null || child.entityId == null || parent.entityId == null) return false;

        GrowthComponent growth = null;
        for (GrowthComponent c : LifecycleManager.ACTIVE_CHILDREN) {
            if (child.entityId.equals(c.childId)) {
                growth = c;
                break;
            }
        }
        if (growth == null) {
            growth = Caskara.load("child_" + child.entityId, GrowthComponent.class);
        }
        if (growth != null) {
            if (parent.entityId.equals(growth.motherId) || parent.entityId.equals(growth.fatherId)) {
                return true;
            }
        }
        if (parent.family != null && parent.family.children != null) {
            for (Child c : parent.family.children) {
                if (child.entityId.equals(c.id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** This NPC's own birth record (who its parents are), regardless of who the parents are. */
    private static GrowthComponent ownGrowthRecord(SimNPCComponent npc) {
        if (npc == null || npc.entityId == null) return null;
        for (GrowthComponent c : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId.equals(c.childId)) return c;
        }
        return Caskara.load("child_" + npc.entityId, GrowthComponent.class);
    }

    private static boolean sameParent(UUID p1, UUID p2) {
        return p1 != null && p1.equals(p2);
    }

    /**
     * Whether {@code a} and {@code b} are close enough family that romance between them should
     * never be offered: the same NPC, parent and child either direction, or siblings (any shared
     * parent, full or half).
     * <p>
     * Used to gate autonomous NPC-NPC romance/marriage — {@link #linkToFamily} already bonds
     * parents and siblings with a strong platonic {@code Relationship}, which is exactly the
     * high-friendship, high-affinity shape the courtship check would otherwise mistake for a
     * good match.
     */
    public static boolean areCloseFamily(SimNPCComponent a, SimNPCComponent b) {
        if (a == null || b == null || a.entityId == null || b.entityId == null) return true;
        if (a.entityId.equals(b.entityId)) return true;
        if (isChildOf(a, b) || isChildOf(b, a)) return true;

        GrowthComponent aGrowth = ownGrowthRecord(a);
        GrowthComponent bGrowth = ownGrowthRecord(b);
        if (aGrowth == null || bGrowth == null) return false;

        return sameParent(aGrowth.motherId, bGrowth.motherId)
                || sameParent(aGrowth.motherId, bGrowth.fatherId)
                || sameParent(aGrowth.fatherId, bGrowth.motherId)
                || sameParent(aGrowth.fatherId, bGrowth.fatherId);
    }
}
