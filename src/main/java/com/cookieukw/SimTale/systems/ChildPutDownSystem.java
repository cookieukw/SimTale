package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Crouch and click a block to put down the child you are carrying.
 *
 * <p><b>Update (testing_checklist.md #21):</b> this used to be the ONLY way to put a child down,
 * built after a {@code PlayerMouseButtonEvent} listener was believed dead (two sessions of logs
 * with zero lines from it). That diagnosis turned out to be the same {@code .register()} vs
 * {@code .registerGlobal()} bug already known from {@code SimTaleEventHandler} — see the fix noted
 * in {@code SimTale.java} — not a real limitation of the event. The crouch gesture now lives on
 * {@code SimTaleEventHandler} too, and works in open air, which this class never could: being
 * built on {@code UseBlockEvent}, it only ever fired when the click actually landed on a block, so
 * crouching with nothing in range to click did nothing — exactly the gap that was reported.
 *
 * <p>Kept anyway as a second path for the block case specifically: {@code Pre} lets it cancel the
 * click so crouch-clicking a chest puts the child down instead of <em>also</em> opening the chest,
 * which {@code SimTaleEventHandler} alone would not prevent.
 *
 * <p>{@code UseBlockEvent} is a different delivery path, not a rename: it is an {@code EcsEvent}
 * dispatched at the entity that acted, which is why this is an {@code EntityEventSystem} and not an
 * event-registry listener. That is the same split {@link BedPlaceBlockEventSystem} documents.
 */
public class ChildPutDownSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final SimLog LOGGER = SimLog.forClass(ChildPutDownSystem.class);

    public ChildPutDownSystem() {
        super(UseBlockEvent.Pre.class);
    }

    /**
     * Only players carry children, so query the component that identifies one.
     *
     * <p>Narrower than the {@code UUIDComponent} the block systems use, because this reacts to a
     * gesture rather than to a world change: an NPC using a block must not drop anybody.
     */
    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        if (!playerRef.isValid()) return;

        /* Crouch first: it is the cheap half, and it is rare enough that gating the log on it costs
        nothing while still telling us which half failed if this ever goes quiet again.
        */
        if (!ChildCarryHelper.isCrouching(store, playerRef)) return;

        boolean carrying = ChildCarryHelper.isCarryingSomeone(store, playerRef);
        LOGGER.info("[SimTale] carry: uso de bloco agachado, carregando={}", carrying);
        if (!carrying) return;

        PlayerRef playerRefComp =
                store.getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComp == null) return;

        if (ChildCarryHelper.putDown(store, playerRef, playerRefComp)) {
            // Cancelled so the same click does not also open the chest or till the soil under it.
            event.setCancelled(true);
        }
    }
}
