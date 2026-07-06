package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookie.runecore.api.EffectHelper;
import com.cookie.runecore.api.StatHelper;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.cookie.caskara.Caskara;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.codec.Codec;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Central orchestrator of the pregnancy and life cycle system.
 * Responsible for:
 * - Starting pregnancy
 * - Performing birth (child spawn)
 * - Updating growth
 * - Applying genetics
 * - Converting adult child into independent NPC
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

        // Only adult NPCs can get pregnant
        if (mother.entityRef != null) {
            NPCEntity npcEntity = mother.entityRef.getStore().getComponent(mother.entityRef, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null && npcEntity.getRoleName() != null && npcEntity.getRoleName().toLowerCase().contains("child")) {
                LOGGER.atWarning().log("SimTale: Gravidez cancelada. A NPC " + mother.name + " e uma criança!");
                return false;
            }
        }

        // Also validates if registered in the active children list and has not yet reached adulthood
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (mother.entityId != null && mother.entityId.equals(child.childId) && !child.isAdult()) {
                LOGGER.atWarning().log("SimTale: Gravidez cancelada. A NPC " + mother.name + " e um filho em crescimento!");
                return false;
            }
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
     * @param worldTick tick atual
     * @return the GrowthComponent of the created child, or null if failed
     */
    public static GrowthComponent birthBaby(SimNPCComponent mother, Store<EntityStore> store,
                                            long worldTick) {
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
            UUIDComponent uuidComp =
                    store.getComponent(childRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                child.childId = uuidComp.getUuid();
            }

            SimNPCComponent childNPCComp = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (childNPCComp != null) {
                childNPCComp.name = child.getFullName();
                store.putComponent(childRef, PersistentDisplayName.getComponentType(),
                    new PersistentDisplayName(Message.raw(child.getFullName())));
                store.putComponent(childRef, Nameplate.getComponentType(),
                    new Nameplate(child.getFullName()));
            }

            Child familyChild = new Child(child.getFullName());
            mother.family.children.add(familyChild);

            if (father != null) {
                Child fatherFamilyChild = new Child(child.getFullName());
                father.family.children.add(fatherFamilyChild);
            }

            child.pickUp(mother.entityId);

            ACTIVE_CHILDREN.add(child);
            Caskara.save("child_" + child.childId.toString(), child);
            BabyCareManager.initializeForChild(child);

            LOGGER.atInfo().log("SimTale: Nasceu " + child.getFullName() + " ("
                + childGender.getDisplayName() + ") — filho(a) de " + mother.name);

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao spawnar bebê: " + e.getMessage());
            return null;
        }

        mother.pregnancy.reset();

        if (mother.entityRef != null) {
            EffectHelper.modifyMovement(mother.entityRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
            StatHelper.subtractHealth(mother.entityRef, 50.0f);
        }

        com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(mother);

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
     * Planned behaviors:
     * - Sleeps more
     * - Eats more
     * - Walks slower
     * - Animation holding belly
     * - Different dialogue
     * - Avoids combat
     */
    public static void applyPregnancyBehavior(SimNPCComponent mother) {
        if (mother.pregnancy == null || !mother.pregnancy.pregnant) return;
        float mult = 1.0f + (mother.pregnancy.trimester * 0.3f);
        // Extra decay per tick
        mother.needs.hunger = Math.max(0, mother.needs.hunger - 0.0001f * (mult - 1.0f));
        mother.needs.energy = Math.max(0, mother.needs.energy - 0.0002f * (mult - 1.0f));
    }

    public static void applyPlayerPregnancyBehavior(Ref<EntityStore> playerRef, SimPlayerComponent playerComp) {
        // Player pregnancy ticking behavior (can be extended for hunger/energy if SimTale player needs exist)
    }

    public static void applyPregnancySpeedDebuff(Ref<EntityStore> entityRef, PregnancyComponent pregnancy) {
        if (entityRef == null || pregnancy == null) return;
        if (!pregnancy.pregnant) {
            EffectHelper.modifyMovement(entityRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
            return;
        }
        if (pregnancy.trimester == 2) {
            EffectHelper.modifyMovement(entityRef, s -> s.baseSpeed = Math.max(1.0f, s.baseSpeed - 1.5f));
        } else if (pregnancy.trimester == 3) {
            EffectHelper.modifyMovement(entityRef, s -> s.baseSpeed = Math.max(1.0f, s.baseSpeed - 3.0f));
        } else {
            EffectHelper.modifyMovement(entityRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
        }
    }

    public static void birthPlayerBaby(Ref<EntityStore> playerRef, SimPlayerComponent playerComp, Store<EntityStore> store, long worldTick) {
        if (playerComp.pregnancy == null || !playerComp.pregnancy.pregnant) return;

        UUID fatherId = playerComp.pregnancy.fatherId;
        Gender childGender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;
        String childFirstName = SimNPCNameGenerator.generate();
        if (childFirstName.contains(" ")) {
            childFirstName = childFirstName.substring(0, childFirstName.indexOf(' '));
        }
        String childSurname = "SimTale";

        GeneticsData childGenetics = GeneticsData.combine(new GeneticsData(), new GeneticsData());

        GrowthComponent child = new GrowthComponent(
            playerComp.playerUuid,
            fatherId,
            worldTick,
            childGender,
            childGenetics,
            childFirstName,
            childSurname
        );

        try {
            Vector3d spawnPos = new Vector3d(0, 64, 0);
            TransformComponent transform =
                store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform != null) {
                spawnPos = transform.getPosition();
            }

            SimNPCFactory.NPCType childType = childGender == Gender.MALE
                ? SimNPCFactory.NPCType.CHILD_MALE
                : SimNPCFactory.NPCType.CHILD_FEMALE;

            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);
            UUIDComponent uuidComp =
                    store.getComponent(childRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                child.childId = uuidComp.getUuid();
            }

            SimNPCComponent childNPCComp = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (childNPCComp != null) {
                childNPCComp.name = child.getFullName();
                store.putComponent(childRef, PersistentDisplayName.getComponentType(),
                    new PersistentDisplayName(Message.raw(child.getFullName())));
                store.putComponent(childRef, Nameplate.getComponentType(),
                    new Nameplate(child.getFullName()));
            }

            child.pickUp(playerComp.playerUuid);
            ACTIVE_CHILDREN.add(child);
            Caskara.save("child_" + child.childId.toString(), child);
            BabyCareManager.initializeForChild(child);

            // Revert speed and subtract health
            EffectHelper.modifyMovement(playerRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
            StatHelper.subtractHealth(playerRef, 50.0f);

            // Give baby item to player
            ItemStack babyItem = new ItemStack("simtale:baby", 1).withMetadata("childId", Codec.STRING, child.childId.toString());
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
            ItemStackTransaction transaction = combinedInventory.addItemStack(babyItem);
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(playerRef, remainder, playerRef.getStore());
            }

            LOGGER.atInfo().log("SimTale: Player " + playerComp.playerUuid + " deu a luz a " + child.getFullName());

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao nascer o bebe do player: " + e.getMessage());
        }

        playerComp.pregnancy.reset();
        SimPlayerPersistence.savePlayer(playerComp);
    }

    /**
     * Mother AI logic for caring for the baby.
     * Stub — will be implemented in the future.
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
           TransformComponent transform =
                store.getComponent(npc.entityRef,
                    TransformComponent.getComponentType());
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
