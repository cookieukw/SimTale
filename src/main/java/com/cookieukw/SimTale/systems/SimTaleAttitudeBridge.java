package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.IAttitudeProvider;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adds SimTale residents as valid targets for NPC roles whose native default is hostile to players.
 *
 * <p>This is registered on Hytale's per-world attitude view instead of replacing vanilla mob
 * attitude assets. Explicit attitude overrides and group relationships run first and are left
 * untouched; this provider only fills the missing relationship for a SimTale resident.
 */
public final class SimTaleAttitudeBridge {
    /** Native providers run at 0 (entity override), 100 (spawn lineage), 200 (group map), then MAX. */
    private static final int PRIORITY_AFTER_EXPLICIT_GROUPS = 250;
    private static final Set<AttitudeView> REGISTERED_VIEWS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final IAttitudeProvider PROVIDER = SimTaleAttitudeBridge::attitudeFor;

    private SimTaleAttitudeBridge() {
    }

    /** Install once per world's singleton attitude view. Safe to call whenever a SimTale NPC is tracked. */
    public static void ensureRegistered(Ref<EntityStore> npcRef) {
        if (npcRef == null || !npcRef.isValid() || SimTale.SIM_NPC_COMPONENT_TYPE == null) return;

        Store<EntityStore> store = npcRef.getStore();
        if (store == null) return;
        Blackboard blackboard = store.getResource(Blackboard.getResourceType());
        if (blackboard == null) return;

        AttitudeView view = blackboard.getView(AttitudeView.class, npcRef, store);
        if (view == null) return;

        synchronized (REGISTERED_VIEWS) {
            if (REGISTERED_VIEWS.contains(view)) return;
            view.registerProvider(PRIORITY_AFTER_EXPLICIT_GROUPS, PROVIDER);
            REGISTERED_VIEWS.add(view);
        }
    }

    private static Attitude attitudeFor(Ref<EntityStore> attackerRef, int attackerGroup,
                                        Ref<EntityStore> targetRef, ComponentAccessor<EntityStore> accessor) {
        if (attackerRef == null || targetRef == null || accessor == null
                || !attackerRef.isValid() || !targetRef.isValid()
                || SimTale.SIM_NPC_COMPONENT_TYPE == null) {
            return null;
        }

        SimNPCComponent target = accessor.getComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (target == null || target.isReaper) return null;

        // SimTale NPCs should not become hostile to each other through this bridge.
        if (accessor.getComponent(attackerRef, SimTale.SIM_NPC_COMPONENT_TYPE) != null
                || accessor.getComponent(attackerRef, NPCEntity.getComponentType()) == null) {
            return null;
        }

        WorldSupport attackerSupport = WorldSupport.get(attackerRef, accessor);
        if (attackerSupport == null) return null;

        // Reuse the mob's actual native stance toward players. Roles with explicit attitude-map
        // entries already returned a result at priority 200, so this only fills the missing case.
        return attackerSupport.getDefaultPlayerAttitude() == Attitude.HOSTILE
                ? Attitude.HOSTILE
                : null;
    }
}
