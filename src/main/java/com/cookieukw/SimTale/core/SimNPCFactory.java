package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.systems.NewSpawnStartTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.Message;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.SimTale;
import it.unimi.dsi.fastutil.Pair;
import java.util.UUID;

/**
 * Factory for creating SimTale NPCs with proper models and components.
 */
public class SimNPCFactory {

    public enum NPCType {
        SLOTHIAN("SimTale_Passive", "Slothian"),
        TRORK("SimTale_Passive", "Trork");

        public final String roleId;
        public final String appearanceId;

        NPCType(String roleId, String appearanceId) {
            this.roleId = roleId;
            this.appearanceId = appearanceId;
        }
    }

    @SuppressWarnings("unchecked")
    public static Ref<EntityStore> spawnNPC(Store<EntityStore> store, Vector3d position, NPCType type) {
        // 1. Spawn the NPC using the official Hytale NPC system
        Pair<Ref<EntityStore>, ?> result = NPCPlugin.get().spawnNPC(
            store, 
            type.roleId, 
            type.appearanceId, 
            position, 
            new Vector3f(0f, 0f, 0f)
        );

        Ref<EntityStore> ref = result.left();
        ComponentAccessor<EntityStore> accessor = (ComponentAccessor<EntityStore>) ref.getStore();
        
        // 2. Add SimTale custom components to the spawned entity
        UUIDComponent uuidComp = accessor.getComponent(ref, UUIDComponent.getComponentType());
        UUID entityId = uuidComp.getUuid();
        
        String name = type.name() + "_" + entityId.toString().substring(0, 4);
        SimNPCComponent simComponent = new SimNPCComponent(entityId, name);

        // Try to load existing data if available
        SimNPCPersistence.loadNPC(simComponent);

        accessor.addComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE, simComponent);
        
        // 3. Add overhead name plate
        accessor.putComponent(ref, DisplayNameComponent.getComponentType(), new DisplayNameComponent(Message.raw(name)));

        // 4. ACTIVATE AI: Queue for ticking
        // This is mandatory for NPCs spawned via API to start their AI logic.
        NewSpawnStartTickingSystem.queueNewSpawn(ref, store);
        
        // 5. Track for chat system
        SimTale.ACTIVE_NPCS.add(simComponent);

        return ref;
    }
}
