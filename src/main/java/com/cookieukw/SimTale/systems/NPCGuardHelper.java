package com.cookieukw.SimTale.systems;


import java.util.Locale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.lifecycle.WorkEligibility;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WeaponCategory;
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

/**
 * Guard combat: detect a hostile mob nearby, walk to it, and take it out. No profession did
 * anything at all for Guard before this — {@code Profession.GUARD} existed only so a sword gift
 * could assign the title, with zero actual behavior behind it.
 * <p>
 * The guard still uses scripted movement rather than the engine's NPC combat role, but the
 * fight itself is no longer a delayed {@code removeEntity}. Each swing applies a real
 * {@link Damage} event to the hostile, so it keeps its model, plays its hurt/death reactions and
 * only disappears when its health reaches zero. That also means the hostile can fight back while
 * the exchange lasts, which is what the guard's extra health and armour are for.
 */
public class NPCGuardHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCGuardHelper.class);

    private static final double GUARD_SCAN_RADIUS = 10.0;
    private static final double GUARD_SCAN_RADIUS_SQ = GUARD_SCAN_RADIUS * GUARD_SCAN_RADIUS;

    /**
     * A hostile that gets inside a village is no longer limited to whichever guard happens to be
     * within ten blocks of it. The two closest guards belonging to that village respond from
     * anywhere in its territory. Two is enough to look like a garrison without emptying every
     * patrol post in town.
     */
    private static final int VILLAGE_RESPONSE_GUARDS = 2;

    /** How far outside the village radius a guard may stand and still answer a village alarm. */
    private static final double VILLAGE_RESPONSE_MARGIN = 16.0;

    private static final double MELEE_RANGE_SQ = 2.5 * 2.5;
    /** How far a Guard with a ranged weapon stops from the hostile instead of closing to melee
     *  range. Comfortably inside {@link #GUARD_SCAN_RADIUS} so a target found by the scan is
     *  never already "too close to keep distance from" the moment it's spotted. */
    private static final double RANGED_RANGE_SQ = 7.0 * 7.0;
    /** Time before the first swing after closing to range. */
    private static final int ATTACK_WINDUP_TICKS = 8;

    /** One visible swing per second, instead of one instant removal after a fixed delay. */
    private static final int ATTACK_INTERVAL_TICKS = 20;

    /** Safety valve: a hostile that cannot be damaged by the event pipeline still leaves. */
    private static final int MAX_FIGHT_TICKS = 200;

    /** Fallback hit for entities without a Health stat. Living mobs take a percentage instead. */
    private static final float FALLBACK_HIT_DAMAGE = 25.0f;

    /** Fraction of the hostile's maximum health removed per swing (roughly three hits). */
    private static final float HIT_MAX_HEALTH_FRACTION = 0.34f;

    /** Animation names inside the held item's own PlayerAnimations set. */
    private static final String ATTACK_MELEE_ANIM = "SwingRight";
    private static final String ATTACK_RANGED_ANIM = "Shoot";

    /** How much faster a guard moves while closing on a hostile. */
    private static final double CHASE_SPEED_MULTIPLIER = 1.9;

    private static final int MOVE_TIMEOUT_TICKS = 600; // 30s, same as other MOVING_* states

    /** Model asset id keywords (lowercase) that mark a hostile mob. Confirmed via
     *  {@code /simtale debugnear}'s entity dump — the skeleton's raw model id is exactly
     *  "Skeleton". The rest are the model names used by Hytale's aggressive undead, goblin,
     *  scarak and outlander roles. Deliberately a curated list rather than "contains any
     *  creature": that mistake made the old Hunter filter match nothing (or everything).
     */
    private static final String[] HOSTILE_MODEL_KEYWORDS = {
            "skeleton", "zombie", "ghoul", "wraith", "werewolf", "hound_bleached",
            "shadow_knight", "trork", "goblin", "scarak", "outlander", "hedera", "fen_stalker"
    };

    /** How often a given guard looks around. One second, as before. */
    private static final int GUARD_SCAN_PERIOD_TICKS = 20;

    /** How long a world-wide hostile snapshot is reused across all guards. */
    private static final int HOSTILE_CACHE_TICKS = 20;

    /**
     * MotionControllerWalk has no public speed setter, but its horizontal multiplier is the
     * value {@code getMaximumSpeed()} already multiplies by. Cached once; when the field cannot
     * be accessed the guard simply keeps its normal walking speed.
     */
    private static final Field HORIZONTAL_SPEED_MULTIPLIER = findHorizontalSpeedMultiplier();

    private static Field findHorizontalSpeedMultiplier() {
        try {
            Field field = MotionControllerWalk.class.getDeclaredField("horizontalSpeedMultiplier");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Spreads guards across the scan period so they never all check on the same tick.
     *
     * <p>Derived from the NPC's own id so it is stable: a phase that changed between ticks would
     * let a guard skip its turn entirely or take several in a row.
     */
    private static int guardPhase(SimNPCComponent npc) {
        if (npc.entityId == null) return 0;
        return Math.floorMod(npc.entityId.hashCode(), GUARD_SCAN_PERIOD_TICKS);
    }

    private static boolean isHostileModelId(String modelAssetId) {
        if (modelAssetId == null) return false;
        String lower = modelAssetId.toLowerCase(Locale.ROOT);
        for (String keyword : HOSTILE_MODEL_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    public static void handleGuardLogic(Ref<EntityStore> ref, SimNPCComponent npc, RoutineAIComponent ai,
            TransformComponent transform, World world, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {

        if (npc.profession != Profession.GUARD) return;

        /* A child guard would patrol the perimeter looking for skeletons at roughly half height.
        The job stays hers; she just starts doing it at TEEN.
        */
        if (!WorkEligibility.canWork(npc)) return;

        /* Every tick, not just on the scan cadence: cheap (both helpers below bail out
        immediately once the weapon already matches), and a guard that just got promoted
        or switched category should not wait up to a second empty-handed before it shows.
        */
        ensureWeaponEquipped(ref, npc, store);
        setChaseSpeed(ref, ai.currentTask == TaskType.MOVING_TO_FIGHT, store);

        if (ai.currentTask == TaskType.IDLE || ai.currentTask == TaskType.WANDERING) {
            /* Staggered per guard rather than on a shared tick boundary: every guard checking on
            the same tick meant the whole squad's work landed in one frame, which is the shape
            that produces a visible hitch even when the total work is modest.
            */
            if (!ai.forcedByDebug && (world.getTick() + guardPhase(npc)) % GUARD_SCAN_PERIOD_TICKS != 0) {
                return;
            }

            Vector3d pos = transform.getPosition();
            UUID hostileId = scanForHostile(npc, pos, world, store);
            if (hostileId != null) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.workTargetEntityId = hostileId;
                ai.currentTask = TaskType.MOVING_TO_FIGHT;
                ai.taskStartTime = world.getTick();
                return;
            }

            /* Nothing to fight: walk the village edge instead of standing still.

            The perimeter is where the threats arrive from, so patrolling it puts the guard's
            scan radius over the frontier rather than over the middle of town, where it overlaps
            everyone else's. It also gives the village a visible garrison, which is most of the
            point of having guards at all.

            Only an idle guard starts a new patrol leg; a guard already walking one keeps that
            destination. Otherwise every scan tick would recompute the same stop and restart the
            walk animation.
            */
            if (ai.currentTask == TaskType.IDLE) {
                patrolPerimeter(ref, ai, transform, store);
            }
            return;
        }

        if (ai.currentTask == TaskType.MOVING_TO_FIGHT) {
            if (ai.workTargetEntityId == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] Guard {} desistiu de chegar ate o hostil", npc.name);
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            Ref<EntityStore> hostileRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
            TransformComponent hostileTransform = hostileRef != null
                    ? store.getComponent(hostileRef, TransformComponent.getComponentType()) : null;
            if (hostileTransform == null) {
                // Gone already — someone/something else got it, or it wandered out of the world.
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            Vector3d pos = transform.getPosition();
            double dx = hostileTransform.getPosition().x - pos.x;
            double dz = hostileTransform.getPosition().z - pos.z;
            double distSq = dx * dx + dz * dz;
            /* A ranged Guard stops well short of melee range instead of walking into the
            hostile's face — a bow held at 2.5 blocks reads as broken, not as combat.
            */
            double engageRangeSq = npc.guardWeaponCategory == WeaponCategory.RANGED
                    ? RANGED_RANGE_SQ : MELEE_RANGE_SQ;
            if (distSq <= engageRangeSq) {
                NPCMovementHelper.clearMoveTarget(ref, ai);
                ai.currentTask = TaskType.FIGHTING;
                ai.taskStartTime = world.getTick();
                ai.nextGuardAttackTick = world.getTick() + ATTACK_WINDUP_TICKS;
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, hostileTransform.getPosition());
            }
            return;
        }

        if (ai.currentTask == TaskType.FIGHTING) {
            if (ai.workTargetEntityId == null) {
                ai.currentTask = TaskType.IDLE;
                return;
            }
            Ref<EntityStore> hostileRef = world.getEntityStore().getRefFromUUID(ai.workTargetEntityId);
            TransformComponent hostileTransform = hostileRef != null
                    ? store.getComponent(hostileRef, TransformComponent.getComponentType()) : null;
            if (hostileTransform == null) {
                /* It vanished between swings. Usually that means somebody else killed it; there
                is no corpse left to announce, so just return to patrol.
                */
                ai.workTargetEntityId = null;
                ai.currentTask = TaskType.IDLE;
                return;
            }

            long tick = world.getTick();
            double engageRangeSq = npc.guardWeaponCategory == WeaponCategory.RANGED
                    ? RANGED_RANGE_SQ : MELEE_RANGE_SQ;
            double dx = hostileTransform.getPosition().x - transform.getPosition().x;
            double dz = hostileTransform.getPosition().z - transform.getPosition().z;

            /* A mob that backs away turns the exchange back into a chase instead of leaving the
            guard swinging at air from ten blocks away.
            */
            if (dx * dx + dz * dz > engageRangeSq * 1.5) {
                ai.currentTask = TaskType.MOVING_TO_FIGHT;
                ai.taskStartTime = tick;
                return;
            }

            if (isDead(hostileRef, store)) {
                finishFight(npc, ai, transform, store);
                return;
            }

            if (tick >= ai.nextGuardAttackTick) {
                playAttackAnimation(ref, npc, store);
                applyGuardHit(ref, hostileRef, store, commandBuffer);
                ai.nextGuardAttackTick = tick + ATTACK_INTERVAL_TICKS;

                if (isDead(hostileRef, store)) {
                    finishFight(npc, ai, transform, store);
                    return;
                }
            }

            /* Damage events can be cancelled by invulnerable mobs or a plugin in the pipeline.
            The fallback keeps a guard from standing in FIGHTING forever against something that
            cannot be hurt.
            */
            if (tick - ai.taskStartTime > MAX_FIGHT_TICKS) {
                LOGGER.debug("[SimTale] Guard {} could not damage a hostile, removing it after timeout", npc.name);
                commandBuffer.removeEntity(hostileRef, RemoveReason.REMOVE);
                finishFight(npc, ai, transform, store);
            }
        }
    }

    private static void playAttackAnimation(Ref<EntityStore> ref, SimNPCComponent npc,
                                            Store<EntityStore> store) {
        boolean ranged = npc.guardWeaponCategory == WeaponCategory.RANGED;

        /* Attack clips are not part of the Player model's own AnimationSets; they belong to the
        held item's PlayerAnimations asset ("Sword", "Axe", "Bow", ...). Passing the raw
        .blockyanim path to the model-based overload produces exactly the client log line
        "No animation with id ... on entity with model Characters/Player.blockymodel" — the
        guard stood still with no swing. Resolve the item's animation set instead.
        */
        String itemId = npc.guardWeaponItemId != null && !npc.guardWeaponItemId.isEmpty()
                ? npc.guardWeaponItemId : weaponItemIdFor(npc.guardWeaponCategory);
        Item item = Item.getAssetMap() != null ? Item.getAssetMap().getAsset(itemId) : null;
        String itemAnimationsId = item != null ? item.getPlayerAnimationsId() : null;
        if (itemAnimationsId == null || itemAnimationsId.isBlank()) {
            itemAnimationsId = ranged ? "Bow" : "Sword";
        }

        AnimationUtils.playAnimation(ref, AnimationSlot.Action, itemAnimationsId,
                ranged ? ATTACK_RANGED_ANIM : ATTACK_MELEE_ANIM, store);
    }

    /**
     * Applies one real hit through the damage pipeline. Scaling by the target's maximum health
     * keeps the exchange readable for both a 50 HP skeleton and a 226 HP Hedera: roughly three
     * swings either way, rather than one-shotting small mobs and taking forever on big ones.
     */
    private static void applyGuardHit(Ref<EntityStore> guardRef, Ref<EntityStore> hostileRef,
                                      Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        float damage = FALLBACK_HIT_DAMAGE;
        EntityStatMap stats = store.getComponent(hostileRef, EntityStatMap.getComponentType());
        if (stats != null) {
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health != null) {
                damage = Math.max(FALLBACK_HIT_DAMAGE, health.getMax() * HIT_MAX_HEALTH_FRACTION);
            }
        }

        Damage hit = new Damage(new Damage.EntitySource(guardRef), DamageCause.PHYSICAL, damage);
        commandBuffer.invoke(hostileRef, hit);
    }

    private static boolean isDead(Ref<EntityStore> hostileRef, Store<EntityStore> store) {
        if (store.getComponent(hostileRef, DeathComponent.getComponentType()) != null) return true;

        EntityStatMap stats = store.getComponent(hostileRef, EntityStatMap.getComponentType());
        if (stats == null) return false;
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health != null && health.get() <= 0f;
    }

    private static void finishFight(SimNPCComponent npc, RoutineAIComponent ai,
                                    TransformComponent transform, Store<EntityStore> store) {
        LOGGER.debug("[SimTale] Guard {} defeated a hostile", npc.name);
        announceVictory(npc, transform, store);
        ai.workTargetEntityId = null;
        ai.currentTask = TaskType.IDLE;
        ai.taskStartTime = store.getExternalData().getWorld().getTick();
        ai.nextGuardAttackTick = 0;
    }

    /**
     * Temporarily raises the guard's horizontal speed while it closes on a hostile. The engine
     * has no public speed setter; {@code horizontalSpeedMultiplier} is the same field
     * {@code MotionControllerWalk.getMaximumSpeed()} already multiplies by, so this makes the
     * guard actually run rather than merely turning up after a long walk.
     */
    private static void setChaseSpeed(Ref<EntityStore> ref, boolean chasing, Store<EntityStore> store) {
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null || npcEntity.getRole() == null) return;
        MotionController controller = npcEntity.getRole().getActiveMotionController();

        if (HORIZONTAL_SPEED_MULTIPLIER != null && controller instanceof MotionControllerWalk walk) {
            try {
                double current = HORIZONTAL_SPEED_MULTIPLIER.getDouble(walk);
                if (chasing && Math.abs(current - CHASE_SPEED_MULTIPLIER) > 1.0e-6) {
                    HORIZONTAL_SPEED_MULTIPLIER.setDouble(walk, CHASE_SPEED_MULTIPLIER);
                } else if (!chasing && Math.abs(current - CHASE_SPEED_MULTIPLIER) <= 1.0e-6) {
                    HORIZONTAL_SPEED_MULTIPLIER.setDouble(walk, 1.0);
                }
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
                // Leave the normal speed in place if the field ever changes shape.
            }
        }

        /* Ask the animation layer for the run cycle too. The movement controller normally sets
        these flags from speed, but the SimTale roles do not declare a run threshold; forcing
        the flag while chasing keeps the legs in step with the faster movement.
        */
        MovementStatesComponent movement = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (movement == null) return;
        MovementStates states = movement.getMovementStates();
        if (chasing) {
            states.running = true;
            states.walking = false;
        } else if (states.running) {
            states.running = false;
        }
    }

    /** How close a child needs to be to the fight to react to it herself instead of the guard
     *  (wiki/dev/status.md, "reacao a briga" -- part of the dialogue-diversity request). */
    private static final double CHILD_WITNESS_RANGE_SQ = 15.0 * 15.0;

    /** Who hears about it at all -- same 25-block radius {@code
     *  MotherAIManager.broadcastLocalMessage} already uses for "an NPC said something nearby". */
    private static final double HEARING_RANGE_SQ = 25.0 * 25.0;

    /**
     * A guard defeating a hostile is small village news (wiki/dev/status.md: "commentary on...
     * heroic guard"). If a child happens to be close enough to have watched, she reacts to it
     * herself instead of the guard -- wide-eyed, not detached, the way a kid would -- otherwise
     * the guard gets to say her own triumphant line. Exactly one chat line per victory either
     * way, never both.
     * <p>
     * Reuses {@link SimTale#ACTIVE_NPCS} the same read-only way {@code
     * NpcContextBuilder.isTopInProfession} already scans it for "best in the village" -- no new
     * registry, nothing persisted, nothing shared with {@link #hostiles} (that cache is scoped to
     * a different keyword set and this is a one-off lookup, not a per-tick sweep).
     */
    private static void announceVictory(SimNPCComponent npc, TransformComponent transform, Store<EntityStore> store) {
        Vector3d pos = transform.getPosition();

        SimNPCComponent witness = null;
        double bestD2 = CHILD_WITNESS_RANGE_SQ;
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other == npc || !InteractionManager.isNpcAChild(other)) continue;
            Ref<EntityStore> otherRef = other.entityRef;
            if (otherRef == null || !otherRef.isValid()) continue;
            TransformComponent ot = otherRef.getStore().getComponent(otherRef, TransformComponent.getComponentType());
            if (ot == null) continue;
            double dx = ot.getPosition().x - pos.x;
            double dy = ot.getPosition().y - pos.y;
            double dz = ot.getPosition().z - pos.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                witness = other;
            }
        }

        Message line = witness != null
                ? Message.raw(witness.name + ": ").insert(pickRandomTranslation("npc-dialogues.world_event.guard_victory_witness"))
                : Message.raw(npc.name + ": ").insert(pickRandomTranslation("npc-dialogues.world_event.guard_victory"));

        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> pRef = pr.getReference();
            if (pRef == null || !pRef.isValid()) continue;
            TransformComponent pt = store.getComponent(pRef, TransformComponent.getComponentType());
            if (pt == null) continue;
            double dx = pt.getPosition().x - pos.x;
            double dy = pt.getPosition().y - pos.y;
            double dz = pt.getPosition().z - pos.z;
            if (dx * dx + dy * dy + dz * dz <= HEARING_RANGE_SQ) {
                pr.sendMessage(line);
            }
        }
    }

    /** Local copy of the same pick-a-numbered-variant idiom {@code InteractionManager} and
     *  {@code RoutineAISystem} each already keep privately, rather than a third class depending
     *  on either one's private method. */
    private static Message pickRandomTranslation(String baseKey) {
        int index = ThreadLocalRandom.current().nextInt(1, 3 + 1);
        return Message.translation(baseKey + "." + index);
    }

    /**
     * Item id standing in for a Guard's weapon in its actual hand -- not the same list as
     * {@link com.cookieukw.SimTale.logic.NPCShowcaseItems#forProfession}, which is a fixed
     * sword used only for the interaction-menu icon and never varies with category.
     */
    private static String weaponItemIdFor(WeaponCategory category) {
        return category == WeaponCategory.RANGED ? "Weapon_Shortbow_Copper" : "Weapon_Sword_Copper";
    }

    /**
     * Puts the weapon that earned the Guard title in the NPC's actual hand, instead of the
     * bare-fisted "Smith"/"LookAround" mime that {@link #handleGuardLogic} played before this --
     * no profession showed anything in-hand at all, this one included.
     * <p>
     * Reuses the exact hotbar-equip path a vanilla NPC role JSON drives via its own
     * {@code "Inventory"}/{@code "EquipHotbar"} actions (confirmed by decompiling
     * {@code ActionInventory} and cross-checking against a Skeleton role that visibly draws a
     * blade the same way) rather than inventing a new mechanism: put the weapon in hotbar slot
     * 0 and make that the active slot. Both {@link InventoryHelper} calls already no-op once
     * the guard is holding the right thing, so this is safe to call unconditionally every tick.
     */
    private static void ensureWeaponEquipped(Ref<EntityStore> ref, SimNPCComponent npc, Store<EntityStore> store) {
        /* Prefer the exact item that promoted this NPC to Guard (an Iron sword shows up as an
        Iron sword) -- the category-based stand-in only covers saves from before
        guardWeaponItemId existed, or the practically-impossible case where it was cleared.
        */
        String itemId = npc.guardWeaponItemId != null && !npc.guardWeaponItemId.isEmpty()
                ? npc.guardWeaponItemId : weaponItemIdFor(npc.guardWeaponCategory);
        if (InventoryHelper.setHotbarItem(ref, itemId, (byte) 0, store)) {
            InventoryHelper.setHotbarSlot(ref, (byte) 0, store);
        }
    }

    /**
     * Nearest hostile within {@link #GUARD_SCAN_RADIUS} of {@code center}, or null.
     *
     * <p>Reads a shared snapshot instead of sweeping the world. The sweep itself visits every
     * modelled entity there is — mobs, dropped items, projectiles, the other NPCs — because there
     * is no hostile or faction component to query against, so the model id can only be checked
     * after the entity has already been visited. Doing that once per guard per second meant the
     * cost scaled with the number of guards while answering the same question every time.
     *
     * <p>Now the sweep runs at most once per {@link #HOSTILE_CACHE_TICKS} for the whole world and
     * every guard filters the resulting handful of positions, which is the part that legitimately
     * differs between them.
     */
    private static UUID scanForHostile(SimNPCComponent guard, Vector3d center, World world, Store<EntityStore> store) {
        double bestDistSq = Double.MAX_VALUE;
        UUID best = null;

        for (Hostile hostile : hostiles(world, store)) {
            double dx = hostile.x() - center.x;
            double dy = hostile.y() - center.y;
            double dz = hostile.z() - center.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            VillageManager.Village hostileVillage = VillageManager.nearest(world, hostile.x(), hostile.z());
            boolean insideVillage = hostileVillage != null && hostileVillage.contains(hostile.x(), hostile.z());

            if (insideVillage) {
                /* Village alarm: range is the whole territory, but only the closest guards from
                that same village answer. Without the second check every guard in town would
                abandon their patrol for the same skeleton.
                */
                if (!answersVillageAlarm(guard, center, hostileVillage, hostile, store)) continue;
            } else if (distSq > GUARD_SCAN_RADIUS_SQ) {
                // Outside a village the old local-scan rule still applies.
                continue;
            }

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hostile.id();
            }
        }

        return best;
    }

    /**
     * Whether this guard is one of the {@link #VILLAGE_RESPONSE_GUARDS} closest guards belonging
     * to the village the hostile entered.
     *
     * <p>Distances are measured from the hostile. Ties are broken by UUID so two guards standing
     * the same distance away cannot both count themselves as "closest" and both stay home.
     */
    private static boolean answersVillageAlarm(SimNPCComponent guard, Vector3d guardPos,
                                               VillageManager.Village village, Hostile hostile,
                                                Store<EntityStore> store) {
        if (guard.entityId == null) return false;

        double guardDistSq = village.distanceSqTo(guardPos.x, guardPos.z);
        double responseRadius = village.radius() + VILLAGE_RESPONSE_MARGIN;
        if (guardDistSq > responseRadius * responseRadius) return false;

        World world = store.getExternalData().getWorld();
        VillageManager.Village guardVillage = VillageManager.nearest(world, guardPos.x, guardPos.z);
        if (guardVillage == null || !guardVillage.equals(village)) return false;

        double hostileDistSq = distanceSq(hostile, guardPos);
        String guardKey = guard.entityId.toString();
        int closer = 0;

        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other == guard || other.profession != Profession.GUARD
                    || !WorkEligibility.canWork(other) || other.entityId == null) {
                continue;
            }
            Ref<EntityStore> otherRef = other.entityRef;
            if (otherRef == null || !otherRef.isValid() || otherRef.getStore() != store) continue;

            TransformComponent otherTransform = store.getComponent(otherRef, TransformComponent.getComponentType());
            if (otherTransform == null) continue;

            Vector3d otherPos = otherTransform.getPosition();
            VillageManager.Village otherVillage = VillageManager.nearest(world, otherPos.x, otherPos.z);
            if (otherVillage == null || !otherVillage.equals(village)) continue;
            if (village.distanceSqTo(otherPos.x, otherPos.z) > responseRadius * responseRadius) continue;

            /* A sleeping guard, or one already running an errand on the far side of town, is not
            part of the response. Guards on patrol (WANDERING) and guards already converging on
            this same hostile do count; otherwise a closer but unavailable guard could use up one
            of the two response slots and leave the alarm unanswered.
            */
            RoutineAIComponent otherAi = store.getComponent(otherRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (otherAi != null
                    && otherAi.currentTask != TaskType.IDLE
                    && otherAi.currentTask != TaskType.WANDERING
                    && !hostile.id().equals(otherAi.workTargetEntityId)) {
                continue;
            }

            double otherDistSq = distanceSq(hostile, otherPos);
            if (otherDistSq < hostileDistSq
                    || (otherDistSq == hostileDistSq && other.entityId.toString().compareTo(guardKey) < 0)) {
                closer++;
                if (closer >= VILLAGE_RESPONSE_GUARDS) return false;
            }
        }

        return true;
    }

    private static double distanceSq(Hostile hostile, Vector3d pos) {
        double dx = hostile.x() - pos.x;
        double dy = hostile.y() - pos.y;
        double dz = hostile.z() - pos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** How far around the circle a guard advances each leg. Twelve stops make a full circuit. */
    private static final double PATROL_STEP_RADIANS = Math.PI / 6.0;

    /** Close enough to call a patrol stop reached. */
    private static final double PATROL_REACH_SQ = 3.0 * 3.0;

    /**
     * Sends the guard to the next stop around its village's edge.
     *
     * <p>Reuses {@code WANDERING} rather than adding a task type: from the routine's point of view
     * this *is* a walk to a point that ends by returning to IDLE, and the existing state already
     * carries the timeout and give-up handling. What makes it a patrol is that the destination
     * advances by a fixed angle each time instead of being drawn at random.
     *
     * <p>A guard with no village stays put. Wandering off to guard nothing is what the old
     * behaviour effectively did.
     */
    private static void patrolPerimeter(Ref<EntityStore> ref, RoutineAIComponent ai,
                                        TransformComponent transform, Store<EntityStore> store) {

        Vector3d pos = transform.getPosition();
        World world = store.getExternalData().getWorld();
        VillageManager.Village village = VillageManager.nearest(world, pos.x, pos.z);
        if (village == null) return;

        /* Start the circuit where the guard already stands, so it does not march across town to
        reach an arbitrary "stop one".
        */
        if (ai.patrolAngle == 0) {
            ai.patrolAngle = Math.atan2(pos.z - village.centerZ(), pos.x - village.centerX());
        }

        double targetX = village.centerX() + Math.cos(ai.patrolAngle) * village.radius();
        double targetZ = village.centerZ() + Math.sin(ai.patrolAngle) * village.radius();

        double dx = targetX - pos.x;
        double dz = targetZ - pos.z;
        if (dx * dx + dz * dz < PATROL_REACH_SQ) {
            ai.patrolAngle += PATROL_STEP_RADIANS;
            return;
        }

        ai.currentTask = TaskType.WANDERING;
        ai.wanderTimer = 0;
        ai.targetBlockPosition = new Vector3i((int) targetX, (int) pos.y, (int) targetZ);
        NPCMovementHelper.playAnim(ref, "Characters/Animations/Actions/Walk.blockyanim", "Walk", store);
    }

    /** A hostile's identity and where it was when the snapshot was taken. */
    private record Hostile(UUID id, double x, double y, double z) {
    }

    /**
     * Snapshot of every hostile in the world, rebuilt at most once per {@link #HOSTILE_CACHE_TICKS}.
     *
     * <p>Deliberately stale by up to a second. A guard reacting to where a skeleton was a moment
     * ago is indistinguishable in play from one reacting instantly, and the target is re-resolved
     * by id before the guard commits to anything.
     *
     * <p>The fields are {@code volatile} and the list is replaced whole rather than mutated, so a
     * reader always sees a complete snapshot. Two ticking threads racing here would at worst each
     * build one — wasteful but harmless, and cheaper than locking on every read.
     */
    private static volatile List<Hostile> hostileCache = List.of();
    private static volatile World hostileCacheWorld = null;

    /**
     * Tick of the last sweep, or {@code -1} when there has not been one.
     *
     * <p>Not {@code Long.MIN_VALUE}. {@code tick - Long.MIN_VALUE} overflows to a large negative
     * number, which is always below the interval, so the freshness check passed forever and the
     * cache was never built — every guard would have read the same empty list and seen no hostiles
     * at all. The map provider shipped with the identical mistake and proved it in the log.
     */
    private static volatile long hostileCacheTick = -1L;

    private static List<Hostile> hostiles(World world, Store<EntityStore> store) {
        long tick = world.getTick();
        if (world == hostileCacheWorld && hostileCacheTick >= 0
                && tick - hostileCacheTick < HOSTILE_CACHE_TICKS) {
            return hostileCache;
        }

        List<Hostile> found = new ArrayList<>();
        store.forEachChunk(PersistentModel.getComponentType(), (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                PersistentModel pm = chunk.getComponent(i, PersistentModel.getComponentType());
                if (pm == null || !isHostileModelId(pm.getModelReference().getModelAssetId())) {
                    continue;
                }
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                UUIDComponent uuidComp = chunk.getComponent(i, UUIDComponent.getComponentType());
                if (t == null || uuidComp == null) continue;

                Vector3d p = t.getPosition();
                found.add(new Hostile(uuidComp.getUuid(), p.x, p.y, p.z));
            }
        });

        hostileCache = List.copyOf(found);
        hostileCacheTick = tick;
        hostileCacheWorld = world;
        return hostileCache;
    }
}
