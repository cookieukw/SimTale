package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NpcFreezeUtil;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.ParentChildBond;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carrying a small child on your shoulders.
 *
 * <p>Uses the engine's own mount system rather than turning the child back into an item. The
 * alternative — despawn the entity, hand the player a "Child" item, respawn on release — is the
 * shape the newborn already uses, and it costs an entity round-trip plus a UUID remap every time.
 * Mounting keeps the same entity alive the whole way through, and the engine already carries
 * mounted entities along when the mount moves or teleports ({@code MountSystems$TeleportMountedEntity})
 * and cleans up if the carrier dies.
 *
 * <p><b>The gesture needs a target.</b> Confirmed from a log: right-clicking at open air produced
 * no {@code PlayerMouseButtonEvent} at all — not a single one of the unconditional "event fired"
 * lines at the top of SimTaleEventHandler appeared, while {@code /simtale putdown} worked in the
 * same session. The event only reaches the server when the click hits something, which is why the
 * hint tells the player to aim at the ground and why the command exists as the guaranteed way out.
 *
 * <p><b>Releasing.</b> Crouch and right-click. That gesture rather than a button, because the
 * interaction panel is exactly what you cannot reach while the child is riding on you — she is not
 * in front of the camera to be clicked. Crouch alone was rejected: players hold crouch constantly to
 * avoid walking off ledges, and dropping a child every time would be maddening. The engine's own
 * dismount input is no help either: {@code MountSystems$HandleMountInput} only reacts to the
 * <em>rider</em>'s input, and the rider here is an NPC with no input at all.
 */
public final class ChildCarryHelper {

    private static final SimLog LOGGER = SimLog.forClass(ChildCarryHelper.class);

    /** Where the first child sits, relative to the carrier: on the shoulders. */
    private static final float SHOULDER_HEIGHT = 1.55f;

    /**
     * Vertical gap between one child and the next one up.
     *
     * <p>Three children all took {@code SHOULDER_HEIGHT}, so they were drawn in exactly the same
     * place and read as one glitched entity with three nameplates. A child at the CHILD stage is
     * about 0.70 of an adult, which is roughly 1.2 blocks tall; 0.85 leaves them clearly separated
     * without a visible gap between feet and shoulders.
     */
    private static final float STACK_STEP = 0.85f;

    /**
     * How tall the tower may get.
     *
     * <p>Not a technical limit — the mount system does not care — but past three the top child is
     * above the block the camera clips against, and a tower nobody can see the top of is worse than
     * a refusal that says why.
     */
    private static final int MAX_STACK = 3;

    /** Beyond this the child is too big to be carried, whoever is asking. */
    private static final GrowthStage OLDEST_CARRIABLE = GrowthStage.CHILD;

    /**
     * Collision boxes parked while their owner is being carried, keyed by child.
     * <p>
     * Kept rather than rebuilt: the box is derived from the model and the current scale, and a
     * child who grows a stage mid-carry would come back down with the wrong one. Storing the exact
     * component removes the question.
     */
    private static final Map<UUID, BoundingBox> PARKED_BOXES = new ConcurrentHashMap<>();

    private ChildCarryHelper() {
    }

    /** Whether this NPC is small enough, and young enough, to be picked up at all. */
    public static boolean isCarriable(SimNPCComponent npc, UUID playerUuid) {
        GrowthStage stage = ParentChildBond.stageOf(npc, playerUuid);
        if (stage == null) return false;
        return stage.ordinal() <= OLDEST_CARRIABLE.ordinal();
    }

