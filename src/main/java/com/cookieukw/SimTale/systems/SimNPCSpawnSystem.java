package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.SimNPCFactory.NPCType;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

public class SimNPCSpawnSystem extends EntityTickingSystem<EntityStore> {

    private long lastSpawnTick = 0;
    private final long systemStartTime = System.currentTimeMillis();

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        long currentTick = System.currentTimeMillis();
        // Give the server 30 seconds to load existing chunk entities first
        if (currentTick - systemStartTime < 30000) {
            return;
        }

        // Check every 10 seconds to avoid spamming
        if (currentTick - lastSpawnTick < 10000) {
            return;
        }

        // Clean up dead/despawned NPCs from the list
        SimTale.ACTIVE_NPCS.removeIf(npc -> npc.entityRef == null);

        // Maximum of 10 active NPCs
        if (SimTale.ACTIVE_NPCS.size() >= 10) {
            return;
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        // Spawn a new NPC nearby
        double offsetX = (Math.random() - 0.5) * 40; // between -20 and 20
        double offsetZ = (Math.random() - 0.5) * 40; // between -20 and 20
        
        // Ensure they don't spawn exactly on the player
        if (Math.abs(offsetX) < 5) offsetX = 5 * Math.signum(offsetX == 0 ? 1 : offsetX);
        if (Math.abs(offsetZ) < 5) offsetZ = 5 * Math.signum(offsetZ == 0 ? 1 : offsetZ);

        Vector3d spawnPos = new Vector3d(
                transform.getPosition().x + offsetX,
                transform.getPosition().y + 5, // A bit higher to avoid spawning in ground
                transform.getPosition().z + offsetZ
        );

        NPCType type = Math.random() > 0.5 ? NPCType.HUMAN_MALE : NPCType.HUMAN_FEMALE;
        
        try {
            SimNPCFactory.spawnNPC(store, spawnPos, type);
            lastSpawnTick = currentTick;
        } catch (Exception e) {
            // Ignore spawn failures (e.g. invalid position)
        }
    }
}
