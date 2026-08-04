package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.SimNPCComponent;
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

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        this(playerRefComp, player, 0);
    }

    public SimChestDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = initialIndex;
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
                    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(cp.x, cp.z));
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
        if (block == null) return Message.translation("ui.debugchests.contents_unloaded");

        ItemContainer container = block.getItemContainer();
        int items = 0;
        int food = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            items++;
            if (NPCFoodHelper.isFood(stack)) food++;
        }

        if (items == 0) return Message.translation("ui.debugchests.contents_empty");
        Message itemWord = Message.translation(items == 1 ? "ui.debugchests.item_singular" : "ui.debugchests.item_plural");
        String itemStr = items + " " + itemWord.getAnsiMessage(); // string helper for formatting
        return Message.translation("ui.debugchests.contents_summary")
                .param("items", items + " " + (items == 1 ? "item" : "itens"))
                .param("food", food);
    }

    /** Resolves the house link, naming the NPC owners when they are loaded. */
    private Message describeOwner(HouseBlockPos pos) {
        // Chests outside a house are unusable by design — that is what keeps NPCs out of the
        // world generator's loot chests — so say so instead of calling them "public".
        UUID houseId = HouseManager.BLOCK_TO_HOUSE_ID.get(pos);
        if (houseId == null) return Message.translation("ui.debugchests.owner_no_house");

        HouseData house = HouseManager.HOUSES_BY_ID.get(houseId);
        if (house == null) return Message.translation("ui.debugchests.owner_no_data").param("id", shortId(houseId));
        if (house.owners.isEmpty()) return Message.translation("ui.debugchests.owner_no_owners").param("id", shortId(houseId));

        List<String> names = new ArrayList<>();
        for (String owner : house.owners) {
            names.add(resolveName(owner));
        }
        return Message.translation("ui.debugchests.owner_names").param("owners", String.join(", ", names));
    }


    /** Owners are stored as UUID strings; show the NPC name when one matches. */
    private String resolveName(String ownerUuid) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId != null && npc.entityId.toString().equals(ownerUuid)) {
                return npc.name;
            }
        }
        return ownerUuid.length() >= 8 ? ownerUuid.substring(0, 8) : ownerUuid;
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

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int chestIndex = selectedIndex * ROWS_PER_PAGE + i;
            String rowSelector = "#Row" + i;

            if (chestIndex >= chests.size()) {
                cmd.set(rowSelector + ".Visible", false);
                continue;
            }

            HouseBlockPos cp = chests.get(chestIndex);
            cmd.set(rowSelector + ".Visible", true);
            cmd.set(rowSelector + " #Coords.TextSpans", Message.translation("ui.debugchests.chest_entry")
                    .param("index", chestIndex + 1)
                    .param("x", cp.x)
                    .param("y", cp.y)
                    .param("z", cp.z));

            Message ownerMsg = describeOwner(cp);
            cmd.set(rowSelector + " #Owner.TextSpans", ownerMsg);
            cmd.set(rowSelector + " #Owner.Style.TextColor",
                    HouseManager.BLOCK_TO_HOUSE_ID.get(cp) != null ? "#ffaa55" : "#44ff88");

            Message contentsMsg = describeContents(world, cp);
            cmd.set(rowSelector + " #Contents.TextSpans", contentsMsg);
            // Green when an NPC could actually eat from here, so the hunger routine can be judged
            // at a glance instead of by opening every chest.
            cmd.set(rowSelector + " #Contents.Style.TextColor", "#44ff88");

            cmd.set(rowSelector + " #BtnTp Label.TextSpans", Message.translation("ui.debugchests.btn_tp"));
            cmd.set(rowSelector + " #BtnForget Label.TextSpans", Message.translation("ui.debugchests.btn_forget"));

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnTp",
                    new EventData().append("action", "tp_" + chestIndex), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnForget",
                    new EventData().append("action", "forget_" + chestIndex), false);
        }

        cmd.set("#PageIndex.TextSpans", Message.translation("ui.debugchests.page_index")
                .param("current", selectedIndex + 1)
                .param("total", totalPages)
                .param("count", chests.size()));
        cmd.set("#BtnPrevPage Label.TextSpans", Message.translation("ui.debugchests.btn_prev"));
        cmd.set("#BtnNextPage Label.TextSpans", Message.translation("ui.debugchests.btn_next"));
        cmd.set("#BtnBack Label.TextSpans", Message.translation("ui.debugchests.btn_back"));


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
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("next_page")) {
            int totalPages = (int) Math.ceil(chests.size() / (double) ROWS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            selectedIndex = Math.min(totalPages - 1, selectedIndex + 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("back")) {
            player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player));
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
                            playerRefComp.sendMessage(Message.translation("ui.debugchests.msg_tp_success").param("index", index + 1));
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
                    playerRefComp.sendMessage(Message.translation("ui.debugchests.msg_forget_success").param("index", index + 1));
                }
            } catch (RuntimeException e) {
                HytaleLogger.forEnclosingClass().atWarning().withCause(e)
                        .log("Failed to process forget event: " + eventData);
            }
            refreshUI(storeRef, store);
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

    private void refreshUI(Ref<EntityStore> storeRef, Store<EntityStore> store) {
        player.getPageManager().setPage(storeRef, store, Page.None);
        player.getPageManager().openCustomPage(storeRef, store,
                new SimChestDebugPage(playerRefComp, player, selectedIndex));
    }
}
