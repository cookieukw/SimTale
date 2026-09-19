package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.lifecycle.SimNPCRevival;
import com.cookieukw.SimTale.db.SimNPCData;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Lists everyone the Grim Reaper has collected, and revives them.
 *
 * <p>Ids are composed the same way SimBedDebugPage does it — "#Row0 #Name", "#Row2 #BtnRevive" —
 * so the row ids in SimGraveyard.ui are part of the contract.
 */
@SuppressWarnings("null")
public class SimGraveyardPage extends InteractiveCustomUIPage<String> {

    private static final SimLog LOGGER = SimLog.forClass(SimGraveyardPage.class);
    private static final int ROWS_PER_PAGE = 5;

    private final Player player;
    private final PlayerRef playerRefComp;
    private int pageIndex;

    public SimGraveyardPage(@Nonnull PlayerRef playerRefComp, Player player) {
        this(playerRefComp, player, 0);
    }

    public SimGraveyardPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.pageIndex = initialIndex;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("SimGraveyard/SimGraveyard.ui");

        List<SimNPCData> buried = SimNPCRevival.listBuried();
        int totalPages = (int) Math.ceil(buried.size() / (double) ROWS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= totalPages) pageIndex = totalPages - 1;

        cmd.set("#EmptyNotice.Visible", buried.isEmpty());
        cmd.set("#EmptyContainer.Visible", buried.isEmpty());
        cmd.set("#ListContainer.Visible", !buried.isEmpty());

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int recordIndex = pageIndex * ROWS_PER_PAGE + i;
            String row = "#Row" + i;

            if (recordIndex >= buried.size()) {
                cmd.set(row + ".Visible", false);
                continue;
            }

            SimNPCData data = buried.get(recordIndex);
            cmd.set(row + ".Visible", true);
            cmd.set(row + " #Name.TextSpans", Message.translation("ui.graveyard.entry")
                    .param("index", recordIndex + 1)
                    .param("name", data.name != null ? data.name : "?"));
            cmd.set(row + " #Detail.TextSpans", buildDetail(data));

            /* Keyed by UUID rather than by list position: reviving reorders the list, and a stale
            index would revive whoever slid into that slot.
            */
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, row + " #BtnRevive",
                    new EventData().append("action", "revive_" + data.id), false);
        }

        cmd.set("#PageIndex.TextSpans", Message.translation("ui.graveyard.pageIndex")
                .param("current", pageIndex + 1)
                .param("total", totalPages)
                .param("count", buried.size()));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevPage",
                new EventData().append("action", "prev_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextPage",
                new EventData().append("action", "next_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBack",
                new EventData().append("action", "back"), false);
    }

    /** Profession plus family size — enough to tell two NPCs with the same name apart. */
    private Message buildDetail(SimNPCData data) {
        Message profession = data.profession != null
                ? Message.translation("ui.prof." + data.profession.name().toLowerCase())
                : Message.translation("ui.prof.unemployed");
        int children = data.family != null && data.family.children != null ? data.family.children.size() : 0;
        /* Composed rather than one string with a {profession} slot: the profession is itself a
        translation, and the separator lives in raw() so neither .lang value has to carry
        leading or trailing whitespace that a parser is free to trim.
        */
        return profession.insert(Message.raw(" · "))
                .insert(Message.translation("ui.graveyard.detail").param("children", children));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store,
                                @Nonnull String rawEventData) {
        String eventData = unwrapAction(rawEventData);

        if (eventData.contains("prev_page")) {
            pageIndex = Math.max(0, pageIndex - 1);
            refreshUI();
            return;
        }
        if (eventData.contains("next_page")) {
            int total = (int) Math.ceil(SimNPCRevival.listBuried().size() / (double) ROWS_PER_PAGE);
            if (total == 0) total = 1;
            pageIndex = Math.min(total - 1, pageIndex + 1);
            refreshUI();
            return;
        }
        if (eventData.contains("back")) {
            /* Closes rather than opening the control panel: nothing opens the graveyard from the
            hub, so "back" there was a one-way door into the developer screen.
            */
            player.getPageManager().setPage(storeRef, store, Page.None);
            return;
        }
        if (eventData.contains("revive_")) {
            handleRevive(storeRef, store, eventData.substring(eventData.indexOf("revive_") + 7));
        }
    }

    private void handleRevive(Ref<EntityStore> storeRef, Store<EntityStore> store, String rawId) {
        UUID graveId;
        try {
            graveId = UUID.fromString(rawId.trim());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[SimTale] Graveyard revive got a malformed id: {}", rawId);
            playerRefComp.sendMessage(Message.translation("ui.graveyard.msgFailed"));
            return;
        }

        World world = store.getExternalData().getWorld();
        TransformComponent transform = store.getComponent(storeRef, TransformComponent.getComponentType());
        if (transform == null) {
            playerRefComp.sendMessage(Message.translation("ui.graveyard.msgFailed"));
            return;
        }

        /* Copy before offsetting: joml's add mutates the receiver, and getPosition() hands back the
        component's live vector — offsetting it in place would teleport the player instead.
        */
        Vector3d spawnAt = new Vector3d(transform.getPosition()).add(1.0, 0.0, 1.0);

        /* Spawning is a structural write and this runs from inside the store's own processing, so
        it has to be deferred the same way the Reaper spawn is.
        */
        world.execute(() -> {
            SimNPCRevival.Result result = SimNPCRevival.revive(store, world, spawnAt, graveId);
            if (!result.ok()) {
                playerRefComp.sendMessage(Message.translation("ui.graveyard.msgFailed"));
                return;
            }
            playerRefComp.sendMessage(Message.translation(
                            result.reclaimedBed() ? "ui.graveyard.msgRevivedWithBed" : "ui.graveyard.msgRevived")
                    .param("name", result.npc().name));

            /* Redrawn in here, not after scheduling: the revival is deferred, so refreshing
            straight away rebuilds the list from a graveyard that still holds the record and
            the row the player just clicked stays on screen as if nothing happened.
            */
            refreshUI();
        });
    }

    /** The client sometimes delivers the payload as JSON rather than the bare action string. */
    private static String unwrapAction(String raw) {
        if (!raw.startsWith("{")) return raw;
        int idx = raw.indexOf("\"action\":\"");
        if (idx == -1) return raw;
        int start = idx + 10;
        int end = raw.indexOf("\"", start);
        return end == -1 ? raw : raw.substring(start, end);
    }

    private void refreshUI() {
        player.getPageManager().clearCustomPageAcknowledgements();
        rebuild();
    }
}
