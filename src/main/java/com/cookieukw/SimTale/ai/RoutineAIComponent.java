package com.cookieukw.SimTale.ai;

import com.hypixel.hytale.component.Component;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.UUID;

public class RoutineAIComponent implements Component<EntityStore> {
    
    public enum TaskType {
        IDLE,
        FINDING_BED,
        MOVING_TO_BED,
        ENTERING_BED,
        SLEEPING,
        WAKING,
        FINDING_FOOD,
        MOVING_TO_FOOD,
        EATING,
        FINDING_BATH,
        MOVING_TO_BATH,
        BATHING,
        DYING,
        DEAD,
        REAPING,
        FINDING_CONSTRUCTION,
        MOVING_TO_CONSTRUCTION,
        BUILDING,
        WANDERING,
        FINDING_LEISURE,
        MOVING_TO_LEISURE,
        DOING_HOBBY,
        SOCIALIZING,
        MOVING_TO_SOCIALIZE,
        MOVING_TO_WORK,
        FARMING,
        HUNTING,
        FISHING,
        CHOPPING,
        EXPEDITION,
        MOVING_TO_DEPOSIT,
        MOVING_TO_SEEDS,
        PLANTING,
        MOVING_TO_FIGHT,
        FIGHTING
    }

    public TaskType currentTask = TaskType.IDLE;
    public Vector3i targetBlockPosition = null;
    /** Marker position of a claimed work post (fishing, and future lumberjack/farmer posts),
     *  released in {@code NPCWorkHelper.abandonTask} or on completing the task. Distinct from
     *  {@code targetBlockPosition}, which for fishing holds the water tile, not the post itself. */
    public Vector3i claimedWorkPost = null;
    public long taskStartTime = 0;
    public Vector3d lastLeashPos = null;
    public long lastLeashTick = 0;
    // Death & Reaper fields
    public UUID dyingEntityId = null;
    public UUID reaperEntityId = null;
    public long reapTimer = 0;
    public UUID workTargetEntityId = null;
    
    // Autonomy fields
    public UUID socializeTargetId = null;
    /**
     * True for the NPC that started the conversation. Only the host applies the social,
     * relationship and mood rewards, so a chat is not counted twice.
     */
    public boolean socializeHost = false;
    /** Tick at which the current wander destination is abandoned. 0 = not started. */
    public long wanderTimer = 0;
    
    // Debug
    public boolean forcedByDebug = false;

    /**
     * Tick a partir do qual vale a pena procurar cama de novo.
     * <p>
     * Existe porque a interrupcao de cansaco zerava {@code taskStartTime} para "bypass cooldown".
     * Quando a cama encontrada nao podia ser reivindicada, a tarefa voltava para IDLE e no tick
     * seguinte a interrupcao disparava de novo — 30 buscas por segundo, sempre da mesma cama.
     * Uma sessao registrou 3447 rejeicoes seguidas em poucos segundos, com a NPC parada.
     * <p>
     * Este campo e independente de {@code taskStartTime} justamente para sobreviver as trocas de
     * tarefa: e ele que a interrupcao consulta.
     */
    public long nextBedSearchTick = 0;

    /**
     * Same guard as {@link #nextBedSearchTick}, for food.
     * <p>
     * The hunger interrupt fires from any task, so without a cooldown an NPC with no reachable
     * food would re-enter FINDING_FOOD every single tick — the exact storm the bed search hit,
     * which produced thousands of failed searches per second.
     */
    public long nextFoodSearchTick = 0;

    /**
     * True when the NPC went to bed because its sleeping window opened, not because it was
     * exhausted.
     * <p>
     * The two cases wake on different conditions: a scheduled sleeper stays down until its window
     * closes, while an exhaustion nap ends as soon as energy is full. Without this flag a villager
     * would pop out of bed in the middle of the night the moment energy hit 100.
     */
    public boolean sleepingOnSchedule = false;

    /** Set when the food is taken from the chest, consumed when the EATING state finishes. */
    public int eatingTier = 0;
    public boolean eatingWasHated = false;
    public boolean eatingWasFavorite = false;
    
    public RoutineAIComponent() {
    }

    @Override
    public RoutineAIComponent clone() {
        RoutineAIComponent comp = new RoutineAIComponent();
        comp.currentTask = this.currentTask;
        if (this.targetBlockPosition != null) {
            comp.targetBlockPosition = new Vector3i(this.targetBlockPosition);
        }
        if (this.claimedWorkPost != null) {
            comp.claimedWorkPost = new Vector3i(this.claimedWorkPost);
        }
        comp.taskStartTime = this.taskStartTime;
        comp.dyingEntityId = this.dyingEntityId;
        comp.reaperEntityId = this.reaperEntityId;
        comp.reapTimer = this.reapTimer;
        comp.workTargetEntityId = this.workTargetEntityId;
        comp.socializeTargetId = this.socializeTargetId;
        comp.socializeHost = this.socializeHost;
        comp.wanderTimer = this.wanderTimer;
        comp.lastLeashPos = this.lastLeashPos;
        comp.lastLeashTick = this.lastLeashTick;
        comp.forcedByDebug = this.forcedByDebug;
        comp.nextBedSearchTick = this.nextBedSearchTick;
        comp.nextFoodSearchTick = this.nextFoodSearchTick;
        comp.sleepingOnSchedule = this.sleepingOnSchedule;
        comp.eatingTier = this.eatingTier;
        comp.eatingWasHated = this.eatingWasHated;
        comp.eatingWasFavorite = this.eatingWasFavorite;
        return comp;
    }
}
