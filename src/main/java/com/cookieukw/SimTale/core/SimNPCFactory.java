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
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
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
        SLOTHIAN("SimTale_Slothian"),
        TRORK("SimTale_Trork");

        public final String roleId;

        NPCType(String roleId) {
            this.roleId = roleId;
        }
    }

    @SuppressWarnings("null")
    public static Ref<EntityStore> spawnNPC(Store<EntityStore> store, Vector3d position, NPCType type) {
        // 1. Spawn the NPC using the official Hytale NPC system
        Pair<Ref<EntityStore>, ?> result = NPCPlugin.get().spawnNPC(
            store, 
            type.roleId, 
            (String) null, 
            position, 
            new Vector3f(0f, 0f, 0f)
        );

        if (result == null) {
            throw new NullPointerException("Spawn result is null");
        }

        Ref<EntityStore> ref = result.left();
        if (ref == null) {
            throw new NullPointerException("Spawned NPC entity reference is null");
        }
        
        ComponentAccessor<EntityStore> accessor = (ComponentAccessor<EntityStore>) ref.getStore();
        
        // 2. Add SimTale custom components to the spawned entity
        UUIDComponent uuidComp = accessor.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            throw new NullPointerException("NPC UUID component is null");
        }
        UUID entityId = uuidComp.getUuid();
        
        String name = SimNPCNameGenerator.generate();
        SimNPCComponent simComponent = new SimNPCComponent(entityId, name);

        // Try to load existing data if available
        SimNPCPersistence.loadNPC(simComponent);

        accessor.addComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE, simComponent);
        
        // 3. Add overhead name plate and make entity interactable
        accessor.putComponent(ref, DisplayNameComponent.getComponentType(), new DisplayNameComponent(Message.raw(simComponent.name)));
        accessor.putComponent(ref, Nameplate.getComponentType(), new Nameplate(simComponent.name));
        accessor.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);

        // 4. ACTIVATE AI: Queue for ticking
        // This is mandatory for NPCs spawned via API to start their AI logic.
        NewSpawnStartTickingSystem.queueNewSpawn(ref, store);
        
        // 5. Track for chat system
        SimTale.ACTIVE_NPCS.add(simComponent);

        return ref;
    }
}
