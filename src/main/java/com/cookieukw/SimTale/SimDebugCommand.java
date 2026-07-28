package com.cookieukw.SimTale;

import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.SimDebugPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Debug command to inspect and control NPC behaviors.
 * Usage: /simdebug
 */
public class SimDebugCommand extends AbstractPlayerCommand {

    public SimDebugCommand() {
        super("simdebug", "Abre o painel de debug do SimTale");
        this.setPermissionGroups("Adventure");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        // Auto-reassemble NPCs if needed
        if (SimTale.ACTIVE_NPCS.isEmpty()) {
            ctx.sendMessage(Message.raw("[SimDebug] Recarregando NPCs do banco..."));
            SimNPCPersistence.reassembleActiveNPCs(world);
        }

        if (SimTale.ACTIVE_NPCS.isEmpty()) {
            ctx.sendMessage(Message.raw("[SimDebug] Nenhum NPC ativo encontrado. Spawne um com /simtale spawn"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        // `assert` is stripped at runtime unless the JVM is started with -ea, so this was
        // effectively no check at all — a null here just became an NPE inside the command.
        if (player == null) {
            ctx.sendMessage(Message.raw("[SimDebug] Componente de jogador indisponivel."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new SimDebugPage(playerRef, player));
        ctx.sendMessage(Message.raw("[SimDebug] Painel aberto! (" + SimTale.ACTIVE_NPCS.size() + " NPCs ativos)"));
    }
}
