package com.cookieukw.SimTale;

import com.cookieukw.SimTale.core.ConstructionSiteComponent;
import com.cookieukw.SimTale.core.Prefab;
import com.cookieukw.SimTale.core.PrefabManager;
import com.cookieukw.SimTale.systems.ConstructionHelper;
import com.cookieukw.SimTale.systems.ConstructionPreviewManager;
import com.cookieukw.SimTale.systems.ConstructionSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.core.Rotation4;
import org.joml.Vector3d;
import org.joml.Vector3i;
import javax.annotation.Nonnull;

public class BuildCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> prefabArg;
    private final OptionalArg<Integer> speedArg;

    public BuildCommand() {
        super("build", "Start a progressive building construction");
        this.setPermissionGroups("Admin", "Adventure");
        this.prefabArg = this.withRequiredArg("prefab", "TavernHouse (or start/clear/rotate/rotateroof/force/speed/simulate)", ArgTypes.STRING);
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

        if (prefabName.equalsIgnoreCase("start")) {
            ConstructionSiteComponent activePreview = ConstructionPreviewManager.get(playerRef.getUuid());
            if (activePreview != null && !activePreview.isClear) {
                ctx.sendMessage(Message.raw(
                        "Construction denied! The area is obstructed. Move the preview or clear the blocks in the way."));
                return;
            }
            ConstructionSiteComponent site = ConstructionPreviewManager.commit(playerRef.getUuid(), world);
            if (site != null) {
                site.isBuilding = true;
                ctx.sendMessage(Message.raw("Construction started! NPCs will now come to build."));
            } else {
                ctx.sendMessage(Message.raw("No active preview found to start. Use '/build <prefab>' first."));
            }
            return;
        }

        if (prefabName.equalsIgnoreCase("clear")) {
            ConstructionPreviewManager.clear(playerRef.getUuid(), world);
            ctx.sendMessage(Message.raw("Construction preview cleared."));
            return;
        }

        if (prefabName.equalsIgnoreCase("rotate")) {
            ConstructionPreviewManager.rotate(playerRef.getUuid(), world);
            ctx.sendMessage(Message.raw("Rotated construction preview."));
            return;
        }

        if (prefabName.equalsIgnoreCase("rotateroof")) {
            ConstructionPreviewManager.rotateRoof(playerRef.getUuid(), world);
            ctx.sendMessage(Message.raw("Rotated construction roof preview."));
            return;
        }

        if (prefabName.equalsIgnoreCase("force")) {
            ConstructionSiteComponent closestSite = null;
            double minDistance = Double.MAX_VALUE;

            for (ConstructionSiteComponent site : SimTale.ACTIVE_SITES) {
                double dist = site.anchor.distance(playerAnchor);
                if (dist < 100.0 && dist < minDistance) {
                    minDistance = dist;
                    closestSite = site;
                }
            }

            /* A marker-block preview is not in ACTIVE_SITES — it only gets there when something
            confirms it, and commit() is what moves it. So this search only ever saw sites that
            had already been confirmed, and reported "no pending construction site" while the
            player was standing inside a hologram. Confirming the nearest pending preview here
            is what the command claims to do, and makes '/build force' a complete test path on
            its own: place the marker, run it, watch the house go up.
            */
            if (closestSite == null) {
                ConstructionSiteComponent pending = null;
                double pendingDistance = Double.MAX_VALUE;
                for (ConstructionSiteComponent site : ConstructionPreviewManager.allPending()) {
                    double dist = site.anchor.distance(playerAnchor);
                    if (dist < 100.0 && dist < pendingDistance) {
                        pendingDistance = dist;
                        pending = site;
                    }
                }
                if (pending != null) {
                    closestSite = ConstructionPreviewManager.commit(pending.ownerId, world);
                }
            }

            if (closestSite != null) {
                closestSite.isBuilding = true;
                closestSite.forceBuild = true;
                ctx.sendMessage(Message.raw("Forced construction started! It will build rapidly."));
                ConstructionHelper.clearPreview(world, closestSite);
            } else {
                ctx.sendMessage(Message.raw("No pending construction site found nearby to force."));
            }
            return;
        }

        if (prefabName.equalsIgnoreCase("speed")) {
            Integer speed = ctx.get(this.speedArg);
            if (speed != null) {
                ConstructionSystem.GLOBAL_SPEED = speed;
                ctx.sendMessage(Message.raw("Construction global speed set to " + speed));
            } else {
                ctx.sendMessage(Message.raw("Current construction global speed is " + ConstructionSystem.GLOBAL_SPEED));
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
                if (dist < 100.0 && dist < minDistance) {
                    minDistance = dist;
                    closestSite = site;
                }
            }

            if (closestSite != null) {
                closestSite.isBuilding = true;
                closestSite.simulatedBuilders = builders;
                ctx.sendMessage(Message.raw("Simulated construction started with " + builders + " ghost builders."));
                ConstructionHelper.clearPreview(world, closestSite);
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

        ConstructionPreviewManager.clear(playerRef.getUuid(), world);

        double yawDegrees = Math.toDegrees(transform.getRotation().yaw());
        Rotation4 facing = Rotation4.fromYawDegrees(yawDegrees);

        ConstructionSiteComponent site = ConstructionPreviewManager.start(playerRef.getUuid(), prefabName, playerAnchor);
        site.facing = facing;
        site.roofFacing = facing;
        ConstructionHelper.placePreview(world, site);

        ctx.sendMessage(Message.raw("Preview placed for " + prefabName + " at " + playerAnchor.x + ", " + playerAnchor.y + ", " + playerAnchor.z));
        ctx.sendMessage(Message.raw("Type '/build start' to confirm, '/build rotate' to rotate, '/build clear' to clear."));
    }
}
