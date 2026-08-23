package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Decides when an NPC should be in bed.
 *
 * <p>Sleep used to be driven purely by exhaustion, so a villager with full energy would wander
 * around all night and the village never went quiet. Now the clock decides: everyone sleeps
 * through the night, and guards run the opposite shift.
 */
public final class NPCSleepHelper {

    private NPCSleepHelper() {
    }

    /**
     * Hytale's own day/night boundaries: sunrise around 06:00, dusk from 19:30 — the point at which
     * the game itself starts allowing beds to be used.
     *
     * <p>Written as hours rather than as fractions of a day because that is how the boundaries are
     * actually documented, and because the previous value (0.75, i.e. 18:00) was a guess that
     * happened to be an hour and a half early. Keeping the unit honest makes the next correction a
     * one-number edit instead of a conversion puzzle.
     */
    private static final float DAY_START_HOUR = 5.5f;
    private static final float NIGHT_START_HOUR = 19.5f;

    private static final float HOURS_PER_DAY = 24.0f;

    /** Day progress in 0..1, or null when the resource is unavailable. */
    private static Float dayProgress(World world) {
        if (world == null) return null;
        try {
            WorldTimeResource time = world.getEntityStore().getStore()
                    .getResource(WorldTimeResource.getResourceType());
            return time == null ? null : time.getDayProgress();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * In-game hour in 0..24, or null when the time resource cannot be read.
     *
     * <p>Exposed so {@code /simtale npcstate} can print it: whether the mod's idea of the hour
     * matches the sky is the one question that separates "the schedule is wrong" from "the clock is
     * being read wrong", and it is not answerable from the code alone.
     */
    public static Float currentHour(World world) {
        Float progress = dayProgress(world);
        return progress == null ? null : progress * HOURS_PER_DAY;
    }

    public static boolean isNight(World world) {
        Float hour = currentHour(world);
        if (hour == null) return false;
        return hour < DAY_START_HOUR || hour >= NIGHT_START_HOUR;
    }

    /** Guards hold the night watch, so their whole routine is inverted. */
    public static boolean isNightWatch(SimNPCComponent npc) {
        return npc != null && npc.profession == Profession.GUARD;
    }

    /**
     * True while this NPC's own sleeping window is open.
     *
     * <p>When the time of day cannot be read the answer is false, which degrades to the old
     * exhaustion-only behaviour rather than trapping everyone in bed.
     */
    public static boolean isSleepPeriod(SimNPCComponent npc, World world) {
        Float hour = currentHour(world);
        if (hour == null) return false;
        boolean night = hour < DAY_START_HOUR || hour >= NIGHT_START_HOUR;
        return isNightWatch(npc) ? !night : night;
    }
}
