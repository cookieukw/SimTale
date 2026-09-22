package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/**
 * Makes the baby you are carrying babble once in a while.
 *
 * <p>A newborn is an item, not an entity — {@code birthPlayerBaby} spawns one only long enough to
 * mint a UUID and removes it again — so it has no AI, no mouth and no way to exist in the world.
 * Which means the only place it can be a character at all is the chat of whoever is holding it.
 *
 * <p>Cheap by construction: it only looks at players, only every {@link #CHECK_INTERVAL_TICKS}
 * ticks, and gets as far as reading the held item only for the handful of players who exist.
 */
public class BabyBabbleSystem extends EntityTickingSystem<EntityStore> {

    /** Item id of a carried newborn. */
    private static final String BABY_ITEM_ID = "Baby";

    /** Roughly every 15 seconds, a chance to babble. */
    private static final int CHECK_INTERVAL_TICKS = 20 * 15;

    /**
     * Chance per check. Deliberately low: a baby that gurgles on a fixed timer stops reading as a
     * baby and starts reading as a notification.
     */
    private static final double BABBLE_CHANCE = 0.35;

    /** How many babble lines ship in the .lang files. */
    private static final int BABBLE_VARIANTS = 8;

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        World world = WorldUtil.first();
        if (world == null) return;

        // Staggered by chunk index so two players carrying babies do not babble in lockstep.
        if ((world.getTick() + index) % CHECK_INTERVAL_TICKS != 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= BABBLE_CHANCE) return;

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        ItemStack held = InventoryComponent.getItemInHand(store, playerRef);
        if (held == null || !BABY_ITEM_ID.equals(held.getItemId())) return;

        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) return;

        int variant = ThreadLocalRandom.current().nextInt(1, BABBLE_VARIANTS + 1);
        player.sendMessage(Message.translation("npc-dialogues.baby.babble." + variant));
    }
}
