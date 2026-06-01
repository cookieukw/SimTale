package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabManager;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

public class BuildCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> prefabArg;

    public BuildCommand() {
        super("build", "Start a progressive building construction");
        this.setPermissionGroups("Admin", "Adventure");
        this.prefabArg = this.withRequiredArg("prefab", "TavernHouse", ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {

        String prefabName = ctx.get(this.prefabArg);
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        Vector3i playerAnchor = new Vector3i((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));

        EntityStore entityStore = world.getEntityStore();
        Store<EntityStore> eStore = entityStore.getStore();

        if (prefabName.equalsIgnoreCase("start")) {
            ConstructionSiteComponent closestSite = null;
            double minDistance = Double.MAX_VALUE;

            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                double dist = site.anchor.distance(playerAnchor);
                if (dist < 20.0 && dist < minDistance) {
                    minDistance = dist;
                    closestSite = site;
                }
            }

            if (closestSite != null && !closestSite.isBuilding) {
                closestSite.isBuilding = true;
                ctx.sendMessage(Message.raw("Construction started! NPCs will now come to build."));
                
                // Clear corners
                Prefab prefab = PrefabManager.getPrefab(closestSite.prefabName);
                if (prefab != null && !prefab.getBlocks().isEmpty()) {
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    for (com.cookieukw.SimTale.core.PrefabBlock b : prefab.getBlocks()) {
                        if (b.getX() < minX) minX = b.getX();
                        if (b.getX() > maxX) maxX = b.getX();
                        if (b.getZ() < minZ) minZ = b.getZ();
                        if (b.getZ() > maxZ) maxZ = b.getZ();
                    }
                    world.setBlock(closestSite.anchor.x + minX, closestSite.anchor.y, closestSite.anchor.z + minZ, "air");
                    world.setBlock(closestSite.anchor.x + maxX, closestSite.anchor.y, closestSite.anchor.z + minZ, "air");
                    world.setBlock(closestSite.anchor.x + minX, closestSite.anchor.y, closestSite.anchor.z + maxZ, "air");
                    world.setBlock(closestSite.anchor.x + maxX, closestSite.anchor.y, closestSite.anchor.z + maxZ, "air");
                }
            } else {
                ctx.sendMessage(Message.raw("No pending construction site found nearby."));
            }
            return;
        }

        Prefab prefab = PrefabManager.getPrefab(prefabName);

        if (prefab == null) {
            ctx.sendMessage(Message.raw("Prefab not found: " + prefabName));
            return;
        }

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        ConstructionSiteComponent site = new ConstructionSiteComponent(prefabName, playerAnchor);
        holder.addComponent(SimTale.CONSTRUCTION_COMPONENT_TYPE, site);

        SimTale.ACTIVE_SITES.add(site);
        eStore.addEntity(holder, AddReason.SPAWN);

        // Place corner blocks for preview
        if (!prefab.getBlocks().isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (com.cookieukw.SimTale.core.PrefabBlock b : prefab.getBlocks()) {
                if (b.getX() < minX) minX = b.getX();
                if (b.getX() > maxX) maxX = b.getX();
                if (b.getZ() < minZ) minZ = b.getZ();
                if (b.getZ() > maxZ) maxZ = b.getZ();
            }
            String markerBlock = prefab.getBlocks().get(0).getName();
            world.setBlock(playerAnchor.x + minX, playerAnchor.y, playerAnchor.z + minZ, markerBlock);
            world.setBlock(playerAnchor.x + maxX, playerAnchor.y, playerAnchor.z + minZ, markerBlock);
            world.setBlock(playerAnchor.x + minX, playerAnchor.y, playerAnchor.z + maxZ, markerBlock);
            world.setBlock(playerAnchor.x + maxX, playerAnchor.y, playerAnchor.z + maxZ, markerBlock);
        }

        ctx.sendMessage(Message.raw("Preview placed for " + prefabName + " at " + playerAnchor.x + ", " + playerAnchor.y + ", " + playerAnchor.z));
        ctx.sendMessage(Message.raw("Type '/build start' to confirm and let NPCs begin building."));
    }
}
