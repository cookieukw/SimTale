package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookie.caskara.Caskara;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Handles persistence for SimTale NPCs using the Caskara database.
 */
public class SimNPCPersistence {

    public static void saveNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = new SimNPCData(
                component.entityId,
                component.name,
                component.personality,
                component.needs,
                component.stats,
                component.memory,
                component.profession,
                component.preferences,
                component.family,
                component.gender,
                component.bedLocation,
                component.pregnancy);

        // Convert Map<UUID, Relationship> to Map<String, Relationship> for Caskara
        for (Map.Entry<UUID, Relationship> entry : component.relationships.entrySet()) {
            data.relationships.put(entry.getKey().toString(), entry.getValue());
        }

        Caskara.save(component.entityId.toString(), data);
    }

    public static void loadNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = Caskara.load(component.entityId.toString(), SimNPCData.class);
        if (data != null) {
            component.name = data.name;
            component.personality = data.personality;
            component.needs = data.needs;
            component.stats = data.stats;
            component.memory = data.memory != null ? data.memory : new MemoryManager();
            if (data.profession != null) {
                component.profession = data.profession;
            }
            if (data.preferences != null) {
                component.preferences = data.preferences;
            }
            if (data.family != null) {
                component.family = data.family;
            }
            if (data.gender != null) {
                component.gender = data.gender;
            }
            if (data.bedLocation != null) {
                component.bedLocation = new com.cookieukw.SimTale.db.SimBedData.BedPos(data.bedLocation.x, data.bedLocation.y, data.bedLocation.z);
            }

            // Reconstruct relationships
            for (Map.Entry<String, Relationship> entry : data.relationships.entrySet()) {
                component.relationships.put(UUID.fromString(entry.getKey()), entry.getValue());
            }
            
            // Restore pregnancy
            if (data.pregnancy != null) {
                component.pregnancy = data.pregnancy;
            }
        }
    }

    /**
     * Loads all saved NPC records from Caskara.
     * Returns SimNPCComponent objects with entityRef = null.
     * The caller must resolve entityRef from the world's EntityStore.
     */
    public static List<SimNPCComponent> loadAllNPCs() {
        List<SimNPCComponent> result = new ArrayList<>();
        try {
            List<SimNPCData> allData = Caskara.list(SimNPCData.class);
            if (allData != null) {
                for (SimNPCData data : allData) {
                    if (data.id == null) continue;
                    UUID entityId = UUID.fromString(data.id);
                    SimNPCComponent comp = new SimNPCComponent(entityId, data.name);
                    comp.personality = data.personality;
                    comp.needs = data.needs;
                    comp.stats = data.stats;
                    comp.memory = data.memory != null ? data.memory : new MemoryManager();
                    if (data.preferences != null) {
                        comp.preferences = data.preferences;
                    }
                    if (data.family != null) {
                        comp.family = data.family;
                    }
                    if (data.gender != null) {
                        comp.gender = data.gender;
                    }
                    if (data.bedLocation != null) {
                        comp.bedLocation = new com.cookieukw.SimTale.db.SimBedData.BedPos(data.bedLocation.x, data.bedLocation.y, data.bedLocation.z);
                    }
                    
                    if (data.relationships != null) {
                        for (Map.Entry<String, Relationship> entry : data.relationships.entrySet()) {
                            comp.relationships.put(UUID.fromString(entry.getKey()), entry.getValue());
                        }
                    }
                    
                    // Restore pregnancy
                    if (data.pregnancy != null) {
                        comp.pregnancy = data.pregnancy;
                    }
                    result.add(comp);
                }
            }
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: Falha ao carregar NPCs do banco: " + e.getMessage());
        }
        return result;
    }
    
    /**
     * Reassembles all NPCs from Caskara database and resolves their entity references.
     * This is useful as a fallback when the server reloads and ACTIVE_NPCS is empty.
     */
    public static void reassembleActiveNPCs(World world) {
        if (world == null) return;
        
        Store<EntityStore> store = world.getEntityStore().getStore();
        ComponentAccessor<EntityStore> accessor = 
            (ComponentAccessor<EntityStore>) store;

        List<SimNPCComponent> savedNPCs = loadAllNPCs();
        
        for (SimNPCComponent comp : savedNPCs) {
            if (comp.entityId == null) continue;

            // Check if already tracked
            boolean alreadyTracked = SimTale.ACTIVE_NPCS.stream()
                .anyMatch(a -> a.entityId != null && a.entityId.equals(comp.entityId));
            if (alreadyTracked) continue;

            // Try to find the entity in the world
            Ref<EntityStore> entityRef = 
                world.getEntityStore().getRefFromUUID(comp.entityId);
            
            if (entityRef != null) {
                comp.entityRef = entityRef;
                
                // Re-attach component to entity
                try {
                    accessor.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                } catch (Exception e) {
                    try {
                        accessor.putComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                    } catch (Exception ignored) {}
                }
                
                SimTale.ACTIVE_NPCS.add(comp);
            }
        }
    }
}
