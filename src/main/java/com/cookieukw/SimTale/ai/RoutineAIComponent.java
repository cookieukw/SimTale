package com.cookieukw.SimTale.ai;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import com.hypixel.hytale.component.Ref;
import java.util.UUID;

public class RoutineAIComponent implements Component<EntityStore> {
    
    public enum TaskType {
        IDLE,
        FINDING_BED,
        MOVING_TO_BED,
        SLEEPING,
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
        MOVING_TO_WANDER,
        SOCIALIZING,
        MOVING_TO_SOCIALIZE
    }

    public TaskType currentTask = TaskType.IDLE;
    public Vector3i targetBlockPosition = null;
    public Ref<EntityStore> currentMoveTarget = null;
    public long taskStartTime = 0;
    
    // Death & Reaper fields
    public UUID dyingEntityId = null;
    public UUID reaperEntityId = null;
    public long reapTimer = 0;
    
    // Autonomy fields
    public UUID socializeTargetId = null;
    public long wanderTimer = 0;
    
    // Debug
    public boolean forcedByDebug = false;
    
    public RoutineAIComponent() {
    }

    @Override
    public RoutineAIComponent clone() {

        RoutineAIComponent comp = new RoutineAIComponent();
        comp.currentTask = this.currentTask;
        if (this.targetBlockPosition != null) {
            comp.targetBlockPosition = new Vector3i(this.targetBlockPosition);
        }
        comp.taskStartTime = this.taskStartTime;
        comp.dyingEntityId = this.dyingEntityId;
        comp.reaperEntityId = this.reaperEntityId;
        comp.reapTimer = this.reapTimer;
        comp.socializeTargetId = this.socializeTargetId;
        comp.wanderTimer = this.wanderTimer;
        return comp;
    }
}
