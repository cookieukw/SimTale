package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> subCommandArg;
    private final RequiredArg<String> npcTypeArg;

    public SimTaleCommand() {
        super("simtale", "SimTale plugin commands");
        this.setPermissionGroups("Adventure");
        this.subCommandArg = this.withRequiredArg("subcommand", "spawn", ArgTypes.STRING);
        this.npcTypeArg = this.withRequiredArg("type", "SLOTHIAN|TRORK|HUMAN_MALE|HUMAN_FEMALE", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String sub = ctx.get(this.subCommandArg);
        String typeName = ctx.get(this.npcTypeArg);

        if ("spawn".equalsIgnoreCase(sub)) {
            handleSpawn(ctx, store, ref, typeName);
            return;
        } else if ("interact".equalsIgnoreCase(sub)) {
            handleInteract(ctx, store, ref, playerRef);
            return;
        }

        sendUsage(ctx);
    }

    private void handleInteract(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        // If no NPCs tracked (e.g., after world reload), try to reassemble from database
        if (SimTale.ACTIVE_NPCS.isEmpty()) {
            ctx.sendMessage(Message.translation("simtale.cmd.reload.db"));
            World world = null;
            for (World w : Universe.get().getWorlds().values()) {
                world = w;
                break;
            }
            SimNPCPersistence.reassembleActiveNPCs(world);
        }
        
        TransformComponent playerTransform = 
            store.getComponent(ref, TransformComponent.getComponentType());

        SimNPCComponent nearestNPC = null;
        double minDistance = Double.MAX_VALUE;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef != null) {
                TransformComponent npcTransform = 
                    store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                
                if (playerTransform != null && npcTransform != null) {
                    Vector3d pPos = playerTransform.getPosition();
                    Vector3d nPos = npcTransform.getPosition();
                    
                    double distSq = pPos.distanceSquared(nPos);
                                    
                    if (distSq < minDistance) {
                        minDistance = distSq;
                        nearestNPC = npc;
                    }
                }
            }
        }

        if (nearestNPC == null) {
            ctx.sendMessage(Message.translation("simtale.cmd.interact.none"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        assert player != null;
        player.getPageManager().openCustomPage(ref, store, new NPCInteractionPage(playerRef, player, nearestNPC));
        ctx.sendMessage(Message.translation("simtale.cmd.interact.success").param("name", nearestNPC.name));
    }

    /**
     * Handles the /simtale interact command.
     */
    private void handleSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, String typeName) {
        SimNPCFactory.NPCType type;
        try {
            type = SimNPCFactory.NPCType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.translation("simtale.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE"));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        assert transform != null;
        Vector3d pos = transform.getPosition().add(2, 0, 2);

        Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
        SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

        // Save initial state to DB
        assert comp != null;
        SimNPCPersistence.saveNPC(comp);

        ctx.sendMessage(Message.translation("simtale.cmd.spawn.success").param("type", type.name()));
    }

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.translation("simtale.cmd.spawn.usage"));
    }
}
