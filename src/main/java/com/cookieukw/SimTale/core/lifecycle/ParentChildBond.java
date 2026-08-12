package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.SimNPCComponent;

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
}
