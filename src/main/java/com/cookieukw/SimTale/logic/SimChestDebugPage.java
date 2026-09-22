package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.DebugAccess;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.ChestRegistry;
import com.cookieukw.SimTale.systems.HouseManager;
import com.cookieukw.SimTale.systems.NPCFoodHelper;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Registered-chest browser, the counterpart of {@link SimBedDebugPage}.
 *
 * <p>Built after a session where {@code /simtale chestcheck} claimed nothing was registered while
 * three chests sat in the house: a one-line command can only ever describe the nearest chest, so
 * it could not distinguish "nothing registered" from "registered somewhere I am not standing".
 * This screen shows the whole registry at once, plus what each chest holds — which is what
 * debugging the hunger routine actually needs.
 */
@SuppressWarnings("null")
public class SimChestDebugPage extends InteractiveCustomUIPage<String> {

    private static final int ROWS_PER_PAGE = 5;

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex;

    /**
     * Read-only mode: hides Teleport and Remove.
     * <p>
     * Set when the screen is opened by the Quartermaster's Glass item rather than by
     * {@code /simtale debugchests}. Seeing what the village has stored is the point of the item.
     * <p>
     * Teleport especially: the registry holds every chest in the world, so shipping that button on
     * a craftable item hands the player a free long-distance warp to anywhere they have ever
     * stored something. Remove is merely pointless here — the block stays and the next scan puts
     * the entry straight back.
     */
    private final boolean readOnly;

