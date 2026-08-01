package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Handles persistence for SimTale NPCs using the Caskara database.
 */
public class SimNPCPersistence {
    /**
     * All SimTale NPC data lives in this named shell.
     * <p>
     * IMPORTANT: never reach for the static {@code Caskara.save/load/list/delete} helpers for
     * {@link SimNPCData}. Those resolve to {@code Caskara.shell("default")} — a different
     * store — so a write here would be invisible to them and vice versa. That mismatch used to
     * be spread across seven call sites: NPC lookups always came back null (which is why
     * SimTaleTickSystem could never re-attach a component), listings were always empty, and
     * deletions silently missed, leaving dead NPCs in the database forever.
     * <p>
     * Go through the helpers below instead.
     */
    public static final Shell DB_SHELL = Caskara.shell("simtale");

    /**
     * Per-world store, for anything that only means something inside one world: NPC entities
     * and the houses whose block coordinates would point at thin air in another world.
     * <p>
     * {@code Caskara.shell(name)} keys purely on the name, so every world shared one database.
     * A brand new world therefore opened already "full" of the previous world's NPCs — which
     * is what stopped the starting group from ever spawning, since the spawner skips a world
     * that already has saved NPCs.
     * <p>
     * Falls back to the global shell only when no world is loaded, which in practice means
     * start-up ordering rather than real use.
     */
    public static Shell worldShell() {
        World world = WorldUtil.first();
        return world != null ? Caskara.shell(world, "simtale") : DB_SHELL;
    }

    /** Reads one NPC record, or null when the id is unknown. */
    public static SimNPCData loadData(UUID entityId) {
        if (entityId == null) return null;
        return worldShell().core(SimNPCData.class).extract(entityId.toString()).sync().orElse(null);
    }

    /** Every stored NPC record. Never null. */
    public static List<SimNPCData> listAll() {
        try {
            List<SimNPCData> all = worldShell().core(SimNPCData.class).extractAll();
            return all != null ? all : new ArrayList<>();
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao listar NPCs do banco: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Removes one NPC record. Safe to call for an id that was never stored. */
    public static void deleteNPC(UUID entityId) {
        if (entityId == null) return;
        try {
            worldShell().core(SimNPCData.class).discard(entityId.toString());
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao apagar NPC " + entityId + ": " + e.getMessage());
        }
    }

    /** Wipes every NPC record. Returns how many were removed. */
    public static int deleteAll() {
        int removed = 0;
        for (SimNPCData data : listAll()) {
            if (data.id == null) continue;
            try {
                worldShell().core(SimNPCData.class).discard(data.id);
                removed++;
            } catch (Exception e) {
                HytaleLogger.forEnclosingClass().atWarning()
                    .log("SimTale: falha ao apagar NPC " + data.id + ": " + e.getMessage());
            }
        }
        return removed;
    }

    public static void saveNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        // Guard against writing a placeholder over real data. Since SimNPCComponent is now
        // persisted by Hytale with a codec that only carries id and name, a reloaded entity
        // arrives with a component whose remaining fields came from the default constructor —
        // including a randomly rolled profession. Anything that saved before loadNPC() ran
        // would have committed that random state to the database.
        if (!component.dataLoaded) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: ignorando save de " + component.entityId + " (dados ainda nao carregados do banco)");
            return;
        }

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

        worldShell().core(SimNPCData.class).preserve(component.entityId.toString(), data);
    }

    public static void loadNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = worldShell().core(SimNPCData.class).extract(component.entityId.toString()).sync().orElse(null);
        if (data != null) {
            applyData(component, data);
        }
        // Set even when there is no record: the component now reflects the database as well as
        // it ever will, and a never-saved NPC must still be allowed to save for the first time.
        component.dataLoaded = true;
    }

    /**
     * Copies a persisted record onto a live component. Single source of truth shared by
     * {@link #loadNPC} and {@link #loadAllNPCs} — previously these two were separate copies
     * that had already drifted apart (loadAllNPCs silently dropped the profession).
     */
    private static void applyData(SimNPCComponent component, SimNPCData data) {
        if (data.name != null) {
            component.name = data.name;
        }
        // These three used to be assigned unconditionally: an older or partial record with a
        // null personality wiped the live one, and every `npc.personality.traits` read
        // downstream then threw NullPointerException.
        if (data.personality != null) {
            component.personality = data.personality;
        }
        if (component.personality != null && component.personality.traits == null) {
            component.personality.traits = new HashSet<>();
        }
        if (data.needs != null) {
            component.needs = data.needs;
        }
        if (data.stats != null) {
            component.stats = data.stats;
        }
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
            List<SimNPCData> allData = worldShell().core(SimNPCData.class).extractAll();
            if (allData != null) {
                for (SimNPCData data : allData) {
                    if (data.id == null) continue;
                    UUID entityId = UUID.fromString(data.id);
                    SimNPCComponent comp = new SimNPCComponent(entityId, data.name);
                    applyData(comp, data);
                    comp.dataLoaded = true;
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

            // O(1) index lookup instead of streaming the whole roster per saved NPC.
            if (SimTale.findNpc(comp.entityId) != null) continue;

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
                
                SimTale.trackNpc(comp);
            }
        }
    }
}
