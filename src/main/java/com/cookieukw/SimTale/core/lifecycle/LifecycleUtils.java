package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.UUID;

public class LifecycleUtils {

    public static Ref<EntityStore> getEntityRef(UUID childId) {
        if (childId == null) return null;
        for (World world : Universe.get().getWorlds().values()) {
            Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(childId);
            if (ref != null) return ref;
        }
        return null;
    }

    public static PlayerRef getPlayerRef(UUID playerUuid) {
        if (playerUuid == null) return null;
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef.getUuid().equals(playerUuid)) return pRef;
        }
        return null;
    }

    public static void updateFamilyChildId(UUID parentId, UUID oldId, UUID newId) {
        if (parentId == null) return;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.equals(parentId)) {
                for (Child c : npc.family.children) {
                    if (c.id != null && c.id.equals(oldId)) {
                        c.id = newId;
                        SimNPCPersistence.saveNPC(npc);
                        return;
                    }
                }
            }
        }
        SimNPCComponent temp = new SimNPCComponent(parentId, "Parent");
        SimNPCPersistence.loadNPC(temp);
        if (!temp.name.equals("Parent")) {
            for (Child c : temp.family.children) {
                if (c.id != null && c.id.equals(oldId)) {
                    c.id = newId;
                    SimNPCPersistence.saveNPC(temp);
                    return;
                }
            }
        }
    }

    public static void removeBabyItemFromPlayer(PlayerRef playerRef, UUID oldChildId) {
        World world = WorldUtil.first();
        if (world == null) return;
        Ref<EntityStore> pRef = world.getEntityStore().getRefFromUUID(playerRef.getUuid());
        if (pRef == null) return;
        CombinedItemContainer combinedInventory = InventoryComponent.getCombined(pRef.getStore(), pRef, InventoryComponent.HOTBAR_FIRST);
        for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
            ItemStack item = combinedInventory.getItemStack(slot);
            if (item != null && item.getItemId().equals("Baby")) {
                String cId = item.getFromMetadataOrNull("childId", Codec.STRING);
                if (cId != null && cId.equals(oldChildId.toString())) {
                    combinedInventory.removeItemStackFromSlot(slot, item, 1);
                    break;
                }
            }
        }
    }

    public static SimNPCComponent findNPCById(UUID id) {
        /* O(1) via the UUID index instead of scanning the whole roster. RoutineAISystem calls
        this from its per-NPC tick path, so the old linear scan was O(n²) per tick.
        */
        return SimTale.findNpc(id);
    }

    public static GeneticsData getOrCreateGenetics(SimNPCComponent npc) {
        if (npc.entityId != null) {
            return new GeneticsData(npc.entityId.getMostSignificantBits(), npc.entityId.getLeastSignificantBits());
        }
        return new GeneticsData();
    }

    public static Vector3d getEntityPosition(SimNPCComponent npc, Store<EntityStore> store) {
        if (npc.entityRef == null) return null;
        try {
            TransformComponent transform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
