package com.cookieukw.SimTale.logic;

import com.cookie.runecore.api.RuneCoreItemManager;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.systems.HouseBlueprintHelper;
import com.cookieukw.SimTale.systems.InspectorJournalHelper;
import com.cookieukw.SimTale.systems.SimTaleEventHandler;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;

public class SimTaleItemRegistry {
    
    private static long lastBellTimeMs = 0;


    /**
     * Same cap {@code SimNPCSpawnSystem} used to enforce before it was removed in favour of this
     * item — without it, a player with a stack of contracts could flood the world with NPCs in
     * one sitting.
     */
    private static final int MAX_ACTIVE_NPCS = 10;

    public static void init() {
        RuneCoreItemManager.register("TownBell", (player, playerRef) -> {
            long now = System.currentTimeMillis();
            if (now < lastBellTimeMs + 300000) {
                playerRef.sendMessage(Message.raw("[SimTale] O sino ainda esta ecoando... (cooldown ativo)"));
                return;
            }
            lastBellTimeMs = now;

            Ref<EntityStore> pRef = playerRef.getReference();
            if (pRef == null || !pRef.isValid()) return;
            Store<EntityStore> store = pRef.getStore();

            InventoryComponent.Hotbar hotbar = store.getComponent(pRef, InventoryComponent.Hotbar.getComponentType());
            if (hotbar != null) {
                ItemStack bellItem = hotbar.getInventory().getItemStackInSlot(hotbar.getActiveSlot());
                if (bellItem != null && "TownBell".equals(bellItem.getItemId())) {
                    String usesStr = bellItem.getFromMetadataOrNull("simtale_uses_left", com.hypixel.hytale.server.core.data.Codec.STRING);
                    int usesLeft = 3;
                    if (usesStr != null) {
                        try {
                            usesLeft = Integer.parseInt(usesStr);
                        } catch (NumberFormatException ignored) {}
                    }
                    usesLeft--;
                    
                    if (usesLeft <= 0) {
                        hotbar.getInventory().removeItemStackFromSlot(hotbar.getActiveSlot(), 1);
                        playerRef.sendMessage(Message.translation("general.bell.broke"));
                    } else {
                        bellItem.setMetadata("simtale_uses_left", String.valueOf(usesLeft), com.hypixel.hytale.server.core.data.Codec.STRING);
                        hotbar.getInventory().putItemStackInSlot(hotbar.getActiveSlot(), bellItem);
                        playerRef.sendMessage(Message.raw("[SimTale] Sino da Vila tocado! Restam " + usesLeft + " usos."));
                    }
                }
            }

            int count = 0;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    com.cookieukw.SimTale.systems.NeedsHelper.setNeed(null, npc.entityRef, com.cookieukw.SimTale.systems.NeedsHelper.ENERGY_ID, 0f);
                    npc.forceSleep = true;
                    count++;
                }
            }
            playerRef.sendMessage(Message.translation("general.bell.rang").param("count", count));
        });
        
        // The three lenses below replace placeholder handlers that only printed a line of hardcoded
        // Portuguese with an emoji the client renders as "??".
        RuneCoreItemManager.register("InspectorsJournal", (player, playerRef) ->
                withPlayerPosition(playerRef, pos ->
                        InspectorJournalHelper.inspect(WorldUtil.first(), playerRef, pos)));

        RuneCoreItemManager.register("InnkeepersLedger", (player, playerRef) ->
                openPage(player, playerRef, (pRef, store) ->
                        new SimBedDebugPage(playerRef, player, 0, true)));

        RuneCoreItemManager.register("HouseBlueprint", (player, playerRef) ->
                withPlayerPosition(playerRef, pos ->
                        HouseBlueprintHelper.inspect(WorldUtil.first(), playerRef, pos)));
        
        RuneCoreItemManager.register("ImmigrationContract", (player, playerRef) -> {
            if (SimTale.ACTIVE_NPCS.size() >= MAX_ACTIVE_NPCS) {
                playerRef.sendMessage(Message.translation("general.contract.village_full"));
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
                    playerRef.sendMessage(Message.translation("general.contract.failed"));
                    return;
                }
                SimNPCComponent npc = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                Message arrival = npc != null
                        ? Message.translation("general.contract.arrived").param("name", npc.name)
                        : Message.translation("general.contract.arrived_unnamed");
                playerRef.sendMessage(arrival);
            });
        });
        
        // Was entirely missing — Baby.json points its Secondary interaction at
        // "RuneCore_GenericItemUse" same as every other custom item here, but with no matching
        // register() call RuneCoreGenericItemInteraction always logged "No handler registered
        // for item: Baby" and the interaction just failed.
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

        // Blueprint_TavernHouse is a real placeable block now (BedPlaceBlockEventSystem/
        // BedBlockEventSystem react to it being placed/broken, and SimTaleEventHandler reacts to
        // it being right-clicked), not an item interaction — see those classes for why: a
        // live-following hologram (tried first) turned out to cost a full obstruction re-scan of
        // the whole prefab on every rotation, which was expensive enough to bog down the server
        // for the one active preview alone. Nothing to register here anymore.

        RuneCoreItemManager.register("QuartermastersGlass", (player, playerRef) ->
                openPage(player, playerRef, (pRef, store) ->
                        new SimChestDebugPage(playerRef, player, 0, true)));
        
        RuneCoreItemManager.register("BirthdayCake", (player, playerRef) -> {
            Ref<EntityStore> pRef = playerRef.getReference();
            if (pRef == null || !pRef.isValid()) return;
            Store<EntityStore> store = pRef.getStore();
            TransformComponent playerTransform = store.getComponent(pRef, TransformComponent.getComponentType());
            if (playerTransform == null) return;

            SimNPCComponent nearestChild = null;
            com.cookieukw.SimTale.core.lifecycle.GrowthComponent nearestGrowth = null;
            double minDistance = Double.MAX_VALUE;

            com.cookieukw.SimTale.core.lifecycle.LifecycleState.ensureLoaded();

            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    for (com.cookieukw.SimTale.core.lifecycle.GrowthComponent child : com.cookieukw.SimTale.core.lifecycle.LifecycleManager.ACTIVE_CHILDREN) {
                        if (child.childId != null && child.childId.equals(npc.entityId)) {
                            TransformComponent npcTransform = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                            if (npcTransform != null) {
                                double distSq = playerTransform.getPosition().distanceSquared(npcTransform.getPosition());
                                if (distSq < minDistance) {
                                    minDistance = distSq;
                                    nearestChild = npc;
                                    nearestGrowth = child;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            if (nearestChild == null || nearestGrowth == null || minDistance > 100.0) { // dentro de 10 blocos aprox
                playerRef.sendMessage(Message.raw("[SimTale] Nenhuma crianca por perto para verificar o estagio."));
            } else {
                playerRef.sendMessage(Message.raw("[SimTale] " + nearestChild.name + " esta no estagio: " + nearestGrowth.stage.name()));
            }
        });
        
        // The ring's real behaviour lives in the gift path (InteractionManager); this only fires
        // when it is used on nothing.
        RuneCoreItemManager.register("WeddingRing", (player, playerRef) ->
                playerRef.sendMessage(Message.translation("general.ring.holding")));
    }

    /**
     * Runs {@code action} with the player's current position, or does nothing if it cannot be read.
     * <p>
     * RuneCore's callback hands over the player and nothing else — no target block, no target
     * entity. Items that need to know "which bed" or "which villager" therefore have to work from
     * where the player is standing.
     */
    private static void withPlayerPosition(PlayerRef playerRef, java.util.function.Consumer<Vector3d> action) {
        Ref<EntityStore> pRef = playerRef.getReference();
        if (pRef == null || !pRef.isValid()) return;

        TransformComponent transform = pRef.getStore()
                .getComponent(pRef, TransformComponent.getComponentType());
        if (transform == null) return;

        action.accept(new Vector3d(transform.getPosition()));
    }

    /**
     * Opens a custom page from an item interaction.
     * <p>
     * Deferred onto the world thread for the same reason every other structural operation in this
     * class is: item interactions run inside the store's processing window.
     */
    private static void openPage(Player player, PlayerRef playerRef, PageFactory factory) {
        Ref<EntityStore> pRef = playerRef.getReference();
        if (pRef == null || !pRef.isValid()) return;

        Store<EntityStore> store = pRef.getStore();
        WorldUtil.execute(() -> {
            if (!pRef.isValid()) return;
            player.getPageManager().openCustomPage(pRef, store, factory.create(pRef, store));
        });
    }

    @FunctionalInterface
    private interface PageFactory {
        InteractiveCustomUIPage<String> create(Ref<EntityStore> playerRef, Store<EntityStore> store);
    }
}
