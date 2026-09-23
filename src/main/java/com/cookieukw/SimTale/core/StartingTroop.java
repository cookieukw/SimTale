package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCFactory.NPCType;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.NPCMovementHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Spawns the founding group of NPCs the first time a player sets foot in a fresh world.
 * <p>
 * The only other spawner used to be {@code SimNPCSpawnSystem}, a tick system that waited 30
 * seconds and then added a single NPC every 10 seconds — reaching a village-sized group took
 * over two minutes of NPCs popping in one at a time, so the "arrive with a troop" moment never
 * read as one. That trickle spawner was removed; growing the population past this founding
 * group is now the player's call, via the {@code ImmigrationContract} item.
 */
public final class StartingTroop {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How many NPCs the world starts with. */
    private static final int TROOP_SIZE = 0;

    /** Ring around the player to place them on. Far enough not to crowd the spawn point. */
    private static final double MIN_RADIUS = 7.0;
    private static final double MAX_RADIUS = 12.0;

    /** How far up and down to look for solid ground under a candidate spot. */
    private static final int VERTICAL_SEARCH = 8;

    private StartingTroop() {
    }

    /**
     * Spawns the troop if this world has never had SimTale NPCs.
     *
     * @return how many were actually spawned
     */
    public static int spawnIfFreshWorld(Store<EntityStore> store, World world, Vector3d playerPos) {
        if (store == null || world == null || playerPos == null) {
            return 0;
        }

        /* Two independent checks. The database answers "has this world ever had NPCs", which
        survives a restart; ACTIVE_NPCS answers "are any loaded right now", which catches the
        case of a second player joining before anything has been saved.
        */
        if (!SimNPCPersistence.listAll().isEmpty() || !SimTale.ACTIVE_NPCS.isEmpty()) {
            return 0;
        }

        LOGGER.atInfo().log("SimTale: mundo novo detectado, spawnando tropa inicial de " + TROOP_SIZE + " NPCs.");

        int spawned = 0;
        for (int i = 0; i < TROOP_SIZE; i++) {
            /* Spread around a circle rather than picking at random, so the group arrives as a
            group instead of clumping on one side by chance.
            */
            double angle = (Math.PI * 2.0 / TROOP_SIZE) * i + Math.random() * 0.4;
            double radius = MIN_RADIUS + Math.random() * (MAX_RADIUS - MIN_RADIUS);

            Vector3d spot = findGround(world,
                    playerPos.x + Math.cos(angle) * radius,
                    playerPos.y,
                    playerPos.z + Math.sin(angle) * radius);

            if (spot == null) {
                LOGGER.atFine().log("SimTale: sem chao valido para o NPC " + (i + 1) + " da tropa, pulando.");
                continue;
            }

            /* Alternate so the founding group can actually pair off and have children later;
            an all-male or all-female start would dead-end the whole lifecycle system.
            */
            NPCType type = (i % 2 == 0) ? NPCType.HUMAN_MALE : NPCType.HUMAN_FEMALE;

            try {
                Ref<EntityStore> ref = SimNPCFactory.spawnNPC(store, spot, type);
                if (ref != null) {
                    spawned++;
                }
            } catch (Exception e) {
                /* Logged rather than swallowed: the old spawner hid exactly this behind an
                empty catch, so a failing spawn looked identical to a disabled feature.
                */
                LOGGER.atWarning().log("SimTale: falha ao spawnar NPC da tropa inicial: " + e);
            }
        }

        LOGGER.atInfo().log("SimTale: tropa inicial spawnada (" + spawned + "/" + TROOP_SIZE + ").");
        return spawned;
    }

    /**
     * Finds a standable block near the given column, searching outward from the player's own
     * height.
     * <p>
     * The old spawner just used {@code player.y + 5} and hoped, with a comment admitting it was
     * "a bit higher to avoid spawning in ground". On uneven terrain that drops NPCs inside a
     * hillside or off a cliff.
     *
     * @return the position to spawn at, or null when nothing suitable was found
     */
    private static Vector3d findGround(World world, double x, double startY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(startY);

        // Walk outward from the player's height: nearest valid ground wins, above or below.
        for (int offset = 0; offset <= VERTICAL_SEARCH; offset++) {
            for (int dir = 0; dir < 2; dir++) {
                int y = (dir == 0) ? by - offset : by + offset;
                if (offset == 0 && dir == 1) continue; // don't test the same block twice

                if (NPCMovementHelper.isStandable(new Vector3i(bx, y, bz), world)) {
                    // Centre of the block, nudged up so the NPC settles instead of clipping.
                    return new Vector3d(bx + 0.5, y + 0.1, bz + 0.5);
                }
            }
        }
        return null;
    }
}
