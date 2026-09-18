package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyManager;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.Objects;

public class NPCMovementHelper {
    private static final SimLog LOGGER = SimLog.forClass(NPCMovementHelper.class);
    private static final double LEASH_UPDATE_THRESHOLD_SQ = 0.5 * 0.5;
    /**
     * State the NPC roles enter to walk to their leash point.
     * <p>
     * This must match a state the role actually declares. It used to be {@code "Moving"},
     * which no SimTale role defined — every call logged
     * {@code "State 'Moving.null' ... does not exist and was set by an external call"} and the
     * NPC kept its idle animation while the leash dragged it around ("sliding on ice").
     * <p>
     * {@code ReturnHome} is vanilla Hytale's own name for this behaviour — see the state of the
     * same name in {@code _Core/Templates/Template_Intelligent.json}, which pairs a
     * {@code Leash} sensor with a pathfinding {@code Seek}. The SimTale roles gained an
     * equivalent block via {@code scripts/add_returnhome_state.py}; renaming this constant
     * without re-running that script will break NPC movement again.
     */
    public static final String STATE_MOVING = "ReturnHome";

    public static void moveTo(Ref<EntityStore> ref, RoutineAIComponent ai, World world, Vector3d targetPos) {
        /* Without the NPC's name here, this line is useless for telling two NPCs' movement
        apart in a busy log — which one is dragging around near (X,Y,Z) was previously a
        guessing game whenever more than one NPC was active at once.
        */
        SimNPCComponent npc = ref.getStore().getComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE);

