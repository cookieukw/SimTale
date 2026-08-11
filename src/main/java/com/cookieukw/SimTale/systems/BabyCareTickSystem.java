package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.codec.Codec;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BabyCareTickSystem extends EntityTickingSystem<EntityStore> {

    private static final int TICK_INTERVAL = 30; // Check every 1.5 seconds
    private long lastTick = 0;

    // Cooldown map for spouse feedback messages to prevent chat spam (Player UUID -> Last Message Time)
    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        
        // Execute only once per interval, at first index
        if (index != 0) return;

        World world = WorldUtil.first();
        if (world == null) return;

        long nowTicks = world.getTick();
        if (nowTicks - lastTick < TICK_INTERVAL) return;
        lastTick = nowTicks;

        long nowMs = System.currentTimeMillis();

        // Process each active player
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            UUID playerUuid = playerRef.getUuid();
            Ref<EntityStore> entityRef = world.getEntityStore().getRefFromUUID(playerUuid);
            if (entityRef == null) continue;

            TransformComponent playerTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (playerTransform == null) continue;

            // Search player inventory for baby item
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, entityRef, InventoryComponent.HOTBAR_FIRST);

            for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
                ItemStack item = combinedInventory.getItemStack(slot);
                if (item != null && item.getItemId().equals("Baby")) {
                    String childIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                    if (childIdStr != null) {
                        break;
                    }
                }
            }

            // Find closest spouse NPC
            SimNPCComponent spouseNpc = null;
            double minDistance = Double.MAX_VALUE;

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef == null || npc.entityId == null) continue;
                if (!npc.family.isMarried || npc.family.spouseId == null || !npc.family.spouseId.equals(playerUuid)) {
                    continue;
                }

                TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (npcTransform != null) {
                    double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                    if (distSq < 16.0 && distSq < minDistance) { // Within 4 blocks
                        minDistance = distSq;
                        spouseNpc = npc;
                    }
                }
            }

            if (spouseNpc == null) continue;

            // --- Process Baby Items in Player Inventory (Swap to NPC if ready) ---
            for (short slot = 0; slot < combinedInventory.getCapacity(); slot++) {
                ItemStack item = combinedInventory.getItemStack(slot);
                if (item != null && item.getItemId().equals("Baby")) {
                    String childIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                    if (childIdStr != null) {
                        UUID invBabyChildId = UUID.fromString(childIdStr);
                        BabyCareData care = BabyCareManager.load(invBabyChildId);
                        if (care != null) {
                            if (nowMs >= care.nextSwapAllowedTime) {
                                // Perform automatic swap: give baby to NPC
                                combinedInventory.removeItemStackFromSlot(slot, item, 1);
                                
                                care.currentHolderId = spouseNpc.entityId.toString();
                                care.currentTurnOwnerId = spouseNpc.entityId.toString();
                                care.turnStartTime = nowMs;
                                care.nextSwapAllowedTime = nowMs + BabyCareManager.TURN_DURATION;
                                care.lastInteractionTime = nowMs;
                                BabyCareManager.save(care);

                                BabyCareManager.addCarriedBaby(spouseNpc.entityId, invBabyChildId);

                                GrowthComponent child = Caskara.load("child_" + care.childId, GrowthComponent.class);
                                Message childMsg = child != null ? Message.raw(child.getFullName()) : Message.translation("general.baby.generic");
                                playerRef.sendMessage(Message.translation("general.baby.custody.spouse_taken")
                                    .param("spouse", spouseNpc.name)
                                    .param("name", childMsg));
                            } else {
                                // Early swap rejection feedback (cooldown check)
                                long lastMsg = MESSAGE_COOLDOWNS.getOrDefault(playerUuid, 0L);
                                if (nowMs - lastMsg > 10000) {
                                    GrowthComponent child = Caskara.load("child_" + care.childId, GrowthComponent.class);
                                    Message childMsg = child != null ? Message.raw(child.getFullName()) : Message.translation("general.baby.generic");
                                    playerRef.sendMessage(Message.translation("npc-dialogues.baby.custody.spouse_reject")
                                        .param("spouse", spouseNpc.name)
                                        .param("name", childMsg));
                                    MESSAGE_COOLDOWNS.put(playerUuid, nowMs);
                                }
                            }
                        }
                    }
                }
            }

            // --- Process Babies Carried by NPC (Swap back to Player if ready) ---
            List<UUID> npcCarried = new ArrayList<>(BabyCareManager.getCarriedBabies(spouseNpc.entityId));
            for (UUID npcChildId : npcCarried) {
                BabyCareData care = BabyCareManager.load(npcChildId);
                if (care != null && nowMs >= care.nextSwapAllowedTime) {
                    // Give baby back to player if inventory has space
                    ItemStack newBabyItem = new ItemStack("Baby", 1).withMetadata("childId", Codec.STRING, npcChildId.toString());
                    ItemStackTransaction transaction = combinedInventory.addItemStack(newBabyItem);
                    ItemStack remainder = transaction.getRemainder();

                    if (remainder == null || remainder.isEmpty()) {
                        // Success!
                        care.currentHolderId = playerUuid.toString();
                        care.currentTurnOwnerId = playerUuid.toString();
                        care.turnStartTime = nowMs;
                        care.nextSwapAllowedTime = nowMs + BabyCareManager.TURN_DURATION;
                        care.lastInteractionTime = nowMs;
                        BabyCareManager.save(care);

                        // Cache update
                        BabyCareManager.removeCarriedBaby(spouseNpc.entityId, npcChildId);

                        GrowthComponent child = Caskara.load("child_" + care.childId, GrowthComponent.class);
                        Message childMsg = child != null ? Message.raw(child.getFullName()) : Message.translation("general.baby.generic");
                        playerRef.sendMessage(Message.translation("general.baby.custody.taken_back")
                            .param("name", childMsg)
                            .param("spouse", spouseNpc.name));
                    } else {
                        // Inventory full feedback
                        long lastMsg = MESSAGE_COOLDOWNS.getOrDefault(playerUuid, 0L);
                        if (nowMs - lastMsg > 10000) {
                            playerRef.sendMessage(Message.translation("general.baby.custody.inventory_full"));
                            MESSAGE_COOLDOWNS.put(playerUuid, nowMs);
                        }
                    }
                }
            }
        }
    }
}
