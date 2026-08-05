package com.cookieukw.SimTale.logic;

import com.cookie.runecore.api.RuneCoreItemManager;
import com.hypixel.hytale.server.core.Message;

public class SimTaleItemRegistry {

    public static void init() {
        RuneCoreItemManager.register("TownBell", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔔 O sino da cidade soou!"));
        });
        
        RuneCoreItemManager.register("InspectorsJournal", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📖 Você abriu o Diário do Inspetor!"));
        });
        
        RuneCoreItemManager.register("InnkeepersLedger", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📒 Você abriu o Livro do Estalajadeiro!"));
        });
        
        RuneCoreItemManager.register("ImmigrationContract", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("📜 Você está lendo o Contrato de Imigração!"));
        });
        
        RuneCoreItemManager.register("QuartermastersGlass", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🔍 Você está olhando pela Lupa do Intendente!"));
        });
        
        RuneCoreItemManager.register("BirthdayCake", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("🎂 Que delícia! Bolo de aniversário!"));
        });
        
        RuneCoreItemManager.register("WeddingRing", (player, playerRef) -> {
            playerRef.sendMessage(Message.raw("💍 Você está segurando uma aliança de casamento!"));
        });
    }
}
