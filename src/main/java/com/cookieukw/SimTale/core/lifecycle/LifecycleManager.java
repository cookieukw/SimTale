package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Personality;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.Child;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Central orchestrator of the pregnancy and life cycle system.
 *
 * Responsible for:
 * - Starting pregnancy
 * - Performing birth (child spawn)
 * - Updating growth
 * - Applying genetics
 * - Converting adult child into independent NPC
 *
 * Many functions are stubs for future implementation.
 */
public class LifecycleManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final List<GrowthComponent> ACTIVE_CHILDREN = new ArrayList<>();

    /**
     * Try to start a pregnancy in a female NPC.
     *
     * @param mother     mother component (must be FEMALE)
     * @param fatherId   UUID of the father
     * @param worldTick  tick actual of the world
     * @return true if the pregnancy was started successfully
     */
    public static boolean startPregnancy(SimNPCComponent mother, UUID fatherId, long worldTick) {
        if (mother.gender != Gender.FEMALE) {
            LOGGER.atWarning().log("SimTale: Tentativa de gravidez em NPC não-feminino: " + mother.name);
            return false;
        }

        if (mother.pregnancy != null && mother.pregnancy.pregnant) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " já está grávida.");
            return false;
        }

        // Check if she is married to the father
        if (!mother.family.isMarried || !fatherId.equals(mother.family.spouseId)) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " não é casada com o pai.");
            return false;
        }

        // Minimum romance check
        Relationship rel = mother.getRelationship(fatherId);
        if (rel.romance < 50) {
            LOGGER.atInfo().log("SimTale: Romance insuficiente para gravidez (" + rel.romance + "/50)");
            return false;
        }

        if (mother.pregnancy == null) {
            mother.pregnancy = new PregnancyComponent();
        }
        mother.pregnancy.start(fatherId, worldTick);

        LOGGER.atInfo().log("SimTale: " + mother.name + " está grávida! Pai: " + fatherId);
        return true;
    }

    /**
     * Perform the birth of a baby.
     * Called by the PregnancyTickSystem when the pregnancy expires.
     *
     * @param mother    mother component
     * @param store     store of entities
     * @param world     world atual
     * @param worldTick tick atual
     * @return the GrowthComponent of the created child, or null if failed
     */
    public static GrowthComponent birthBaby(SimNPCComponent mother, Store<EntityStore> store,
                                             World world, long worldTick) {
        if (mother.pregnancy == null || !mother.pregnancy.pregnant) {
            return null;
        }

        UUID fatherId = mother.pregnancy.fatherId;

        SimNPCComponent father = findNPCById(fatherId);
        GeneticsData motherGenetics = getOrCreateGenetics(mother);
        GeneticsData fatherGenetics = father != null ? getOrCreateGenetics(father) : new GeneticsData();

        GeneticsData childGenetics = GeneticsData.combine(motherGenetics, fatherGenetics);

        Gender childGender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;

        String childFirstName = SimNPCNameGenerator.generate();
        if (childFirstName.contains(" ")) {
            childFirstName = childFirstName.substring(0, childFirstName.indexOf(' '));
        }

        String fatherName = father != null ? father.name : "";
        String childSurname = GeneticsData.inheritSurname(mother.name, fatherName);

        GrowthComponent child = new GrowthComponent(
            mother.entityId,
            fatherId,
            worldTick,
            childGender,
            childGenetics,
            childFirstName,
            childSurname
        );

        SimNPCFactory.NPCType childType = childGender == Gender.MALE
            ? SimNPCFactory.NPCType.CHILD_MALE
            : SimNPCFactory.NPCType.CHILD_FEMALE;

        try {
            // Position: next to mother
            Vector3d spawnPos = getEntityPosition(mother, store);
            if (spawnPos == null) {
                spawnPos = new Vector3d(0, 64, 0); // fallback
            }

            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);
            if (childRef != null) {
                com.hypixel.hytale.server.core.entity.UUIDComponent uuidComp = 
                    store.getComponent(childRef, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                if (uuidComp != null) {
                    child.childId = uuidComp.getUuid();
                }
                
                SimNPCComponent childNPCComp = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                if (childNPCComp != null) {
                    childNPCComp.name = child.getFullName();
                    store.putComponent(childRef, com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName.getComponentType(), 
                        new com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName(com.hypixel.hytale.server.core.Message.raw(child.getFullName())));
                    store.putComponent(childRef, com.hypixel.hytale.server.core.entity.nameplate.Nameplate.getComponentType(), 
                        new com.hypixel.hytale.server.core.entity.nameplate.Nameplate(child.getFullName()));
                }
            }

            Child familyChild = new Child(child.getFullName());
            mother.family.children.add(familyChild);

            if (father != null) {
                Child fatherFamilyChild = new Child(child.getFullName());
                father.family.children.add(fatherFamilyChild);
            }

            child.pickUp(mother.entityId);

            ACTIVE_CHILDREN.add(child);

            LOGGER.atInfo().log("SimTale: Nasceu " + child.getFullName() + " ("
                + childGender.getDisplayName() + ") — filho(a) de " + mother.name);

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao spawnar bebê: " + e.getMessage());
            return null;
        }

        mother.pregnancy.reset();

        return child;
    }

    /**
     * Growth tick. Called by the GrowthTickSystem for each active child.
     *
     * @param child     growth component
     * @param worldTick current tick
     */
    public static void tickGrowth(GrowthComponent child, long worldTick) {
        // Update stage
        boolean stageChanged = child.updateStage(worldTick);

        if (stageChanged) {
            LOGGER.atInfo().log("SimTale: " + child.getFullName() + " grew to "
                + child.stage.getDisplayName() + " (scale: " + child.currentScale + ")");

            // TODO: Update model/visual scale of the entity
            onStageChanged(child);
        }

        // If still needs care, decay needs
        if (child.needsCare() && child.babyNeeds != null) {
            child.babyNeeds.tickDecay();
        }
    }

    /**
     * Callback when growth stage changes.
     * Stub — will be implemented to change model/scale.
     */
    private static void onStageChanged(GrowthComponent child) {
        // TODO: Update PersistentModel with new scale
        // TODO: Change model to suitable version for the stage
        // TODO: If reached ADULT, convert into independent NPC
        if (child.isAdult()) {
            onBecameAdult(child);
        }
    }

    /**
     * Callback when the child becomes an adult.
     * Stub — will be implemented to convert into independent NPC.
     */
    private static void onBecameAdult(GrowthComponent child) {
        LOGGER.atInfo().log("SimTale: " + child.getFullName() + " se tornou adulto!");
        // TODO: Convert the "child" entity into an independent adult NPC
        // TODO: Generate personality based on experiences (babyNeeds.getPersonalityTendency())
        // TODO: Remove from ACTIVE_CHILDREN list
        // TODO: Apply adult model
    }

    /**
     * Get the visual scale for a given stage.
     */
    public static float getScaleForStage(GrowthStage stage) {
        return stage.getScale();
    }

    /**
     * Apply modifications of behavior during pregnancy.
     * Stub — will be implemented in the future.
     *
     * Planned behaviors:
     * - Sleeps more
     * - Eats more
     * - Walks slower
     * - Animation holding belly
     * - Different dialogue
     * - Avoids combat
     */
    public static void applyPregnancyBehavior(SimNPCComponent mother) {
        // TODO: Implement pregnancy behavior
    }

    /**
     * Get the speed multiplier during pregnancy.
     * Stub — returns 1.0 for now.
     */
    public static float getPregnancySpeedMultiplier(PregnancyComponent pregnancy, long currentTick) {
        if (pregnancy == null || !pregnancy.pregnant) return 1.0f;
        // Slower as pregnancy advances
        // TODO: Implement real curve
        return 1.0f;
    }

    /**
     * Mother AI logic for caring for the baby.
     * Stub — will be implemented in the future.
     *
     * Planned behaviors:
     * - If baby is far → go to baby
     * - If baby is on the ground → pick up baby
     * - If baby is crying → comfort
     * - If night → sleep with baby
     * - If player holding → wait
     */
    public static void tickMotherAI(SimNPCComponent mother, GrowthComponent baby, long worldTick) {
        // TODO: Implement mother AI
    }

  
    /**
     * Find an NPC in the active list by UUID.
     */
    private static SimNPCComponent findNPCById(UUID id) {
        if (id == null) return null;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (id.equals(npc.entityId)) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Get or create genetic data for an existing NPC.
     * Necessary because adult NPCs didn't have genetics before this system.
     */
    private static GeneticsData getOrCreateGenetics(SimNPCComponent npc) {
        // Old NPCs don't have genetic data — we create based on entityId as seed
        if (npc.entityId != null) {
            return new GeneticsData(npc.entityId.getMostSignificantBits(), npc.entityId.getLeastSignificantBits());
        }
        return new GeneticsData();
    }

    /**
     * Get the position of an NPC in the world.
     */
    private static Vector3d getEntityPosition(SimNPCComponent npc, Store<EntityStore> store) {
        if (npc.entityRef == null) return null;
        try {
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                store.getComponent(npc.entityRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Find active children by mother's UUID.
     */
    public static List<GrowthComponent> findChildrenOfMother(UUID motherId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (motherId.equals(child.motherId)) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Find children who need care (BABY or TODDLER).
     */
    public static List<GrowthComponent> findChildrenNeedingCare(UUID parentId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (child.needsCare() && (parentId.equals(child.motherId) || parentId.equals(child.fatherId))) {
                result.add(child);
            }
        }
        return result;
    }
}
