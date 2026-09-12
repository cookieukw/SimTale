package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;

/**
 * Crouch + jump releases the child you are carrying — a second, independent way to trigger
 * {@link ChildCarryHelper#putDown} alongside {@code SimTaleEventHandler} (crouch + right-click,
 * open air) and {@code ChildPutDownSystem} (crouch + right-click, block only).
 *
 * <p>Built to sidestep a specific mystery rather than solve it (testing_checklist.md #21): a full
 * play session, checked line by line, showed zero evidence that either click-based path ever
 * receives a {@code PlayerMouseButtonEvent} while the player is crouching — not even the fully
 * unconditional diagnostic log placed ahead of every filter in {@code SimTaleEventHandler}, and
 * not even {@code ChildPutDownSystem}'s own log, which fires on {@code isCrouching()} alone. Both
 * paths depend on the client actually sending a mouse-click network packet, which this test
 * session gave no evidence of doing even once, crouched or not.
 *
 * <p>Movement state doesn't have that problem: {@code MovementStates} (crouching, jumping, and
 * everything else in it) is synced every tick via {@code ClientMovement}, a packet the game sends
 * constantly regardless of clicking — and {@link ChildCarryHelper#isCrouching} already reads from
 * it without a single reported failure across this whole mod's history. Checking
 * {@code isCrouching() && isJumping()} here instead of waiting on a click event moves the release
 * gesture onto data already proven reliable.
 *
 * <p>No edge detection needed: the condition below requires {@code isCarryingSomeone()}, and
 * {@code putDown()} makes that false the instant it succeeds — so holding crouch+jump doesn't
 * repeat the action every tick, it just stops matching after the first one.
 *
 * <p>Requiring two simultaneous inputs (not crouch alone) is deliberate, same reasoning that ruled
 * out crouch-alone the first time this gesture was designed: players hold crouch near ledges for
 * unrelated reasons, but they don't also happen to be jumping at that exact moment.
 */
public class ChildCarryReleaseTickSystem extends EntityTickingSystem<EntityStore> {

    private static final SimLog LOGGER = SimLog.forClass(ChildCarryReleaseTickSystem.class);

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        // Cheapest check first: most players, most ticks, are not carrying anyone.
        if (!ChildCarryHelper.isCarryingSomeone(store, playerRef)) return;

        if (!ChildCarryHelper.isCrouching(store, playerRef)) return;
        if (!ChildCarryHelper.isJumping(store, playerRef)) return;

        PlayerRef playerRefComp =
                store.getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComp == null) return;

        boolean putDownOk = ChildCarryHelper.putDown(store, playerRef, playerRefComp);
        LOGGER.info("[SimTale] carry: agachado+pulo liberou o colo -> putDown={}", putDownOk);
    }
}
