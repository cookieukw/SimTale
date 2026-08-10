package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.logic.SimBedDebugPage;
import com.cookieukw.SimTale.logic.SimChestDebugPage;
import com.cookieukw.SimTale.systems.FurnitureAnchorHelper;
import com.cookieukw.SimTale.systems.NPCMovementHelper;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;

/**
 * Inspection subcommands: what is registered, what is nearby, and how to undo a bad registration.
 *
 * <p>Split out of {@code SimTaleCommand}, which had grown to 2053 lines holding 36 subcommands as
 * nested classes. Grouped by purpose rather than one file per command: these four are read together
 * when diagnosing "the registry says nothing is there", and keeping them adjacent is the point.
 *
 * <p>Package-private on purpose — nothing outside the command layer should be constructing these.
 */
final class DebugCommands {

    private DebugCommands() {
    }

    static class DebugBedsSubCommand extends AbstractPlayerCommand {
        public DebugBedsSubCommand() {
            super("debugbeds", "Opens the bed debug screen");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            // No scan here — see DebugChestsSubCommand for why it was removed.
            player.getPageManager().openCustomPage(ref, store, new SimBedDebugPage(playerRef, player));
        }
    }

    /**
     * Removes the SimTale NPC component from entities that were adopted by mistake.
     *
     * <p>Before the guard in SimTaleEventHandler, right-clicking any entity attached
     * SIM_NPC_COMPONENT_TYPE to it — cows included. The guard stops new cases but does nothing
     * about the ones already carrying the component, and those keep opening the villager panel and
     * running the villager routine.
     *
     * <p>The tell is {@code gender}: {@code SimNPCFactory.spawnNPC} always sets it, while the
     * adoption path built the component with the bare constructor, which leaves it null.
     */
    static class ForgetSubCommand extends AbstractPlayerCommand {
        public ForgetSubCommand() {
            super("forget", "Removes NPC component from mistakenly adopted entities (cows, mobs)");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            List<SimNPCComponent> adopted = new ArrayList<>();
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.gender == null) {
                    adopted.add(npc);
                }
            }

            if (adopted.isEmpty()) {
                ctx.sendMessage(Message.raw("[SimTale] No mistakenly adopted entity found."));
                return;
            }

            int cleaned = 0;
            for (SimNPCComponent npc : adopted) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    Store<EntityStore> npcStore = npc.entityRef.getStore();
                    npcStore.tryRemoveComponent(npc.entityRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                    npcStore.tryRemoveComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                    // Leave the entity free to move: the routine may have parked it in a bed.
                    npcStore.tryRemoveComponent(npc.entityRef, MountedComponent.getComponentType());
                    npcStore.tryRemoveComponent(npc.entityRef, Frozen.getComponentType());
                    NPCMovementHelper.setSleepingState(npc.entityRef, npcStore, false);
                }

                // Deleting the saved record is what makes this stick. SimTaleTickSystem re-attaches
                // any entity that still has one when its chunk loads, so stripping the component
                // alone would hand the cow straight back on the next reload.
                if (npc.entityId != null) {
                    SimNPCPersistence.deleteNPC(npc.entityId);
                }

