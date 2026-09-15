package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Shell;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
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

    /**
     * Separate per-world shell for NPCs the Grim Reaper has collected. Distinct from
     * {@link #worldShell()} so a live NPC's record and an archived dead one never collide under
     * the same id.
     * <p>
     * Nothing reads from this yet — it exists so death stops being destructive. Foundation for a
     * future revive mechanic or a graveyard/cemetery feature.
     */
    public static Shell graveyardShell() {
        World world = WorldUtil.first();
        return world != null ? Caskara.shell(world, "simtale_graveyard") : Caskara.shell("simtale_graveyard");
    }

    /**
     * Moves an NPC's record into the graveyard shell instead of deleting it outright — same
     * spot {@link #deleteNPC} used to be called from when the Reaper finishes collecting a
     * soul. The data survives there for whatever uses it later.
     */
    public static void archiveToGraveyard(UUID entityId) {
        if (entityId == null) return;
        try {
            SimNPCData data = worldShell().core(SimNPCData.class).extract(entityId.toString()).sync().orElse(null);
            if (data != null) {
                graveyardShell().core(SimNPCData.class).preserve(entityId.toString(), data);
            }
            worldShell().core(SimNPCData.class).discard(entityId.toString());
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao arquivar NPC " + entityId + " no cemiterio: " + e.getMessage());
        }
    }

    /**
     * Writes a record back as-is, without going through a live component.
     * <p>
     * Needed when a revival rewrites references inside NPCs that are not loaded right now: their
     * data has to be corrected in the database directly, or they come back later still married to
     * a UUID that no longer exists.
     */
    public static void saveData(SimNPCData data) {
        if (data == null || data.id == null) return;
        try {
            worldShell().core(SimNPCData.class).preserve(data.id, data);
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao salvar o registro " + data.id + ": " + e.getMessage());
        }
    }

    /** Every archived record in the graveyard, newest-first ordering not guaranteed. Never null. */
    public static List<SimNPCData> listGraveyard() {
        try {
            List<SimNPCData> all = graveyardShell().core(SimNPCData.class).extractAll();
            return all != null ? all : new ArrayList<>();
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao listar o cemiterio: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** One archived record, or null when that id was never buried. */
    public static SimNPCData loadFromGraveyard(UUID entityId) {
        if (entityId == null) return null;
        try {
            return graveyardShell().core(SimNPCData.class).extract(entityId.toString()).sync().orElse(null);
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao ler o registro " + entityId + " do cemiterio: " + e.getMessage());
            return null;
        }
    }

    /** Drops a record from the graveyard. Called once a revival has actually succeeded. */
    public static void removeFromGraveyard(UUID entityId) {
        if (entityId == null) return;
        try {
            graveyardShell().core(SimNPCData.class).discard(entityId.toString());
        } catch (Exception e) {
            HytaleLogger.forEnclosingClass().atWarning()
                .log("SimTale: falha ao remover " + entityId + " do cemiterio: " + e.getMessage());
        }
    }

    /**
     * Copies an archived record onto a freshly spawned component.
     * <p>
     * Public because revival is the one legitimate caller from outside this class: it has to build
     * the body first (which mints a new entity UUID — see SimNPCRevival for why the old one cannot
     * be reused) and only then pour the saved life into it.
     */
    public static void applyArchivedData(SimNPCComponent component, SimNPCData data) {
        if (component == null || data == null) return;
        applyData(component, data);
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
        data.guardWeaponCategory = component.guardWeaponCategory;
        data.guardWeaponItemId = component.guardWeaponItemId;
        data.armorItemIds = component.armorItemIds != null ? component.armorItemIds.clone() : null;

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
     * Re-attaches {@link SimTale#SIM_NPC_COMPONENT_TYPE} to an entity whose component did not
     * survive a world reload, using the record in this shell as proof that it really is one of
     * ours.
     * <p>
     * This used to be copied almost verbatim between {@code SimTaleEventHandler} (right click)
     * and {@code SimTaleUseNPCInteraction} (the F key) — the same duplication that once let a
     * fix to the "any entity gets adopted" bug land in only one of the two paths while the other
     * kept adopting cows and other players. Both call sites now go through here instead.
     *
     * @param accessor  the {@link Store} or {@link com.hypixel.hytale.component.CommandBuffer}
     *                  to read and write components with — both implement {@link ComponentAccessor}
     * @param targetRef the entity that was right-clicked / interacted with
     * @return the re-attached, already-tracked component, or {@code null} if the target is a
     *         player or has no record in this shell (nothing to re-attach)
     */
    public static SimNPCComponent tryReattach(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> targetRef) {
        if (accessor == null || targetRef == null) return null;

        // It used to adopt ANY entity: right-clicking a chicken, a hostile mob or another player
        // added SIM_NPC_COMPONENT_TYPE to it and gave it a generated name. Since
        // RoutineAISystem's query is exactly that component, the victim then started running the
        // villager routine — walking to beds, being mounted, getting Frozen — with no way out.
        if (accessor.getComponent(targetRef, Player.getComponentType()) != null) {
            return null;
        }

        UUIDComponent uuidComp = accessor.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uuidComp == null) return null;

        // "simtale" shell, not Caskara's "default" — see DB_SHELL above. A record here is the
        // proof that this entity really is one of ours; without it there is nothing to
        // re-attach and adopting the entity would be an invention.
        SimNPCData data = loadData(uuidComp.getUuid());
        if (data == null) {
            HytaleLogger.forEnclosingClass().atFine()
                    .log("SimTale: entidade sem registro no shell simtale, ignorada.");
            return null;
        }

        String name = data.name;
        if (name == null || name.isEmpty()) {
            PersistentDisplayName displayName = accessor.getComponent(targetRef, PersistentDisplayName.getComponentType());
            if (displayName != null && displayName.getDisplayName() != null) {
                name = displayName.getDisplayName().toString();
            }
        }
        if (name == null || name.isEmpty()) {
            name = SimNPCNameGenerator.generate();
        }

        SimNPCComponent npc = new SimNPCComponent(uuidComp.getUuid(), name);
        npc.entityRef = targetRef;
        loadNPC(npc);
        accessor.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
        SimTale.trackNpc(npc);
        return npc;
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
        if (data.stats != null) {
            component.stats = data.stats;
        }
        component.memory = data.memory != null ? data.memory : new MemoryManager();
        if (data.profession != null) {
            component.profession = data.profession;
            if (InteractionManager.isNpcAChild(component) && !component.profession.isSafeForChildren()) {
                component.profession = Profession.UNEMPLOYED;
            }
        }
        // Absent on a record saved before this field existed — MELEE (the SimNPCComponent
        // default) is exactly what every Guard was back then, so leaving it unset is correct,
        // not just harmless.
        if (data.guardWeaponCategory != null) {
            component.guardWeaponCategory = data.guardWeaponCategory;
        }
        if (data.guardWeaponItemId != null) {
            component.guardWeaponItemId = data.guardWeaponItemId;
        }
        if (data.armorItemIds != null) {
            component.armorItemIds = data.armorItemIds.clone();
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
            //
            // Only a tracked entry with a STILL-VALID entityRef counts as "already reassembled".
            // ACTIVE_NPCS/NPCS_BY_ID are plain static fields -- nothing clears them when a player
            // leaves a world, only the manual /simtale clearall debug command does. Leave and
            // rejoin the SAME server (no restart) and every NPC from the previous session is
            // still sitting in the index with an entityRef pointing at that old, now-gone
            // EntityStore. The old `findNpc(...) != null` check treated that stale leftover as
            // "already handled" and skipped it forever -- so on rejoin NOTHING ever got
            // reconnected to the entities the engine actually reloaded: sendAllToPlayer still
            // listed every NPC by name from the stale ACTIVE_NPCS (matching what looked like the
            // NPCs "being in the player list"), but their dead refs meant no map marker and
            // nothing for RoutineAISystem to tick, i.e. NPCs never came back even though their
            // entities were sitting right there in the reloaded world waiting to be reattached.
            SimNPCComponent alreadyTracked = SimTale.findNpc(comp.entityId);
            if (alreadyTracked != null && alreadyTracked.entityRef != null && alreadyTracked.entityRef.isValid()) {
                continue;
            }

            // Try to find the entity in the world
            Ref<EntityStore> entityRef = 
                world.getEntityStore().getRefFromUUID(comp.entityId);
            
            if (entityRef != null) {
                comp.entityRef = entityRef;

                /* Re-attach component to entity. addComponent fails when the entity already
                 * carries a stale instance (a normal case after some reloads); putComponent
                 * overwrites it instead. If BOTH fail, the entity keeps loading without
                 * SIM_NPC_COMPONENT_TYPE attached at all — tracking it anyway would make it show
                 * up as an "active" NPC that no other system can actually query (a silent ghost
                 * entry), so this is logged and the tracking is skipped instead of continuing
                 * quietly.
                 */
                boolean attached = true;
                try {
                    accessor.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                } catch (Exception e) {
                    try {
                        accessor.putComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                    } catch (Exception e2) {
                        attached = false;
                        HytaleLogger.forEnclosingClass().atWarning().withCause(e2)
                                .log("SimTale: falha ao reanexar SimNPCComponent em " + comp.entityId
                                        + " durante reassembleActiveNPCs (addComponent e putComponent falharam).");
                    }
                }

                if (attached) {
                    SimTale.trackNpc(comp);
                }
            }
        }
    }
}
