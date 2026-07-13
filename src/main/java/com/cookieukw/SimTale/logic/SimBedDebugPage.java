package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.BedRegistry;
import com.cookieukw.SimTale.systems.BedWorldBootstrap;
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

@SuppressWarnings("null")
public class SimBedDebugPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex; // Represents current Page Index

    public SimBedDebugPage(@Nonnull PlayerRef playerRefComp, Player player) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = 0;
    }

    public SimBedDebugPage(@Nonnull PlayerRef playerRefComp, Player player, int initialIndex) {
        super(playerRefComp, CustomPageLifetime.CanDismiss, BuilderCodec.builder(String.class, String::new).build());
        this.player = player;
        this.playerRefComp = playerRefComp;
        this.selectedIndex = initialIndex;
    }

    private List<BedPos> getBeds(World world) {
        synchronized (BedRegistry.BEDS) {
            if (world != null) {
                // Self-healing: prune bed ONLY if chunk is loaded AND block is no longer a bed block
                BedRegistry.BEDS.removeIf(bp -> {
                    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bp.x, bp.z));
                    if (chunk != null) {
                        BlockType type = world.getBlockType(bp.x, bp.y, bp.z);
                        return type == null || type.getId() == null || !BedRegistry.isBedId(type.getId());
                    }
                    return false; // Keep bed if chunk is unloaded
                });
            }
            List<BedPos> list = new ArrayList<>();
            for (BedPos bp : BedRegistry.BEDS) {
                if (world != null) {
                    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bp.x, bp.z));
                    // Display-only deduplication: skip the foot/secondary part of the bed
                    if (chunk != null && BedWorldBootstrap.isPrimaryBedBlock(world, bp.x, bp.y, bp.z)) {
                        continue;
                    }
                }
                list.add(bp);
            }
            list.sort((b1, b2) -> {
                if (b1.x != b2.x) return Integer.compare(b1.x, b2.x);
                if (b1.y != b2.y) return Integer.compare(b1.y, b2.y);
                return Integer.compare(b1.z, b2.z);
            });
            return list;
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, UICommandBuilder cmd, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        cmd.append("SimBedDebug/SimBedDebug.ui");

        World world = store.getExternalData().getWorld();
        List<BedPos> beds = getBeds(world);
        int totalPages = (int) Math.ceil(beds.size() / 5.0);
        if (totalPages == 0) totalPages = 1;

        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= totalPages) selectedIndex = totalPages - 1;

        // Render 5 items for the current page
        for (int i = 0; i < 5; i++) {
            int bedIndex = selectedIndex * 5 + i;
            String rowSelector = "#Row" + i;

            if (bedIndex < beds.size()) {
                BedPos bp = beds.get(bedIndex);
                cmd.set(rowSelector + ".Visible", true);
                cmd.set(rowSelector + " #Coords.Text", String.format("Cama %d — X: %d, Y: %d, Z: %d", bedIndex + 1, bp.x, bp.y, bp.z));

                // Find owners (accept distance <= 1 block to handle offsets/deduplications)
                List<String> owners = new ArrayList<>();
                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.bedLocation != null && 
                        Math.abs(npc.bedLocation.x - bp.x) <= 1 && 
                        Math.abs(npc.bedLocation.y - bp.y) <= 1 && 
                        Math.abs(npc.bedLocation.z - bp.z) <= 1) {
                        owners.add(npc.name);
                    }
                }

                if (owners.isEmpty()) {
                    cmd.set(rowSelector + " #Status.Text", "Status: LIVRE");
                    cmd.set(rowSelector + " #Status.Style.TextColor", "#44ff88");
                } else {
                    cmd.set(rowSelector + " #Status.Text", "Dono(s): " + String.join(", ", owners));
                    cmd.set(rowSelector + " #Status.Style.TextColor", "#ffaa55");
                }

                // Bind buttons uniquely for this row's bed index
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnTp", new EventData().append("action", "tp_" + bedIndex), false);
                eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, rowSelector + " #BtnUnclaim", new EventData().append("action", "unclaim_" + bedIndex), false);
            } else {
                // Hide unused rows
                cmd.set(rowSelector + ".Visible", false);
            }
        }

        cmd.set("#PageIndex.Text", "Página " + (selectedIndex + 1) + " / " + totalPages);

        // Register navigation buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevPage", new EventData().append("action", "prev_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextPage", new EventData().append("action", "next_page"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBack", new EventData().append("action", "back"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String rawEventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimBedDebug [EVENT]: " + rawEventData);

        // Safely extract action string if Hytale client wrapped the event inside a JSON string
        String eventData = rawEventData;
        if (rawEventData.startsWith("{")) {
            int idx = rawEventData.indexOf("\"action\":\"");
            if (idx != -1) {
                int start = idx + 10;
                int end = rawEventData.indexOf("\"", start);
                if (end != -1) {
                    eventData = rawEventData.substring(start, end);
                }
            }
        }

        World world = store.getExternalData().getWorld();
        List<BedPos> beds = getBeds(world);

        if (eventData.contains("prev_page")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("next_page")) {
            int totalPages = (int) Math.ceil(beds.size() / 5.0);
            if (totalPages == 0) totalPages = 1;
            selectedIndex = Math.min(totalPages - 1, selectedIndex + 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("back")) {
            player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player));
            return;
        }

        // Handle Row-specific actions
        if (eventData.contains("tp_")) {
            try {
                int bedIndex = Integer.parseInt(eventData.substring(eventData.indexOf("tp_") + 3));
                if (bedIndex >= 0 && bedIndex < beds.size()) {
                    BedPos bp = beds.get(bedIndex);
                    TransformComponent transform = store.getComponent(storeRef, TransformComponent.getComponentType());
                    if (transform != null) {

                        world.execute(() -> {
                            Teleport tp = Teleport.createForPlayer(
                                world,
                                new Vector3d(bp.x + 0.5, bp.y + 1.2, bp.z + 0.5),
                                transform.getRotation()
                            );
                            store.putComponent(storeRef, Teleport.getComponentType(), tp);
                            playerRefComp.sendMessage(Message.raw("[SimBedDebug] Teletransportado para a Cama #" + (bedIndex + 1)));
                        });
                    }
                }
            } catch (Exception e) {
                HytaleLogger.forEnclosingClass().atWarning().withCause(e).log("Failed to process teleport event: " + eventData);
            }
            player.getPageManager().setPage(storeRef, store, Page.None);
        } else if (eventData.contains("unclaim_")) {
            try {
                int bedIndex = Integer.parseInt(eventData.substring(eventData.indexOf("unclaim_") + 8));
                if (bedIndex >= 0 && bedIndex < beds.size()) {
                    BedPos bp = beds.get(bedIndex);
                    int count = 0;
                    for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                        if (npc.bedLocation != null && 
                            npc.bedLocation.x == bp.x && 
                            npc.bedLocation.y == bp.y && 
                            npc.bedLocation.z == bp.z) {
                            
                            npc.bedLocation = null;
                            npc.family.hasSharedHome = false;
                            SimNPCPersistence.saveNPC(npc);
                            count++;
                        }
                    }
                    playerRefComp.sendMessage(Message.raw("[SimBedDebug] " + count + " NPCs desvinculados da Cama #" + (bedIndex + 1) + "!"));
                }
            } catch (Exception e) {
                HytaleLogger.forEnclosingClass().atWarning().withCause(e).log("Failed to process unclaim event: " + eventData);
            }
            refreshUI(storeRef, store);
        }
    }

    private void refreshUI(Ref<EntityStore> storeRef, Store<EntityStore> store) {
        player.getPageManager().setPage(storeRef, store, Page.None);
        player.getPageManager().openCustomPage(storeRef, store, new SimBedDebugPage(playerRefComp, player, selectedIndex));
    }
}
