package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Shell;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.systems.BedRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Handles persistence for SimTale NPCs using the Caskara database.
 */
public class SimNPCPersistence {
    public static final Shell DB_SHELL = Caskara.shell("simtale");

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

        data.activeEmotion = component.activeEmotion != null ? component.activeEmotion.name() : "NEUTRAL";
        data.emotionIntensity = component.emotionIntensity;
        data.emotionSource = component.emotionSource;
        data.lastEmotionChangeTick = component.lastEmotionChangeTick;

        DB_SHELL.core(SimNPCData.class).preserve(component.entityId.toString(), data);
    }

    public static void loadNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = DB_SHELL.core(SimNPCData.class).extract(component.entityId.toString()).sync().orElse(null);
        if (data != null) {
            applyData(component, data);
        }
    }

    /**
     * Copies a persisted record onto a live component. Single source of truth shared by
     * {@link #loadNPC} and {@link #loadAllNPCs} — previously these two were separate copies
     * that had already drifted apart (loadAllNPCs silently dropped the profession).
     */
    private static void applyData(SimNPCComponent component, SimNPCData data) {
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
            component.bedLocation = new SimBedData.BedPos(
                    data.bedLocation.x, data.bedLocation.y, data.bedLocation.z, data.bedLocation.yaw);
            BedRegistry.addOrReplace(
                    data.bedLocation.x, data.bedLocation.y, data.bedLocation.z, data.bedLocation.yaw);
        }

        // Reconstruct relationships. One malformed key must not abort the whole load.
        if (data.relationships != null) {
            for (Map.Entry<String, Relationship> entry : data.relationships.entrySet()) {
                try {
                    component.relationships.put(UUID.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException e) {
                    HytaleLogger.forEnclosingClass().atWarning()
                            .log("SimTale: chave de relacionamento inválida ignorada: " + entry.getKey());
                }
            }
        }

        if (data.pregnancy != null) {
            component.pregnancy = data.pregnancy;
        }

        // Restore emotion state
        if (data.activeEmotion != null) {
            try {
                component.activeEmotion = Mood.valueOf(data.activeEmotion);
            } catch (IllegalArgumentException e) {
                component.activeEmotion = Mood.NEUTRAL;
            }
        }
        component.emotionIntensity = data.emotionIntensity;
        if (data.emotionSource != null) {
            component.emotionSource = data.emotionSource;
        }
        component.lastEmotionChangeTick = data.lastEmotionChangeTick;
    }

    /**
     * Loads all saved NPC records from Caskara.
     * Returns SimNPCComponent objects with entityRef = null.
     * The caller must resolve entityRef from the world's EntityStore.
     */
    public static List<SimNPCComponent> loadAllNPCs() {
        List<SimNPCComponent> result = new ArrayList<>();
        try {
            List<SimNPCData> allData = DB_SHELL.core(SimNPCData.class).extractAll();
            if (allData != null) {
                for (SimNPCData data : allData) {
                    if (data.id == null) continue;
                    UUID entityId = UUID.fromString(data.id);
                    SimNPCComponent comp = new SimNPCComponent(entityId, data.name);
                    applyData(comp, data);
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
