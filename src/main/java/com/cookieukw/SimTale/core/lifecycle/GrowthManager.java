package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.UUID;

public class GrowthManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static float calculateTargetScale(GrowthComponent child, long worldTick) {
        int age = child.getAgeDays(worldTick);
        return switch (child.stage) {
            case BABY -> 0.35f;
            case TODDLER -> {
                float toddlerProgress = (float) (age - 4) / 4.0f;
                yield 0.45f + toddlerProgress * 0.10f;
            }
            case CHILD -> {
                float childProgress = (float) (age - 9) / 11.0f;
                yield 0.55f + childProgress * 0.30f;
            }
            case TEEN -> {
                float teenProgress = (float) (age - 21) / 19.0f;
                yield 0.75f + teenProgress * 0.20f;
            }
            default -> 1.00f;
        };
    }

    public static void applyVisualScale(Ref<EntityStore> ref, float scale) {
        Store<EntityStore> store = ref.getStore();
        PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
        if (pm != null) {
            ModelReference oldRef = pm.getModelReference();
            if (Math.abs(oldRef.getScale() - scale) > 0.01f) {
                ModelReference newRef = new ModelReference(oldRef.getModelAssetId(), scale, oldRef.getRandomAttachmentIds());
                pm.setModelReference(newRef);
                store.putComponent(ref, PersistentModel.getComponentType(), pm);
            }
        }
    }

    public static void tickGrowth(GrowthComponent child, long worldTick) {
        boolean stageChanged = child.updateStage(worldTick);
        child.currentScale = calculateTargetScale(child, worldTick);

        Ref<EntityStore> entityRef = LifecycleUtils.getEntityRef(child.childId);
        if (entityRef != null && entityRef.isValid()) {
            applyVisualScale(entityRef, child.currentScale);
        }

        if (stageChanged) {
            LOGGER.atInfo().log("SimTale: " + child.getFullName() + " grew to "
                + child.stage.getDisplayName() + " (scale: " + child.currentScale + ")");
            onStageChanged(child);
        }

        if (child.needsCare() && child.babyNeeds != null) {
            child.babyNeeds.tickDecay();
        }
    }

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
                Ref<EntityStore> holderRef = LifecycleUtils.getEntityRef(holderId);
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
            
            UUID newEntityId = Objects.requireNonNull(childRef.getStore().getComponent(childRef, UUIDComponent.getComponentType())).getUuid();
            
            child.childId = newEntityId;
            Caskara.delete("child_" + oldChildId.toString(), GrowthComponent.class);
            Caskara.save("child_" + newEntityId, child);
            
            SimNPCComponent toddlerNpc = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (toddlerNpc != null) {
                toddlerNpc.name = child.getFullName();
                childRef.getStore().putComponent(childRef, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(toddlerNpc.name)));
                childRef.getStore().putComponent(childRef, Nameplate.getComponentType(), new Nameplate(toddlerNpc.name));
                SimNPCPersistence.saveNPC(toddlerNpc);
            }
            
            if (care != null) {
                Caskara.delete("babycare_" + oldChildId, BabyCareData.class);
                care.childId = newEntityId.toString();
                BabyCareManager.save(care);
            }
            
            LifecycleUtils.updateFamilyChildId(child.motherId, oldChildId, newEntityId);
            LifecycleUtils.updateFamilyChildId(child.fatherId, oldChildId, newEntityId);
            
            if (holderId != null) {
                PlayerRef pRef = LifecycleUtils.getPlayerRef(holderId);
                if (pRef != null) {
                    LifecycleUtils.removeBabyItemFromPlayer(pRef, oldChildId);
                    pRef.sendMessage(Message.raw("Seu bebê " + child.getFullName() + " cresceu e começou a andar!"));
                }
            }
            
            if (holderId != null) {
                BabyCareManager.NPC_CARRIED_BABIES.remove(holderId);
            }
        } 
        else if (child.stage == GrowthStage.TEEN) {
            Ref<EntityStore> childRef = LifecycleUtils.getEntityRef(child.childId);
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
            
            UUID newEntityId = Objects.requireNonNull(teenRef.getStore().getComponent(teenRef, UUIDComponent.getComponentType())).getUuid();
            
            UUID oldChildId = child.childId;
            child.childId = newEntityId;
            Caskara.delete("child_" + oldChildId.toString(), GrowthComponent.class);
            Caskara.save("child_" + newEntityId, child);
            
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
                SimNPCPersistence.saveNPC(teenNpc);
            }
            
            LifecycleUtils.updateFamilyChildId(child.motherId, oldChildId, newEntityId);
            LifecycleUtils.updateFamilyChildId(child.fatherId, oldChildId, newEntityId);
            
            PlayerRef pRef = LifecycleUtils.getPlayerRef(child.motherId);
            if (pRef == null) pRef = LifecycleUtils.getPlayerRef(child.fatherId);
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
        
        LifecycleState.ACTIVE_CHILDREN.remove(child);
        Caskara.delete("child_" + child.childId.toString(), GrowthComponent.class);
        
        Ref<EntityStore> childRef = LifecycleUtils.getEntityRef(child.childId);
        if (childRef != null && childRef.isValid()) {
            applyVisualScale(childRef, 1.0f);
        }
        
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                if (child.babyNeeds != null) {
                    BabyNeeds.PersonalityTendency tendency = child.babyNeeds.getPersonalityTendency();
                    Trait extraTrait = tendency == BabyNeeds.PersonalityTendency.SOCIABLE ? Trait.LOYAL : Trait.SHY;
                    npc.personality.traits.add(extraTrait);
                }
                SimNPCPersistence.saveNPC(npc);
                break;
            }
        }
        
        PlayerRef pRef = LifecycleUtils.getPlayerRef(child.motherId);
        if (pRef == null) pRef = LifecycleUtils.getPlayerRef(child.fatherId);
        if (pRef != null) {
            pRef.sendMessage(Message.raw("Seu filho " + child.getFullName() + " atingiu a fase adulta e agora e independente!"));
        }
    }

    public static float getScaleForStage(GrowthStage stage) {
        return stage.getScale();
    }
}