    /**
     * Whether the control panel opened this screen, which is the only case where Back has a
     * destination.
     *
     * <p>Separate from {@code readOnly} because {@code /simtale debugchests} is neither: it wants
     * the editing buttons, but it was not reached through the hub, so sending Back there dropped
     * the player into the force-sleep/force-eat panel they never asked for.
     */
    private final boolean fromHub;

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        this(playerRefComp, player, 0, false, false);
    }

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        this(playerRefComp, player, initialIndex, false, false);
    }

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex, boolean readOnly) {
        this(playerRefComp, player, initialIndex, readOnly, false);
    }

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex,
                             boolean readOnly, boolean fromHub) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = initialIndex;
        this.readOnly = readOnly;
        this.fromHub = fromHub;
    }

    /** Teleport and Remove need both an editing entry point and creative mode. */
    private boolean canEdit() {
        return !readOnly && DebugAccess.canEdit(player);
    }

    /**
     * Snapshot of the registry, pruned of chests that no longer exist.
     *
     * <p>Pruning only touches loaded chunks: a chest in an unloaded chunk cannot be inspected and
     * must be assumed to still be there, otherwise walking away from a house would quietly erase
     * its storage from the registry.
     */
    private List<HouseBlockPos> getChests(World world) {
        synchronized (ChestRegistry.CHESTS) {
            if (world != null) {
                ChestRegistry.CHESTS.removeIf(cp -> {
                    WorldChunk chunk = world.getChunkStore().getChunkComponent(ChunkUtil.indexChunkFromBlock(cp.x, cp.z), WorldChunk.getComponentType());
                    if (chunk == null) return false;
                    return !ChestRegistry.isContainerAt(world, cp.x, cp.y, cp.z);
                });
            }
            List<HouseBlockPos> list = new ArrayList<>(ChestRegistry.CHESTS);
            list.sort((a, b) -> {
                if (a.x != b.x) return Integer.compare(a.x, b.x);
                if (a.y != b.y) return Integer.compare(a.y, b.y);
                return Integer.compare(a.z, b.z);
            });
            return list;
        }
    }

    /** "3 itens (2 comida)", or "Vazio" / "Fora de alcance" when there is nothing to report. */
    private Message describeContents(World world, HouseBlockPos pos) {
        ItemContainerBlock block = BlockModule.getComponent(
                ItemContainerBlock.getComponentType(), world, pos.x, pos.y, pos.z);
        if (block == null) return Message.translation("ui.debugchests.contentsUnloaded");

        ItemContainer container = block.getItemContainer();
        int items = 0;
        int food = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            items++;
            if (NPCFoodHelper.isFood(stack)) food++;
        }

        if (items == 0) return Message.translation("ui.debugchests.contentsEmpty");

        /* The count and its noun are composed as Messages: writing "item"/"itens" inline would
        print Portuguese to a player running the game in English, which is the same leak the
        profession names had.
        */
        Message itemWord = Message.translation(
                items == 1 ? "ui.debugchests.itemSingular" : "ui.debugchests.itemPlural");
        Message itemsPart = Message.raw(items + " ").insert(itemWord);

        return Message.translation("ui.debugchests.contentsSummary")
                .param("items", itemsPart)
                .param("food", String.valueOf(food));
    }

    /** Resolves the house link, naming the NPC owners when they are loaded. */
    private Message describeOwner(HouseBlockPos pos) {
        /* Chests outside a house are unusable by design — that is what keeps NPCs out of the
        world generator's loot chests — so say so instead of calling them "public".
        */
        UUID houseId = HouseManager.findHouseIdForChest(pos);
        if (houseId == null) return Message.translation("ui.debugchests.ownerNoHouse");

        HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
        if (house == null) return Message.translation("ui.debugchests.ownerNoData").param("id", shortId(houseId));
        if (house.owners.isEmpty()) return Message.translation("ui.debugchests.ownerNoOwners").param("id", shortId(houseId));

        /* One database read per owner, not two: resolving the name and classifying the state both
        need the same record, and this runs for every row every time the page renders.
        */
        List<String> names = new ArrayList<>();
        boolean anyOrphan = false;
        boolean anyOffline = false;
        for (String owner : house.owners) {
            ResolvedOwner resolved = resolveOwner(owner);
            names.add(resolved.label());
            if (resolved.state() == OwnerState.ORPHAN) anyOrphan = true;
            if (resolved.state() == OwnerState.OFFLINE) anyOffline = true;
        }

        /* The worst state wins the label: an orphan is a data problem worth acting on, while an
        owner that is merely out of range is normal and should not raise an alarm.
        */
        String joined = String.join(", ", names);
        if (anyOrphan) {
            return Message.translation("ui.debugchests.ownerNamesOrphan").param("owners", joined);
        }
        if (anyOffline) {
            return Message.translation("ui.debugchests.ownerNamesOffline").param("owners", joined);
        }
        return Message.translation("ui.debugchests.ownerNames").param("owners", joined);
    }

    /** Whether an owner id still corresponds to something. */
    private enum OwnerState { LOADED, OFFLINE, ORPHAN }

    private record ResolvedOwner(String label, OwnerState state) {
    }

    /**
     * Names an owner and classifies it in one pass.
     *
     * <p>This used to fall straight back to the first eight characters of the UUID, which read as
     * a glitch and hid the only question worth asking: is that owner an NPC that merely is not
     * loaded right now, or one that no longer exists at all?
     *
     * <p>The distinction matters because houses never released an owner, so the list accumulated.
     * It also rules out the tempting wrong fix: {@code ACTIVE_NPCS} alone cannot answer it, because
     * an NPC in an unloaded chunk is absent from it and still very much alive. Only the database
     * can tell those two apart.
     */
    private ResolvedOwner resolveOwner(String ownerUuid) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.toString().equals(ownerUuid)) {
                return new ResolvedOwner(npc.name, OwnerState.LOADED);
            }
        }

        String shortForm = ownerUuid.length() >= 8 ? ownerUuid.substring(0, 8) : ownerUuid;

        SimNPCData stored;
        try {
            stored = SimNPCPersistence.loadData(UUID.fromString(ownerUuid));
        } catch (IllegalArgumentException malformed) {
            // Not even a UUID — corrupt entry, and there is nothing to look up.
            return new ResolvedOwner(shortForm, OwnerState.ORPHAN);
        }

        if (stored == null) {
            return new ResolvedOwner(shortForm, OwnerState.ORPHAN);
        }

        /* The record still carries the name of an NPC that is simply not loaded, which is far more
        useful than eight hex characters.
        */
        String name = stored.name != null && !stored.name.isBlank() ? stored.name : shortForm;
        return new ResolvedOwner(name, OwnerState.OFFLINE);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd,
            @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("SimChestDebug/SimChestDebug.ui");

        World world = store.getExternalData().getWorld();
        List<HouseBlockPos> chests = getChests(world);

        int totalPages = (int) Math.ceil(chests.size() / (double) ROWS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= totalPages) selectedIndex = totalPages - 1;

        cmd.set("#Title.TextSpans", Message.translation("ui.debugchests.title"));
        cmd.set("#EmptyContainer.Visible", chests.isEmpty());
        cmd.set("#ListContainer.Visible", !chests.isEmpty());

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int chestIndex = selectedIndex * ROWS_PER_PAGE + i;
            String rowSelector = "#Row" + i;

            if (chestIndex >= chests.size()) {
                cmd.set(rowSelector + ".Visible", false);
                continue;
            }

            HouseBlockPos cp = chests.get(chestIndex);
            cmd.set(rowSelector + ".Visible", true);
            cmd.set(rowSelector + " #Coords.TextSpans", Message.translation("ui.debugchests.chestEntry")
                    .param("index", chestIndex + 1)
                    .param("x", cp.x)
                    .param("y", cp.y)
                    .param("z", cp.z));

            Message ownerMsg = describeOwner(cp);
            cmd.set(rowSelector + " #Owner.TextSpans", ownerMsg);
            cmd.set(rowSelector + " #Owner.Style.TextColor",
                    HouseManager.findHouseIdForChest(cp) != null ? "#ffaa55" : "#44ff88");

            Message contentsMsg = describeContents(world, cp);
            cmd.set(rowSelector + " #Contents.TextSpans", contentsMsg);
            /* Green when an NPC could actually eat from here, so the hunger routine can be judged
            at a glance instead of by opening every chest.
            */
            cmd.set(rowSelector + " #Contents.Style.TextColor", "#44ff88");


            // Hidden as well as unbound: a button that does nothing reads as broken.
            boolean canEdit = canEdit();
            cmd.set(rowSelector + " #BtnTp.Visible", canEdit);
            cmd.set(rowSelector + " #BtnForget.Visible", canEdit);

            if (canEdit) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnTp",
                        new EventData().append("action", "tp_" + chestIndex), false);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnForget",
                        new EventData().append("action", "forget_" + chestIndex), false);
            }
        }

        cmd.set("#PageIndex.TextSpans", Message.translation("ui.debugchests.pageIndex")
                .param("current", selectedIndex + 1)
                .param("total", totalPages)
                .param("count", chests.size()));


        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevPage",
                new EventData().append("action", "prev_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextPage",
                new EventData().append("action", "next_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBack",
                new EventData().append("action", "back"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store,
            @Nonnull String rawEventData) {
        String eventData = extractAction(rawEventData);

        World world = store.getExternalData().getWorld();
        List<HouseBlockPos> chests = getChests(world);

        if (eventData.contains("prev_page")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI();
            return;
        }
        if (eventData.contains("next_page")) {
            int totalPages = (int) Math.ceil(chests.size() / (double) ROWS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            selectedIndex = Math.min(totalPages - 1, selectedIndex + 1);
            refreshUI();
            return;
        }
        if (eventData.contains("back")) {
            // Only a screen the hub opened has anywhere to go back to.
            if (fromHub) {
                player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player));
            } else {
                player.getPageManager().setPage(storeRef, store, Page.None);
            }
            return;
        }

        /* The bindings are already withheld, but the client sends the action string, so the guard
        belongs here too rather than only on the button that produced it.
        */
        if ((eventData.contains("tp_") || eventData.contains("forget_")) && !canEdit()) {
            return;
        }

        if (eventData.contains("tp_")) {
            try {
                int index = Integer.parseInt(eventData.substring(eventData.indexOf("tp_") + 3));
                if (index >= 0 && index < chests.size()) {
                    HouseBlockPos cp = chests.get(index);
                    TransformComponent transform =
                            store.getComponent(storeRef, TransformComponent.getComponentType());
                    if (transform != null) {
                        world.execute(() -> {
                            Teleport tp = Teleport.createForPlayer(
                                    world,
                                    new Vector3d(cp.x + 0.5, cp.y + 1.2, cp.z + 0.5),
                                    transform.getRotation());
                            store.putComponent(storeRef, Teleport.getComponentType(), tp);
                            playerRefComp.sendMessage(Message.translation("ui.debugchests.msgTpSuccess").param("index", index + 1));
                        });
                    }
                }
            } catch (RuntimeException e) {
                HytaleLogger.forEnclosingClass().atWarning().withCause(e)
                        .log("Failed to process teleport event: " + eventData);
            }
            player.getPageManager().setPage(storeRef, store, Page.None);
        } else if (eventData.contains("forget_")) {
            try {
                int index = Integer.parseInt(eventData.substring(eventData.indexOf("forget_") + 7));
                if (index >= 0 && index < chests.size()) {
                    HouseBlockPos cp = chests.get(index);
                    ChestRegistry.removeAt(cp.x, cp.y, cp.z);
                    playerRefComp.sendMessage(Message.translation("ui.debugchests.msgForgetSuccess").param("index", index + 1));
                }
            } catch (RuntimeException e) {
                HytaleLogger.forEnclosingClass().atWarning().withCause(e)
                        .log("Failed to process forget event: " + eventData);
            }
            refreshUI();
        }
    }


    /** The client may wrap the action in JSON, so pull the value out when it does. */
    private static String extractAction(String rawEventData) {
        if (!rawEventData.startsWith("{")) return rawEventData;
        int idx = rawEventData.indexOf("\"action\":\"");
        if (idx == -1) return rawEventData;
        int start = idx + 10;
        int end = rawEventData.indexOf("\"", start);
        return end == -1 ? rawEventData : rawEventData.substring(start, end);
    }

    private void refreshUI() {
        player.getPageManager().clearCustomPageAcknowledgements();
        rebuild();
    }
}