package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.NPCPreferences.Hobby;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

/**
 * Leisure routine: a bored NPC goes and does its {@link Hobby}, which is the only thing in
 * the mod that restores {@code Needs.fun}.
 * <p>
 * Before this existed, {@code fun} decayed every tick with no source of replenishment
 * anywhere in gameplay. Since {@code Needs.isMiserable()} reads it, every NPC eventually
 * settled into a permanent {@link Mood#SAD}. The {@code Hobby} rolled per NPC in
 * {@code NPCPreferences} was likewise only ever displayed, never acted upon.
 */
public final class NPCLeisureHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCLeisureHelper.class);

    private static final String ANIM_WALK = "Characters/Animations/Actions/Walk.blockyanim";
    private static final String ANIM_IDLE = "Characters/Animations/Actions/Idle.blockyanim";
    private static final String ANIM_SMITH = "Characters/Animations/Actions/Smith.blockyanim";

    /** Below this the NPC starts looking for something fun to do. */
    public static final float FUN_THRESHOLD = 40f;
    /** Retry cadence for the block scan, matching the bath search. */
    public static final int LEISURE_SEARCH_COOLDOWN_TICKS = 100;

    private static final int SEARCH_RADIUS = 15;
    private static final int SEARCH_HEIGHT = 5;
    private static final double REACH_DISTANCE_SQ = 2.0 * 2.0;
    private static final int HOBBY_DURATION_TICKS = 200;
    private static final int MOVE_TIMEOUT_TICKS = 600;
    /** Fun restored per tick while doing the hobby (200 ticks fills roughly half the bar). */
    private static final float FUN_PER_TICK = 0.25f;
    /** Hobbies done at home give a smaller boost than going out to the right spot. */
    private static final float FUN_PER_TICK_AT_HOME = 0.15f;

    private NPCLeisureHelper() {
    }

    public static void handleLeisureLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        handleFindingLeisure(ref, npc, ai, transform, world, store);
        handleMovingToLeisure(ref, ai, transform, world, store);
        handleDoingHobby(ref, npc, ai, world, store);
    }

    // ------------------------------------------------------------------
    // FINDING_LEISURE
    // ------------------------------------------------------------------

    private static void handleFindingLeisure(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.FINDING_LEISURE) {
            return;
        }
        if (world.getTick() - ai.taskStartTime < LEISURE_SEARCH_COOLDOWN_TICKS) {
            return;
        }
        ai.taskStartTime = world.getTick();

        Hobby hobby = hobbyOf(npc);
        String blockKeyword = blockKeywordFor(hobby);

        // READING and SLEEPING are "indoor" hobbies: no block to look for, the NPC just
        // settles down at home (or where it stands, if homeless).
        if (blockKeyword == null) {
            ai.targetBlockPosition = homeSpot(npc, transform);
            ai.currentTask = TaskType.MOVING_TO_LEISURE;
            NPCMovementHelper.playAnim(ref, ANIM_WALK, "Walk", store);
            return;
        }

        Vector3i found = scanForBlock(transform.getPosition(), world, blockKeyword);
        if (found != null) {
            ai.targetBlockPosition = found;
            ai.currentTask = TaskType.MOVING_TO_LEISURE;
            NPCMovementHelper.playAnim(ref, ANIM_WALK, "Walk", store);
            LOGGER.debug("[SimTale] NPC '{}' found a spot for {} at {}", npc.name, hobby, found);
        } else {
            // Nothing suitable nearby — fall back to relaxing at home instead of standing
            // around bored forever.
            ai.targetBlockPosition = homeSpot(npc, transform);
            ai.currentTask = TaskType.MOVING_TO_LEISURE;
            NPCMovementHelper.playAnim(ref, ANIM_WALK, "Walk", store);
        }
    }

    // ------------------------------------------------------------------
    // MOVING_TO_LEISURE
    // ------------------------------------------------------------------

    private static void handleMovingToLeisure(
            Ref<EntityStore> ref,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.MOVING_TO_LEISURE) {
            return;
        }
        if (ai.targetBlockPosition == null) {
            stop(ref, ai, store);
            return;
        }

        // Unreachable destination must not strand the NPC.
        if (ai.taskStartTime > 0 && world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
            stop(ref, ai, store);
            return;
        }

        Vector3d pos = transform.getPosition();
        Vector3i target = ai.targetBlockPosition;
        double dx = (target.x + 0.5) - pos.x;
        double dz = (target.z + 0.5) - pos.z;

        if (dx * dx + dz * dz < REACH_DISTANCE_SQ) {
            NPCMovementHelper.clearMoveTarget(ref, ai);
            ai.currentTask = TaskType.DOING_HOBBY;
            ai.taskStartTime = world.getTick();
        } else {
            NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(target.x + 0.5, pos.y, target.z + 0.5));
        }
    }

    // ------------------------------------------------------------------
    // DOING_HOBBY
    // ------------------------------------------------------------------

    private static void handleDoingHobby(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            World world,
            Store<EntityStore> store
    ) {
        if (ai.currentTask != TaskType.DOING_HOBBY) {
            return;
        }

        Hobby hobby = hobbyOf(npc);
        long elapsed = world.getTick() - ai.taskStartTime;

        if (elapsed == 1) {
            NPCMovementHelper.playAnim(ref, animationFor(hobby), animationNameFor(hobby), store);
        }

        boolean atProperSpot = blockKeywordFor(hobby) != null;


        NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) + (atProperSpot ? FUN_PER_TICK : FUN_PER_TICK_AT_HOME));

        if (NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) >= 100f || elapsed >= HOBBY_DURATION_TICKS) {
            npc.setEmotion(Mood.HAPPY, 0.5f, "hobby", world.getTick());
            LOGGER.debug("[SimTale] NPC '{}' finished {} (fun={})", npc.name, hobby, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID));
            stop(ref, ai, store);
        }
    }

    // ------------------------------------------------------------------
    // Hobby mapping
    // ------------------------------------------------------------------

    /** Never null — NPCs loaded from older saves may have no preferences yet. */
    public static Hobby hobbyOf(SimNPCComponent npc) {
        if (npc.preferences == null || npc.preferences.getHobby() == null) {
            return Hobby.READING;
        }
        return npc.preferences.getHobby();
    }

    /**
     * Block substring the NPC walks to for this hobby, or {@code null} for hobbies performed
     * at home with no scenery requirement.
     */
    private static String blockKeywordFor(Hobby hobby) {
        return switch (hobby) {
            case FISHING -> "water";
            case MINING -> "stone";
            case GARDENING -> "crop";
            case READING, SLEEPING -> null;
        };
    }

    private static String animationFor(Hobby hobby) {
        return switch (hobby) {
            case MINING, GARDENING -> ANIM_SMITH;
            case FISHING, READING, SLEEPING -> ANIM_IDLE;
        };
    }

    private static String animationNameFor(Hobby hobby) {
        return switch (hobby) {
            case MINING, GARDENING -> "Smith";
            case FISHING, READING, SLEEPING -> "Idle";
        };
    }

    /**
     * Professions whose daily work already scratches the same itch as the hobby. Used for the
     * mood bonus when an NPC works a job it genuinely enjoys.
     */
    public static boolean matchesProfession(Hobby hobby, Profession profession) {
        if (hobby == null || profession == null) {
            return false;
        }
        return switch (hobby) {
            case FISHING -> profession == Profession.FISHERMAN;
            case MINING -> profession == Profession.MINER;
            case GARDENING -> profession == Profession.FARMER;
            case READING, SLEEPING -> false;
        };
    }

    /** True when a gifted item lines up with what this NPC does for fun. */
    public static boolean isHobbyItem(Hobby hobby, String itemIdLower) {
        if (hobby == null || itemIdLower == null) {
            return false;
        }
        return switch (hobby) {
            case FISHING -> itemIdLower.contains("fishing") || itemIdLower.contains("fish")
                    || itemIdLower.contains("rod") || itemIdLower.contains("bait");
            // "ore" is deliberately not matched bare: it would hit "store", "forest", "more"...
            case MINING -> itemIdLower.contains("pickaxe") || itemIdLower.contains("_ore")
                    || itemIdLower.endsWith("ore") || itemIdLower.contains("gem")
                    || itemIdLower.contains("lantern");
            case GARDENING -> itemIdLower.contains("seed") || itemIdLower.contains("sapling")
                    || itemIdLower.contains("flower") || itemIdLower.contains("hoe");
            case READING -> itemIdLower.contains("book") || itemIdLower.contains("scroll")
                    || itemIdLower.contains("paper") || itemIdLower.contains("quill");
            case SLEEPING -> itemIdLower.contains("pillow") || itemIdLower.contains("blanket")
                    || itemIdLower.contains("bed");
        };
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** The NPC's bed, or its current position when it has no home yet. */
    private static Vector3i homeSpot(SimNPCComponent npc, TransformComponent transform) {
        if (npc.bedLocation != null) {
            return new Vector3i(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
        }
        Vector3d pos = transform.getPosition();
        return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
    }

    /**
     * Scans loaded chunks around the NPC for a block whose id contains {@code keyword}.
     * <p>
     * Mirrors the bath search: a single reusable cursor and an allocation-free id comparison,
     * and {@code getChunkIfInMemory} so the scan never forces a chunk load from the tick loop.
     */
    private static Vector3i scanForBlock(Vector3d from, World world, String keyword) {
        int sx = (int) from.x;
        int sy = (int) from.y;
        int sz = (int) from.z;
        Vector3i cursor = new Vector3i();

        for (int cx = (sx - SEARCH_RADIUS) >> 4; cx <= (sx + SEARCH_RADIUS) >> 4; cx++) {
            for (int cz = (sz - SEARCH_RADIUS) >> 4; cz <= (sz + SEARCH_RADIUS) >> 4; cz++) {
                WorldChunk chunkAt = world.getChunkIfInMemory(ChunkUtil.indexChunk(cx, cz));
                if (chunkAt == null) continue;

                int minX = Math.max(sx - SEARCH_RADIUS, cx << 4);
                int maxX = Math.min(sx + SEARCH_RADIUS, (cx << 4) + 15);
                int minZ = Math.max(sz - SEARCH_RADIUS, cz << 4);
                int maxZ = Math.min(sz + SEARCH_RADIUS, (cz << 4) + 15);

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = sy - SEARCH_HEIGHT; y <= sy + SEARCH_HEIGHT; y++) {
                            BlockType bType = chunkAt.getBlockType(cursor.set(x, y, z));
                            if (bType == null) continue;
                            if (containsIgnoreCase(bType.getId(), keyword)) {
                                return new Vector3i(x, y, z);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null) return false;
        int limit = haystack.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    private static void stop(Ref<EntityStore> ref, RoutineAIComponent ai, Store<EntityStore> store) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.targetBlockPosition = null;
        ai.currentTask = TaskType.IDLE;
        ai.taskStartTime = 0;
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
    }
}