        Vector3d effectiveTarget = targetPos;
        /* AUDITORIA.md #4.1 workaround: there is no public API to set an NPC's real movement
        speed (see PregnancyManager.isPausedTick for why), so a pregnant NPC's slowdown is
        simulated here by periodically re-pinning the leash to her own current position
        instead of the real target — reads as a stutter/waddle, not a real speed change.
        Visual approximation only; not yet confirmed in a live game.
        */
        if (npc != null && PregnancyManager.isPausedTick(npc.pregnancy, world.getTick())) {
            TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                effectiveTarget = new Vector3d(transform.getPosition());
            }
        }

        boolean needsUpdate;

        if (ai.lastLeashPos == null) {
            needsUpdate = true;
        } else {
            double d2 = ai.lastLeashPos.distanceSquared(effectiveTarget);
            needsUpdate = d2 > LEASH_UPDATE_THRESHOLD_SQ;
        }

        if (needsUpdate) {
            /* currentTask alongside the name: nine different call sites across five helper
            classes all funnel through here, and a moveTo firing for a task the caller wasn't
            expecting (e.g. an interrupt nobody logged) was previously invisible.
            */
            LOGGER.debug("[SimTale] moveTo({}, task={}) updating leash point to ({},{},{})",
                    npc != null ? npc.name : "?", ai.currentTask, effectiveTarget.x, effectiveTarget.y, effectiveTarget.z);
            ai.lastLeashPos = new Vector3d(effectiveTarget);
            ai.lastLeashTick = world.getTick();
            
            NPCEntity npcEntity = ref.getStore().getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null) {
                npcEntity.setLeashPoint(new Vector3d(effectiveTarget.x, effectiveTarget.y, effectiveTarget.z));
                StateSupport stateSupport = StateSupport.get(ref, ref.getStore());
                if (stateSupport != null) {
                    stateSupport.setState(ref, STATE_MOVING, null, ref.getStore());
                }
            }
        }
    }

    /**
     * Pins the leash at an explicit position, without altering role state.
     *
     * <p>Exists because of a subtle distinction from {@link #clearMoveTarget}: that one
     * locks the leash where the NPC currently <em>is</em> and forces the {@code Idle} state.
     * This works for an NPC who just arrived at a destination on foot, but not for one about
     * to be teleported immediately afterwards — nor for one that needs a distinct state like {@code Sleep}.
     *
     * <p>The concrete case is the bed. The NPC walks to the block <em>next to</em> the bed,
     * clearMoveTarget pins the leash there, and only then is the NPC teleported onto the mattress.
     * The leash continued pointing to the neighbor block, so the role's own AI pulled the NPC
     * right back: sliding off the bed onto the floor all night.
     */
    public static void pinLeashAt(Ref<EntityStore> npcRef, RoutineAIComponent ai, Vector3d pos) {
        if (npcRef == null || pos == null) return;

        if (ai != null) {
            /* Kept in sync with the actual leash, otherwise the next moveTo compares against an
            old value and may conclude that nothing needs updating.
            */
            ai.lastLeashPos = new Vector3d(pos);
        }

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            npcEntity.setLeashPoint(new Vector3d(pos));
        }
    }

    public static void clearMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai) {
        ai.lastLeashPos = null;
        ai.lastLeashTick = 0;

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            TransformComponent transform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null) {
                npcEntity.setLeashPoint(new Vector3d(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z));
            }
            StateSupport stateSupport = StateSupport.get(npcRef, npcRef.getStore());
            if (stateSupport != null) {
                stateSupport.setState(npcRef, "Idle", null, npcRef.getStore());
            }
        }
    }

    public static void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        playAnim(ref, AnimationSlot.Action, anim, name, store);
    }

    public static void playAnim(Ref<EntityStore> ref, AnimationSlot slot, String anim, String name, Store<EntityStore> store) {
        /* This used to forward (anim, name) straight into AnimationUtils.playAnimation, which
        resolves by type to the (itemAnimationsId, animationId, ComponentAccessor) overload --
        putting the real clip path in itemAnimationsId (meant for held-item view animations,
        unrelated here) and the short debug label in animationId, the field the engine actually
        uses to pick the clip.

        The correct value for animationId depends on the slot:
         - Face/Movement/Status/ServerAction: AnimationUtils.playAnimation checks the call against
           model.getAnimationSetMap(), so animationId must be a SET NAME registered on the model
           (own or inherited via "Parent", e.g. "Walk"/"Idle"/"Sleep" come from the base Player
           model) -- that's `name` here, not the raw clip path.
         - Action/Emote: the engine skips that registry check for these two slots (its own
           comment says combat/charging get custom client handling), so there is no registered
           name to match -- `anim`, the real .blockyanim path, is what should go out instead.
        */
        if (slot == AnimationSlot.Action && ("Walk".equals(name) || "Idle".equals(name))) {
            return;
        }
        boolean usesModelRegistry = slot != AnimationSlot.Action && slot != AnimationSlot.Emote;
        LOGGER.debug("[NPCMovementHelper] Playing '{}' ({}) on slot {}", name, anim, slot);
        AnimationUtils.playAnimation(ref, slot, usesModelRegistry ? name : anim, store);
    }

    /**
     * Variant without {@link CommandBuffer}, for use outside of a tick system (commands).
     * <p>
     * {@code store.getComponent} returns the live instance, so mutating fields already alters
     * the component. The {@code replaceComponent} of the full overload exists to signal the
     * update, not for the write itself — and commands have no CommandBuffer to invoke.
     */
    public static void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, boolean sleeping) {
        setSleepingState(ref, store, null, sleeping);
    }

    public static void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, boolean sleeping) {
        MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (msc == null) return;
        MovementStates ms = msc.getMovementStates();
        ms.idle = true;
        ms.horizontalIdle = true;
        ms.walking = false;
        ms.running = false;
        ms.sprinting = false;
        ms.jumping = false;
        ms.falling = false;
        ms.mantling = false;
        ms.sliding = false;
        /* sitting is a SEPARATE flag from sleeping, and was left untouched here. If anything
        enabled it prior, it stayed enabled throughout sleep — and the body was drawn sitting
        instead of lying down. Clearing in both directions is worthwhile: when sleeping and waking,
        we never want the NPC sitting.

        This is not just pose: ModelSystems$UpdateMovementStateBoundingBox derives the collision
        box from these flags, so incorrect sitting/sleeping gives the wrong hitbox — which is
        precisely the cause of the lateral push on the bed.
        */
        ms.sitting = false;
        ms.mounting = sleeping;
        ms.sleeping = sleeping;
        /* Null when invoked from a command (see overload above). Fields have already been
        modified on the live instance; replaceComponent merely signals the change.
        */
        if (commandBuffer != null) {
            commandBuffer.replaceComponent(ref, MovementStatesComponent.getComponentType(), msc);
        }
    }

    public static void setSittingState(Ref<EntityStore> ref, Store<EntityStore> store, boolean sitting) {
        setSittingState(ref, store, null, sitting);
    }

    public static void setSittingState(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, boolean sitting) {
        MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (msc == null) return;
        MovementStates ms = msc.getMovementStates();
        ms.idle = true;
        ms.horizontalIdle = true;
        ms.walking = false;
        ms.running = false;
        ms.sprinting = false;
        ms.jumping = false;
        ms.falling = false;
        ms.mantling = false;
        ms.sliding = false;
        ms.sitting = sitting;
        ms.mounting = sitting;
        ms.sleeping = false;
        if (commandBuffer != null) {
            commandBuffer.replaceComponent(ref, MovementStatesComponent.getComponentType(), msc);
        }
    }

    /**
     * A spot beside the bed the NPC can stand on, or null when there is none.
     *
     * <p>Only the four orthogonal neighbours of the anchor used to be considered. A bed spans six
     * blocks, so the neighbours along the bed's own axis are its filler blocks and never standable
     * — which leaves very few options, and a bed pushed against a wall can leave none at all.
     * Diagonals and the row one block further out are searched too.
     */
    public static Vector3i findStandableBeside(Vector3i bedPos, TransformComponent transform, World world) {
        Vector3i[] candidates = {
            // Orthogonal neighbours first: they read as "getting out of bed" rather than a hop.
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z - 1),
            // Diagonals.
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z - 1),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z - 1),
            // Two blocks out, to clear the far end of the bed itself.
            new Vector3i(bedPos.x + 2, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x - 2, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z + 2),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z - 2)
        };

        Vector3d npcPos = transform.getPosition();
        Vector3i best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Vector3i c : candidates) {
            if (!isStandable(c, world)) continue;
            double dx = (c.x + 0.5) - npcPos.x;
            double dz = (c.z + 0.5) - npcPos.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = c;
            }
        }

        return best;
    }

    /**
     * Walk target for reaching the bed. Falls back to the bed itself, which is fine here: this is
     * only a destination to walk toward, and arriving on top of the bed still triggers mounting.
     *
     * <p>Do NOT reuse this for the wake-up teleport — see {@link #findStandableBeside}.
     */
    public static Vector3i getBedApproachPosition(Vector3i bedPos, TransformComponent transform, World world) {
        Vector3i standable = findStandableBeside(bedPos, transform, world);
        return standable != null ? standable : bedPos;
    }

    /**
     * Whether a straight line from {@code from} to the centre of {@code target} is free of solid
     * blocks.
     *
     * <p>Exists because "is the NPC close enough to get into bed?" used to be a flat XZ distance
     * test. An NPC standing outside the house, with nothing but a wall between it and the bed,
     * passed that test and mounted straight through the wall — it looked like the NPC vanished.
     *
     * <p>Deliberately cheap: it samples the segment rather than doing a proper voxel traversal, and
     * ignores the endpoints. It only has to reject a wall, not be exact.
     */
    public static boolean hasClearPath(World world, Vector3d from, Vector3i target) {
        if (world == null) return false;

        // If NPC is already within adjacent reach on similar elevation, path is clear
        double dxDirect = (target.x + 0.5) - from.x;
        double dzDirect = (target.z + 0.5) - from.z;
        double dyDirect = Math.abs((target.y + 0.5) - from.y);
        if ((dxDirect * dxDirect + dzDirect * dzDirect) <= 2.25 && dyDirect <= 1.2) {
            return true;
        }

        double tx = target.x + 0.5;
        double ty = target.y + 0.8; // Torso level to avoid hitting floor voxels
        double tz = target.z + 0.5;

        double fromY = from.y + 0.8; // Torso level

        double dx = tx - from.x;
        double dy = ty - fromY;
        double dz = tz - from.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(distance * 2);
        if (steps <= 1) return true;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int bx = (int) Math.floor(from.x + dx * t);
            int by = (int) Math.floor(fromY + dy * t);
            int bz = (int) Math.floor(from.z + dz * t);

            if (bx == target.x && by == target.y && bz == target.z) continue;

            BlockType block = world.getBlockType(bx, by, bz);
            if (isPassable(block)) continue;

            return false;
        }
        return true;
    }

    public static boolean isPassable(BlockType type) {
        if (type == null || type.getId() == null) return true;
        String id = type.getId();
        if (id.equalsIgnoreCase("Empty") || id.equalsIgnoreCase("Air")) return true;
        if (BedRegistry.isBedId(id) || ChairRegistry.isChair(id)) return true;
        String lower = id.toLowerCase();
        return lower.contains("door") || lower.contains("gate") || lower.contains("trapdoor")
                || lower.contains("carpet") || lower.contains("rug") || lower.contains("mat")
                || lower.contains("flower") || lower.contains("grass") || lower.contains("plant")
                || lower.contains("fern");
    }

    private static boolean isAir(BlockType type) {
        if (type == null || type.getId() == null) return true;
        String id = type.getId();
        return id.equalsIgnoreCase("Empty") || id.equalsIgnoreCase("Air");
    }

    private static boolean isPassableFloorDecor(BlockType type) {
        if (type == null || type.getId() == null) return true;
        String lower = type.getId().toLowerCase();
        return lower.contains("carpet") || lower.contains("rug") || lower.contains("mat")
                || lower.contains("flower") || lower.contains("grass") || lower.contains("fern");
    }

    public static boolean isStandable(Vector3i pos, World world) {
        BlockType atPos = world.getBlockType(pos.x, pos.y, pos.z);
        if (!isAir(atPos) && !isPassableFloorDecor(atPos)) {
            return false;
        }

        BlockType above = world.getBlockType(pos.x, pos.y + 1, pos.z);
        if (!isAir(above)) {
            return false;
        }

        BlockType below = world.getBlockType(pos.x, pos.y - 1, pos.z);
        return !isAir(below);
    }
}
