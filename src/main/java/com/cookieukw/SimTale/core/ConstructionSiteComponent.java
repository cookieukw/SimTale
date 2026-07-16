package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import org.joml.Vector3i;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConstructionSiteComponent implements Component<EntityStore> {
    public String prefabName;
    public Vector3i anchor;
    public int currentIndex;
    public int ticksSinceLastBlock;
    
    public boolean isBuilding;
    public boolean forceBuild;
    public int simulatedBuilders;
    public transient int activeBuilders;

    public Rotation4 facing = Rotation4.NORTH;
    public Rotation4 roofFacing = Rotation4.NORTH;
    public final Set<Long> previewBody = ConcurrentHashMap.newKeySet();
    public final Set<Long> previewRoof = ConcurrentHashMap.newKeySet();
    
    public ConstructionSiteComponent() {
    }

    public ConstructionSiteComponent(String prefabName, Vector3i anchor) {
        this.prefabName = prefabName;
        this.anchor = anchor;
        this.currentIndex = 0;
        this.ticksSinceLastBlock = 0;
        this.isBuilding = false;
        this.forceBuild = false;
        this.simulatedBuilders = 0;
        this.activeBuilders = 0;
        this.facing = Rotation4.NORTH;
        this.roofFacing = Rotation4.NORTH;
    }

    public static final BuilderCodec<ConstructionSiteComponent> CODEC = BuilderCodec
        .builder(ConstructionSiteComponent.class, ConstructionSiteComponent::new)
        .append(new KeyedCodec<>("PrefabName", Codec.STRING), (c, v) -> c.prefabName = v, c -> c.prefabName).add()
        .append(new KeyedCodec<>("AnchorX", Codec.INTEGER), (c, v) -> { if (c.anchor == null) c.anchor = new Vector3i(); c.anchor.x = v; }, c -> c.anchor != null ? c.anchor.x : 0).add()
        .append(new KeyedCodec<>("AnchorY", Codec.INTEGER), (c, v) -> { if (c.anchor == null) c.anchor = new Vector3i(); c.anchor.y = v; }, c -> c.anchor != null ? c.anchor.y : 0).add()
        .append(new KeyedCodec<>("AnchorZ", Codec.INTEGER), (c, v) -> { if (c.anchor == null) c.anchor = new Vector3i(); c.anchor.z = v; }, c -> c.anchor != null ? c.anchor.z : 0).add()
        .append(new KeyedCodec<>("CurrentIndex", Codec.INTEGER), (c, v) -> c.currentIndex = v, c -> c.currentIndex).add()
        .append(new KeyedCodec<>("IsBuilding", Codec.BOOLEAN), (c, v) -> c.isBuilding = v, c -> c.isBuilding).add()
        .append(new KeyedCodec<>("ForceBuild", Codec.BOOLEAN), (c, v) -> c.forceBuild = v, c -> c.forceBuild).add()
        .append(new KeyedCodec<>("SimulatedBuilders", Codec.INTEGER), (c, v) -> c.simulatedBuilders = v, c -> c.simulatedBuilders).add()
        .append(new KeyedCodec<>("Facing", Codec.STRING), (c, v) -> c.facing = Rotation4.valueOf(v), c -> c.facing.name()).add()
        .append(new KeyedCodec<>("RoofFacing", Codec.STRING), (c, v) -> c.roofFacing = Rotation4.valueOf(v), c -> c.roofFacing.name()).add()
        .build();

    @Override
    public ConstructionSiteComponent clone() {
        ConstructionSiteComponent clone = new ConstructionSiteComponent();
        clone.prefabName = this.prefabName;
        if (this.anchor != null) {
            clone.anchor = new Vector3i(this.anchor);
        }
        clone.currentIndex = this.currentIndex;
        clone.ticksSinceLastBlock = this.ticksSinceLastBlock;
        clone.isBuilding = this.isBuilding;
        clone.forceBuild = this.forceBuild;
        clone.simulatedBuilders = this.simulatedBuilders;
        clone.activeBuilders = this.activeBuilders;
        clone.facing = this.facing;
        clone.roofFacing = this.roofFacing;
        return clone;
    }
}
