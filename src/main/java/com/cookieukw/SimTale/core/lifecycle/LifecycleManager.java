package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;

/**
 * Facade for the SimTale Lifecycle system.
 * To maintain backward compatibility without massively refactoring the codebase,
 * this class delegates calls to the specialized sub-managers.
 */
public class LifecycleManager {

    // Delegate state
    public static final List<GrowthComponent> ACTIVE_CHILDREN = LifecycleState.ACTIVE_CHILDREN;

    // Delegate PregnancyManager
    public static boolean startPregnancy(SimNPCComponent mother, UUID fatherId, long worldTick) {
        return PregnancyManager.startPregnancy(mother, fatherId, worldTick);
    }

    public static GrowthComponent birthBaby(SimNPCComponent mother, Store<EntityStore> store, long worldTick) {
        return PregnancyManager.birthBaby(mother, store, worldTick);
    }

    public static void applyPregnancyBehavior(SimNPCComponent mother) {
        PregnancyManager.applyPregnancyBehavior(mother);
    }

    public static void applyPregnancySpeedDebuff(Ref<EntityStore> entityRef, PregnancyComponent pregnancy) {
        PregnancyManager.applyPregnancySpeedDebuff(entityRef, pregnancy);
    }

    public static void applyPlayerPregnancyBehavior(Ref<EntityStore> playerRef, SimPlayerComponent playerComp) {
        PregnancyManager.applyPlayerPregnancyBehavior(playerRef, playerComp);
    }

    public static void birthPlayerBaby(Ref<EntityStore> playerRef, SimPlayerComponent playerComp, Store<EntityStore> store, long worldTick) {
        PregnancyManager.birthPlayerBaby(playerRef, playerComp, store, worldTick);
    }

    // Delegate GrowthManager
    public static float calculateTargetScale(GrowthComponent child, long worldTick) {
        return GrowthManager.calculateTargetScale(child, worldTick);
    }

    public static void applyVisualScale(Ref<EntityStore> ref, float scale) {
        GrowthManager.applyVisualScale(ref, scale);
    }

    public static void tickGrowth(GrowthComponent child, long worldTick) {
        GrowthManager.tickGrowth(child, worldTick);
    }

    public static float getScaleForStage(GrowthStage stage) {
        return GrowthManager.getScaleForStage(stage);
    }

    // Delegate MotherAIManager
    public static void tickMotherAI(SimNPCComponent mother, GrowthComponent baby, long worldTick) {
        MotherAIManager.tickMotherAI(mother, baby, worldTick);
    }

    // Delegate LifecycleState
    public static List<GrowthComponent> findChildrenOfMother(UUID motherId) {
        return LifecycleState.findChildrenOfMother(motherId);
    }

    public static List<GrowthComponent> findChildrenNeedingCare(UUID parentId) {
        return LifecycleState.findChildrenNeedingCare(parentId);
    }
}
