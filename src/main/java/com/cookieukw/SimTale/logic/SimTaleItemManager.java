package com.cookieukw.SimTale.logic;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class SimTaleItemManager {
    
    // Maps item ID suffixes (e.g., "TownBell") to their handler logic.
    private static final Map<String, BiConsumer<Player, PlayerRef>> itemHandlers = new HashMap<>();

    static {
        // Register your items here!
        register("TownBell", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔔 O sino da cidade soou!"));
        });
        
        register("InspectorsJournal", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📖 Você abriu o Diário do Inspetor!"));
        });
        
        register("InnkeepersLedger", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📒 Você abriu o Livro do Estalajadeiro!"));
        });
        
        register("ImmigrationContract", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📜 Você está lendo o Contrato de Imigração!"));
        });
        
        register("QuartermastersGlass", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔍 Você está olhando pela Lupa do Intendente!"));
        });
        
        register("BirthdayCake", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🎂 Que delícia! Bolo de aniversário!"));
        });
    }

    public static void register(String itemIdSuffix, BiConsumer<Player, PlayerRef> handler) {
        itemHandlers.put(itemIdSuffix, handler);
    }

    public static boolean handleItemUse(String itemId, Player player, PlayerRef playerRef) {
        if (itemId == null) return false;
        
        for (Map.Entry<String, BiConsumer<Player, PlayerRef>> entry : itemHandlers.entrySet()) {
            if (itemId.endsWith(entry.getKey())) {
                entry.getValue().accept(player, playerRef);
                return true;
            }
        }
        return false;
    }
}
