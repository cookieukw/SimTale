package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Relationship;

import java.util.UUID;

/**
 * Answers "is this NPC that player's child, and how old is she?".
 *
 * <p>Split out because two different systems ask it and neither should have to know how parentage
 * is stored: the interaction page asks in order to show <em>Scold</em> in place of <em>Insult</em>,
 * and the scold handler asks again to pick the reaction for her age.
 *
 * <p>The lookup goes through {@link LifecycleManager#ACTIVE_CHILDREN} rather than through the NPC's
 * own family record: a child knows its parents by the {@code GrowthComponent} it was born with, and
 * that is the only place a <em>player</em> ever appears as a parent — {@code FamilySystem.children}
 * on the NPC side lists an NPC's own offspring, which is a different relationship.
 */
public final class ParentChildBond {

    private ParentChildBond() {
    }

    /** The growth record tying {@code npc} to {@code playerUuid} as a parent, or null. */
    public static GrowthComponent findChildOf(SimNPCComponent npc, UUID playerUuid) {
        if (npc == null || npc.entityId == null || playerUuid == null) return null;

        // The panel calling an NPC "your child" while Scold and Pick up are missing is this list
        // being empty, not the parentage being wrong — the two facts come from different places.
        LifecycleState.ensureLoaded();

        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (child == null || !npc.entityId.equals(child.childId)) continue;
            if (playerUuid.equals(child.motherId) || playerUuid.equals(child.fatherId)) {
                return child;
            }
        }

        GrowthComponent direct = Caskara.load("child_" + npc.entityId, GrowthComponent.class);
        if (direct != null && (playerUuid.equals(direct.motherId) || playerUuid.equals(direct.fatherId))) {
            return direct;
        }

        return null;
    }

    /** Whether {@code npc} is a child of {@code playerUuid}, at any life stage. */
    public static boolean isChildOf(SimNPCComponent npc, UUID playerUuid) {
        return findChildOf(npc, playerUuid) != null;
    }

    /** Life stage of that child, or null when the NPC is not this player's child. */
    public static GrowthStage stageOf(SimNPCComponent npc, UUID playerUuid) {
        GrowthComponent child = findChildOf(npc, playerUuid);
        return child != null ? child.stage : null;
    }

    /**
     * Affinity at or above this is a warm bond — {@code mamãe}/{@code papai} instead of the plain
     * {@code mãe}/{@code pai}.
     */
    private static final int WARM_BOND_THRESHOLD = 40;

    /**
     * Below this the bond has soured enough that the title itself is gone: the child calls this
     * parent by their own name instead. {@link Relationship#affinity} starts at 0 and a scold
     * always costs at least a little of it (more the more it repeats in a day), so a parent who
     * only ever scolds eventually crosses this on their own, with no separate tracking needed.
     */
    private static final int NAME_ONLY_THRESHOLD = 0;

    /**
     * Translation key for the term this child currently uses to address {@code playerUuid} as
     * their parent — the warm form, the plain form, or null once the bond has soured enough that
     * they use the player's own name instead.
     * <p>
     * Read fresh every call off the live {@link Relationship#affinity}, not decided once at some
     * milestone (e.g. growing up) and remembered: a parent who mends things earns the title back
     * exactly the way they lost it, at any age.
     *
     * @return null when {@code playerUuid} is not this NPC's parent at all, or when the bond is
     *         too poor for a parental title — the caller should fall back to the player's own name
     *         in the second case. Use {@link #isChildOf} first if the two need to be told apart.
     */
    public static String parentTermKey(SimNPCComponent npc, UUID playerUuid) {
        GrowthComponent child = findChildOf(npc, playerUuid);
        if (child == null || npc == null) return null;

        Relationship rel = npc.getRelationship(playerUuid);
        if (rel.affinity < NAME_ONLY_THRESHOLD) return null;

        boolean isMother = playerUuid.equals(child.motherId);
        boolean warm = rel.affinity >= WARM_BOND_THRESHOLD;
        if (isMother) {
            return warm ? "npc-dialogues.terms.mom_warm" : "npc-dialogues.terms.mom_plain";
        }
        return warm ? "npc-dialogues.terms.dad_warm" : "npc-dialogues.terms.dad_plain";
    }
}
