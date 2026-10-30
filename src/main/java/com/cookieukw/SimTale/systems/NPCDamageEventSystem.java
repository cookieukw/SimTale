package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.config.SimTaleConfig;
import com.cookieukw.SimTale.config.SimTaleConfigManager;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Handles damage dealt to SimTale NPCs.
 * Records {@link MemoryEvent#ATTACKED} for the victim, registers fear/hostility towards the attacker,
 * and notifies nearby NPC bystanders within 12 blocks who witness the attack.
 */
public class NPCDamageEventSystem extends DamageEventSystem {

    private static final SimLog LOGGER = SimLog.forClass(NPCDamageEventSystem.class);
    private static final double WITNESS_DISTANCE_SQ = 12.0 * 12.0;

    public NPCDamageEventSystem() {
        super();
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return SimTale.SIM_NPC_COMPONENT_TYPE;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage event
    ) {
        SimNPCComponent victimNpc = chunk.getComponent(index, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (victimNpc == null) {
            return;
        }

        DamageCause cause = event.getCause();
        String causeId = cause != null && cause.getId() != null ? cause.getId() : "";

        /* Suffocation and drowning are pathfinding accidents, not combat. Left alone they dealt
        15 damage per second until the NPC died — and because the engine removes a 0 HP NPC
        before SimTale's own tick sees it, the death flow never ran (the log only showed
        "removed externally"). Cancel the damage and move the victim back to its bed/home so it
        stops accumulating more.
        */
        if (isSuffocationOrDrowning(causeId)) {
            event.setAmount(0f);
            event.setCancelled(true);
            victimNpc.lastDamageCause = "por " + causeId;
            rescueFromEnvironment(chunk.getReferenceTo(index), victimNpc, store, causeId);
            return;
        }

        /* Village safe zone. A resident standing inside the village boundary cannot be damaged at
        all: hostile mobs, fall damage, fire and player hits are all cancelled. This is deliberately
        broader than "mobs only" — a safe zone that still lets a player execute someone is not a
        safe zone. /simtale forcekill bypasses damage and still works for administration.
        */
        TransformComponent victimTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (victimTransform != null && isInsideVillageSafeZone(victimTransform.getPosition())) {
            event.setAmount(0f);
            event.setCancelled(true);
            LOGGER.debug("[SimTale] damage to NPC '{}' blocked by the village safe zone", victimNpc.name);
            return;
        }

        Damage.Source source = event.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef.isValid()) {
                Player p = store.getComponent(attackerRef, Player.getComponentType());
                if (p != null) {
                    victimNpc.lastDamageCause = "por um jogador";
                } else {
                    victimNpc.lastDamageCause = "por uma entidade";
                }
            } else {
                victimNpc.lastDamageCause = "por uma entidade";
            }
        } else {
            victimNpc.lastDamageCause = causeId.isBlank()
                    ? "por " + source.getClass().getSimpleName()
                    : "por " + causeId;
        }

        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (!attackerRef.isValid()) {
            return;
        }

        Player playerComp = store.getComponent(attackerRef, Player.getComponentType());
        if (playerComp == null) {
            return;
        }

        UUIDComponent uuidComp = store.getComponent(attackerRef, UUIDComponent.getComponentType());
        UUID playerUuid = uuidComp != null ? uuidComp.getUuid() : null;
        if (playerUuid == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        long currentTick = world.getTick();

        // Victim records memory of being attacked
        victimNpc.memory.addMemory(MemoryEvent.ATTACKED, playerUuid, false, null);
        victimNpc.setEmotion(Mood.SCARED, 1.0f, "attacked", currentTick);

        Relationship victimRel = victimNpc.getRelationship(playerUuid);
        if (victimRel != null) {
            victimRel.addFriendship(-15);
            victimRel.addAffinity(-20);
            victimRel.addTrust(-25);
            if (victimRel.affinity <= -50 || victimRel.friendship <= -50) {
                victimRel.status = RelationshipStatus.ENEMIES;
            }
        }

        LOGGER.info("[SimTale] NPC '{}' was attacked by player {}", victimNpc.name, playerUuid);

        Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
        SimTaleJuiceHelper.playDamagePanic(victimRef, victimNpc, store, currentTick);

        // Notify nearby bystanders who witness the attack
        TransformComponent victimTrans = chunk.getComponent(index, TransformComponent.getComponentType());
        if (victimTrans != null) {
            Vector3d victimPos = victimTrans.getPosition();
            for (SimNPCComponent bystander : SimTale.ACTIVE_NPCS) {
                if (bystander == victimNpc || bystander.entityId == null || bystander.entityId.equals(victimNpc.entityId)) {
                    continue;
                }
                if (bystander.entityRef != null && bystander.entityRef.isValid()) {
                    TransformComponent bTrans = store.getComponent(bystander.entityRef, TransformComponent.getComponentType());
                    if (bTrans != null && bTrans.getPosition().distanceSquared(victimPos) <= WITNESS_DISTANCE_SQ) {
                        bystander.memory.addMemory(MemoryEvent.ATTACKED, playerUuid, true, victimNpc.name);
                        Relationship bRel = bystander.getRelationship(playerUuid);
                        if (bRel != null) {
                            bRel.addTrust(-10);
                            bRel.addAffinity(-10);
                            bRel.addFriendship(-5);
                        }
                        bystander.setEmotion(Mood.SCARED, 0.8f, "witness_attack", currentTick);
                        SimTaleJuiceHelper.playDamagePanic(bystander.entityRef, bystander, store, currentTick);
                        LOGGER.debug("[SimTale] NPC '{}' witnessed attack on '{}'", bystander.name, victimNpc.name);
                    }
                }
            }
        }
    }

    private static boolean isSuffocationOrDrowning(String causeId) {
        return "Suffocation".equalsIgnoreCase(causeId) || "Drowning".equalsIgnoreCase(causeId);
    }

    /**
     * Moves a victim of suffocation/drowning back to its bed or shared home. The damage itself is
     * already cancelled; this stops the NPC from standing in the bad spot and re-triggering the
     * same damage every second.
     */
    private static void rescueFromEnvironment(Ref<EntityStore> victimRef, SimNPCComponent npc,
                                              Store<EntityStore> store, String causeId) {
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        long tick = world.getTick();
        if (tick - npc.lastEnvironmentRescueTick < 200) return;
        npc.lastEnvironmentRescueTick = tick;

        Vector3d target = null;
        String destination = "above their current position";
        if (npc.bedLocation != null) {
            target = new Vector3d(npc.bedLocation.x + 0.5, npc.bedLocation.y + 1.0, npc.bedLocation.z + 0.5);
            destination = "their bed";
        } else if (npc.family != null && npc.family.hasSharedHome) {
            target = new Vector3d(npc.family.homeX, npc.family.homeY + 1.0, npc.family.homeZ);
            destination = "their home";
        } else {
            TransformComponent transform = store.getComponent(victimRef, TransformComponent.getComponentType());
            if (transform != null) {
                target = new Vector3d(transform.getPosition()).add(0, 3.0, 0);
            }
        }
        if (target == null) return;

        Vector3d rescueTarget = target;
        String rescueDestination = destination;
        world.execute(() -> {
            if (victimRef == null || !victimRef.isValid()) return;
            Store<EntityStore> liveStore = victimRef.getStore();
            TransformComponent transform = liveStore.getComponent(victimRef, TransformComponent.getComponentType());
            if (transform != null) {
                transform.setPosition(rescueTarget);
                liveStore.putComponent(victimRef, TransformComponent.getComponentType(), transform);
            }
            RoutineAIComponent ai = liveStore.getComponent(victimRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai != null) {
                NPCMovementHelper.clearMoveTarget(victimRef, ai);
            }
            LOGGER.info("[SimTale] NPC '{}' rescued from {} and moved to {}",
                    npc.name, causeId, rescueDestination);
        });
    }

    /**
     * True when the point lies inside a village and the safe zone is enabled.
     *
     * <p>Villages are derived from houses, so the safe zone moves and disappears with the village
     * itself. There is no separate persisted region to keep in sync.
     */
    public static boolean isInsideVillageSafeZone(Vector3d pos) {
        SimTaleConfig config = SimTaleConfigManager.getConfig();
        if (config != null && !config.villageSafeZoneEnabled) return false;

        VillageManager.Village village = VillageManager.nearest(pos.x, pos.z);
        return village != null && village.contains(pos.x, pos.z);
    }
}
