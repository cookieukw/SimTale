package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.systems.PlumbobSystem;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Profession;
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
        World world = WorldUtil.first();
        if (world != null && !world.isTicking()) {
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

            // Scattered around the holder instead of exactly on them.
            //
            // Several children promoted in the same tick landed on identical coordinates, and five
            // toddlers sharing one point look like one glitched entity: the nameplates draw on top
            // of each other and they walk in perfect lockstep because they are all being pushed by
            // the same physics from the same spot.
            double angle = Math.random() * Math.PI * 2.0;
            spawnPos.add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);

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
            promoteToAdultBody(child, true);
        }
        else if (child.isAdult()) {
            // Also here, not only on TEEN.
            //
            // The stage branches read the stage the child landed on, not the one it came from, so
            // jumping straight to ADULT — which is exactly what /simtale setstage does — skipped
            // the only branch that swaps the body. The result was an adult wearing the child model
            // scaled up: right size, wrong proportions. Growing up naturally passed through TEEN
            // and hid it.
            //
            // Guarded so the normal path does not respawn twice: promoteToAdultBody is a no-op
            // once the entity is already on an adult body.
            promoteToAdultBody(child, false);
            onBecameAdult(child);
        }
    }

    /**
     * Whether this entity is still wearing a child model.
     *
     * <p>Read from the model asset id because that is the only thing that survives every path into
     * here: the role names its appearance {@code SimTale_Human_Child_<gender>_<variant>}, and the
     * adult roles have no {@code Child} segment. A missing model counts as "needs the adult body" —
     * something is wrong with the entity either way, and respawning is the recovery.
     */
    private static boolean usesChildBody(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return false;
        PersistentModel pm = ref.getStore().getComponent(ref, PersistentModel.getComponentType());
        if (pm == null || pm.getModelReference() == null) return true;
        String assetId = pm.getModelReference().getModelAssetId();
        return assetId == null || assetId.contains("Child");
    }

    /**
     * Respawns the child on an adult human body, carrying its identity across.
     *
     * @param announceTeen true when this is the teenage promotion, which has its own message
     */
    private static void promoteToAdultBody(GrowthComponent child, boolean announceTeen) {
        World world = WorldUtil.first();
        if (world == null) {
            LOGGER.atWarning().log("SimTale: nenhum mundo carregado; promocao de corpo adiada.");
            return;
        }

        if (!usesChildBody(LifecycleUtils.getEntityRef(child.childId))) {
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

        // The body just removed above had its own Plumbob, tracked under oldChildId. Nothing
        // ever ticks that UUID again once the entity is gone, so PlumbobSystem's own orphan
        // sweep never notices — it only reaps a crystal that is NOT in trackedPlumbobRefs, and
        // this one still is, forever, because untracking it was never anybody's job. Left alone
        // it floats exactly where the promotion happened for the rest of the server's life,
        // while a second, correct Plumbob spawns fresh for the new body and follows it around —
        // "two Plumbobs stuck in the air" after every single CHILD->TEEN or TEEN->ADULT growth.
        // Untracking here (not removing outright: no CommandBuffer to do that with from here)
        // lets the very next PlumbobSystem tick reap it through the same orphan sweep that
        // already cleans up every other kind of stale crystal.
        PlumbobSystem.removePlumbob(oldChildId);
        
        SimNPCComponent teenNpc = store.getComponent(teenRef, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (teenNpc != null) {
            teenNpc.name = child.getFullName();
            if (oldNpc != null) {
                teenNpc.personality = oldNpc.personality;
                teenNpc.preferences = oldNpc.preferences;
                teenNpc.profession = (oldNpc.profession != null && oldNpc.profession != Profession.UNEMPLOYED)
                        ? oldNpc.profession
                        : Profession.values()[(int)(Math.random() * Profession.values().length)];
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
        
        if (announceTeen) {
            PlayerRef pRef = LifecycleUtils.getPlayerRef(child.motherId);
            if (pRef == null) pRef = LifecycleUtils.getPlayerRef(child.fatherId);
            if (pRef != null) {
                pRef.sendMessage(Message.raw("Seu filho " + child.getFullName() + " virou um adolescente!"));
            }
        }
    }

    private static void onBecameAdult(GrowthComponent child) {
        LOGGER.atInfo().log("SimTale: " + child.getFullName() + " se tornou adulto!");
        
        // Dropped from the growth-tick list — an adult does not age further, and
        // GrowthTickSystem's own scan already does the same removal as a backstop for
        // /simtale setstage jumping a child straight to ADULT. But the record itself has to
        // survive on disk: ParentChildBond.findChildOf answers "is this NPC my child, at any
        // life stage" (see its own javadoc), and once ACTIVE_CHILDREN has dropped this entry
        // its ONLY remaining path is Caskara.load("child_" + npc.entityId, ...) reading this
        // exact key back. Deleting it here — which is what this used to do — reads as "an
        // adult is done growing, so the growth record is garbage now", but it silently deletes
        // the one thing that still says "this adult IS your child": Scold/Insult on the panel,
        // the mãe/papai/name address term, and every other adult-child check would go back to
        // treating them as a stranger the moment they grew up, no matter how good the
        // relationship was raised to be as a kid. Persisted instead of deleted, same as every
        // other stage promotion in this file — the child's identity does not stop being real
        // just because GrowthTickSystem no longer needs to tick it.
        LifecycleState.ACTIVE_CHILDREN.remove(child);
        Caskara.save("child_" + child.childId.toString(), child);
        
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
