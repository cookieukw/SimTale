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
import com.cookieukw.SimTale.core.lifecycle.FamilyBonds;
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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

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

        // --- Put down a carried child: crouch + right-click ---
        //
        // Before anything else that consumes a click. The gesture has to work with the child on
        // your shoulders, which is precisely when her interaction panel is unreachable — she is not
        // in front of the camera to be clicked on.
        //
        // Crouch is required rather than a bare right-click so this cannot fire while placing
        // blocks or opening a chest, and crouch *alone* was rejected because players hold it
        // constantly near ledges.
        Store<EntityStore> carryStore = world.getEntityStore().getStore();
        // Crouch is read first, and it is what gates the log.
        //
        // The order matters for diagnosis, not for behaviour: the previous version only logged once
        // isCarryingSomeone had already returned true, so a false there produced complete silence —
        // indistinguishable from the event never firing. Crouching while right-clicking is rare
        // enough that logging on it costs nothing and tells us which half failed.
        boolean crouching = ChildCarryHelper.isCrouching(playerRef.getStore(), playerRef);
        if (crouching) {
            boolean carrying = ChildCarryHelper.isCarryingSomeone(carryStore, playerRef);
            LOGGER.atInfo().log("[SimTale] carry: clique direito agachado, carregando=" + carrying);

            if (carrying && ChildCarryHelper.putDown(carryStore, playerRef, playerRefComp)) {
                event.setCancelled(true);
                return;
            }
        }

        // The tool items are NOT handled here — see SimTaleItemRegistry.
        //
        // They were, briefly, on the assumption that none of them declared an "Interactions" block.
        // Three of the four do: they point at RuneCore_GenericItemUse, whose handler runs first and
        // consumes the click, so nothing added here ever fired for them. Every custom item in this
        // mod goes through RuneCoreItemManager, and these are no exception.

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

        // The body follows the stage, not the fact that this came out of a "Baby" item.
        //
        // It was always a child model, which is fine while the item is what it says on the tin.
        // /simtale growbaby can hand you a teenager or an adult still in item form, and placing
        // one of those produced an adult in a child body — the same mismatch GrowthManager had at
        // the ADULT branch, reached by a different door. Nothing corrects it afterwards either:
        // the body swap hangs off a stage *change*, and this one already happened in the item.
        boolean grownBody = childComp.stage != null
                && childComp.stage.ordinal() >= GrowthStage.TEEN.ordinal();
        SimNPCFactory.NPCType childType;
        if (grownBody) {
            childType = childComp.gender == Gender.MALE
                ? SimNPCFactory.NPCType.HUMAN_MALE
                : SimNPCFactory.NPCType.HUMAN_FEMALE;
        } else {
            childType = childComp.gender == Gender.MALE
                ? SimNPCFactory.NPCType.CHILD_MALE
                : SimNPCFactory.NPCType.CHILD_FEMALE;
        }

        // Scale at spawn rather than a resize afterwards: the resize below left a frame where she
        // was drawn full size before shrinking, which is the flash reported during carry testing.
        Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType,
                LifecycleManager.calculateTargetScale(childComp, WorldUtil.tick()));
        childComp.childId = Objects.requireNonNull(store.getComponent(childRef, UUIDComponent.getComponentType())).getUuid();

        SimNPCComponent childNPCComp = store.getComponent(childRef, SimTale.SIM_NPC_COMPONENT_TYPE);
        if (childNPCComp != null) {
            childNPCComp.name = childComp.getFullName();
            store.putComponent(childRef, PersistentDisplayName.getComponentType(),
                new PersistentDisplayName(Message.raw(childComp.getFullName())));
            store.putComponent(childRef, Nameplate.getComponentType(),
                new Nameplate(childComp.getFullName()));

            // A child used to be born a social stranger to its own parents: the family data lived
            // in GrowthComponent and was never projected onto the relationship map that gifts,
            // dialogue and the map tint actually read.
            FamilyBonds.linkToFamily(childNPCComp, childComp);
            SimNPCPersistence.saveNPC(childNPCComp);
        }

        // Kept in sync with what the entity was actually spawned at, so the growth tick and the
        // saved record start from the same number.
        childComp.currentScale = LifecycleManager.calculateTargetScale(childComp, WorldUtil.tick());

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
