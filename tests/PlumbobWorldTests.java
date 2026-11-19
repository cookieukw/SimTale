package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.tests.Assert;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Uses two real, minimal ECS stores: no running server, world or assets required.
 * Mirrors a player moving from the adventure world to a Forgotten Temple instance
 * while their original plumbob is still alive in the source store.
 */
public final class PlumbobWorldTests {

    private PlumbobWorldTests() {
    }

    public static void run() {
        Assert.suite("Plumbobs reject live refs from another world", PlumbobWorldTests::worldTransfer);
        Assert.suite("Plumbob removal uses only the owning store", PlumbobWorldTests::removal);
    }

    public static void main(String[] args) {
        run();
    }

    private static void worldTransfer() {
        try (Stores stores = new Stores()) {
            UUID playerId = UUID.randomUUID();
            UUID otherPlayerId = UUID.randomUUID();
            Ref<EntityStore> oldPlumbob = stores.spawn(stores.source);
            Ref<EntityStore> otherPlumbob = stores.spawn(stores.destination);
            try {
                PlumbobSystem.trackPlumbob(playerId, oldPlumbob);
                PlumbobSystem.trackPlumbob(otherPlayerId, otherPlumbob);
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), oldPlumbob,
                        "Same-store marker is reused");
                Assert.isTrue(oldPlumbob.isValid(), "Source marker is still valid during transfer");

                boolean wrongStoreThrew = false;
                try {
                    stores.destination.getComponent(oldPlumbob, stores.markerType);
                } catch (IllegalStateException e) {
                    wrongStoreThrew = "Incorrect store for entity reference".equals(e.getMessage());
                }
                Assert.isTrue(wrongStoreThrew, "Reproduces the reported Ref.validate crash");

                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.destination), null,
                        "Foreign marker is discarded before any component read");
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), null,
                        "Old marker is no longer claimed and can be reaped in the source world");
                Assert.isTrue(oldPlumbob.isValid(), "Destination does not mutate source-world entities");

                Ref<EntityStore> newPlumbob = stores.spawn(stores.destination);
                PlumbobSystem.trackPlumbob(playerId, newPlumbob);
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.destination), newPlumbob,
                        "Player can own a new marker in the temple instance");
                Assert.isTrue(stores.destination.getComponent(newPlumbob, stores.markerType) != null,
                        "Returned marker can safely be read through the destination store");
                Assert.equal(PlumbobSystem.getPlumbobForStore(otherPlayerId, stores.destination), otherPlumbob,
                        "Another player's marker is unaffected");

                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), null,
                        "Returning through the portal also discards the instance marker");
                Ref<EntityStore> returnedPlumbob = stores.spawn(stores.source);
                PlumbobSystem.trackPlumbob(playerId, returnedPlumbob);
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), returnedPlumbob,
                        "Marker can be recreated after returning to the original world");

                stores.source.removeEntity(returnedPlumbob, RemoveReason.REMOVE);
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), null,
                        "Invalid markers are discarded too");
            } finally {
                PlumbobSystem.removePlumbob(playerId);
                PlumbobSystem.removePlumbob(otherPlayerId);
            }
        }
    }

    private static void removal() {
        try (Stores stores = new Stores()) {
            UUID playerId = UUID.randomUUID();
            Ref<EntityStore> oldPlumbob = stores.spawn(stores.source);
            stores.spawn(stores.destination); // Ensures forEachChunk supplies a destination buffer.
            try {
                PlumbobSystem.trackPlumbob(playerId, oldPlumbob);
                stores.destination.forEachChunk((chunk, buffer) -> {
                    PlumbobSystem.removePlumbob(playerId, buffer);
                });
                Assert.isTrue(oldPlumbob.isValid(),
                        "Destination buffer does not queue removal of a source-world ref");
                Assert.equal(PlumbobSystem.getPlumbobForStore(playerId, stores.source), null,
                        "Foreign marker is untracked for source-world cleanup");

                Ref<EntityStore> localPlumbob = stores.spawn(stores.destination);
                PlumbobSystem.trackPlumbob(playerId, localPlumbob);
                stores.destination.forEachChunk((chunk, buffer) -> {
                    PlumbobSystem.removePlumbob(playerId, buffer);
                    PlumbobSystem.removePlumbob(playerId, buffer);
                });
                Assert.isFalse(localPlumbob.isValid(), "Local marker is removed through its own buffer");
                Assert.isTrue(oldPlumbob.isValid(), "Removing the new marker does not affect the old store");
            } finally {
                PlumbobSystem.removePlumbob(playerId);
            }
        }
    }

    public static final class Marker implements Component<EntityStore> {
        @Override
        public Marker clone() {
            return new Marker();
        }
    }

    private static final class Stores implements AutoCloseable {
        private final ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        private final ComponentType<EntityStore, Marker> markerType =
                registry.registerComponent(Marker.class, Marker::new);
        private final Store<EntityStore> source = registry.addStore(null, EmptyResourceStorage.get());
        private final Store<EntityStore> destination = registry.addStore(null, EmptyResourceStorage.get());

        private Ref<EntityStore> spawn(Store<EntityStore> store) {
            Holder<EntityStore> holder = registry.newHolder();
            holder.addComponent(markerType, new Marker());
            return store.addEntity(holder, AddReason.SPAWN);
        }

        @Override
        public void close() {
            registry.shutdown();
        }
    }
}
