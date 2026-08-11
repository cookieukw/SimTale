package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerProcessMovementSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PlumbobSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Maps Player/NPC UUID to Plumbob Entity Ref
    private static final Map<UUID, Ref<EntityStore>> playerPlumbobs = Collections.synchronizedMap(new HashMap<>());
    /**
     * Reverse index of {@link #playerPlumbobs}. The orphan check used
     * {@code playerPlumbobs.containsValue(ref)}, which scans the entire map under its lock for
     * every plumbob on every tick.
     */
    private static final Set<Ref<EntityStore>> trackedPlumbobRefs =
            ConcurrentHashMap.newKeySet();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        // Was UUIDComponent, i.e. *every entity in the world*, every tick — dropped items,
        // projectiles, the lot. Only three archetypes matter here: the NPCs and players that
        // own a plumbob, plus modelled entities so orphaned plumbobs can still be reaped.
        return Query.or(
                SimTale.SIM_NPC_COMPONENT_TYPE,
                Player.getComponentType(),
                PersistentModel.getComponentType()
        );
    }

   @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
            new SystemDependency<>(Order.AFTER, PlayerProcessMovementSystem.class)
        );
    }
    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        
        PersistentModel pm = chunk.getComponent(index, PersistentModel.getComponentType());
        if (pm != null && pm.getModelReference().getModelAssetId() != null && pm.getModelReference().getModelAssetId().startsWith("Plumbob")) {
            Ref<EntityStore> thisRef = chunk.getReferenceTo(index);
            if (!trackedPlumbobRefs.contains(thisRef)) {
                commandBuffer.removeEntity(thisRef, RemoveReason.REMOVE);
                LOGGER.atFine().log("[SimTale] Limpando Plumbob orfao do mundo: " + uuidComp.getUuid());
            }
            return;
        }
        
        boolean isPlayer = chunk.getComponent(index, Player.getComponentType()) != null;
        SimNPCComponent npcHere = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        boolean isNpc = npcHere != null;

        if (!isPlayer && !isNpc) return;

        // The Reaper is a ceremonial entity that exists for one death and is despawned when the
        // ritual ends — a mood indicator over Death itself reads as a bug even when it works, and
        // the plumbob outliving her was one.
        //
        // Despawning rather than just skipping: this system can tick the Reaper once in the window
        // between her entity being added and isReaper being set, and that one tick is enough to
        // give her a plumbob. Returning early from then on meant the crystal was never updated and
        // never cleaned either — it stayed in trackedPlumbobRefs, which is exactly what the orphan
        // sweep above refuses to touch — so it hung at her spawn point forever, outliving her.
        UUID entityUuid = uuidComp.getUuid();
        if (npcHere != null && npcHere.isReaper) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        // Away on an expedition. Skipping the update is not enough — the plumbob already exists and
        // would simply stop being moved, leaving a mood crystal parked in mid-air over an NPC the
        // player was told had left. It has to actually go, and come back when she does.
        if (npcHere != null && NPCWorkHelper.isAwayOnExpedition(store, chunk.getReferenceTo(index))) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        // Being carried hides the crystal entirely, third case after the Reaper and the expedition.
        //
        // The first attempt made it follow the carrier instead, which fixed the stale position but
        // produced a worse picture: the child's plumbob and the carrier's own ended up side by side
        // over one head. Two mood crystals on one player reads as a bug even though both are
        // "correct". A carried child is not going about her business anyway, which is what the
        // crystal is there to report.
        //
        // (The stale position is real and worth remembering: a mounted entity is drawn attached to
        // its mount, but its own TransformComponent stays where it was when it mounted. Anything
        // that follows a carried NPC has to read the carrier, not her.)
        if (chunk.getComponent(index, MountedComponent.getComponentType()) != null) {
            despawnPlumbob(entityUuid, commandBuffer);
            return;
        }

        TransformComponent entityTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (entityTransform == null) return;
        
        World world = WorldUtil.first();
        if (world == null) return;

        Ref<EntityStore> plumbobRef = playerPlumbobs.get(entityUuid);
        // Get Mood
        String moodModelName = "Plumbob"; // Default fallback
        if (isNpc) {
            SimNPCComponent npc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
            if (npc != null && npc.getMood() != null) {
                moodModelName = "Plumbob_" + npc.getMood().name();
            }
        }

        double height = 2.2;
        BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
        if (box != null) {
            height = box.getBoundingBox().height() + 0.35;
        }

        boolean needsNewPlumbob = false;
        if (plumbobRef == null || !plumbobRef.isValid()) {
            needsNewPlumbob = true;
        } else {
            // Check if the plumbob still exists
            TransformComponent plumbobTransform = store.getComponent(plumbobRef, TransformComponent.getComponentType());
            if (plumbobTransform == null) {
                needsNewPlumbob = true;
            } else {
                // Update position
                plumbobTransform.teleportPosition(new Vector3d(
                    entityTransform.getPosition().x,
                    entityTransform.getPosition().y + height,
                    entityTransform.getPosition().z
                ));
                float yaw = (float) ((world.getTick() * 0.04f) % (2.0f * Math.PI));
                plumbobTransform.setRotation(new Rotation3f(0f, yaw, 0f));
                commandBuffer.replaceComponent(plumbobRef, TransformComponent.getComponentType(), plumbobTransform);
                
                // Update Model if mood changed
                PersistentModel currPm = store.getComponent(plumbobRef, PersistentModel.getComponentType());
                if (currPm != null) {
                    if (!moodModelName.equals(currPm.getModelReference().getModelAssetId())) {
                        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(moodModelName);
                        if (modelAsset != null) {
                            Model model = Model.createScaledModel(modelAsset, 0.9f);
                            commandBuffer.replaceComponent(plumbobRef, PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                            commandBuffer.replaceComponent(plumbobRef, ModelComponent.getComponentType(), new ModelComponent(model));
                        }
                    }
                }
            }
        }

        if (needsNewPlumbob) {
            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(moodModelName);
            if (modelAsset == null) {
                modelAsset = ModelAsset.getAssetMap().getAsset("Plumbob");
            }
            if (modelAsset != null) {
                Model model = Model.createScaledModel(modelAsset, 0.9f);
                holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(new Vector3d(entityTransform.getPosition().x, entityTransform.getPosition().y + height, entityTransform.getPosition().z), new Rotation3f()));
                holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                // Deliberately no BoundingBox: this is a purely cosmetic floating icon, and
                // giving it a real collision box (copied from the model's own bounds) made it
                // block interaction/break raycasts aimed through it — e.g. looking up at an
                // NPC's Plumbob and trying to hit a block behind it just failed.
                holder.addComponent(NetworkId.getComponentType(), new NetworkId(store.getExternalData().takeNextNetworkId()));
                holder.ensureComponent(UUIDComponent.getComponentType());
                
                Ref<EntityStore> newPlumbob = commandBuffer.addEntity(holder, AddReason.SPAWN);
                Ref<EntityStore> replaced = playerPlumbobs.put(entityUuid, newPlumbob);
                if (replaced != null) {
                    trackedPlumbobRefs.remove(replaced);
                }
                trackedPlumbobRefs.add(newPlumbob);
                LOGGER.atFine().log("[SimTale] Spawned Plumbob for entity " + entityUuid);
            } else {
                LOGGER.atWarning().log("[SimTale-ERROR] Plumbob ModelAsset not found!");
            }
        }
    }

    /**
     * Untracks and removes the plumbob owned by an entity, if it has one.
     * <p>
     * Untracking alone would work eventually — the orphan sweep at the top of the tick reaps
     * plumbobs nobody claims — but only once that entity's chunk happens to be ticked again, so the
     * crystal lingers visibly in the meantime. Removing it here makes it disappear on the same tick
     * the NPC does.
     */
    private static void despawnPlumbob(UUID entityUuid, CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> existing = playerPlumbobs.remove(entityUuid);
        if (existing == null) return;
        trackedPlumbobRefs.remove(existing);
        if (existing.isValid()) {
            commandBuffer.removeEntity(existing, RemoveReason.REMOVE);
        }
    }

    public static void removePlumbob(UUID entityUuid) {
        Ref<EntityStore> removed = playerPlumbobs.remove(entityUuid);
        if (removed != null) {
            trackedPlumbobRefs.remove(removed);
        }
        LOGGER.atFine().log("[SimTale] Plumbob untracked para a entidade: " + entityUuid);
    }
}
