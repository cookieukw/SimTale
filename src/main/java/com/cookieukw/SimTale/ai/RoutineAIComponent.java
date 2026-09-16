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
        FIGHTING,
        FINDING_CHAIR,
        MOVING_TO_CHAIR,
        SITTING,
        TAG_CHASING,
        TAG_FLEEING,
        MOVING_TO_HIDE,
        HIDING,
        SEEKING
    }

    public TaskType currentTask = TaskType.IDLE;
    public Vector3i targetBlockPosition = null;
    public Vector3i targetChairPos = null;
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
    /** Reserved when another NPC has chosen this NPC for socialization. */
    public UUID reservedForSocialUuid = null;
    /** Tick of last player proximity greeting to prevent spam. */
    public long lastPlayerGreetingTick = 0;
    /** Ticking counter during socialization to alternate talk turns. */
    public long socialTalkTimer = 0;
    /** Current conversation topic index. */
    public int socialTopic = 0;
    /**
     * True for the NPC that started the conversation. Only the host applies the social,
     * relationship and mood rewards, so a chat is not counted twice.
     */
    public boolean socializeHost = false;
    /** Tick at which the current wander destination is abandoned. 0 = not started. */
    public long wanderTimer = 0;

    /**
     * Where a Guard is around the village perimeter, in radians.
     *
     * <p>Kept per guard and advanced by a fixed step each leg, which is what turns a series of
     * independent destinations into a circuit that reads as patrolling. Randomising the angle
     * instead would look like ordinary wandering that happens to stay near the edge.
     */
    public double patrolAngle = 0;

    // Child play (tag / hide-and-seek) fields
    /** Who this child is currently playing tag or hide-and-seek with. */
    public UUID playPartnerId = null;
    /** Tags/finds remaining before the current game winds down on its own. */
    public int playRoundsLeft = 0;
    /** Same guard as {@link #nextBathSearchTick} et al., for the child-play search. */
    public long nextPlaySearchTick = 0;
    
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
     * Same guard as {@link #nextFoodSearchTick}, for the bath search.
     * <p>
     * This one was missing entirely, and its absence froze NPCs solid. The IDLE branch routes a
     * dirty NPC into FINDING_BATH while deliberately backdating taskStartTime to skip the search
     * cooldown; the search then sweeps ~10.500 blocks and, finding no water, drops straight back
     * to IDLE without recording anything. The next tick repeats it, forever. Because the IDLE
     * checks are one else-if chain, the NPC also never reached the socialise and wander branches
     * below — it just stood still, silently, for as long as it stayed dirty.
     */
    public long nextBathSearchTick = 0;
    public long nextChairSearchTick = 0;

    /**
     * Tick the NPC last got out of bed. Zero means "never slept in this session".
     * <p>
     * Replaces the energy cap that used to guard the scheduled sleep. That cap was asking "did this
     * one just wake up?" through a proxy that does not hold: sleeping refills energy to 100 and it
     * drains at 0.0002/tick, so getting back under the 90 threshold took roughly forty real minutes
     * — far longer than a full day/night cycle. After their first night everybody sat permanently
     * above the cap and the whole village stopped sleeping, which is the exact problem the schedule
     * was added to solve. Recording the wake time answers the question directly and stops the sleep
     * schedule from depending on how the needs happen to be tuned.
     */
    public long lastWakeTick = 0;

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
        if (this.targetChairPos != null) {
            comp.targetChairPos = new Vector3i(this.targetChairPos);
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
        comp.reservedForSocialUuid = this.reservedForSocialUuid;
        comp.lastPlayerGreetingTick = this.lastPlayerGreetingTick;
        comp.socialTalkTimer = this.socialTalkTimer;
        comp.socialTopic = this.socialTopic;
        comp.socializeHost = this.socializeHost;
        comp.wanderTimer = this.wanderTimer;
        comp.patrolAngle = this.patrolAngle;
        comp.playPartnerId = this.playPartnerId;
        comp.playRoundsLeft = this.playRoundsLeft;
        comp.nextPlaySearchTick = this.nextPlaySearchTick;
        comp.lastLeashPos = this.lastLeashPos;
        comp.lastLeashTick = this.lastLeashTick;
        comp.forcedByDebug = this.forcedByDebug;
        comp.nextBedSearchTick = this.nextBedSearchTick;
        comp.nextFoodSearchTick = this.nextFoodSearchTick;
        comp.nextBathSearchTick = this.nextBathSearchTick;
        comp.nextChairSearchTick = this.nextChairSearchTick;
        comp.lastWakeTick = this.lastWakeTick;
        comp.sleepingOnSchedule = this.sleepingOnSchedule;
        comp.eatingTier = this.eatingTier;
        comp.eatingWasHated = this.eatingWasHated;
        comp.eatingWasFavorite = this.eatingWasFavorite;
        return comp;
    }
}
