package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.codec.Codec;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BabyCareManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    
    // Default turn duration: 4 hours (14,400,000 ms) for natural real-time co-parenting pacing
    public static final long TURN_DURATION = 14400000L;

    // Cache for fast lookup during ticking: NPC UUID -> List of Child UUIDs
    public static final Map<UUID, List<UUID>> NPC_CARRIED_BABIES = new ConcurrentHashMap<>();

    public static void addCarriedBaby(UUID npcId, UUID childId) {
        if (npcId == null || childId == null) return;
        NPC_CARRIED_BABIES.computeIfAbsent(npcId, _ -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(childId);
    }

    public static void removeCarriedBaby(UUID npcId, UUID childId) {
        if (npcId == null || childId == null) return;
        List<UUID> list = NPC_CARRIED_BABIES.get(npcId);
        if (list != null) {
            list.remove(childId);
            if (list.isEmpty()) {
                NPC_CARRIED_BABIES.remove(npcId);
            }
        }
    }

    public static List<UUID> getCarriedBabies(UUID npcId) {
        if (npcId == null) return java.util.Collections.emptyList();
        return NPC_CARRIED_BABIES.getOrDefault(npcId, java.util.Collections.emptyList());
    }

    public static void loadCache() {
        NPC_CARRIED_BABIES.clear();
        try {
            List<BabyCareData> all = Caskara.list(BabyCareData.class);
            if (all != null) {
                for (BabyCareData data : all) {
                    if (data.currentHolderId != null && !data.currentHolderId.equals("none")) {
                        try {
                            UUID holderId = UUID.fromString(data.currentHolderId);
                            UUID childId = UUID.fromString(data.childId);
                            addCarriedBaby(holderId, childId);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Erro ao carregar cache de bebês: " + e.getMessage());
        }
    }

    public static void save(BabyCareData data) {
        if (data == null || data.childId == null) return;
        Caskara.save("babycare_" + data.childId, data);
    }

    public static BabyCareData load(UUID childId) {
        if (childId == null) return null;
        return Caskara.load("babycare_" + childId, BabyCareData.class);
    }

    public static void initializeForChild(GrowthComponent child) {
        if (child == null || child.childId == null) return;
        BabyCareData data = new BabyCareData(
            child.childId.toString(),
            child.motherId.toString(),
            child.fatherId.toString()
        );
        save(data);
        LOGGER.atInfo().log("SimTale: Co-parenting inicializado para o bebê " + child.getFullName());
    }

    public static void simulateOfflineTime(Ref<EntityStore> playerRef, SimPlayerComponent playerComp) {
        if (playerComp == null || playerComp.playerUuid == null) return;
        
        // Ensure cache is loaded
        loadCache();

        String playerUuidStr = playerComp.playerUuid.toString();
        List<BabyCareData> allCares;
        try {
            allCares = Caskara.list(BabyCareData.class);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao carregar registros de cuidado offline: " + e.getMessage());
            return;
        }
        
        if (allCares == null) return;

        long now = System.currentTimeMillis();
        ComponentAccessor<EntityStore> store = playerRef.getStore();
        PlayerRef playerRefComp = store.getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComp == null) return;

        for (BabyCareData care : allCares) {
            if (care.childId == null) continue;
            if (!playerUuidStr.equals(care.motherId) && !playerUuidStr.equals(care.fatherId)) {
                continue;
            }

            // Sync interaction time
            care.lastInteractionTime = now;

            // If turn time expired while offline
            if (now > care.nextSwapAllowedTime) {
                long elapsed = now - care.turnStartTime;
                long turnsPassed = elapsed / TURN_DURATION;
                if (turnsPassed > 0) {
                    // Toggle turn owner based on how many turns passed
                    for (int t = 0; t < turnsPassed; t++) {
                        if (care.currentTurnOwnerId.equals(care.motherId)) {
                            care.currentTurnOwnerId = care.fatherId;
                        } else {
                            care.currentTurnOwnerId = care.motherId;
                        }
                    }
                    care.turnStartTime = care.turnStartTime + (turnsPassed * TURN_DURATION);
                    care.nextSwapAllowedTime = care.turnStartTime + TURN_DURATION;
                }

                // If the turn owner is now the player, but they don't hold the baby currently
                if (care.currentTurnOwnerId.equals(playerUuidStr) && !playerUuidStr.equals(care.currentHolderId)) {
                    // Give baby to player if inventory has space
                    CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
                    ItemStack babyItem = new ItemStack("simtale:Baby", 1).withMetadata("childId", Codec.STRING, care.childId);
                    
                    ItemStackTransaction transaction = combinedInventory.addItemStack(babyItem);
                    ItemStack remainder = transaction.getRemainder();
                    
                    if (remainder == null || remainder.isEmpty()) {
                        // Success! Removed from NPC / Ground if any
                        removeBabyEntityFromWorld(UUID.fromString(care.childId));
                        // Remove from cache
                        try {
                            removeCarriedBaby(UUID.fromString(care.currentHolderId), UUID.fromString(care.childId));
                        } catch (Exception ignored) {}
                        
                        care.currentHolderId = playerUuidStr;
                        
                        String otherParentName = getNPCName(UUID.fromString(playerUuidStr.equals(care.motherId) ? care.fatherId : care.motherId));
                        playerRefComp.sendMessage(Message.raw("Enquanto você estava fora, você pegou o bebê de volta de " + otherParentName + " para cuidar!"));
                    } else {
                        // Inventory full
                        playerRefComp.sendMessage(Message.raw("Era a sua vez de cuidar do bebê, mas seu inventário está cheio! O outro pai continuará com ele por enquanto."));
                    }
                }
                // If the turn owner is now the NPC, but the player currently holds the baby in inventory
                else if (!care.currentTurnOwnerId.equals(playerUuidStr) && playerUuidStr.equals(care.currentHolderId)) {
                    // Search player inventory for baby item and remove it
                    CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
                    boolean removed = false;
                    for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
                        ItemStack item = combinedInventory.getItemStack(slot);
                        if (item != null && item.getItemId().equals("simtale:Baby")) {
                            String cId = item.getFromMetadataOrNull("childId", Codec.STRING);
                            if (care.childId.equals(cId)) {
                                combinedInventory.removeItemStackFromSlot(slot, item, 1);
                                removed = true;
                                break;
                            }
                        }
                    }

                    if (removed) {
                        care.currentHolderId = care.currentTurnOwnerId;
                        // Add to cache
                        try {
                            addCarriedBaby(UUID.fromString(care.currentHolderId), UUID.fromString(care.childId));
                        } catch (Exception ignored) {}
                        
                        String otherParentName = getNPCName(UUID.fromString(care.currentTurnOwnerId));
                        playerRefComp.sendMessage(Message.raw("Enquanto você estava fora, " + otherParentName + " pegou o bebê para cuidar!"));
                    }
                }

                save(care);
            }
        }
    }

    private static void removeBabyEntityFromWorld(UUID babyId) {
        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;
        
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(babyId);
        if (ref != null) {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static String getNPCName(UUID npcId) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.equals(npcId)) {
                return npc.name;
            }
        }
        SimNPCComponent temp = new SimNPCComponent(npcId, "Parceiro");
        SimNPCPersistence.loadNPC(temp);
        return temp.name;
    }
}
