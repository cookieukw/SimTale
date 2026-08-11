package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The Inspector's Journal item: right-click an NPC to read her needs, mood and current task.
 *
 * <p>The player-facing half of {@code /simtale npcstate}. That command dumps role state, animation
 * slots, movement flags and search cooldowns — the things you want when the AI is misbehaving, and
 * noise when you just want to know whether someone is hungry. This reports the five needs, the
 * mood, the job and what she is doing, and nothing about the engine underneath.
 */
public final class InspectorJournalHelper {

    private InspectorJournalHelper() {
    }

    /**
     * @param target the entity that was right-clicked, or null when the click hit nothing
     * @return true when the click was consumed
     */
    public static boolean inspect(World world, PlayerRef playerRef, Ref<EntityStore> target) {
        if (world == null || playerRef == null) return false;

        if (target == null || !target.isValid()) {
            playerRef.sendMessage(Message.translation("general.journal.no_target"));
            return true;
        }

        Store<EntityStore> store = target.getStore();
        SimNPCComponent npc = store.getComponent(target, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (npc == null) {
            playerRef.sendMessage(Message.translation("general.journal.not_npc"));
            return true;
        }

        playerRef.sendMessage(Message.translation("general.journal.header").param("name", npc.name));

        playerRef.sendMessage(Message.translation("general.journal.needs")
                .param("hunger", round(NeedsHelper.getNeed(store, target, NeedsHelper.HUNGER_ID)))
                .param("energy", round(NeedsHelper.getNeed(store, target, NeedsHelper.ENERGY_ID)))
                .param("hygiene", round(NeedsHelper.getNeed(store, target, NeedsHelper.HYGIENE_ID)))
                .param("fun", round(NeedsHelper.getNeed(store, target, NeedsHelper.FUN_ID)))
                .param("social", round(NeedsHelper.getNeed(store, target, NeedsHelper.SOCIAL_ID))));

        Message mood = npc.getMood() != null
                ? Message.translation("ui.mood." + npc.getMood().name().toLowerCase())
                : Message.translation("ui.mood.neutral");
        Message profession = npc.profession != null
                ? Message.translation("ui.prof." + npc.profession.name().toLowerCase())
                : Message.translation("ui.prof.unemployed");

        playerRef.sendMessage(Message.translation("general.journal.mood").insert(mood));
        playerRef.sendMessage(Message.translation("general.journal.profession").insert(profession));

        // The task is reported in words the player already knows from the rest of the UI rather
        // than as the raw TaskType constant, which is an implementation detail.
        RoutineAIComponent ai = store.getComponent(target, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        playerRef.sendMessage(Message.translation("general.journal.doing")
                .insert(describeTask(ai)));

        if (npc.bedLocation != null) {
            playerRef.sendMessage(Message.translation("general.journal.home")
                    .param("x", npc.bedLocation.x)
                    .param("y", npc.bedLocation.y)
                    .param("z", npc.bedLocation.z));
        } else {
            playerRef.sendMessage(Message.translation("general.journal.homeless"));
        }

        return true;
    }

    /**
     * Groups the many internal task states into the handful a player cares about.
     * <p>
     * MOVING_TO_FOOD, FINDING_FOOD and EATING are three states and one answer: she is dealing with
     * being hungry.
     */
    private static Message describeTask(RoutineAIComponent ai) {
        if (ai == null || ai.currentTask == null) {
            return Message.translation("general.journal.task.idle");
        }
        return switch (ai.currentTask) {
            case FINDING_FOOD, MOVING_TO_FOOD, EATING -> Message.translation("general.journal.task.eating");
            case FINDING_BED, MOVING_TO_BED, ENTERING_BED, SLEEPING, WAKING ->
                    Message.translation("general.journal.task.sleeping");
            case FINDING_BATH, MOVING_TO_BATH, BATHING -> Message.translation("general.journal.task.bathing");
            case FINDING_LEISURE, MOVING_TO_LEISURE, DOING_HOBBY ->
                    Message.translation("general.journal.task.leisure");
            case MOVING_TO_SOCIALIZE, SOCIALIZING -> Message.translation("general.journal.task.socializing");
            case WANDERING -> Message.translation("general.journal.task.wandering");
            case EXPEDITION -> Message.translation("general.journal.task.away");
            case DYING, DEAD, REAPING -> Message.translation("general.journal.task.dying");
            case FINDING_CONSTRUCTION, MOVING_TO_CONSTRUCTION, BUILDING ->
                    Message.translation("general.journal.task.building");
            case IDLE -> Message.translation("general.journal.task.idle");
            default -> Message.translation("general.journal.task.working");
        };
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value));
    }
}