    /** Whether {@code npc} is currently riding on someone. */
    public static boolean isBeingCarried(Store<EntityStore> store, SimNPCComponent npc) {
        if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) return false;
        return store.getComponent(npc.entityRef, MountedComponent.getComponentType()) != null;
    }

    /**
     * Puts {@code npc} on {@code carrier}'s shoulders.
     *
     * @return true when the child was picked up
     */
    public static boolean pickUp(Store<EntityStore> store, Ref<EntityStore> carrier,
                                 PlayerRef carrierRef, SimNPCComponent npc) {
        if (store == null || carrier == null || !carrier.isValid() || npc == null) return false;
        if (npc.entityRef == null || !npc.entityRef.isValid()) return false;

        if (isBeingCarried(store, npc)) {
            carrierRef.sendMessage(Message.translation("npc-dialogues.carry.already")
                    .param("name", npc.name));
            return false;
        }

        // Where this one goes: on the shoulders if she is the first, on the previous child's
        // shoulders otherwise. Counting the existing stack is what turns "three children occupying
        // the same point" into a tower.
        int alreadyCarried = carriedBy(store, carrier).size();
        if (alreadyCarried >= MAX_STACK) {
            carrierRef.sendMessage(Message.translation("npc-dialogues.carry.stackFull")
                    .param("count", MAX_STACK));
            return false;
        }
        final float ridingHeight = SHOULDER_HEIGHT + alreadyCarried * STACK_STEP;

        // The routine has to stand down first. A carried child still ticks, and an AI that keeps
        // setting leash points and walking states while its body is pinned to someone's shoulders
        // is how an NPC ends up sliding along the floor — the exact failure mode that took a whole
        // session to diagnose the first time.
        RoutineAIComponent ai = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
        if (ai != null) {
            NPCMovementHelper.clearMoveTarget(npc.entityRef, ai);
            ai.currentTask = RoutineAIComponent.TaskType.IDLE;
        }

        // Structural write from inside an interaction's processing window — deferred like every
        // other one in this project.
        // Built through the constructor, not by mutating: every field on MountedComponent is
        // private and there are no setters, so the three-arg constructor is the only way in.
        //
        // MountController.Minecart is the entity-mount controller — the enum has exactly two
        // values, and the other one (BlockMount) is for chairs and beds. It normally means "the
        // rider steers", which is harmless here because the rider is an NPC with no input at all.
        // This is the one part of this feature that could not be confirmed from the bytecode
        // alone; if a carried child ends up steering the player, this is the line to look at.
        final Ref<EntityStore> childRef = npc.entityRef;
        WorldUtil.execute(() -> {
            if (!childRef.isValid() || !carrier.isValid()) return;
            MountedComponent mounted = new MountedComponent(
                    carrier, new Vector3f(0f, ridingHeight, 0f), MountController.Minecart);
            store.putComponent(childRef, MountedComponent.getComponentType(), mounted);

            // The collision box has to go while she is up there.
            //
            // Riding on your shoulders puts her hitbox right where your own attack and block
            // raycasts start, so every swing and every mined block hit the child instead. This is
            // the same lesson the plumbob taught: anything parked in front of the player's camera
            // must not carry a real bounding box. Restored on put down.
            BoundingBox box = store.getComponent(childRef, BoundingBox.getComponentType());
            if (box != null && npc.entityId != null) {
                PARKED_BOXES.put(npc.entityId, box);
                store.tryRemoveComponent(childRef, BoundingBox.getComponentType());
            }
        });

        // Freeze plus stop the action animation.
        //
        // Standing the routine down is not enough on its own: the Hytale role keeps running its own
        // Idle instructions underneath, which is what kept a carried child walking on the spot and
        // turning to look around while pinned to a shoulder. This is the same pair the dialogue
        // lock already uses for exactly the same reason.
        NpcFreezeUtil.freeze(store, npc.entityRef);
        AnimationUtils.stopAnimation(npc.entityRef, AnimationSlot.Action, true, store);
        AnimationUtils.stopAnimation(npc.entityRef, AnimationSlot.Status, true, store);

        // Queued after the freeze, which defers itself the same way, so the settle is the last
        // word on her pose.
        WorldUtil.execute(() -> {
            if (childRef.isValid()) {
                settleMovementStates(store, childRef);
            }
        });

        // Being carried by a parent is a happy thing. Without this the mood kept decaying while she
        // rode along, and the plumbob overhead settled on BORED — which reads as the game telling
        // you the child hates being picked up.
        npc.setEmotion(Mood.HAPPY, 0.7f, "carried", WorldUtil.tick());

        carrierRef.sendMessage(Message.translation("npc-dialogues.carry.picked_up")
                .param("name", npc.name));
        // Said once, at the moment it becomes relevant: a gesture nobody is told about is a
        // mechanic that does not exist.
        carrierRef.sendMessage(Message.translation("npc-dialogues.carry.hint"));
        LOGGER.info("[SimTale] {} foi pega no colo", npc.name);
        return true;
    }

    /**
     * Takes whoever this player is carrying off their shoulders.
     *
     * @return true when someone was actually put down
     */
    public static boolean putDown(Store<EntityStore> store, Ref<EntityStore> carrier, PlayerRef carrierRef) {
        if (store == null || carrier == null || !carrier.isValid()) return false;

        SimNPCComponent carried = findCarriedBy(store, carrier);
        if (carried == null) return false;

        final Ref<EntityStore> childRef = carried.entityRef;
        WorldUtil.execute(() -> {
            if (childRef != null && childRef.isValid()) {
                store.tryRemoveComponent(childRef, MountedComponent.getComponentType());

                // Her hitbox comes back, or she stays permanently unhittable and walks through
                // things.
                BoundingBox parked = carried.entityId != null ? PARKED_BOXES.remove(carried.entityId) : null;
                if (parked != null) {
                    store.putComponent(childRef, BoundingBox.getComponentType(), parked);
                }

                // Unfreezing has to happen here, not before the deferral: dropping Frozen while the
                // mount is still attached would let the role start steering a body that is still
                // pinned, which is the sliding-NPC failure again.
                NpcFreezeUtil.unfreeze(store, childRef);
            }
        });

        carrierRef.sendMessage(Message.translation("npc-dialogues.carry.put_down")
                .param("name", carried.name));
        LOGGER.info("[SimTale] {} foi colocada no chao", carried.name);
        return true;
    }

    /**
     * Stops the walk cycle a carried child kept playing on someone's shoulders.
     *
     * <p>Freezing and clearing the AI stops her from <em>moving</em>, but the walk animation is not
     * driven by either. The client plays whatever {@code MovementStates} says, and those flags come
     * from {@code MovementStatesSystem}, which hands the role the entity's {@code Velocity} every
     * tick and lets it decide. Nothing zeroes that velocity when an NPC is frozen mid-stride, so
     * the role kept being told she was moving and kept setting {@code walking} — she walked on the
     * spot for the whole ride.
     *
     * <p>Hence both halves: the velocity so the role stops deriving a walk, and the states so the
     * current frame is corrected instead of waiting for the next recompute. The sync is a plain
     * equals() diff against {@code sentMovementStates}
     * ({@code MovementStatesSystems$TickingSystem}), so the write does reach the client. Only the
     * locomotion flags are touched; crouching, sitting and the fluid flags stay as the engine set
     * them.
     */
    public static void settleMovementStates(Store<EntityStore> store, Ref<EntityStore> childRef) {
        Velocity velocity = store.getComponent(childRef, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
            velocity.setClient(0.0, 0.0, 0.0);
        }

        MovementStatesComponent msc =
                store.getComponent(childRef, MovementStatesComponent.getComponentType());
        if (msc == null) return;

        MovementStates settled = new MovementStates(msc.getMovementStates());
        settled.walking = false;
        settled.running = false;
        settled.sprinting = false;
        settled.jumping = false;
        settled.falling = false;
        settled.fallingFar = false;
        settled.sliding = false;
        settled.climbing = false;
        settled.swimming = false;
        settled.swimJumping = false;
        settled.gliding = false;
        settled.mantling = false;
        settled.rolling = false;
        settled.idle = true;
        settled.horizontalIdle = true;
        msc.setMovementStates(settled);
    }

    /** Whether this player currently has one of our children on their shoulders. */
    public static boolean isCarryingSomeone(Store<EntityStore> store, Ref<EntityStore> carrier) {
        return findCarriedBy(store, carrier) != null;
    }

    /**
     * Whether the player is crouching right now.
     *
     * <p>Read from MovementStates rather than tracked ourselves — the engine already knows, and a
     * flag we maintained would drift the first time a player crouched in a way we did not observe.
     */
    public static boolean isCrouching(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        MovementStatesComponent msc = store.getComponent(playerRef, MovementStatesComponent.getComponentType());
        if (msc == null) return false;
        MovementStates states = msc.getMovementStates();
        return states.crouching || states.forcedCrouching;
    }

    /**
     * The child this player is carrying, or null.
     *
     * <p>Scans the tracked NPCs instead of reading a {@code MountedByComponent} off the carrier,
     * because a player can only be carrying one of ours and the roster is small — and this way
     * there is no second piece of state to keep in sync with the mount.
     */
    private static SimNPCComponent findCarriedBy(Store<EntityStore> store, Ref<EntityStore> carrier) {
        List<SimNPCComponent> stack = carriedBy(store, carrier);
        // The one on top comes off first — taking someone out of the middle would leave the rest
        // floating a step above nothing.
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /**
     * Everyone this player is carrying, ordered from the shoulders upwards.
     *
     * <p>The order is read back from each child's own {@code attachmentOffset} rather than tracked
     * in a map here. That offset is the same value the client draws them at, so the list can never
     * disagree with what the player sees — and there is no second piece of state to keep in sync
     * with the mounts, which is the mistake the plumbob and the bounding box both taught.
     */
    public static List<SimNPCComponent> carriedBy(Store<EntityStore> store, Ref<EntityStore> carrier) {
        List<SimNPCComponent> carried = new ArrayList<>();
        UUID carrierId = uuidOf(store, carrier);
        if (carrierId == null) return carried;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) continue;

            MountedComponent mounted = store.getComponent(npc.entityRef, MountedComponent.getComponentType());
            if (mounted == null) continue;

            Ref<EntityStore> mount = mounted.getMountedToEntity();
            if (mount == null || !mount.isValid()) continue;
            if (carrierId.equals(uuidOf(store, mount))) carried.add(npc);
        }

        carried.sort(Comparator.comparingDouble(npc -> offsetHeight(store, npc)));
        return carried;
    }

    /** Height this child is riding at, or 0 when it cannot be read. */
    private static float offsetHeight(Store<EntityStore> store, SimNPCComponent npc) {
        MountedComponent mounted = store.getComponent(npc.entityRef, MountedComponent.getComponentType());
        if (mounted == null || mounted.getAttachmentOffset() == null) return 0f;
        return mounted.getAttachmentOffset().y();
    }

    /**
     * Identity of an entity, for comparing two references to the same thing.
     *
     * <p>{@code Ref} does not override {@code equals} — it has no {@code equals} method at all, so
     * two references are only "equal" when they are literally the same object. The mount stored one
     * reference at pickup and {@code Player.getReference()} hands out another later, so the old
     * {@code mount.equals(carrier)} test was false for the very player who was carrying the child.
     * That is why crouch + right-click did nothing and why even the diagnostic log line never
     * printed: the whole branch was skipped before crouch was ever read.
     *
     * <p>The index is no substitute — {@code Ref.setIndex} exists, so it moves. The UUID does not.
     */
    private static UUID uuidOf(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }
}
