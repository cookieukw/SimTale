package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.NPCInteractionPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
    private final RequiredArg<String> subCommandArg;
    private final RequiredArg<String> npcTypeArg;

    public SimTaleCommand(String pluginName, String pluginVersion) {
        super("simtale", "SimTale plugin commands");
        this.setPermissionGroups("Adventure");
        this.subCommandArg = this.withRequiredArg("subcommand", "spawn", ArgTypes.STRING);
        this.npcTypeArg = this.withRequiredArg("type", "SLOTHIAN|TRORK", ArgTypes.STRING);
        this.pluginVersion = pluginVersion;
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
            ctx.sendMessage(Message.raw("Remontando NPCs do banco de dados..."));
            reassembleNPCsFromDatabase(store);
        }
        
        TransformComponent playerTransform = 
            store.getComponent(ref, TransformComponent.getComponentType());
        
        Ref<EntityStore> nearestRef = null;
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
                        nearestRef = npc.entityRef;
                        nearestNPC = npc;
                    }
                }
            }
        }

        if (nearestNPC == null) {
            ctx.sendMessage(Message.raw("Nenhum NPC vivo por perto!"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        player.getPageManager().openCustomPage(ref, store, new NPCInteractionPage(playerRef, player, nearestNPC));
        ctx.sendMessage(Message.raw("Forced UI to open for " + nearestNPC.name));
    }

    /**
     * Reassembles all NPCs from Caskara database after world reload.
     * Resolves entity references from the current world.
     */
    private void reassembleNPCsFromDatabase(Store<EntityStore> store) {
        com.hypixel.hytale.server.core.universe.world.World world = null;
        for (com.hypixel.hytale.server.core.universe.world.World w : com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().values()) {
            world = w;
            break;
        }
        if (world == null) return;

        java.util.List<SimNPCComponent> savedNPCs = SimNPCPersistence.loadAllNPCs();
        com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor = 
            (com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>) store;

        for (SimNPCComponent comp : savedNPCs) {
            if (comp.entityId == null) continue;

            // Check if already tracked
            boolean alreadyTracked = SimTale.ACTIVE_NPCS.stream()
                .anyMatch(a -> a.entityId != null && a.entityId.equals(comp.entityId));
            if (alreadyTracked) continue;

            // Try to find the entity in the world
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef = 
                world.getEntityStore().getRefFromUUID(comp.entityId);
            
            if (entityRef != null) {
                comp.entityRef = entityRef;
                
                // Re-attach component to entity
                try {
                    accessor.addComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                } catch (Exception e) {
                    // Component might already exist, try put instead
                    try {
                        accessor.putComponent(entityRef, SimTale.SIM_NPC_COMPONENT_TYPE, comp);
                    } catch (Exception ignored) {}
                }
                
                SimTale.ACTIVE_NPCS.add(comp);
            }
        }
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
        ctx.sendMessage(Message.raw("SimTale v" + pluginVersion + " - Use /simtale spawn <SLOTHIAN|TRORK> or /simtale interact"));
    }
}
