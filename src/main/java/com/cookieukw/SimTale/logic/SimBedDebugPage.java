package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.BedRegistrySystem;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class SimBedDebugPage extends InteractiveCustomUIPage<String> {

    private final Player player;
    private final PlayerRef playerRefComp;
    private int selectedIndex;

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

    private List<BedPos> getBeds() {
        synchronized (BedRegistrySystem.BEDS) {
            return new ArrayList<>(BedRegistrySystem.BEDS);
        }
    }

    private BedPos getSelectedBed(List<BedPos> beds) {
        if (beds.isEmpty()) return null;
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= beds.size()) selectedIndex = beds.size() - 1;
        return beds.get(selectedIndex);
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> playerRef, UICommandBuilder cmd, @NonNullDecl UIEventBuilder eventBuilder, @NonNullDecl Store<EntityStore> store) {
        cmd.append("SimBedDebug/SimBedDebug.ui");

        List<BedPos> beds = getBeds();
        BedPos selectedBed = getSelectedBed(beds);

        if (selectedBed != null) {
            cmd.set("#BedCoords.Text", String.format("Posição: X: %d, Y: %d, Z: %d", selectedBed.x, selectedBed.y, selectedBed.z));
            cmd.set("#BedYaw.Text", String.format("Direção (Yaw): %.1f°", selectedBed.yaw));

            // Find owners
            List<String> owners = new ArrayList<>();
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.bedLocation != null && 
                    npc.bedLocation.x == selectedBed.x && 
                    npc.bedLocation.y == selectedBed.y && 
                    npc.bedLocation.z == selectedBed.z) {
                    owners.add(npc.name);
                }
            }

            if (owners.isEmpty()) {
                cmd.set("#BedStatus.Text", "Status: LIVRE");
                cmd.set("#BedStatus.Style.TextColor", "#44ff88");
            } else {
                cmd.set("#BedStatus.Text", "Dono(s): " + String.join(", ", owners));
                cmd.set("#BedStatus.Style.TextColor", "#ffaa55");
            }

            cmd.set("#BedIndex.Text", (selectedIndex + 1) + " / " + beds.size());
        } else {
            cmd.set("#BedCoords.Text", "Nenhuma cama cadastrada");
            cmd.set("#BedYaw.Text", "—");
            cmd.set("#BedStatus.Text", "Status: Vazio");
            cmd.set("#BedIndex.Text", "0 / 0");
        }

        // Register buttons
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnTeleport", new EventData().append("action", "teleport"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnUnclaim", new EventData().append("action", "unclaim"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnBack", new EventData().append("action", "back"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPrevBed", new EventData().append("action", "prev_bed"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnNextBed", new EventData().append("action", "next_bed"), false);
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> storeRef, @NonNullDecl Store<EntityStore> store, @NonNullDecl String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimBedDebug [EVENT]: " + eventData);

        List<BedPos> beds = getBeds();

        if (eventData.contains("prev_bed")) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("next_bed")) {
            selectedIndex = Math.min(beds.size() - 1, selectedIndex + 1);
            refreshUI(storeRef, store);
            return;
        }
        if (eventData.contains("back")) {
            player.getPageManager().openCustomPage(storeRef, store, new SimDebugPage(playerRefComp, player));
            return;
        }

        BedPos selectedBed = getSelectedBed(beds);
        if (selectedBed == null) {
            playerRefComp.sendMessage(Message.raw("[SimBedDebug] Cama indisponível."));
            return;
        }

        if (eventData.contains("teleport")) {
            TransformComponent transform = store.getComponent(storeRef, TransformComponent.getComponentType());
            if (transform != null) {
                // Teleport slightly above the bed to avoid getting stuck
                transform.teleportPosition(new Vector3d(selectedBed.x + 0.5, selectedBed.y + 1.2, selectedBed.z + 0.5));
                store.putComponent(storeRef, TransformComponent.getComponentType(), transform);
                playerRefComp.sendMessage(Message.raw("[SimBedDebug] Teletransportado para: X=" + selectedBed.x + " Y=" + selectedBed.y + " Z=" + selectedBed.z));
            }
            player.getPageManager().setPage(storeRef, store, Page.None);
        } else if (eventData.contains("unclaim")) {
            int count = 0;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.bedLocation != null && 
                    npc.bedLocation.x == selectedBed.x && 
                    npc.bedLocation.y == selectedBed.y && 
                    npc.bedLocation.z == selectedBed.z) {
                    
                    npc.bedLocation = null;
                    npc.family.hasSharedHome = false;
                    SimNPCPersistence.saveNPC(npc);
                    count++;
                }
            }
            playerRefComp.sendMessage(Message.raw("[SimBedDebug] " + count + " NPCs desvinculados desta cama!"));
            refreshUI(storeRef, store);
        }
    }

    private void refreshUI(Ref<EntityStore> storeRef, Store<EntityStore> store) {
        player.getPageManager().setPage(storeRef, store, Page.None);
        player.getPageManager().openCustomPage(storeRef, store, new SimBedDebugPage(playerRefComp, player, selectedIndex));
    }
}
