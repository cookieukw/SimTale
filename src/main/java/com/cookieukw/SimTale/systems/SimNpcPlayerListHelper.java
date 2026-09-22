package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Synchronizes SimTale NPCs to the client player list (tab list and player registry),
 * following the pattern used by GlymeraFakePlayers.
 */
public final class SimNpcPlayerListHelper {

    private static final SimLog LOGGER = SimLog.forClass(SimNpcPlayerListHelper.class);

    private SimNpcPlayerListHelper() {}

    /**
     * Sends the complete list of active NPCs to a player on join.
     */
    public static void sendAllToPlayer(PlayerRef playerRef, World world) {
        if (playerRef == null) return;
        UUID worldUuid = world != null ? world.getWorldConfig().getUuid() : null;

        List<ServerPlayerListPlayer> list = new ArrayList<>();
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId == null || npc.name == null) continue;
            ServerPlayerListPlayer p = new ServerPlayerListPlayer();
            p.uuid = npc.entityId;
            p.username = npc.name;
            p.worldUuid = worldUuid;
            p.ping = 0;
            list.add(p);
        }

        if (!list.isEmpty()) {
            AddToServerPlayerList packet = new AddToServerPlayerList(list.toArray(new ServerPlayerListPlayer[0]));
            try {
                playerRef.getPacketHandler().writeNoCache(packet);
            } catch (Exception e) {
                LOGGER.debug("[SimTale] Falha ao sincronizar lista de NPCs para player {}: {}", playerRef.getUuid(), e.getMessage());
            }
        }
    }

    /**
     * Broadcasts a newly tracked NPC to all connected players.
     */
    public static void broadcastAdd(SimNPCComponent npc) {
        if (npc == null || npc.entityId == null || npc.name == null) return;
        World world = WorldUtil.first();
        UUID worldUuid = world != null ? world.getWorldConfig().getUuid() : null;

        ServerPlayerListPlayer p = new ServerPlayerListPlayer();
        p.uuid = npc.entityId;
        p.username = npc.name;
        p.worldUuid = worldUuid;
        p.ping = 0;

        AddToServerPlayerList packet = new AddToServerPlayerList(new ServerPlayerListPlayer[]{ p });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null) {
                try {
                    playerRef.getPacketHandler().writeNoCache(packet);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Broadcasts removal of an untracked NPC to all connected players.
     */
    public static void broadcastRemove(UUID entityId) {
        if (entityId == null) return;
        RemoveFromServerPlayerList packet = new RemoveFromServerPlayerList(new UUID[]{ entityId });
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null) {
                try {
                    playerRef.getPacketHandler().writeNoCache(packet);
                } catch (Exception ignored) {}
            }
        }
    }
}
