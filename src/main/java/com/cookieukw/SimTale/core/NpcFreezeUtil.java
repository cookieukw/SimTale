package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Adds/removes the {@link Frozen} component on an NPC from UI page callbacks.
 * <p>
 * Adding or removing a component is a structural store write, and {@code Store} rejects those
 * while it is ticking with {@code "Store is currently processing!"}. UI pages cannot assume
 * they run outside of processing: {@code PageManager} opens and dismisses pages from inside
 * systems — a player dying while an NPC page is open makes the death screen replace it from
 * within {@code Store.tick}, and the dismissed page's cleanup then runs mid-tick.
 * <p>
 * {@link World#isTicking()} tells us which case we are in; when mid-tick the change is
 * deferred to the world thread instead of throwing.
 */
public final class NpcFreezeUtil {

    private NpcFreezeUtil() {
    }

    public static void freeze(Store<EntityStore> store, Ref<EntityStore> npcRef) {
        apply(store, npcRef, true);
    }

    public static void unfreeze(Store<EntityStore> store, Ref<EntityStore> npcRef) {
        apply(store, npcRef, false);
    }

    private static void apply(Store<EntityStore> store, Ref<EntityStore> npcRef, boolean frozen) {
        if (store == null || npcRef == null || !npcRef.isValid()) {
            return;
        }

        World world = WorldUtil.first();
        if (world != null && world.isTicking()) {
            WorldUtil.execute(() -> {
                if (npcRef.isValid()) {
                    write(npcRef.getStore(), npcRef, frozen);
                }
            });
            return;
        }

        write(store, npcRef, frozen);
    }

    private static void write(Store<EntityStore> store, Ref<EntityStore> npcRef, boolean frozen) {
        if (frozen) {
            store.ensureComponent(npcRef, Frozen.getComponentType());
        } else {
            store.tryRemoveComponent(npcRef, Frozen.getComponentType());
        }
    }
}
