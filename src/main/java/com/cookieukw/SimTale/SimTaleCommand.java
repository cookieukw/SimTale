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
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.component.RemoveReason;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.ArrayList;
import com.cookieukw.SimTale.systems.PlumbobSystem;
import com.cookie.caskara.Caskara;
/**
 * Commands for the SimTale plugin.
 */
public class SimTaleCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> subCommandArg;
    private final OptionalArg<String> npcTypeArg;

    public SimTaleCommand() {
        super("simtale", "SimTale plugin commands");
        this.setPermissionGroups("Adventure");
        this.subCommandArg = this.withRequiredArg("subcommand", "spawn", ArgTypes.STRING);
        this.npcTypeArg = this.withOptionalArg("type", "SLOTHIAN|TRORK|HUMAN_MALE|HUMAN_FEMALE", ArgTypes.STRING);
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
        } else if ("tpall".equalsIgnoreCase(sub)) {
            handleTpAll(ctx, store, ref);
            return;
        } else if ("clearall".equalsIgnoreCase(sub)) {
            handleClearAll(ctx, store);
            return;
        } else if ("forcespawn".equalsIgnoreCase(sub)) {
            handleForceSpawn(ctx, store, ref, typeName);
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
        if (typeName == null) {
            ctx.sendMessage(Message.raw("Por favor, especifique o tipo do NPC (spawn <tipo>). Ex: /simtale spawn human_male"));
            return;
        }
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

    private void handleTpAll(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent playerTransform = store.getComponent(ref, TransformComponent.getComponentType());
        if (playerTransform == null) return;
        Vector3d pPos = playerTransform.getPosition();
        int count = 0;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef != null && npc.entityRef.isValid()) {
                TransformComponent npcTransform = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (npcTransform != null) {
                    npcTransform.setPosition(new Vector3d(pPos.x + (Math.random() - 0.5) * 4, pPos.y, pPos.z + (Math.random() - 0.5) * 4));
                    store.putComponent(npc.entityRef, TransformComponent.getComponentType(), npcTransform);
                    count++;
                }
            }
        }
        ctx.sendMessage(Message.raw("Teleportados " + count + " NPCs do SimTale para sua posicao."));
    }

    private void handleClearAll(CommandContext ctx, Store<EntityStore> store) {
        int count = 0;
        List<SimNPCComponent> toRemove = new ArrayList<>(SimTale.ACTIVE_NPCS);
        for (SimNPCComponent npc : toRemove) {
            if (npc.entityRef != null && npc.entityRef.isValid()) {
                store.removeEntity(npc.entityRef, RemoveReason.REMOVE);
                count++;
            }
            PlumbobSystem.removePlumbob(npc.entityId);
            Caskara.delete(npc.entityId.toString(), com.cookieukw.SimTale.db.SimNPCData.class);
        }
        SimTale.ACTIVE_NPCS.clear();
        ctx.sendMessage(Message.raw("Removidos permanentemente " + count + " NPCs do Hytale e banco de dados."));
    }

    private void handleForceSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, String typeName) {
        SimNPCFactory.NPCType type;
        if (typeName != null) {
            try {
                type = SimNPCFactory.NPCType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.translation("simtale.cmd.spawn.error").param("type", "SLOTHIAN/TRORK/HUMAN_MALE/HUMAN_FEMALE"));
                return;
            }
        } else {
            type = Math.random() > 0.5 ? SimNPCFactory.NPCType.HUMAN_MALE : SimNPCFactory.NPCType.HUMAN_FEMALE;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        assert transform != null;
        Vector3d pos = transform.getPosition().add(2, 0, 2);

        Ref<EntityStore> npcRef = SimNPCFactory.spawnNPC(store, pos, type);
        SimNPCComponent comp = store.getComponent(npcRef, SimTale.SIM_NPC_COMPONENT_TYPE);

        assert comp != null;
        SimNPCPersistence.saveNPC(comp);

        ctx.sendMessage(Message.raw("Forcado spawn de NPC de debug do tipo: " + type.name()));
    }

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.translation("simtale.cmd.spawn.usage"));
    }
}
