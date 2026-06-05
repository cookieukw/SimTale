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
    private final com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg<Integer> speedArg;

    public BuildCommand() {
        super("build", "Start a progressive building construction");
        this.setPermissionGroups("Admin", "Adventure");
        this.prefabArg = this.withRequiredArg("prefab", "TavernHouse (or start/force/speed/simulate)", ArgTypes.STRING);
        this.speedArg = this.withOptionalArg("speed", "Multiplier or Simulated Builders", ArgTypes.INTEGER);
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
                
                // Clear wireframe
                com.cookieukw.SimTale.systems.ConstructionHelper.clearPreview(world, closestSite);
            } else {
                ctx.sendMessage(Message.raw("No pending construction site found nearby."));
            }
            return;
        }

        if (prefabName.equalsIgnoreCase("force")) {
            ConstructionSiteComponent closestSite = null;
            double minDistance = Double.MAX_VALUE;

            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                double dist = site.anchor.distance(playerAnchor);
                if (dist < 20.0 && dist < minDistance) {
                    minDistance = dist;
                    closestSite = site;
                }
            }

            if (closestSite != null) {
                closestSite.isBuilding = true;
                closestSite.forceBuild = true;
                ctx.sendMessage(Message.raw("Forced construction started! It will build rapidly."));
                com.cookieukw.SimTale.systems.ConstructionHelper.clearPreview(world, closestSite);
            } else {
                ctx.sendMessage(Message.raw("No pending construction site found nearby to force."));
            }
            return;
        }

        if (prefabName.equalsIgnoreCase("speed")) {
            Integer speed = ctx.get(this.speedArg);
            if (speed != null) {
                com.cookieukw.SimTale.systems.ConstructionSystem.GLOBAL_SPEED = speed;
                ctx.sendMessage(Message.raw("Construction global speed set to " + speed));
            } else {
                ctx.sendMessage(Message.raw("Current construction global speed is " + com.cookieukw.SimTale.systems.ConstructionSystem.GLOBAL_SPEED));
            }
            return;
        }

        if (prefabName.equalsIgnoreCase("simulate")) {
            Integer builders = ctx.get(this.speedArg);
            if (builders == null) builders = 1;

            ConstructionSiteComponent closestSite = null;
            double minDistance = Double.MAX_VALUE;

            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                double dist = site.anchor.distance(playerAnchor);
                if (dist < 20.0 && dist < minDistance) {
                    minDistance = dist;
                    closestSite = site;
                }
            }

            if (closestSite != null) {
                closestSite.isBuilding = true;
                closestSite.simulatedBuilders = builders;
                ctx.sendMessage(Message.raw("Simulated construction started with " + builders + " ghost builders."));
                com.cookieukw.SimTale.systems.ConstructionHelper.clearPreview(world, closestSite);
            } else {
                ctx.sendMessage(Message.raw("No pending construction site found nearby to simulate."));
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

        com.cookieukw.SimTale.systems.ConstructionHelper.placePreview(world, eStore, playerAnchor, prefabName);

        ctx.sendMessage(Message.raw("Preview placed for " + prefabName + " at " + playerAnchor.x + ", " + playerAnchor.y + ", " + playerAnchor.z));
        ctx.sendMessage(Message.raw("Type '/build start' to confirm and let NPCs begin building."));
    }
}
