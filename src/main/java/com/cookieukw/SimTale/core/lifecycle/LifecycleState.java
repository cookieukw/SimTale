package com.cookieukw.SimTale.core.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class LifecycleState {

    public static final List<GrowthComponent> ACTIVE_CHILDREN = new CopyOnWriteArrayList<>();

    /** Whether the disk load has been attempted since the server came up. */
    private static volatile boolean loadAttempted = false;

    /**
     * Guarantees the growth list has been read from disk at least once.
     *
     * <p>{@code PlayerJoinHandler} rebuilds the list on join, and everything that asks "is this NPC
     * my child?" trusts that it did. When it does not — a join that fired before the world was
     * ready, an exception earlier in that handler, a mod reload without relogging — the list stays
     * empty for the whole session and the failure is silent and confusing: the interaction panel
     * still calls the NPC your child, because that text comes from the NPC's own persisted family
     * record, while <em>Scold</em> and <em>Pick up</em> vanish, because those go through
     * {@code ACTIVE_CHILDREN}. Same NPC, two sources, one of them empty.
     *
     * <p>Attempted once rather than whenever the list is empty: a world with no children is a
     * legitimate empty list, and retrying on every lookup would be a database read per interaction.
     */
    public static void ensureLoaded() {
        if (loadAttempted) return;
        synchronized (LifecycleState.class) {
            if (loadAttempted) return;
            loadAttempted = true;
            BabyCareManager.loadActiveChildren();
        }
    }

    /** Lets the join handler stay authoritative: its load counts as the one attempt. */
    public static void markLoaded() {
        loadAttempted = true;
    }

    public static List<GrowthComponent> findChildrenOfMother(UUID motherId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (motherId.equals(child.motherId)) {
                result.add(child);
            }
        }
        return result;
    }

    public static List<GrowthComponent> findChildrenNeedingCare(UUID parentId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (child.needsCare() && (parentId.equals(child.motherId) || parentId.equals(child.fatherId))) {
                result.add(child);
            }
        }
        return result;
    }
}
