package com.cookieukw.SimTale.logic;

import com.cookie.runecore.api.RuneCoreItemManager;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;

public class SimTaleItemRegistry {

    /**
     * Same cap {@code SimNPCSpawnSystem} used to enforce before it was removed in favour of this
     * item — without it, a player with a stack of contracts could flood the world with NPCs in
     * one sitting.
     */
    private static final int MAX_ACTIVE_NPCS = 10;

    public static void init() {
        RuneCoreItemManager.register("TownBell", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔔 O sino da cidade soou!"));
        });
        
        RuneCoreItemManager.register("InspectorsJournal", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📖 Você abriu o Diário do Inspetor!"));
        });
        
        RuneCoreItemManager.register("InnkeepersLedger", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📒 Você abriu o Livro do Estalajadeiro!"));
        });
        
        RuneCoreItemManager.register("ImmigrationContract", (player, playerRef) -> {
            if (SimTale.ACTIVE_NPCS.size() >= MAX_ACTIVE_NPCS) {
                playerRef.sendMessage(Message.raw("A vila já está cheia — não há espaço para mais um morador agora."));
                return;
            }

            if (playerRef.getReference() == null || !playerRef.getReference().isValid()) {
                return;
            }
            Ref<EntityStore> pRef = playerRef.getReference();
            Store<EntityStore> store = pRef.getStore();
            TransformComponent transform = store.getComponent(pRef, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }

            Vector3d pos = new Vector3d(transform.getPosition()).add(2, 0, 2);
            SimNPCFactory.NPCType type = Math.random() < 0.5
                ? SimNPCFactory.NPCType.HUMAN_MALE
                : SimNPCFactory.NPCType.HUMAN_FEMALE;

            // "Consumable": true in the item JSON only drives the engine's built-in food/potion
            // consumption — it has no effect on a custom RuneCore_GenericItemUse interaction, so
            // the contract has to be removed from the hotbar by hand, same as InteractionManager
            // does for gifts.
            InventoryComponent.Hotbar hotbar = store.getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
            if (hotbar != null) {
                hotbar.getInventory().removeItemStackFromSlot(hotbar.getActiveSlot(), 1);
            }

            // Item interactions tick from inside the store's own processing window;
            // Store.addEntity (inside spawnNPC) is a structural write and throws
            // "Store is currently processing!" if called straight from here. Same fix as
            // everywhere else in the mod that mutates entities off a system's own tick.
            WorldUtil.execute(() -> {
                Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
                if (npcRef == null) {
                    playerRef.sendMessage(Message.raw("O contrato não encontrou ninguém disposto a se mudar agora. Tente de novo."));
                    return;
                }
                SimNPCComponent npc = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                String name = npc != null ? npc.name : "Alguém";
                playerRef.sendMessage(Message.raw(name + " chegou para morar na vila!"));
            });
        });
        
        // Was entirely missing — Baby.json points its Secondary interaction at
        // "RuneCore_GenericItemUse" same as every other custom item here, but with no matching
        // register() call RuneCoreGenericItemInteraction always logged "No handler registered
        // for item: Baby" and the interaction just failed. Nothing to do with the raw
        // PlayerMouseButtonEvent path (that one's real bug — SimTale.java using .register
        // instead of .registerGlobal — is separate, and matters for the Blueprint item instead,
        // which has no Interactions override and so falls through to that path directly).
        RuneCoreItemManager.register("Baby", (player, playerRef) -> {
            if (playerRef.getReference() == null || !playerRef.getReference().isValid()) {
                return;
            }
            Ref<EntityStore> pRef = playerRef.getReference();
            Store<EntityStore> store = pRef.getStore();
            TransformComponent transform = store.getComponent(pRef, TransformComponent.getComponentType());
            if (transform == null) {
                return;
            }
            ItemStack heldItem = InventoryComponent.getItemInHand(store, pRef);
            if (heldItem == null || !"Baby".equals(heldItem.getItemId())) {
                return;
            }

            // No target block available from this interaction (unlike a raycasted click) — same
            // "just in front of the player" placement ImmigrationContract above uses.
            Vector3d spawnPos = new Vector3d(transform.getPosition()).add(2, 0, 2);

            // Structural write (spawnNPC -> Store.addEntity) from inside the interaction's own
            // processing window — same deferral ImmigrationContract needs above.
            WorldUtil.execute(() -> SimTaleEventHandler.placeBabyFromHeldItem(store, pRef, playerRef, heldItem, spawnPos));
        });

        RuneCoreItemManager.register("QuartermastersGlass", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔍 Você está olhando pela Lupa do Intendente!"));
        });
        
        RuneCoreItemManager.register("BirthdayCake", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🎂 Que delícia! Bolo de aniversário!"));
        });
        
        RuneCoreItemManager.register("WeddingRing", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("💍 Você está segurando uma aliança de casamento!"));
        });
    }
}
