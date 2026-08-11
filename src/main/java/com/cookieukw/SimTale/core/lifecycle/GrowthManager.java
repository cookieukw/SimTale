package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
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

import java.util.Map;

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

    /**
     * Run {@code task} outside the Store processing window.
     * <p>
     * All growth originates from {@code GrowthTickSystem.tick()}, and the Store refuses structural writes
     * ({@code putComponent}, {@code addComponent}, entity spawn) while it is processing:
     * <pre>
     *   IllegalStateException: Store is currently processing!
     * </pre>
     * Enqueueing on the world thread resolves this because that queue drains outside the
     * systems tick.
     */
    private static void runOutsideTick(Store<EntityStore> store, Runnable task) {
        if (store != null && !store.isProcessing()) {
            task.run();
            return;
        }
        if (!WorldUtil.execute(task)) {
            LOGGER.atWarning().log("SimTale: nenhum mundo carregado; etapa de crescimento ignorada.");
        }
    }

    public static void applyVisualScale(Ref<EntityStore> ref, float scale) {
        Store<EntityStore> store = ref.getStore();
        PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
        if (pm == null) return;

        ModelReference oldRef = pm.getModelReference();
        if (Math.abs(oldRef.getScale() - scale) <= 0.01f) return;

        // BOTH components have to be written, and this is the whole reason resizing never showed
        // up in game.
        //
        // PersistentModel is what gets saved and respawned; ModelComponent is what is drawn, and it
        // is the one carrying the isNetworkOutdated flag that makes the server resend the model to
        // clients. Writing only PersistentModel changes the saved size and nothing else — the
        // server believes the NPC is smaller, the client keeps drawing the old one, and it stays
        // that way until the entity is reloaded from disk. That is why /simtale setstage appeared
        // to do nothing at all, and why newborns kept spawning at adult size even though the
        // placement code sets 0.35 on them.
        //
        // PlumbobSystem is the one place in the mod that already wrote both, and the plumbob is
        // also the one model that visibly changes on demand. That was the tell.
        //
        // Nothing is mutated before the write lands either. The previous version called
        // pm.setModelReference(newRef) first and only then queued the write, which meant the guard
        // above already read the new scale on the next tick and returned early — so a write that
        // never reached the client was never retried either.
        //
        // The detour off the tick stays: these are structural writes and growth runs from inside
        // GrowthTickSystem, where the Store refuses them.
        String modelId = oldRef.getModelAssetId();
        Map<String, String> attachments = oldRef.getRandomAttachmentIds();
        runOutsideTick(store, () -> SimNPCFactory.applyModel(store, ref, modelId, scale, attachments));
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

            // The entire promotion is deferred, not just the spawn. It creates the new entity and
            // immediately after uses its reference for name, nameplate, family and persistence
            // — deferring only the spawn would leave this whole block working with a reference
            // that does not exist yet. As this chain starts in GrowthTickSystem.tick(), without
            // this, no child would ever change stage in normal gameplay.
            World world = WorldUtil.first();
            Store<EntityStore> store = world != null ? world.getEntityStore().getStore() : null;
            runOutsideTick(store, () -> onStageChanged(child));
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
                
            World world = WorldUtil.first();
            if (world == null) {
                LOGGER.atWarning().log("SimTale: nenhum mundo carregado; promocao para TODDLER adiada.");
                return;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, type,
                    calculateTargetScale(child, WorldUtil.tick()));
            
            UUID newEntityId = Objects.requireNonNull(childRef.getStore().getComponent(childRef, UUIDComponent.getComponentType())).getUuid();
            
            child.childId = newEntityId;
            Caskara.delete("child_" + oldChildId.toString(), GrowthComponent.class);
            Caskara.save("child_" + newEntityId, child);
            
            SimNPCComponent toddlerNpc = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (toddlerNpc != null) {
                toddlerNpc.name = child.getFullName();
                childRef.getStore().putComponent(childRef, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(toddlerNpc.name)));
                childRef.getStore().putComponent(childRef, Nameplate.getComponentType(), new Nameplate(toddlerNpc.name));
                // Growing up respawns the entity under a new id, so the family bond has to be
                // rebuilt or the child becomes a stranger to its parents every promotion.
                FamilyBonds.linkToFamily(toddlerNpc, child);
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
            World world = WorldUtil.first();
            if (world == null) {
                LOGGER.atWarning().log("SimTale: nenhum mundo carregado; promocao para TEEN adiada.");
                return;
            }

            Ref<EntityStore> childRef = LifecycleUtils.getEntityRef(child.childId);
            Vector3d spawnPos = new Vector3d(0, 100, 0);
            if (childRef != null && childRef.isValid()) {
                TransformComponent t = childRef.getStore().getComponent(childRef, TransformComponent.getComponentType());
                if (t != null) spawnPos = new Vector3d(t.getPosition());
                world.getEntityStore().getStore().removeEntity(childRef, RemoveReason.REMOVE);
            }

            SimNPCComponent oldNpc = null;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                    oldNpc = npc;
                    SimTale.untrackNpc(npc);
                    break;
                }
            }
            
            SimNPCFactory.NPCType type = child.gender == Gender.MALE 
                ? SimNPCFactory.NPCType.HUMAN_MALE 
                : SimNPCFactory.NPCType.HUMAN_FEMALE;
                
            Store<EntityStore> store = world.getEntityStore().getStore();
            Ref<EntityStore> teenRef = SimNPCFactory.spawnNPC(store, spawnPos, type,
                    calculateTargetScale(child, WorldUtil.tick()));
            
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
                // Personality and preferences are carried over above; the family bond has to be
                // carried over too, or growing up costs the teenager its parents.
                FamilyBonds.linkToFamily(teenNpc, child);
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
