package com.cookieukw.SimTale.systems;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Rotation4;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.PrefabManager;
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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
        if (heldItem != null && heldItem.getItemId().equals("simtale:Baby")) {
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock != null) {
                String childIdStr = heldItem.getFromMetadataOrNull("childId", Codec.STRING);
                if (childIdStr != null) {
                    UUID childId;
                    try {
                        childId = UUID.fromString(childIdStr);
                    } catch (IllegalArgumentException badId) {
                        // Corrupt/hand-edited item metadata used to throw straight out of the
                        // click handler instead of just ignoring the item.
                        LOGGER.atWarning().log("SimTale: item de bebe com childId invalido: " + childIdStr);
                        return;
                    }

                    GrowthComponent childComp = null;
                    for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
                        if (child.childId != null && child.childId.equals(childId)) {
                            childComp = child;
                            break;
                        }
                    }

                    if (childComp == null) {
                        childComp = Caskara.load("child_" + childId, GrowthComponent.class);
                        if (childComp != null) {
                            LifecycleManager.ACTIVE_CHILDREN.add(childComp);
                        }
                    }

                    if (childComp != null) {
                        if (childComp.stage == GrowthStage.BABY) {
                            playerRefComp.sendMessage(Message.translation("general.baby.newborn_cannot_place"));
                            event.setCancelled(true);
                            return;
                        }

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
            
            // Derived from the blueprint item instead of hardcoded: every blueprint used to
            // build a tavern. "Blueprint_TavernHouse" -> "TavernHouse".
            String itemId = event.getItemInHand().getId();
            int separator = itemId.lastIndexOf('_');
            String prefabName = separator >= 0 && separator < itemId.length() - 1
                    ? itemId.substring(separator + 1)
                    : "TavernHouse";
            if (PrefabManager.getPrefab(prefabName) == null) {
                prefabName = "TavernHouse";
            }
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock != null) {
                PlayerRef pRef = event.getPlayerRefComponent();
                ConstructionSiteComponent activePreview = ConstructionPreviewManager.get(pRef.getUuid());

                if (activePreview != null) {
                    // Confirm and commit if player right-clicks close to the preview anchor
                    if (targetBlock.distance(activePreview.anchor) < 4.0) {
                        if (!activePreview.isClear) {
                            pRef.sendMessage(Message.raw("Construction denied! The area is obstructed (marked red)."));
                            return;
                        }
                        ConstructionSiteComponent committed = ConstructionPreviewManager.commit(pRef.getUuid(), world);
                        if (committed != null) {
                            committed.isBuilding = true;
                            pRef.sendMessage(Message.raw("Construction started! NPCs will now come to build."));
                        }
                    } else {
                        // Otherwise, move/update the preview to the new looked block
                        Vector3i spawnPos = new Vector3i(targetBlock.x, targetBlock.y + 1, targetBlock.z);
                        TransformComponent transform = playerAccessor.getComponent(playerRef, TransformComponent.getComponentType());
                        Rotation4 facing = Rotation4.NORTH;
                        if (transform != null) {
                            facing = Rotation4.fromYawDegrees(Math.toDegrees(transform.getRotation().yaw()));
                        }
                        ConstructionPreviewManager.update(pRef.getUuid(), world, spawnPos, facing);
                        pRef.sendMessage(Message.raw("Moved preview to new location. Right click the preview to confirm."));
                    }
                } else {
                    // Start a new preview session
                    Vector3i spawnPos = new Vector3i(targetBlock.x, targetBlock.y + 1, targetBlock.z);
                    TransformComponent transform = playerAccessor.getComponent(playerRef, TransformComponent.getComponentType());
                    Rotation4 facing = Rotation4.NORTH;
                    if (transform != null) {
                        facing = Rotation4.fromYawDegrees(Math.toDegrees(transform.getRotation().yaw()));
                    }
                    ConstructionSiteComponent site = ConstructionPreviewManager.start(pRef.getUuid(), prefabName, spawnPos);
                    site.facing = facing;
                    site.roofFacing = facing;
                    ConstructionHelper.placePreview(world, site);
                    pRef.sendMessage(Message.raw("Ghost preview placed. Right click the preview to confirm, or use '/build rotate'."));
                }
            }
            return;
        }

        Ref<EntityStore> targetRef = event.getTargetEntityRef();
        
        // This used to log at INFO on *every* right click by *every* player, flooding the
        // server console. Nothing is logged until an NPC is actually involved.
        if (targetRef == null)
            return;

        // world.getEntityStore() returns EntityStore, which has getStore() ->
        // Store<EntityStore>
        Store<EntityStore> store = world.getEntityStore().getStore();
        SimNPCComponent npc = store.getComponent(targetRef,
                SimTale.SIM_NPC_COMPONENT_TYPE);

        if (npc == null) {
            // Re-attach path, for a SimTale NPC whose component did not survive a world reload.
            //
            // It used to adopt ANY entity: right-clicking a chicken, a hostile mob or another
            // player added SIM_NPC_COMPONENT_TYPE to it and gave it a generated name. Since
            // RoutineAISystem's query is exactly that component, the victim then started running
            // the villager routine — walking to beds, being mounted, getting Frozen — with no way
            // out. Two guards now stand in the way of that.
            if (store.getComponent(targetRef, Player.getComponentType()) != null) {
                return;
            }

            UUIDComponent uuidComp = store.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                // "simtale" shell, not Caskara's "default" — see SimNPCPersistence.DB_SHELL.
                // A record here is the proof that this entity really is one of ours; without it
                // there is nothing to re-attach and adopting the entity would be an invention.
                SimNPCData data = SimNPCPersistence.loadData(uuidComp.getUuid());
                if (data == null) {
                    LOGGER.atFine().log("SimTale: entidade sem registro no shell simtale, ignorada.");
                    return;
                }

                String name = data.name;
                if (name == null || name.isEmpty()) {
                    PersistentDisplayName displayName = store.getComponent(targetRef, PersistentDisplayName.getComponentType());
                    if (displayName != null && displayName.getDisplayName() != null) {
                        name = displayName.getDisplayName().toString();
                    }
                }
                if (name == null || name.isEmpty()) {
                    name = com.cookieukw.SimTale.core.SimNPCNameGenerator.generate();
                }

                LOGGER.atInfo().log("SimTale: NPC " + name + " remontado apos carregamento do mundo!");
                npc = new SimNPCComponent(uuidComp.getUuid(), name);
                npc.entityRef = targetRef;
                SimNPCPersistence.loadNPC(npc);
                store.addComponent(targetRef, SimTale.SIM_NPC_COMPONENT_TYPE, npc);

                SimTale.trackNpc(npc);
            }
        }

        if (npc == null)
            return;

        // Entities adopted before the guard above still carry the component, so the cow keeps
        // opening the villager panel until it is cleaned up. Gender is the tell: spawnNPC always
        // sets it, the old adoption path never did. Same criterion /simtale forget uses, so what
        // refuses to open here is exactly what that command will clear.
        if (npc.gender == null) {
            LOGGER.atFine().log("SimTale: entidade adotada por engano ignorada. Use /simtale forget.");
            return;
        }

        // --- Pick up Baby NPC into Inventory ---
        GrowthComponent childComp = null;
        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(child.childId)) {
                childComp = child;
                break;
            }
        }

        if (childComp != null && childComp.stage == GrowthStage.BABY) {
            ItemStack babyItem = new ItemStack("simtale:Baby", 1).withMetadata("childId", Codec.STRING, childComp.childId.toString());

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
