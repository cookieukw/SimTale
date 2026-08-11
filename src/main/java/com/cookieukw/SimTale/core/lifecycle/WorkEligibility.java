package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Locale;

/**
 * Whether an NPC is old enough for the job it was given.
 *
 * <p>Professions are rolled at random when an NPC is created, including for children, and nothing
 * checked age before letting them work. Most of the time that is a feature — a child wandering off
 * to plant carrots is exactly the kind of thing that makes the village feel alive. The exceptions
 * are the professions with a real mechanic attached: a Guard child walks the perimeter hunting
 * skeletons, and a Hunter or Miner child shrinks to near-nothing and disappears for two minutes.
 *
 * <p>Nothing is taken away permanently. The profession stays, and the day the NPC turns TEEN it
 * simply starts working.
 */
public final class WorkEligibility {

    private WorkEligibility() {
    }

    /** Whether {@code npc} may work its current profession right now. */
    public static boolean canWork(SimNPCComponent npc) {
        if (npc == null || npc.profession == null) return false;
        if (npc.profession.isSafeForChildren()) return true;
        return !isYoungerThanTeen(npc);
    }

    /**
     * Whether this NPC is still a child.
     *
     * <p>Two sources on purpose, because they cover different NPCs. The growth records only exist
     * for NPCs that were actually born in-game; a child spawned straight from a command or from a
     * child role has no record at all, and is recognised by its role name — the same pair of checks
     * {@code InteractionManager.isNpcAChild} already relies on.
     */
    private static boolean isYoungerThanTeen(SimNPCComponent npc) {
        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                return child.stage == GrowthStage.BABY
                        || child.stage == GrowthStage.TODDLER
                        || child.stage == GrowthStage.CHILD;
            }
        }

        if (npc.entityRef != null && npc.entityRef.isValid()) {
            NPCEntity npcEntity = npc.entityRef.getStore()
                    .getComponent(npc.entityRef, NPCEntity.getComponentType());
            if (npcEntity != null && npcEntity.getRoleName() != null) {
                return npcEntity.getRoleName().toLowerCase(Locale.ROOT).contains("child");
            }
        }

        return false;
    }
}
