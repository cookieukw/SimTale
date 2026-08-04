package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Draws every SimTale NPC on the world map, tinted by how it feels about the viewing player.
 *
 * <p>Replaces the {@code NPCMarkerComponent} tag rather than complementing it. That component makes
 * the engine draw a default marker; producing a second one here would put two icons on the same
 * NPC. Tint, height arrows and context menus all live on the {@code MapMarker} payload, which only
 * a provider can build — so the provider has to own the marker outright.
 */
public final class SimTaleMarkerProvider implements WorldMapManager.MarkerProvider {

    private static final SimLog LOGGER = SimLog.forClass(SimTaleMarkerProvider.class);

    /** Name the provider is registered under, and the prefix of every marker id it produces. */
    public static final String PROVIDER_ID = "simtale_npcs";

    private static final Color COLOR_ENEMY = rgb(220, 70, 70);
    private static final Color COLOR_STRANGER = rgb(200, 200, 200);
    private static final Color COLOR_FRIEND = rgb(90, 210, 120);
    private static final Color COLOR_CLOSE = rgb(255, 200, 90);
    private static final Color COLOR_ROMANCE = rgb(240, 130, 200);

    private static Color rgb(int r, int g, int b) {
        Color color = new Color();
        // The protocol stores channels as signed bytes, so values above 127 wrap negative. That is
        // the wire format, not a bug — the client reads them back unsigned.
        color.red = (byte) r;
        color.green = (byte) g;
        color.blue = (byte) b;
        return color;
    }

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        PlayerRef playerRef = player.getReference() != null
                ? player.getReference().getStore().getComponent(
                        player.getReference(), Universe.get().getPlayerRefComponentType())
                : null;
        UUID viewerId = playerRef != null ? playerRef.getUuid() : null;

        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            try {
                addMarker(collector, npc, viewerId);
            } catch (RuntimeException e) {
                // One malformed NPC must not cost every other marker on the map.
                LOGGER.debug("[MAPA] falha ao marcar '{}': {}", npc.name, e.toString());
            }
        }
    }

    private void addMarker(MarkersCollector collector, SimNPCComponent npc, UUID viewerId) {
        if (npc.entityId == null || npc.entityRef == null || !npc.entityRef.isValid()) return;

        TransformComponent transform = npc.entityRef.getStore()
                .getComponent(npc.entityRef, TransformComponent.getComponentType());
        if (transform == null) return;

        TintComponent tint = new TintComponent();
        tint.color = colorFor(npc, viewerId);

        // Position and icon go through the constructor, not through with* calls: the builder keeps
        // id, image and transform as fixed state and only the optional parts are chainable.
        // A null image leaves the client's default marker sprite in place.
        Transform markerTransform = new Transform(transform.getPosition());

        collector.add(new MapMarkerBuilder(PROVIDER_ID + ":" + npc.entityId, null, markerTransform)
                .withName(Message.raw(npc.name))
                .withComponent(tint)
                .build());
    }

    /**
     * Marker colour, from this NPC's relationship with the player currently looking at the map.
     *
     * <p>Per-viewer on purpose: the same NPC is a friend to one player and an enemy to another, and
     * {@code update} is already called once per player, so the map costs nothing extra to
     * personalise.
     */
    private Color colorFor(SimNPCComponent npc, UUID viewerId) {
        if (viewerId == null) return COLOR_STRANGER;

        Relationship rel = npc.getRelationship(viewerId);
        if (rel == null) return COLOR_STRANGER;

        return switch (rel.status) {
            case MARRIED, ENGAGED, PARTNER, DATING, CRUSH -> COLOR_ROMANCE;
            case ENEMIES -> COLOR_ENEMY;
            case BEST_FRIEND, GOOD_FRIEND -> COLOR_CLOSE;
            case FRIEND -> COLOR_FRIEND;
            case UNKNOWN, STRANGER, ACQUAINTANCE -> COLOR_STRANGER;
        };
    }
}
