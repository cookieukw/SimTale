package com.cookieukw.SimTale.core;


import java.util.NoSuchElementException;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth for "give me the world".
 * <p>
 * SimTale is a single-world mod, but this lookup was duplicated in ~20 places, five of
 * which used {@code getWorlds().values().iterator().next()} and threw
 * {@link NoSuchElementException} whenever they ran before any world was loaded
 * (server boot, world unload, shutdown).
 */
public final class WorldUtil {

    private WorldUtil() {
    }

    /**
     * @return the first loaded world, or {@code null} if none is loaded yet.
     *         Callers must null-check instead of assuming a world exists.
     */
    public static World first() {
        for (World w : Universe.get().getWorlds().values()) {
            return w;
        }
        return null;
    }

    /**
     * @return the world that owns the given ECS store, or {@code first()} if none matches.
     *         This fixes bugs where multi-world servers (like a lobby + adventure world)
     *         cause AI systems to read time and ticks from the wrong world.
     */
    public static World fromStore(Store<EntityStore> store) {
        if (store == null) return first();
        try {
            World w = store.getExternalData().getWorld();
            if (w != null) return w;
        } catch (Exception e) {
            // fallback
        }
        return first();
    }

    /**
     * @return the world where the player with the given PlayerRef component is located.
     */
    public static World findWorldForPlayer(PlayerRef playerRef) {
        if (playerRef == null) return first();
        UUID uuid = playerRef.getUuid();
        for (World w : Universe.get().getWorlds().values()) {
            if (w.getEntityStore().getRefFromUUID(uuid) != null) {
                return w;
            }
        }
        return first();
    }

    /** @return the current tick of the first loaded world, or {@code 0} when there is none. */
    public static long tick() {
        World world = first();
        return world != null ? world.getTick() : 0L;
    }

    /**
     * Runs {@code task} on the world thread.
     * <p>
     * Anything that touches entity components, sends a {@code Message} or mutates NPC state
     * has to go through here when it originates off-thread — async AI callbacks, chat event
     * handlers, delayed replies. Those paths previously ran straight on a
     * {@code ForkJoinPool.commonPool()} worker, racing the tick systems that read the very
     * same fields.
     * <p>
     * If no world is loaded the task is dropped rather than executed on the calling thread:
     * without a world there is nothing valid to act on anyway.
     *
     * @return true if the task was handed to the world thread
     */
    public static boolean execute(Runnable task) {
        if (task == null) return false;
        World world = first();
        if (world == null) return false;
        world.execute(task);
        return true;
    }

    /**
     * Runs {@code task} on the world thread after {@code delayMs}, without blocking a pooled
     * thread while waiting.
     */
    public static void executeLater(Runnable task, long delayMs) {
        if (task == null) return;
        if (delayMs <= 0) {
            execute(task);
            return;
        }
        SCHEDULER.schedule(() -> execute(task), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Single daemon timer for deferred work. Replaces {@code CompletableFuture.runAsync} +
     * {@code Thread.sleep}, which parked a common-pool worker for the whole delay — the common
     * pool is shared process-wide, so sleeping in it can starve unrelated tasks.
     */
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SimTale-Scheduler");
                thread.setDaemon(true);
                return thread;
            });
}
