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
        Prefab prefab = PrefabManager.getPrefab(prefabName);

        if (prefab == null) {
            ctx.sendMessage(Message.raw("§cPrefab not found: " + prefabName));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d pos = transform.getPosition();
        Vector3i anchor = new Vector3i((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));

        // Spawn construction marker entity
        EntityStore entityStore = world.getEntityStore();
        Store<EntityStore> eStore = entityStore.getStore();
        
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        ConstructionSiteComponent site = new ConstructionSiteComponent(prefabName, anchor);
        holder.addComponent(SimTale.CONSTRUCTION_COMPONENT_TYPE, site);

        eStore.addEntity(holder, AddReason.SPAWN);

        ctx.sendMessage(Message.raw("§aStarted construction of " + prefabName + " at " + anchor.x + ", " + anchor.y + ", " + anchor.z));
    }
}
