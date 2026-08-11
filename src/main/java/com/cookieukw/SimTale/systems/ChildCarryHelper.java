package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.ParentChildBond;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3f;

import java.util.UUID;

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
 * <p><b>Releasing.</b> Crouch and right-click. That gesture rather than a button, because the
 * interaction panel is exactly what you cannot reach while the child is riding on you — she is not
 * in front of the camera to be clicked. Crouch alone was rejected: players hold crouch constantly to
 * avoid walking off ledges, and dropping a child every time would be maddening. The engine's own
 * dismount input is no help either: {@code MountSystems$HandleMountInput} only reacts to the
 * <em>rider</em>'s input, and the rider here is an NPC with no input at all.
 */
public final class ChildCarryHelper {

    private static final SimLog LOGGER = SimLog.forClass(ChildCarryHelper.class);

    /** Where the child sits, relative to the carrier: on the shoulders. */
    private static final float SHOULDER_HEIGHT = 1.55f;

    /** Beyond this the child is too big to be carried, whoever is asking. */
    private static final GrowthStage OLDEST_CARRIABLE = GrowthStage.CHILD;

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
                    carrier, new Vector3f(0f, SHOULDER_HEIGHT, 0f), MountController.Minecart);
            store.putComponent(childRef, MountedComponent.getComponentType(), mounted);
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
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) continue;

            MountedComponent mounted = store.getComponent(npc.entityRef, MountedComponent.getComponentType());
            if (mounted == null) continue;

            Ref<EntityStore> mount = mounted.getMountedToEntity();
            if (mount != null && mount.equals(carrier)) return npc;
        }
        return null;
    }
}
