package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends AbstractPlayerCommand {

    private final String pluginVersion;

    public SimTaleCommand(String pluginName, String pluginVersion) {
        super("simtale");
        this.setPermissionGroup(GameMode.Adventure);
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String input = ctx.getInputString();
        if (input == null) {
            sendUsage(ctx);
            return;
        }

        String[] args = input.split(" ");
        if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
            handleSpawn(ctx, store, ref, args[1]);
            return;
        }

        sendUsage(ctx);
    }

    private void handleSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, String typeName) {
        SimNPCFactory.NPCType type;
        try {
            type = SimNPCFactory.NPCType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("Invalid NPC type. Use SLOTHIAN or TRORK."));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Vector3d pos = transform.getPosition().add(2, 0, 2);

        Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
        SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

        // Save initial state to DB
        SimNPCPersistence.saveNPC(comp);

        ctx.sendMessage(Message.raw("Spawned " + type.name() + " at " + pos.toString()));
    }

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("SimTale v" + pluginVersion + " - Use /simtale spawn <SLOTHIAN|TRORK>"));
    }
}
