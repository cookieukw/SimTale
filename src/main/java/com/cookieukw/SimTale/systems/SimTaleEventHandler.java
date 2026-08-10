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
import com.cookieukw.SimTale.logic.NPCPregnancyPage;
import com.cookieukw.SimTale.logic.PlayerPregnancyPage;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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

        World world = WorldUtil.first();
        if (world == null)
            return;

        Player player = event.getPlayer();
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null) return;
        ComponentAccessor<EntityStore> playerAccessor = playerRef.getStore();
        PlayerRef playerRefComp = playerAccessor.getComponent(playerRef, Universe.get().getPlayerRefComponentType());
        if (playerRefComp == null) return;
        
        LOGGER.atInfo().log("SimTale Debug: PlayerMouseButtonEvent fired!");
        ItemStack heldItemTest = InventoryComponent.getItemInHand(playerRef.getStore(), playerRef);
        if (heldItemTest != null && heldItemTest.getItemId() != null) {
            LOGGER.atInfo().log("SimTale Debug: Held item is: " + heldItemTest.getItemId());
        } else {
            LOGGER.atInfo().log("SimTale Debug: Held item is null or has no ID");
        }

        // --- Place Baby Item on Block Click ---
        ItemStack heldItem = InventoryComponent.getItemInHand(playerRef.getStore(), playerRef);
        if (heldItem != null) {
            LOGGER.atInfo().log("SimTale Debug: Right-clicked holding item with ID: " + heldItem.getItemId());
        }

        if (heldItem != null && heldItem.getItemId().equals("Baby")) {
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock != null) {
                Vector3d spawnPos = new Vector3d(targetBlock.x + 0.5, targetBlock.y + 1, targetBlock.z + 0.5);
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (placeBabyFromHeldItem(store, playerRef, playerRefComp, heldItem, spawnPos)) {
                    event.setCancelled(true);
                }
            }
        }

        // --- Confirm a blueprint marker's construction on right-click ---
        // Placing Blueprint_TavernHouse (BedPlaceBlockEventSystem) shows the hologram; this is
        // the other half — right-clicking that same marker block starts the real build, the same
        // way '/build start' commits a command-driven preview. Breaking the marker instead
        // (BedBlockEventSystem) cancels it.
        Vector3i confirmTarget = event.getTargetBlock();
        if (confirmTarget != null) {
            BlockType confirmType = world.getBlockType(confirmTarget.x, confirmTarget.y, confirmTarget.z);
            // Same tolerant match the placement half uses — an exact equals here would confirm
            // nothing for exactly the ids that the placement half already failed to recognise.
            if (confirmType != null && BedPlaceBlockEventSystem.isBlueprintMarker(confirmType.getId())) {
                UUID siteId = ConstructionPreviewManager.idForBlock(confirmTarget);
                ConstructionSiteComponent pendingSite = ConstructionPreviewManager.get(siteId);
                if (pendingSite != null && !pendingSite.isBuilding) {
                    if (!pendingSite.isClear) {
                        playerRefComp.sendMessage(Message.raw(
                                "[SimTale] Não é possível iniciar a construção: a área ao redor do marcador ainda está obstruída."));
                    } else {
                        ConstructionSiteComponent committedSite = ConstructionPreviewManager.commit(siteId, world);
                        if (committedSite != null) {
                            committedSite.isBuilding = true;
                            playerRefComp.sendMessage(Message.raw(
                                    "[SimTale] Construção iniciada! NPCs virão construir a TavernHouse."));
                        }
                    }
                    event.setCancelled(true);
                    return;
                }
            }
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
            ItemStack babyItem = new ItemStack("Baby", 1).withMetadata("childId", Codec.STRING, childComp.childId.toString());

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

    /**
     * Spawns the child NPC a held "Baby" item refers to at {@code spawnPos}, consumes the item,
     * and persists the change. Shared by the block-click flow above and
     * {@code SimTaleCommand}'s {@code forceplacebaby} debug command — the click path turned out
     * to be unreliable enough (see the ForcePlaceBabySubCommand javadoc) that testing needed a
     * direct alternative that doesn't depend on it.
     *
     * @return true if a child was placed (caller may want to cancel the triggering event/consume
     *         the click); false if the held item wasn't a valid placeable baby.
     */
    public static boolean placeBabyFromHeldItem(Store<EntityStore> store, Ref<EntityStore> playerRef,
            PlayerRef playerRefComp, ItemStack heldItem, Vector3d spawnPos) {
        if (heldItem == null || !"Baby".equals(heldItem.getItemId())) {
            return false;
        }
        String childIdStr = heldItem.getFromMetadataOrNull("childId", Codec.STRING);
        if (childIdStr == null) {
            return false;
        }
        UUID childId;
        try {
            childId = UUID.fromString(childIdStr);
        } catch (IllegalArgumentException badId) {
            // Corrupt/hand-edited item metadata used to throw straight out of the click handler
            // instead of just ignoring the item.
            LOGGER.atWarning().log("SimTale: item de bebe com childId invalido: " + childIdStr);
            return false;
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
        if (childComp == null) {
            return false;
        }

        if (childComp.stage == GrowthStage.BABY) {
            playerRefComp.sendMessage(Message.translation("general.baby.newborn_cannot_place"));
            return false;
        }

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

        // Scale baby down visually to match its current growth stage
        PersistentModel pm = store.getComponent(childRef, PersistentModel.getComponentType());
        if (pm != null) {
            ModelReference oldRef = pm.getModelReference();
            ModelReference newRef = new ModelReference(oldRef.getModelAssetId(), childComp.currentScale, new HashMap<>());
            store.replaceComponent(childRef, PersistentModel.getComponentType(), new PersistentModel(newRef));
        }

        childComp.putDown();
        Caskara.save("child_" + childComp.childId.toString(), childComp);

        InventoryComponent.Hotbar hotbarComponent = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbarComponent != null && hotbarComponent.getActiveSlot() != -1) {
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
            combinedInventory.removeItemStackFromSlot(hotbarComponent.getActiveSlot(), heldItem, 1);
        }

        playerRefComp.sendMessage(Message.raw("Você colocou o bebê " + childComp.getFullName() + " no chão."));
        return true;
    }
}
