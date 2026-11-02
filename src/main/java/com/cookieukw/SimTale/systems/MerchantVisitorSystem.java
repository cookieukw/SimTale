package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.MerchantVisitorComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Removes temporary travelling merchants after their visit expires. */
public final class MerchantVisitorSystem extends EntityTickingSystem<EntityStore> {
    @Override
    public Query<EntityStore> getQuery() {
        return SimTale.MERCHANT_VISITOR_COMPONENT_TYPE;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull com.hypixel.hytale.component.Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        MerchantVisitorComponent visitor = chunk.getComponent(index, SimTale.MERCHANT_VISITOR_COMPONENT_TYPE);
        if (visitor == null) return;
        if (chunk.getReferenceTo(index).isValid()
                && store.getExternalData().getWorld().getTick() >= visitor.departureTick) {
            commandBuffer.removeEntity(chunk.getReferenceTo(index), com.hypixel.hytale.component.RemoveReason.REMOVE);
        }
    }
}
