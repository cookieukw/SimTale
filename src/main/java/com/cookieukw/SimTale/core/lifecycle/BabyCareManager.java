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
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.event.EventPriority;

import java.util.HashSet;
import java.util.Set;
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

    /**
     * Rebuilds {@link LifecycleState#ACTIVE_CHILDREN} from disk.
     *
     * <p>That list is plain memory and nothing ever refilled it. Growth records were being written
     * to Caskara as {@code child_<id>} all along — several places read individual ones back — but no
     * boot path read them into the list, so every restart emptied it. Everything that asks "is this
     * NPC a child, and how old" answers from that list, so after a restart:
     *
     * <ul>
     *   <li>children stopped growing entirely — {@code GrowthTickSystem} iterates the list</li>
     *   <li>{@code /simtale setstage} reported "no active children nearby"</li>
     *   <li>Scold and Pick Up never appeared, because {@code ParentChildBond} found no parent</li>
     * </ul>
     *
     * <p>The missing buttons were the symptom that surfaced first; the frozen growth is the part
     * that actually mattered.
     */
    public static void loadActiveChildren() {
        LifecycleState.ACTIVE_CHILDREN.clear();
        try {
            List<GrowthComponent> all = Caskara.list(GrowthComponent.class);
            if (all == null) return;

            int restored = 0;
            for (GrowthComponent child : all) {
                if (child == null || child.childId == null) continue;
                // An adult is done growing and does not belong in the growth list; it is also the
                // state most records end in, so skipping them keeps the tick short.
                if (child.stage == GrowthStage.ADULT) continue;
                LifecycleState.ACTIVE_CHILDREN.add(child);
                restored++;
            }
            LOGGER.atInfo().log("SimTale: " + restored + " filho(s) em crescimento recarregados do banco.");
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: erro ao recarregar os filhos em crescimento: " + e.getMessage());
        }
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

    /**
     * Sets up shared custody for a newborn.
     *
     * <p>A child can legitimately have no father. {@code /simtale forcepreg --target=me} starts a
     * solo pregnancy and deliberately stores a null father rather than inventing a UUID that would
     * match nobody — so this blew up with
     * {@code Cannot invoke "java.util.UUID.toString()" because "child.fatherId" is null} and the
     * birth failed outright, which is a far worse outcome than a baby with one parent.
     *
     * <p>With no second parent there is no custody to share: the mother simply keeps the child, and
     * the swap logic has nobody to hand it to.
     */
    public static void initializeForChild(GrowthComponent child) {
        if (child == null || child.childId == null) return;
        if (child.motherId == null) {
            LOGGER.atWarning().log("SimTale: bebê sem mãe registrada; co-parenting não inicializado.");
            return;
        }

        BabyCareData data = new BabyCareData(
            child.childId.toString(),
            child.motherId.toString(),
            child.fatherId != null ? child.fatherId.toString() : null
        );
        save(data);
        LOGGER.atInfo().log("SimTale: Co-parenting inicializado para o bebê " + child.getFullName()
                + (child.fatherId == null ? " (sem segundo responsável)" : ""));
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
                    ItemStack babyItem = new ItemStack("Baby", 1).withMetadata("childId", Codec.STRING, care.childId);
                    
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
                        playerRefComp.sendMessage(Message.translation("general.baby.custody.offline_taken")
                            .param("name", Message.translation("general.baby.generic"))
                            .param("spouse", otherParentName));
                    } else {
                        // Inventory full
                        playerRefComp.sendMessage(Message.translation("general.baby.custody.inventory_full"));
                    }
                }
                // If the turn owner is now the NPC, but the player currently holds the baby in inventory
                else if (!care.currentTurnOwnerId.equals(playerUuidStr) && playerUuidStr.equals(care.currentHolderId)) {
                    // Search player inventory for baby item and remove it
                    CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
                    boolean removed = false;
                    for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
                        ItemStack item = combinedInventory.getItemStack(slot);
                        if (item != null && item.getItemId().equals("Baby")) {
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
                        playerRefComp.sendMessage(Message.translation("general.baby.custody.offline_given")
                            .param("name", Message.translation("general.baby.generic"))
                            .param("spouse", otherParentName));
                    }
                }

                save(care);
            }
        }
    }

    private static void removeBabyEntityFromWorld(UUID babyId) {
        World world = WorldUtil.first();
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

    public static void syncCarriedBabiesToInventory(UUID npcId, ItemContainer container) {
        if (npcId == null || container == null) return;
        List<UUID> carried = getCarriedBabies(npcId);
        
        // Find existing baby items in container
        Set<UUID> presentIds = new HashSet<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && item.getItemId().equals("Baby")) {
                String cIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                if (cIdStr != null) {
                    try {
                        presentIds.add(UUID.fromString(cIdStr));
                    } catch (Exception ignored) {}
                }
            }
        }

        // Add missing babies
        for (UUID childId : carried) {
            if (!presentIds.contains(childId)) {
                ItemStack babyItem = new ItemStack("Baby", 1).withMetadata("childId", Codec.STRING, childId.toString());
                container.addItemStack(babyItem);
            }
        }

        // Remove extra babies (that are no longer carried by this NPC)
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && item.getItemId().equals("Baby")) {
                String cIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                if (cIdStr != null) {
                    try {
                        UUID cId = UUID.fromString(cIdStr);
                        if (!carried.contains(cId)) {
                            container.removeItemStackFromSlot(slot, item, 1);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public static void registerInventoryListener(UUID npcId, ItemContainer container, UUID playerUuid) {
        if (npcId == null || container == null || playerUuid == null) return;

        final boolean[] isSelfModifying = {false};

        container.registerChangeEvent(EventPriority.NORMAL, event -> {
            if (isSelfModifying[0]) return;

            isSelfModifying[0] = true;
            try {
                Set<UUID> currentInInv = new HashSet<>();
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    ItemStack item = container.getItemStack(slot);
                    if (item != null && item.getItemId().equals("Baby")) {
                        String cIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                        if (cIdStr != null) {
                            try {
                                currentInInv.add(UUID.fromString(cIdStr));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                List<UUID> carried = getCarriedBabies(npcId);

                // 1. Check if a baby was REMOVED (taken by player)
                for (UUID childId : new HashSet<>(carried)) {
                    if (!currentInInv.contains(childId)) {
                        BabyCareData care = load(childId);
                        if (care != null) {
                            care.currentHolderId = playerUuid.toString();
                            long now = System.currentTimeMillis();
                            care.currentTurnOwnerId = playerUuid.toString();
                            care.turnStartTime = now;
                            care.nextSwapAllowedTime = now + TURN_DURATION;
                            care.lastInteractionTime = now;
                            save(care);
                            removeCarriedBaby(npcId, childId);

                            PlayerRef pRef = LifecycleUtils.getPlayerRef(playerUuid);
                            if (pRef != null) {
                                GrowthComponent child = Caskara.load("child_" + childId, GrowthComponent.class);
                                Message childMsg = child != null ? Message.raw(child.getFullName()) : Message.translation("general.baby.generic");
                                pRef.sendMessage(Message.translation("general.baby.custody.taken").param("name", childMsg));
                            }
                        }
                    }
                }

                // 2. Check if a baby was ADDED (given by player to NPC)
                for (UUID childId : currentInInv) {
                    if (!carried.contains(childId)) {
                        BabyCareData care = load(childId);
                        if (care != null) {
                            care.currentHolderId = npcId.toString();
                            long now = System.currentTimeMillis();
                            care.currentTurnOwnerId = npcId.toString();
                            care.turnStartTime = now;
                            care.nextSwapAllowedTime = now + TURN_DURATION;
                            care.lastInteractionTime = now;
                            save(care);
                            addCarriedBaby(npcId, childId);

                            PlayerRef pRef = LifecycleUtils.getPlayerRef(playerUuid);
                            if (pRef != null) {
                                GrowthComponent child = Caskara.load("child_" + childId, GrowthComponent.class);
                                Message childMsg = child != null ? Message.raw(child.getFullName()) : Message.translation("general.baby.generic");
                                pRef.sendMessage(Message.translation("general.baby.custody.given").param("name", childMsg));
                            }
                        }
                    }
                }
            } finally {
                isSelfModifying[0] = false;
            }
        });
    }
}
