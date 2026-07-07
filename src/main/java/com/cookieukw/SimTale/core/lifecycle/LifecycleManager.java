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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.component.RemoveReason;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;

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

            // Immediately despawn the entity because the baby is in item form
            store.removeEntity(childRef, RemoveReason.REMOVE);

            Child familyChild = new Child(child.childId, child.getFullName());
            mother.family.children.add(familyChild);

            if (father != null) {
                Child fatherFamilyChild = new Child(child.childId, child.getFullName());
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
    public static float calculateTargetScale(GrowthComponent child) {
        int age = child.ageDays;
        switch (child.stage) {
            case BABY:
                return 0.35f;
            case TODDLER:
                // Age 4 to 8. Toddler scale: starts at 0.45f, grows to 0.55f
                float toddlerProgress = (float)(age - 4) / 4.0f; // 0.0 to 1.0
                return 0.45f + toddlerProgress * 0.10f;
            case CHILD:
                // Age 9 to 20. Child scale: starts at 0.55f, grows to 0.85f
                float childProgress = (float)(age - 9) / 11.0f; // 0.0 to 1.0
                return 0.55f + childProgress * 0.30f;
            case TEEN:
                // Age 21 to 40. Teen scale (adult model): starts at 0.75f, grows to 0.95f
                float teenProgress = (float)(age - 21) / 19.0f; // 0.0 to 1.0
                return 0.75f + teenProgress * 0.20f;
            case ADULT:
            default:
                return 1.00f;
        }
    }

    public static void applyVisualScale(Ref<EntityStore> ref, float scale) {
        Store<EntityStore> store = ref.getStore().getStore();
        PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
        if (pm != null) {
            ModelReference oldRef = pm.getModelReference();
            if (oldRef != null && Math.abs(oldRef.getScale() - scale) > 0.01f) {
                ModelReference newRef = new ModelReference(oldRef.getModelAssetId(), scale, oldRef.getRandomAttachmentIds());
                pm.setModelReference(newRef);
                store.putComponent(ref, PersistentModel.getComponentType(), pm);
            }
        }
    }

    private static Ref<EntityStore> getEntityRef(UUID childId) {
        for (World world : Universe.get().getWorlds().values()) {
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(childId);
            if (ref != null) return ref;
        }
        return null;
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

        // Calculate and apply scale
        child.currentScale = calculateTargetScale(child);

        Ref<EntityStore> entityRef = getEntityRef(child.childId);
        if (entityRef != null && entityRef.isValid()) {
            applyVisualScale(entityRef, child.currentScale);
        }

        if (stageChanged) {
            LOGGER.atInfo().log("SimTale: " + child.getFullName() + " grew to "
                + child.stage.getDisplayName() + " (scale: " + child.currentScale + ")");

            onStageChanged(child);
        }

        // If still needs care, decay needs
        if (child.needsCare() && child.babyNeeds != null) {
            child.babyNeeds.tickDecay();
        }
    }

    /**
     * Callback when growth stage changes.
     */
    private static void onStageChanged(GrowthComponent child) {
        if (child.stage == GrowthStage.TODDLER) {
            UUID oldChildId = child.childId;
            UUID holderId = null;
            BabyCareData care = BabyCareManager.load(oldChildId);
            if (care != null) {
                try {
                    holderId = UUID.fromString(care.currentHolderId);
                } catch (Exception ignored) {}
            }

            Vector3d spawnPos = new Vector3d(0, 100, 0); // fallback
            if (holderId != null) {
                Ref<EntityStore> holderRef = getEntityRef(holderId);
                if (holderRef != null) {
                    TransformComponent t = holderRef.getStore().getComponent(holderRef, TransformComponent.getComponentType());
                    if (t != null) spawnPos = new Vector3d(t.getPosition());
                }
            }

            SimNPCFactory.NPCType type = child.gender == Gender.MALE 
                ? SimNPCFactory.NPCType.CHILD_MALE 
                : SimNPCFactory.NPCType.CHILD_FEMALE;
                
            World world = Universe.get().getWorlds().values().iterator().next();
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, type);
            
            UUID newEntityId = childRef.getStore().getComponent(childRef, UUIDComponent.getComponentType()).getUuid();
            
            child.childId = newEntityId;
            Caskara.delete("child_" + oldChildId.toString(), GrowthComponent.class);
            Caskara.save("child_" + newEntityId.toString(), child);
            
            SimNPCComponent toddlerNpc = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (toddlerNpc != null) {
                toddlerNpc.name = child.getFullName();
                childRef.getStore().putComponent(childRef, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(toddlerNpc.name)));
                childRef.getStore().putComponent(childRef, Nameplate.getComponentType(), new Nameplate(toddlerNpc.name));
                com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(toddlerNpc);
            }
            
            if (care != null) {
                Caskara.delete("babycare_" + oldChildId.toString(), BabyCareData.class);
                care.childId = newEntityId.toString();
                BabyCareManager.save(care);
            }
            
            updateFamilyChildId(child.motherId, oldChildId, newEntityId);
            updateFamilyChildId(child.fatherId, oldChildId, newEntityId);
            
            if (holderId != null) {
                PlayerRef pRef = getPlayerRef(holderId);
                if (pRef != null) {
                    removeBabyItemFromPlayer(pRef, oldChildId);
                    pRef.sendMessage(Message.raw("Seu bebê " + child.getFullName() + " cresceu e começou a andar!"));
                }
            }
            
            if (holderId != null) {
                BabyCareManager.NPC_CARRIED_BABIES.remove(holderId);
            }
        } 
        else if (child.stage == GrowthStage.TEEN) {
            Ref<EntityStore> childRef = getEntityRef(child.childId);
            Vector3d spawnPos = new Vector3d(0, 100, 0);
            if (childRef != null && childRef.isValid()) {
                TransformComponent t = childRef.getStore().getComponent(childRef, TransformComponent.getComponentType());
                if (t != null) spawnPos = new Vector3d(t.getPosition());
                Universe.get().getWorlds().values().iterator().next().getEntityStore().getStore().removeEntity(childRef, RemoveReason.REMOVE);
            }
            
            SimNPCComponent oldNpc = null;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                    oldNpc = npc;
                    SimTale.ACTIVE_NPCS.remove(npc);
                    break;
                }
            }
            
            SimNPCFactory.NPCType type = child.gender == Gender.MALE 
                ? SimNPCFactory.NPCType.HUMAN_MALE 
                : SimNPCFactory.NPCType.HUMAN_FEMALE;
                
            World world = Universe.get().getWorlds().values().iterator().next();
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> teenRef = SimNPCFactory.spawnNPC(store, spawnPos, type);
            
            UUID newEntityId = teenRef.getStore().getComponent(teenRef, UUIDComponent.getComponentType()).getUuid();
            
            UUID oldChildId = child.childId;
            child.childId = newEntityId;
            Caskara.delete("child_" + oldChildId.toString(), GrowthComponent.class);
            Caskara.save("child_" + newEntityId.toString(), child);
            
            SimNPCComponent teenNpc = store.getComponent(teenRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (teenNpc != null) {
                teenNpc.name = child.getFullName();
                if (oldNpc != null) {
                    teenNpc.personality = oldNpc.personality;
                    teenNpc.preferences = oldNpc.preferences;
                    teenNpc.profession = oldNpc.profession;
                }
                teenRef.getStore().putComponent(teenRef, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(teenNpc.name)));
                teenRef.getStore().putComponent(teenRef, Nameplate.getComponentType(), new Nameplate(teenNpc.name));
                com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(teenNpc);
            }
            
            updateFamilyChildId(child.motherId, oldChildId, newEntityId);
            updateFamilyChildId(child.fatherId, oldChildId, newEntityId);
            
            PlayerRef pRef = getPlayerRef(child.motherId);
            if (pRef == null) pRef = getPlayerRef(child.fatherId);
            if (pRef != null) {
                pRef.sendMessage(Message.raw("Seu filho " + child.getFullName() + " virou um adolescente!"));
            }
        }
        else if (child.isAdult()) {
            onBecameAdult(child);
        }
    }

    private static void onBecameAdult(GrowthComponent child) {
        LOGGER.atInfo().log("SimTale: " + child.getFullName() + " se tornou adulto!");
        
        ACTIVE_CHILDREN.remove(child);
        Caskara.delete("child_" + child.childId.toString());
        
        Ref<EntityStore> childRef = getEntityRef(child.childId);
        if (childRef != null && childRef.isValid()) {
            applyVisualScale(childRef, 1.0f);
        }
        
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                if (child.babyNeeds != null) {
                    Trait extraTrait = child.babyNeeds.getPersonalityTendency();
                    if (extraTrait != null && !npc.personality.traits.contains(extraTrait)) {
                        npc.personality.traits.add(extraTrait);
                    }
                }
                com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
                break;
            }
        }
        
        PlayerRef pRef = getPlayerRef(child.motherId);
        if (pRef == null) pRef = getPlayerRef(child.fatherId);
        if (pRef != null) {
            pRef.sendMessage(Message.raw("Seu filho " + child.getFullName() + " atingiu a fase adulta e agora e independente!"));
        }
    }

    private static void updateFamilyChildId(UUID parentId, UUID oldId, UUID newId) {
        if (parentId == null) return;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.equals(parentId)) {
                for (Child c : npc.family.children) {
                    if (c.id != null && c.id.equals(oldId)) {
                        c.id = newId;
                        com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
                        return;
                    }
                }
            }
        }
        SimNPCComponent temp = new SimNPCComponent(parentId, "Parent");
        if (com.cookieukw.SimTale.db.SimNPCPersistence.loadNPC(temp)) {
            for (Child c : temp.family.children) {
                if (c.id != null && c.id.equals(oldId)) {
                    c.id = newId;
                    com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(temp);
                    return;
                }
            }
        }
    }

    private static PlayerRef getPlayerRef(UUID playerUuid) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef.getUuid().equals(playerUuid)) return pRef;
        }
        return null;
    }

    private static void removeBabyItemFromPlayer(PlayerRef playerRef, UUID oldChildId) {
        Ref<EntityStore> pRef = Universe.get().getWorlds().values().iterator().next().getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (pRef == null) return;
        CombinedItemContainer combinedInventory = InventoryComponent.getCombined(pRef.getStore(), pRef, InventoryComponent.HOTBAR_FIRST);
        for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
            ItemStack item = combinedInventory.getItemStack(slot);
            if (item != null && item.getItemId().equals("simtale:baby")) {
                String cId = item.getFromMetadataOrNull("childId", Codec.STRING);
                if (cId != null && cId.equals(oldChildId.toString())) {
                    combinedInventory.removeItemStackFromSlot(slot, item, 1);
                    break;
                }
            }
        }
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

            // Immediately despawn the entity because the baby is in item form
            store.removeEntity(childRef, RemoveReason.REMOVE);

            // Add to father's family children if NPC
            if (fatherId != null) {
                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityId != null && npc.entityId.equals(fatherId)) {
                        Child fatherFamilyChild = new Child(child.childId, child.getFullName());
                        npc.family.children.add(fatherFamilyChild);
                        com.cookieukw.SimTale.db.SimNPCPersistence.saveNPC(npc);
                        break;
                    }
                }
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
