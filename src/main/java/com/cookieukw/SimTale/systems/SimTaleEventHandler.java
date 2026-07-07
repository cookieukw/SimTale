package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles interactions between players and NPCs.
 */
public class SimTaleEventHandler implements Consumer<PlayerMouseButtonEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void accept(PlayerMouseButtonEvent event) {
        if (event.getMouseButton() == null ||
            event.getMouseButton().mouseButtonType != MouseButtonType.Right ||
            event.getMouseButton().state != MouseButtonState.Pressed) {
            return;
        }

        World world = null;
        for (World w : Universe.get().getWorlds().values()) {
            world = w;
            break;
        }

        if (world == null)
            return;

        Player player = event.getPlayer();
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null) return;
        ComponentAccessor<EntityStore> playerAccessor = playerRef.getStore();
        PlayerRef playerRefComp = playerAccessor.getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComp == null) return;

        // --- Place Baby Item on Block Click ---
        ItemStack heldItem = InventoryComponent.getItemInHand(playerRef.getStore(), playerRef);
        if (heldItem != null && heldItem.getItemId().equals("simtale:baby")) {
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock != null) {
                String childIdStr = heldItem.getFromMetadataOrNull("childId", Codec.STRING);
                if (childIdStr != null) {
                    UUID childId = UUID.fromString(childIdStr);

                    GrowthComponent childComp = null;
                    for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
                        if (child.childId != null && child.childId.equals(childId)) {
                            childComp = child;
                            break;
                        }
                    }

                    if (childComp == null) {
                        childComp = Caskara.load("child_" + childId.toString(), GrowthComponent.class);
                        if (childComp != null) {
                            LifecycleManager.ACTIVE_CHILDREN.add(childComp);
                        }
                    }

                    if (childComp != null) {
                        // Spawn baby entity back at target block position (1 block above)
                        Vector3d spawnPos = new Vector3d(targetBlock.x + 0.5, targetBlock.y + 1, targetBlock.z + 0.5);
                        Store<EntityStore> store = world.getEntityStore().getStore();

                        SimNPCFactory.NPCType childType = childComp.gender == Gender.MALE
                            ? SimNPCFactory.NPCType.CHILD_MALE
                            : SimNPCFactory.NPCType.CHILD_FEMALE;

                        Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);
                        childComp.childId = Objects.requireNonNull(store.getComponent(childRef, UUIDComponent.getComponentType())).getUuid();

                        SimNPCComponent childNPCComp = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                        if (childNPCComp != null) {
                            childNPCComp.name = childComp.getFullName();
                            store.putComponent(childRef, PersistentDisplayName.getComponentType(),
                                new PersistentDisplayName(Message.raw(childComp.getFullName())));
                            store.putComponent(childRef, Nameplate.getComponentType(),
                                new Nameplate(childComp.getFullName()));
                        }

                        // Scale baby down visually to match BABY stage
                        PersistentModel pm = store.getComponent(childRef, PersistentModel.getComponentType());
                        if (pm != null) {
                            ModelReference oldRef = pm.getModelReference();
                            ModelReference newRef = new ModelReference(oldRef.getModelAssetId(), childComp.currentScale, new HashMap<>());
                            store.replaceComponent(childRef, PersistentModel.getComponentType(), new PersistentModel(newRef));
                        }

                        // Update baby state and persist
                        childComp.putDown();
                        Caskara.save("child_" + childComp.childId.toString(), childComp);

                        // Remove item from hand
                        InventoryComponent.Hotbar hotbarComponent = playerRef.getStore().getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
                        if (hotbarComponent != null && hotbarComponent.getActiveSlot() != -1) {
                            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
                            combinedInventory.removeItemStackFromSlot(hotbarComponent.getActiveSlot(), heldItem, 1);
                        }

                        playerRefComp.sendMessage(Message.raw("Você colocou o bebê " + childComp.getFullName() + " no chão."));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Blueprint item handling
        if (event.getItemInHand() != null && event.getItemInHand().getId() != null &&
            event.getItemInHand().getId().toLowerCase().contains("blueprint")) {
            
            // Extract prefab name (e.g. from simtale:blueprint_tavernhouse -> TavernHouse)
            // For now, hardcode "TavernHouse" or map it if needed. 
            // Better: use the item's custom data or fallback to TavernHouse for the prototype
            String prefabName = "TavernHouse";
            
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock != null) {
                ConstructionSiteComponent closestSite = null;
                double minDistance = Double.MAX_VALUE;

                for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                    double dist = site.anchor.distance(targetBlock);
                    if (dist < 20.0 && dist < minDistance) {
                        minDistance = dist;
                        closestSite = site;
                    }
                }

                PlayerRef pRef = event.getPlayerRefComponent();
                
                if (closestSite != null && !closestSite.isBuilding) {
                    closestSite.isBuilding = true;
                    pRef.sendMessage(Message.raw("Construction started! NPCs will now come to build."));
                    ConstructionHelper.clearPreview(world, closestSite);
                } else {
                    // Place new preview 1 block above the clicked block
                    Vector3i spawnPos = new Vector3i(targetBlock.x, targetBlock.y + 1, targetBlock.z);
                    Store<EntityStore> eStore = world.getEntityStore().getStore();
                    ConstructionHelper.placePreview(world, eStore, spawnPos, prefabName);
                    pRef.sendMessage(Message.raw("Preview placed for " + prefabName + ". Right click again nearby to confirm."));
                }
            }
            return;
        }

        Ref<EntityStore> targetRef = event.getTargetEntityRef();
        
        LOGGER.atInfo().log("SimTale [DEBUG]: PlayerMouseButtonEvent (Right Click) DISPARADO. Alvo Ref: " + (targetRef != null ? targetRef.toString() : "null"));
        
        if (targetRef == null)
            return;

        // world.getEntityStore() returns EntityStore, which has getStore() ->
        // Store<EntityStore>
        Store<EntityStore> store = world.getEntityStore().getStore();
        SimNPCComponent npc = store.getComponent(targetRef,
                SimTale.SIM_NPC_COMPONENT_TYPE);

        if (npc == null) {
            UUIDComponent uuidComp = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                SimNPCData data = Caskara.load(uuidComp.getUuid().toString(), SimNPCData.class);
                if (data != null) {
                    LOGGER.atInfo().log("SimTale: NPC " + data.name + " remontado apos carregamento do mundo!");
                    npc = new SimNPCComponent(uuidComp.getUuid(), data.name);
                    npc.entityRef = targetRef;
                    SimNPCPersistence.loadNPC(npc);
                    store.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);
                    
                    final UUID targetId = uuidComp.getUuid();
                    SimTale.ACTIVE_NPCS.removeIf(active -> active.entityId != null && active.entityId.equals(targetId));
                    SimTale.ACTIVE_NPCS.add(npc);
                }
            }
        }

        if (npc == null)
            return;

        // --- Pick up Baby NPC into Inventory ---
        GrowthComponent childComp = null;
        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                childComp = child;
                break;
            }
        }

        if (childComp != null && childComp.stage == GrowthStage.BABY) {
            ItemStack babyItem = new ItemStack("simtale:baby", 1).withMetadata("childId", Codec.STRING, childComp.childId.toString());

            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
            ItemStackTransaction transaction = combinedInventory.addItemStack(babyItem);
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(playerRef, remainder, playerRef.getStore());
            }

            // Update baby state and persist
            childComp.pickUp(playerRefComp.getUuid());
            Caskara.save("child_" + childComp.childId.toString(), childComp);

            // Remove the baby entity from the world
            store.removeEntity(targetRef, RemoveReason.REMOVE);

            playerRefComp.sendMessage(Message.raw("Você pegou o bebê " + childComp.getFullName() + " no colo!"));
            return;
        }

        LOGGER.atInfo().log("SimTale: Interacao com NPC detectada: " + npc.name);

        // Open the NPC interaction page
        player.getPageManager().openCustomPage(playerRef, playerRef.getStore(), new NPCInteractionPage(playerRefComp, player, npc));
    }
}
