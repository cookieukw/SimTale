package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.DebugAccess;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.systems.BathRegistry;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Registered-bath browser, the counterpart of {@link SimBedDebugPage} and {@link SimChestDebugPage}.
 *
 * <p>Unlike a bed's claim (a persistent {@code SimNPCComponent.bedLocation}), "in use" here is
 * whatever {@link RoutineAIComponent} state happens to be true this tick -- an NPC in
 * {@code MOVING_TO_BATH} or {@code BATHING} with a matching {@code targetBlockPosition}. There is
 * nothing to unclaim, so the destructive action is Forget (drop the tile from the registry),
 * matching {@link SimChestDebugPage} rather than the bed page's Unclaim.
 */
@SuppressWarnings("null")
public class SimBathDebugPage extends InteractiveCustomUIPage<String> {

    private static final SimLog LOGGER = SimLog.forClass(SimBathDebugPage.class);
    private static final int ROWS_PER_PAGE = 5;

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex;

    /** Read-only mode: hides Teleport and Forget. See SimBedDebugPage for why Teleport especially matters here. */
    private final boolean readOnly;

    /** Whether the control panel opened this screen, which is the only case where Back leads back. */
    private final boolean fromHub;

    public SimBathDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        this(playerRefComp, player, 0, false, false);
    }

    public SimBathDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        this(playerRefComp, player, initialIndex, false, false);
    }

    public SimBathDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex, boolean readOnly) {
        this(playerRefComp, player, initialIndex, readOnly, false);
    }

    public SimBathDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex,
                            boolean readOnly, boolean fromHub) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = initialIndex;
        this.readOnly = readOnly;
        this.fromHub = fromHub;
    }

    /** Teleport and Forget need both an editing entry point and creative mode. */
    private boolean canEdit() {
        return !readOnly && DebugAccess.canEdit(player);
    }

    private List<HouseBlockPos> getBaths(World world) {
        synchronized (BathRegistry.BATHS) {
            int before = BathRegistry.BATHS.size();
            if (world != null) {
                /* Self-healing, same rule as the bed/chest pages: only prune a tile whose chunk is
                actually loaded and confirmed to no longer be a bath block. An unloaded chunk
                cannot be checked, so it is assumed to still be there.
                */
                BathRegistry.BATHS.removeIf(bp -> {
                    WorldChunk chunk = world.getChunkStore().getChunkComponent(ChunkUtil.indexChunkFromBlock(bp.x, bp.z), WorldChunk.getComponentType());
                    if (chunk != null) {
                        BlockType type = world.getBlockType(bp.x, bp.y, bp.z);
                        return type == null || type.getId() == null || !BathRegistry.isBathId(type.getId());
                    }
                    return false;
                });
            }
            int pruned = before - BathRegistry.BATHS.size();
            if (pruned > 0) {
                LOGGER.info("[SimTale] Bath page pruned {} stale bath(s), {} left", pruned, BathRegistry.BATHS.size());
            }
            List<HouseBlockPos> list = new ArrayList<>(BathRegistry.BATHS);
            list.sort((a, b) -> {
                if (a.x != b.x) return Integer.compare(a.x, b.x);
                if (a.y != b.y) return Integer.compare(a.y, b.y);
                return Integer.compare(a.z, b.z);
            });
            return list;
        }
    }

    /** Name of whoever is currently moving to or already in this bath tile, or null when free. */
    private String findBather(HouseBlockPos pos) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef == null || !npc.entityRef.isValid()) continue;
            RoutineAIComponent ai = npc.entityRef.getStore().getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (ai == null || ai.targetBlockPosition == null) continue;
            boolean atThisTile = ai.targetBlockPosition.x == pos.x
                    && ai.targetBlockPosition.y == pos.y
                    && ai.targetBlockPosition.z == pos.z;
            if (!atThisTile) continue;
            if (ai.currentTask == RoutineAIComponent.TaskType.MOVING_TO_BATH
                    || ai.currentTask == RoutineAIComponent.TaskType.BATHING) {
                return npc.name;
            }
        }
        return null;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("SimBathDebug/SimBathDebug.ui");

        World world = store.getExternalData().getWorld();
        List<HouseBlockPos> baths = getBaths(world);
        int totalPages = (int) Math.ceil(baths.size() / (double) ROWS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= totalPages) selectedIndex = totalPages - 1;

        cmd.set("#Title.TextSpans", Message.translation("ui.debugbaths.title"));
        cmd.set("#EmptyContainer.Visible", baths.isEmpty());
        cmd.set("#ListContainer.Visible", !baths.isEmpty());

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int bathIndex = selectedIndex * ROWS_PER_PAGE + i;
            String rowSelector = "#Row" + i;

            if (bathIndex >= baths.size()) {
                cmd.set(rowSelector + ".Visible", false);
                continue;
            }

            HouseBlockPos bp = baths.get(bathIndex);
            cmd.set(rowSelector + ".Visible", true);
            cmd.set(rowSelector + " #Coords.TextSpans", Message.translation("ui.debugbaths.bathEntry")
                    .param("index", bathIndex + 1)
                    .param("x", bp.x)
                    .param("y", bp.y)
                    .param("z", bp.z));

            String bather = findBather(bp);
            if (bather == null) {
                cmd.set(rowSelector + " #Status.TextSpans", Message.translation("ui.debugbaths.statusFree"));
                cmd.set(rowSelector + " #Status.Style.TextColor", "#44ff88");
            } else {
                cmd.set(rowSelector + " #Status.TextSpans", Message.translation("ui.debugbaths.statusInUse")
                        .param("name", bather));
                cmd.set(rowSelector + " #Status.Style.TextColor", "#ffaa55");
            }

            boolean canEdit = canEdit();
            cmd.set(rowSelector + " #BtnTp.Visible", canEdit);
            cmd.set(rowSelector + " #BtnForget.Visible", canEdit);

            if (canEdit) {
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnTp",
                        new EventData().append("action", "tp_" + bathIndex), false);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnForget",
                        new EventData().append("action", "forget_" + bathIndex), false);
            }
        }

        cmd.set("#PageIndex.TextSpans", Message.translation("ui.debugbaths.pageIndex")
                .param("current", selectedIndex + 1)
                .param("total", totalPages)
                .param("count", baths.size()));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevPage", new EventData().append("action", "prev_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextPage", new EventData().append("action", "next_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBack", new EventData().append("action", "back"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String rawEventData) {
        String eventData = extractAction(rawEventData);

        World world = store.getExternalData().getWorld();
        List<HouseBlockPos> baths = getBaths(world);

        if (eventData.contains("prev_page")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("next_page")) {
            int totalPages = (int) Math.ceil(baths.size() / (double) ROWS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            selectedIndex = Math.min(totalPages - 1, selectedIndex + 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("back")) {
            if (fromHub) {
                player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player));
            } else {
                player.getPageManager().setPage(storeRef, store, Page.None);
            }
            return;
        }

        if ((eventData.contains("tp_") || eventData.contains("forget_")) && !canEdit()) {
            return;
        }

        if (eventData.contains("tp_")) {
            try {
                int index = Integer.parseInt(eventData.substring(eventData.indexOf("tp_") + 3));
                if (index >= 0 && index < baths.size()) {
                    HouseBlockPos bp = baths.get(index);
                    TransformComponent transform = store.getComponent(storeRef, TransformComponent.getComponentType());
                    if (transform != null) {
                        world.execute(() -> {
                            Teleport tp = Teleport.createForPlayer(
                                    world,
                                    new Vector3d(bp.x + 0.5, bp.y + 1.2, bp.z + 0.5),
                                    transform.getRotation());
                            store.putComponent(storeRef, Teleport.getComponentType(), tp);
                            playerRefComp.sendMessage(Message.translation("ui.debugbaths.msgTpSuccess").param("index", index + 1));
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
                if (index >= 0 && index < baths.size()) {
                    HouseBlockPos bp = baths.get(index);
                    BathRegistry.removeAt(bp.x, bp.y, bp.z);
                    playerRefComp.sendMessage(Message.translation("ui.debugbaths.msgForgetSuccess").param("index", index + 1));
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
                new SimBathDebugPage(playerRefComp, player, selectedIndex, readOnly, fromHub));
    }
}
