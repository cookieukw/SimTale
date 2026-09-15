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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
 * <p><b>Releasing.</b> Crouch and right-click (open air via {@code SimTaleEventHandler}, block-only
 * via {@code ChildPutDownSystem}) — or crouch and jump, via {@link ChildCarryReleaseTickSystem}.
 * Crouch alone was rejected: players hold crouch constantly to avoid walking off ledges, and
 * dropping a child every time would be maddening. The engine's own dismount input is no help
 * either: {@code MountSystems$HandleMountInput} only reacts to the <em>rider</em>'s input, and the
 * rider here is an NPC with no input at all.
 *
 * <p><b>Update (testing_checklist.md #21, 12/09):</b> a full test session showed zero evidence that
 * either click-based path above ever receives a {@code PlayerMouseButtonEvent} while the player is
 * crouching, click-based or block-only — not even the fully unconditional diagnostic log placed
 * ahead of every filter. {@link ChildCarryReleaseTickSystem} was added as a click-free alternative,
 * built entirely on {@code MovementStates} (crouching + jumping), which syncs every tick regardless
 * of clicking and which {@link #isCrouching} already reads without a single reported failure. Both
 * click-based paths are left in place, not removed.
 */
public final class ChildCarryHelper {

    private static final SimLog LOGGER = SimLog.forClass(ChildCarryHelper.class);

    /** Where the first child sits, relative to the carrier: on the shoulders. */
    private static final float SHOULDER_HEIGHT = 1.55f;

    /**
     * Fallback vertical gap, used only when a carried child's real height is not known yet (her
     * BoundingBox has not been parked — should not normally happen for anyone actually mounted).
     *
     * <p>Three children all took {@code SHOULDER_HEIGHT} originally, so they were drawn in exactly
     * the same place and read as one glitched entity with three nameplates; that bug is what this
     * constant fixed at first. It stopped being the real answer once children of different growth
     * stages could end up in the same stack — a fixed step matched only a tower where everyone
     * happened to be the same height, and floated or buried the next child otherwise. The real
     * spacing is now read per-child from {@code PARKED_BOXES}' stored height in {@link #pickUp} and
     * {@link #reseat}; this constant only covers the gap that math cannot yet.
     */
    private static final float STACK_STEP = 0.85f;

    /**
     * How tall the tower may get.
     *
     * <p>Ten puts the top child around 9.2 blocks up, well past anything the camera frames at once.
     * The cap exists so the refusal explains itself instead of the eleventh child silently vanishing
     * into the ceiling — not because the mount system objects.
     */
    private static final int MAX_STACK = 10;

    /** Beyond this the child is too big to be carried, whoever is asking. */
    private static final GrowthStage OLDEST_CARRIABLE = GrowthStage.CHILD;

    /**
     * Each carried child's real collision box, keyed by child, while her live one is hollowed out.
     * <p>
     * Kept rather than rebuilt: the box is derived from the model and the current scale, and a
     * child who grows a stage mid-carry would come back down with the wrong one. Storing the exact
     * shape removes the question.
     * <p>
     * Stores the {@code Box} shape, not the whole {@code BoundingBox} component. An earlier version
     * removed the component entirely while carried — which is exactly what a carried child's own
     * hitbox needed to stop catching the carrier's swings and mining raycasts — but the engine's
     * own {@code BodyMotionFindWithTarget.canComputeMotion} reads {@code accessor.getComponent(self,
     * BoundingBox.getComponentType()).getBoundingBox()} on every entity it ticks with no null guard
     * left in a release build (the assertion that would have caught it compiles out with
     * assertions disabled) — so a still-ticking NPC with no BoundingBox component at all crashed
     * that engine system with a bare NullPointerException the instant it next tried to move her,
     * which is exactly what a growth promotion firing on a carried child does moments later. Also
     * doubles as the source of truth for each carried child's real height when stacking a second
     * child on top of her (see {@link #pickUp} and {@link #reseat}) — her live box reads zero while
     * she is carried, so this map is the only place her actual size still lives.
     */
    private static final Map<UUID, Box> PARKED_BOXES = new ConcurrentHashMap<>();

    /**
     * Stand-in box for a carried child's real one: present (so the engine's own movement AI never
     * dereferences a null BoundingBox, see PARKED_BOXES above) but small enough that nothing can
     * practically land a hit inside it. Deliberately not {@code Box.ZERO} — see the comment where
     * this is applied, in {@link #pickUp}.
     */
    private static final Box HOLLOW_BOX = Box.centeredCube(new org.joml.Vector3d(0, 0, 0), 0.001);

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
        List<SimNPCComponent> existingStack = carriedBy(store, carrier);
        int alreadyCarried = existingStack.size();
        if (alreadyCarried >= MAX_STACK) {
            carrierRef.sendMessage(Message.translation("npc-dialogues.carry.stackFull")
                    .param("count", MAX_STACK));
            return false;
        }

        // Stacked on the ACTUAL height of the child she is landing on, not a one-size-fits-all
        // step. A fixed STACK_STEP looked right only when every rider happened to be the same
        // growth stage: mix a Toddler (scale 0.50) under a Child (0.70) and the fixed gap either
        // buried one in the other or left a visible gap under the next one's feet — "só o primeiro
        // fica encaixado, os outros flutuam acima da cabeça" is exactly a constant gap failing to
        // track a variable height. PARKED_BOXES is read here, not each child's live BoundingBox,
        // because a child already in the stack has hers hollowed to Box.ZERO for exactly the
        // reason documented on that map above.
        final float ridingHeight;
        if (alreadyCarried == 0) {
            ridingHeight = SHOULDER_HEIGHT;
        } else {
            SimNPCComponent below = existingStack.get(existingStack.size() - 1);
            float belowTop = offsetHeight(store, below);
            Box belowBox = below.entityId != null ? PARKED_BOXES.get(below.entityId) : null;
            float belowHeight = belowBox != null ? (float) belowBox.height() : STACK_STEP;
            ridingHeight = belowTop + belowHeight;
        }

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
        //
        // Confirmed safe via bytecode (testing_checklist.md #21): MountSystems$HandleMountInput —
        // the only system that reads a rider's movement input — has an AND query requiring BOTH
        // MountedComponent and PlayerInput on the SAME entity to tick at all. The child here only
        // ever gets MountedComponent; she has no PlayerInput component (only real players do), so
        // this system's query never matches her and its tick() body — the part that would read a
        // movement-update queue and act on it — never runs for her, regardless of controller type.
        // There is no live minecart entity in between either: `carrier` below is the player's own
        // Ref, not a spawned vehicle.
        final Ref<EntityStore> childRef = npc.entityRef;
        WorldUtil.execute(() -> {
            // This check existed already, but until now a failure here failed SILENTLY while
            // the caller had already sent "picked up" and set her mood HAPPY, both unconditional,
            // both synchronous, both already gone through before this deferred block even runs.
            // The gap between "pickUp() was called" and "this lambda actually executes" is a real
            // window — a growth-stage promotion (CHILD -> TEEN) firing in between removes exactly
            // this childRef, since it respawns her under a new entity — and a player who picked up
            // a child right as she aged out from under them got told it worked, watched her mood
            // go happy, and then nothing happened: no one ever appeared on their shoulders. Moving
            // the outcome-dependent parts here, behind the SAME check that was already guarding
            // the mount itself, is what makes the message finally match reality.
            if (!childRef.isValid() || !carrier.isValid()) return;
            MountedComponent mounted = new MountedComponent(
                    carrier, new Vector3f(0f, ridingHeight, 0f), MountController.Minecart);
            store.putComponent(childRef, MountedComponent.getComponentType(), mounted);

            // Her collision box has to stop catching hits while she is up there — but the
            // component itself has to stay. Riding on your shoulders puts her hitbox right where
            // your own attack and block raycasts start, so every swing and every mined block hit
            // the child instead; that part is the same lesson the plumbob taught, anything parked
            // in front of the player's camera must not carry a real hitbox. What the plumbob's
            // lesson missed is that she is not a decorative prop like the crystal — she is a real,
            // still-ticking NPC role, and the engine's own movement AI
            // (BodyMotionFindWithTarget.canComputeMotion) reads her BoundingBox component
            // unconditionally on every tick it processes her, with no null check surviving into a
            // release build. Removing the component outright (the previous version of this code)
            // left that engine system dereferencing null the next time it ticked her — a bare
            // NullPointerException, most visibly the moment a growth promotion fired on a carried
            // child a tick later. Shrinking the box in place with setBoundingBox keeps the
            // component non-null (assign() mutates the existing Box object, it is never replaced)
            // while making it a zero-volume box nothing can hit. The real shape is cloned into
            // PARKED_BOXES first so put down — and stacking a second child on her, see pickUp's
            // ridingHeight above — can still see her actual size.
            BoundingBox box = store.getComponent(childRef, BoundingBox.getComponentType());
            if (box != null && npc.entityId != null) {
                PARKED_BOXES.put(npc.entityId, box.getBoundingBox().clone());
                // NOT Box.ZERO. Confirmed in game (12/09): with an exactly-zero box, the carried
                // child stopped rendering entirely — invisible the whole time she was mounted,
                // reappearing only on put down, at the frozen position her plumbob had been stuck
                // at the whole time. This engine has no real invisibility flag at all — the
                // expedition system (see SimTaleMarkerProvider's own comment on it) already learned
                // that lesson and works around it with scale 0.001, deliberately never exactly 0,
                // for what is presumably this same reason: something in the client's render/attach
                // math treats a truly zero-size box as "nothing to draw" rather than "draw a very
                // small thing here". HOLLOW_BOX is that same 0.001-style epsilon applied to the
                // collision box instead of scale — small enough that no melee swing or mining
                // raycast can practically land inside it, without being the exact zero that broke
                // rendering.
                box.setBoundingBox(HOLLOW_BOX);
            }

            // Being carried by a parent is a happy thing. Without this the mood kept decaying
            // while she rode along, and the plumbob overhead settled on BORED — which reads as
            // the game telling you the child hates being picked up.
            npc.setEmotion(Mood.HAPPY, 0.7f, "carried", WorldUtil.tick());

            carrierRef.sendMessage(Message.translation(
                            alreadyCarried > 0 ? "npc-dialogues.carry.stacked" : "npc-dialogues.carry.picked_up")
                    .param("name", npc.name));
            // Said at the moment it becomes relevant, and only for the first child: a gesture
            // nobody is told about is a mechanic that does not exist, but repeating it up a
            // three-child tower is just noise.
            if (alreadyCarried == 0) {
                carrierRef.sendMessage(Message.translation("npc-dialogues.carry.hint"));
            }
            LOGGER.info("[SimTale] {} foi pega no colo", npc.name);
        });

        // Freeze plus stop the action animation.
        //
        // Standing the routine down is not enough on its own: the Hytale role keeps running its own
        // Idle instructions underneath, which is what kept a carried child walking on the spot and
        // turning to look around while pinned to a shoulder. This is the same pair the dialogue
        // lock already uses for exactly the same reason.
        //
        // Left unconditional and outside the deferred block above (unlike the mount itself): if
        // the race described above does invalidate childRef a moment later, this entity is being
        // deleted anyway as part of that same promotion, so freezing/stopping it first changes
        // nothing observable — there is no risk of leaving a stray NPC stuck frozen with nobody
        // ever having actually picked her up.
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
                // things. The component was never removed (see PARKED_BOXES's javadoc for why),
                // only shrunk to Box.ZERO in place, so restoring is the same in-place write back —
                // never a putComponent, which would mean adding back a component that was never
                // actually gone.
                BoundingBox box = store.getComponent(childRef, BoundingBox.getComponentType());
                Box parked = carried.entityId != null ? PARKED_BOXES.remove(carried.entityId) : null;
                if (box != null && parked != null) {
                    box.setBoundingBox(parked);
                }

                // Unfreezing has to happen here, not before the deferral: dropping Frozen while the
                // mount is still attached would let the role start steering a body that is still
                // pinned, which is the sliding-NPC failure again.
                NpcFreezeUtil.unfreeze(store, childRef);
            }

            // Close the gap the departure left.
            //
            // Nobody is standing on anybody: each child is mounted to the *player* at an absolute
            // height, so removing one does not bring the others down and does not shuffle them
            // along either — it leaves a hole, and the rest keep floating exactly where they were.
            // Normally the one leaving is the top one and there is no hole to close, but a child
            // can also leave from the middle without asking: growing a stage respawns her body, and
            // the mount goes with the old entity.
            reseat(store, carrier);
        });

        carrierRef.sendMessage(Message.translation("npc-dialogues.carry.put_down")
                .param("name", carried.name));
        LOGGER.info("[SimTale] {} foi colocada no chao", carried.name);
        return true;
    }

    /**
     * Re-seats the tower so the heights run shoulders-upward with no gaps.
     *
     * <p>Called after anyone leaves. Written as "assign every seat from scratch" rather than "shift
     * the ones above down", because the second version has to know who left and from where, and
     * gets it wrong the moment two children leave in the same tick — which is exactly what a batch
     * of overdue promotions does.
     *
     * <p>Rebuilt through the constructor because every field on {@code MountedComponent} is private
     * with no setters; the mount is replaced, not edited.
     */
    public static void reseat(Store<EntityStore> store, Ref<EntityStore> carrier) {
        List<SimNPCComponent> stack = carriedBy(store, carrier);
        // Same running-height logic as pickUp's ridingHeight: each seat sits on the actual height
        // of whoever is riding just below it, not a fixed multiple of i. A fixed step re-seated a
        // mixed-stage tower onto heights nobody was actually occupying the moment the child in the
        // middle of it left (the exact case this method exists for).
        float nextHeight = SHOULDER_HEIGHT;
        for (SimNPCComponent npc : stack) {
            if (npc.entityRef == null || !npc.entityRef.isValid()) continue;

            float target = nextHeight;
            MountedComponent current =
                    store.getComponent(npc.entityRef, MountedComponent.getComponentType());
            if (current != null
                    && !(current.getAttachmentOffset() != null
                            && Math.abs(current.getAttachmentOffset().y() - target) < 0.01f)) {
                store.putComponent(npc.entityRef, MountedComponent.getComponentType(),
                        new MountedComponent(carrier, new Vector3f(0f, target, 0f),
                                MountController.Minecart));
            }

            Box parkedBox = npc.entityId != null ? PARKED_BOXES.get(npc.entityId) : null;
            float height = parkedBox != null ? (float) parkedBox.height() : STACK_STEP;
            nextHeight = target + height;
        }
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

    /**
     * Keeps a carried child's OWN {@code TransformComponent} tracking the carrier live, instead
     * of leaving it frozen at wherever she was standing at pickup.
     *
     * <p>Root cause of "crianca some do nada ao pular/voar" (13-14/09): the client draws a
     * carried child from {@code carrier position + attachmentOffset}, never from her own
     * Transform, so nothing about the carry feature itself needed her real position to move --
     * that assumption is documented all over this file and in GrowthManager. But
     * {@code UpdateLocationSystems} (the ONE other place anywhere in HytaleServer.jar that both
     * touches chunk validity and creates a {@code Teleport} component --
     * confirmed by grepping every class in the jar for the two together) runs off that same real
     * Transform for EVERY entity, every tick, to keep its cached chunk-section reference in sync.
     * A child parked at one unmoving spot for the whole carry is exactly what that bookkeeping
     * does not expect: if the carrier flies or walks far enough that her stale position's
     * original chunk section is no longer valid where the check looks for it, it can decide she
     * needs a corrective {@code Teleport} -- and {@code MountSystems$TeleportMountedEntity}
     * reacts to ANY Teleport landing on a mounted entity by silently stripping her
     * {@code MountedComponent}, no message, no fallback. She is left wherever that correction put
     * her, no longer mounted, with nothing in this mod aware anything happened -- which is exactly
     * "desapareceu do nada" and why {@code /simtale putdown} found nobody afterward. Reported
     * reproduction (14/09): a single carried child vanishes on the very first jump while flying in
     * Creative, and a freshly picked-up second child repeats it on her own next jump.
     *
     * <p>Called unconditionally every tick a child is carried (see the
     * {@code ChildCarryHelper.isBeingCarried} branch in {@code RoutineAISystem}), not throttled
     * like the mood/settle top-ups above: a position that always matches wherever the carrier
     * validly is can never itself go stale, which removes the precondition for that chunk check
     * to ever flag her, whatever the exact async timing that trips it. Reads
     * {@code attachmentOffset} straight off her own {@code MountedComponent} rather than
     * recomputing a height, so this can never disagree with what the client is already drawing.
     */
    public static void syncCarriedTransform(Store<EntityStore> store, SimNPCComponent npc) {
        if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) return;

        MountedComponent mounted = store.getComponent(npc.entityRef, MountedComponent.getComponentType());
        if (mounted == null || mounted.getAttachmentOffset() == null) return;

        Ref<EntityStore> carrier = mounted.getMountedToEntity();
        if (carrier == null || !carrier.isValid()) return;

        TransformComponent carrierTransform =
                store.getComponent(carrier, TransformComponent.getComponentType());
        TransformComponent childTransform =
                store.getComponent(npc.entityRef, TransformComponent.getComponentType());
        if (carrierTransform == null || childTransform == null) return;

        Vector3fc offset = mounted.getAttachmentOffset();
        Vector3d carrierPos = carrierTransform.getPosition();
        childTransform.setPosition(new Vector3d(
                carrierPos.x + offset.x(),
                carrierPos.y + offset.y(),
                carrierPos.z + offset.z()));
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
     * True if the player's most recently synced {@link MovementStates} has the jump flag set.
     *
     * <p>Added for the crouch+jump release gesture (testing_checklist.md #21): the crouch+click
     * gesture it replaces depends on {@code PlayerMouseButtonEvent}/{@code UseBlockEvent.Pre}
     * actually being delivered by the client, which a full test session showed zero evidence of
     * (not even the fully unconditional diagnostic log placed ahead of every filter). Movement
     * state, by contrast, is synced every tick via {@code ClientMovement} — the same packet
     * {@link #isCrouching} already reads from without a single reported failure — so building the
     * release gesture on movement state instead of a click event sidesteps that whole mystery
     * rather than solving it.
     */
    public static boolean isJumping(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        MovementStatesComponent msc = store.getComponent(playerRef, MovementStatesComponent.getComponentType());
        if (msc == null) return false;
        MovementStates states = msc.getMovementStates();
        return states.jumping;
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
