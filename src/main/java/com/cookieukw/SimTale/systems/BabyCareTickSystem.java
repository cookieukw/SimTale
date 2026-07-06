package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookie.runecore.api.util.Codec;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.joml.Vector3d;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;
import java.util.HashMap;
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

        long nowTicks = Universe.get().getWorlds().values().iterator().next().getTick();
        if (nowTicks - lastTick < TICK_INTERVAL) return;
        lastTick = nowTicks;

        long nowMs = System.currentTimeMillis();

        // Process each active player
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            UUID playerUuid = playerRef.getUuid();
            Ref<EntityStore> entityRef = Universe.get().getWorlds().values().iterator().next().getEntityStore().getRefFromUUID(playerUuid);
            if (entityRef == null) continue;

            TransformComponent playerTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (playerTransform == null) continue;

            // Search player inventory for baby item
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, entityRef, InventoryComponent.HOTBAR_FIRST);
            ItemStack babyItem = null;
            int babySlot = -1;
            UUID babyChildId = null;

            for (int slot = 0; slot < combinedInventory.getSlotCount(); slot++) {
                ItemStack item = combinedInventory.getItemStack(slot);
                if (item != null && item.getItemId().equals("simtale:baby")) {
                    String childIdStr = item.getFromMetadataOrNull("childId", Codec.STRING);
                    if (childIdStr != null) {
                        babyItem = item;
                        babySlot = slot;
                        babyChildId = UUID.fromString(childIdStr);
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

            // --- Case 1: Player holds baby, approaches NPC spouse ---
            if (babyItem != null && babyChildId != null) {
                BabyCareData care = BabyCareManager.load(babyChildId);
                if (care != null) {
                    if (nowMs >= care.nextSwapAllowedTime) {
                        // Perform automatic swap: give baby to NPC
                        combinedInventory.removeItemStackFromSlot(babySlot, babyItem, 1);
                        
                        care.currentHolderId = spouseNpc.entityId.toString();
                        care.currentTurnOwnerId = spouseNpc.entityId.toString();
                        care.turnStartTime = nowMs;
                        care.nextSwapAllowedTime = nowMs + BabyCareManager.TURN_DURATION;
                        care.lastInteractionTime = nowMs;
                        BabyCareManager.save(care);

                        // Cache update
                        BabyCareManager.NPC_CARRIED_BABIES.put(spouseNpc.entityId, babyChildId);

                        GrowthComponent child = Caskara.load("child_" + care.childId, GrowthComponent.class);
                        String childName = child != null ? child.getFullName() : "do bebê";
                        playerRef.sendMessage(Message.raw(spouseNpc.name + " pegou o bebê " + childName + " para cuidar!"));
                    } else {
                        // Early swap rejection feedback (cooldown check)
                        long lastMsg = MESSAGE_COOLDOWNS.getOrDefault(playerUuid, 0L);
                        if (nowMs - lastMsg > 5000) {
                            playerRef.sendMessage(Message.raw("<" + spouseNpc.name + "> Agora é a sua vez de cuidar dele por um tempo."));
                            MESSAGE_COOLDOWNS.put(playerUuid, nowMs);
                        }
                    }
                }
            }
            // --- Case 2: Player has no baby, approaches NPC spouse who holds baby ---
            else {
                UUID npcChildId = BabyCareManager.NPC_CARRIED_BABIES.get(spouseNpc.entityId);
                if (npcChildId != null) {
                    BabyCareData care = BabyCareManager.load(npcChildId);
                    if (care != null && nowMs >= care.nextSwapAllowedTime) {
                        // Give baby back to player if inventory has space
                        ItemStack newBabyItem = new ItemStack("simtale:baby", 1).withMetadata("childId", Codec.STRING, npcChildId.toString());
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
                            BabyCareManager.NPC_CARRIED_BABIES.remove(spouseNpc.entityId);

                            GrowthComponent child = Caskara.load("child_" + care.childId, GrowthComponent.class);
                            String childName = child != null ? child.getFullName() : "do bebê";
                            playerRef.sendMessage(Message.raw("Você pegou o bebê " + childName + " de volta de " + spouseNpc.name + "!"));
                        } else {
                            // Inventory full feedback
                            long lastMsg = MESSAGE_COOLDOWNS.getOrDefault(playerUuid, 0L);
                            if (nowMs - lastMsg > 5000) {
                                playerRef.sendMessage(Message.raw("Seu inventário está cheio! Abra espaço para pegar o bebê de volta."));
                                MESSAGE_COOLDOWNS.put(playerUuid, nowMs);
                            }
                        }
                    }
                }
            }
        }
    }
}
