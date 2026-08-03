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
     * Night spans the last quarter of one day and the first quarter of the next. Same split
     * {@code InteractionManager} already uses to pick night-time greetings, kept identical so the
     * dialogue and the routine never disagree about what time it is.
     */
    private static final float NIGHT_END = 0.25f;
    private static final float NIGHT_START = 0.75f;

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

    public static boolean isNight(World world) {
        Float progress = dayProgress(world);
        if (progress == null) return false;
        return progress < NIGHT_END || progress > NIGHT_START;
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
        Float progress = dayProgress(world);
        if (progress == null) return false;
        boolean night = progress < NIGHT_END || progress > NIGHT_START;
        return isNightWatch(npc) ? !night : night;
    }
}