                SimTale.untrackNpc(npc);
                cleaned++;
            }

            ctx.sendMessage(Message.raw("[SimTale] " + cleaned
                    + " adopted entity(s) released and removed from the bank. "
                    + "Entities not yet loaded will only be cleaned when they appear."));
        }
    }

    static class DebugChestsSubCommand extends AbstractPlayerCommand {
        public DebugChestsSubCommand() {
            super("debugchests", "Opens the registered chests debug screen");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            // The radius scan that used to run here is gone, and it is why the command took
            // seconds to answer: radius 32 is a 65x65x33 box, about 139 thousand getBlockType
            // calls, each followed by an ItemContainerBlock component lookup — all synchronous,
            // before a single row was drawn.
            //
            // It was only ever a workaround. Furniture was not registered on placement because
            // PlaceBlockEvent never reached the handler, so these pages scanned on every open to
            // hide that. With placement registering correctly, the registry is already right and
            // the scan is redundant work that also made the bug invisible.
            //
            // Worlds that predate the fix still need one sweep: that is what the join scan and
            // '/simtale rescan' are for, and rescan stays explicit so the cost is asked for.
            player.getPageManager().openCustomPage(ref, store,
                    new SimChestDebugPage(playerRef, player));
        }
    }

    static class DebugNearSubCommand extends AbstractPlayerCommand {
        public DebugNearSubCommand() {
            super("debugnear", "Shows details of nearby blocks and entities");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                ctx.sendMessage(Message.raw("Error: TransformComponent is null."));
                return;
            }

            Vector3d pos = tc.getPosition();
            int px = (int) Math.floor(pos.x);
            int py = (int) Math.floor(pos.y);
            int pz = (int) Math.floor(pos.z);

            StringBuilder sb = new StringBuilder();
            sb.append("--- NEAREST (Your Pos: ").append(px).append(",").append(py).append(",").append(pz).append(") ---");

            // Scan blocks in 3x3x3
            sb.append("\nNearby blocks/furniture:");
            Map<String, Integer> countBlocksByAnchor = new LinkedHashMap<>();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int bx = px + dx, by = py + dy, bz = pz + dz;
                        BlockType type = world.getBlockType(bx, by, bz);
                        if (type == null || type.getId() == null || type.getId().equalsIgnoreCase("Empty")) {
                            continue;
                        }
                        Vector3i anchor = FurnitureAnchorHelper.anchorOf(world, bx, by, bz);
                        String key = type.getId() + " @ (" + anchor.x + "," + anchor.y + "," + anchor.z + ")";
                        countBlocksByAnchor.merge(key, 1, Integer::sum);
                    }
                }
            }

            for (Entry<String, Integer> e : countBlocksByAnchor.entrySet()) {
                String blocos = e.getValue() > 1 ? "  [" + e.getValue() + " blocks]" : "";
                sb.append("\n  ").append(e.getKey()).append(blocos);
            }

            // Scan all ACTIVE_NPCS near the player
            sb.append("\nNearby Active NPCs:");
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.entityRef != null && npc.entityRef.isValid()) {
                    TransformComponent npcTc = npc.entityRef.getStore().getComponent(npc.entityRef, TransformComponent.getComponentType());
                    if (npcTc != null) {
                        double dist = pos.distance(npcTc.getPosition());
                        if (dist <= 15.0) {
                            sb.append("\n  NPC: Name='").append(npc.name).append("' Dist=")
                              .append(String.format("%.2f", dist)).append(" (id=").append(npc.entityId).append(")");
                        }
                    }
                }
            }

            // Temporary: dump every nearby modelled entity (SimTale NPCs, hostile mobs, animals,
            // etc.) with its raw model asset id — needed to find out what identifies a hostile
            // mob (skeleton, zombie...) before Guard combat can detect one instead of guessing at
            // a keyword like the old broken Hunter "contains creature" filter did.
            sb.append("\nNearby modelled entities (any type):");
            store.forEachChunk(PersistentModel.getComponentType(), (chunk, cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent entTc = chunk.getComponent(i, TransformComponent.getComponentType());
                    PersistentModel entPm = chunk.getComponent(i, PersistentModel.getComponentType());
                    if (entTc == null || entPm == null || entPm.getModelReference() == null) continue;
                    double dist = pos.distance(entTc.getPosition());
                    if (dist > 15.0) continue;
                    sb.append("\n  Entity model='").append(entPm.getModelReference().getModelAssetId())
                      .append("' Dist=").append(String.format("%.2f", dist));
                }
            });

            // Results go to the server log, not chat — this dumps a lot of lines and chat isn't
            // a good place to read/scroll through them.
            HytaleLogger.forEnclosingClass().atInfo().log(sb.toString());
            ctx.sendMessage(Message.raw("[SimTale] debugnear: resultado no log do servidor."));
        }
    }
}
