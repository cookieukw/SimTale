package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.systems.VillageManager.Village;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.PlayerMarkerComponent;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * Sprite drawn for an NPC.
     *
     * <p>This was {@code null}, on the assumption that the client would fall back to a default
     * marker. It does not — a marker with no image draws nothing, which is why the map stayed empty
     * even after the provider was registered and the thread violation was fixed. Every vanilla
     * provider names a file: {@code OtherPlayersMarkerProvider} uses {@code Player.png},
     * {@code SpawnMarkerProvider} uses {@code Spawn.png}, {@code DeathMarkerProvider} uses
     * {@code Death.png}, all confirmed in the server jar.
     *
     * <p>Reusing the vanilla player sprite is deliberate: it is guaranteed to resolve, and an NPC
     * is a person. A custom icon means shipping the asset and getting its path right, which is a
     * separate problem from making markers appear at all.
     */
    private static final String MARKER_IMAGE = "Player.png";
    private static final String MARKER_IMAGE_VILLAGE = "Spawn.png";
    private static final String MARKER_IMAGE_HOUSE = "Home.png";
    private static final String MARKER_IMAGE_WORK = "Spawn.png";

    /**
     * Worlds already carrying the provider, so a re-registration is a no-op instead of a stack.
     *
     * <p>Keyed by name because {@code World} exposes {@code getName()} and no id getter — checked
     * against the server jar rather than assumed.
     */
    private static final Set<String> REGISTERED_WORLDS = ConcurrentHashMap.newKeySet();

    /**
     * Attaches the provider to {@code world}, once.
     *
     * <p>This used to happen in the plugin's registry setup, looping over
     * {@code Universe.get().getWorlds().values()}. That loop ran before any world existed, so it
     * iterated nothing and no provider was ever registered — the markers could not appear no
     * matter what this class did. The server log shows the gap plainly: "Setting up SimTale
     * registries" lands about a minute before the first world is loaded.
     *
     * <p>Same shape as the {@code register} versus {@code registerGlobal} bug documented next to
     * that loop: a registration call that silently succeeds while attaching to nothing.
     *
     * <p>Called from the join handler, which by definition has a world in hand.
     */
    public static void ensureRegistered(World world) {
        if (world == null) return;

        String worldName = world.getName();
        if (!REGISTERED_WORLDS.add(worldName)) return;

        WorldMapManager manager = world.getWorldMapManager();

        manager.addMarkerProvider(PROVIDER_ID, new SimTaleMarkerProvider());
        LOGGER.info("[SimTale] Map markers registered in world '{}'", worldName);

        IWorldMap currentGen = manager.getGenerator();
        if (currentGen != null && !(currentGen instanceof SimTaleWorldMapGenerator)) {
            manager.setGenerator(new SimTaleWorldMapGenerator(currentGen));
            LOGGER.info("[SimTale] Wrapped WorldMap generator for village chunk coloring in world '{}'", worldName);
        }
    }

    private static final Color COLOR_ENEMY = rgb(220, 70, 70);
    private static final Color COLOR_STRANGER = rgb(200, 200, 200);
    private static final Color COLOR_FRIEND = rgb(90, 210, 120);
    private static final Color COLOR_CLOSE = rgb(255, 200, 90);
    private static final Color COLOR_ROMANCE = rgb(240, 130, 200);
    private static final Color COLOR_VILLAGE_CENTER = rgb(255, 215, 0);
    private static final Color COLOR_HOUSE_OCCUPIED = rgb(80, 220, 120);
    private static final Color COLOR_HOUSE_EMPTY = rgb(100, 200, 255);
    private static final Color COLOR_BED_UNCLAIMED = rgb(150, 170, 190);
    private static final Color COLOR_FISHING_POST = rgb(70, 170, 220);
    private static final Color COLOR_LUMBER_POST = rgb(170, 120, 65);
    private static final Color COLOR_FARM_POST = rgb(100, 190, 90);

    private static Color rgb(int r, int g, int b) {
        Color color = new Color();
        /* The protocol stores channels as signed bytes, so values above 127 wrap negative. That is
        the wire format, not a bug — the client reads them back unsigned.
        */
        color.red = (byte) r;
        color.green = (byte) g;
        color.blue = (byte) b;
        return color;
    }

    /**
     * What the map needs to draw one NPC, captured on the world thread.
     *
     * <p>{@code statuses} is a copy rather than the live relationship map: the marker thread must
     * not read a collection another thread is writing to.
     */
    private record NpcMarker(UUID id, String name, Transform transform,
                             Map<UUID, RelationshipStatus> statuses) {
    }

    private record HouseMapMarker(String id, List<String> ownerNames, Vector3d position, boolean occupied) {
    }

    private record WorkPostMapMarker(String id, String type, Vector3d position) {
    }

    private record WorldMarkerSnapshot(List<NpcMarker> npcs, List<Village> villages,
                                       List<HouseMapMarker> houses, List<WorkPostMapMarker> workPosts,
                                       List<HouseBlockPos> unclaimedBeds) {
    }

    private static volatile Map<String, WorldMarkerSnapshot> snapshots = Map.of();

    /**
     * Tick of the last capture, or {@code -1} when there has not been one.
     *
     * <p>Not {@code Long.MIN_VALUE}: the throttle below computes {@code tick - snapshotTick}, and
     * subtracting {@code MIN_VALUE} overflows to a large negative number, which is always less
     * than the interval. The guard therefore returned on every single call and the snapshot stayed
     * empty forever — "0 NPC(s) no snapshot" with a world full of NPCs.
     */
    private static final Map<String, Long> snapshotTicks = new ConcurrentHashMap<>();
    private static final AtomicBoolean snapshotDirty = new AtomicBoolean(true);

    /** How often the snapshot is rebuilt. The map redraws slower than this anyway. */
    private static final int SNAPSHOT_INTERVAL_TICKS = 10;

    /** Requests the next world-thread capture immediately instead of waiting for the interval. */
    public static void requestRefresh() {
        snapshotDirty.set(true);
    }

    /**
     * Copies what the map needs, from the world thread.
     *
     * <p>This exists because {@code update} does not run on the world thread — the server gives the
     * world map its own {@code WorldMap - <world>} thread. Touching the ECS from there trips
     * {@code Store.assertThread} and the whole marker pass dies with
     * {@code IllegalStateException: Assert not in thread!}, which is exactly what happened once the
     * provider was finally registered: the markers were being computed and thrown away.
     *
     * <p>Called from {@link SimTaleTickSystem}, which is on the world thread by construction.
     */
    public static void captureSnapshot(World world, Store<EntityStore> store) {
        if (world == null || store == null) return;

        long tick = world.getTick();
        String worldName = world.getName();
        long previousTick = snapshotTicks.getOrDefault(worldName, -1L);
        if (!snapshotDirty.get() && previousTick >= 0 && tick - previousTick < SNAPSHOT_INTERVAL_TICKS) return;
        snapshotTicks.put(worldName, tick);
        snapshotDirty.set(false);

        List<NpcMarker> captured = new ArrayList<>();
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityId == null) continue;

            Ref<EntityStore> ref = npc.entityRef;
            if (ref == null || !ref.isValid()) {
                ref = world.getEntityStore().getRefFromUUID(npc.entityId);
                if (ref != null && ref.isValid()) {
                    npc.entityRef = ref;
                }
            }
            if (ref == null || !ref.isValid()) continue;

            /* An NPC away on an expedition is meant to read as gone. She is still standing in the
            world at scale 0.001 because the engine has no invisibility flag, so leaving her on
            the map draws an arrow onto an NPC the player cannot find.
            */
            if (NPCWorkHelper.isAwayOnExpedition(store, ref)) continue;

            /* A carried child (MountedComponent) has her TransformComponent frozen at the exact
            spot she was picked up -- only the client draws her following the carrier, the
            server-side position never moves (same fact ChildCarryHelper's own javadoc already
            relies on). Leaving her on the map would pin a second marker at that stale pickup
            spot forever, standing still while the player who is actually carrying her walks
            away from it -- confusing at best, and a second "person" appearing to have been
            abandoned exactly where she was picked up.
            */
            if (store.getComponent(ref, MountedComponent.getComponentType()) != null) continue;

            TransformComponent transform =
                    store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;

            Map<UUID, RelationshipStatus> statuses = new HashMap<>();
            if (npc.relationships != null) {
                for (Map.Entry<UUID, Relationship> entry : npc.relationships.entrySet()) {
                    if (entry.getValue() != null) {
                        statuses.put(entry.getKey(), entry.getValue().status);
                    }
                }
            }

            captured.add(new NpcMarker(npc.entityId, npc.name,
                    transform.getTransform().clone(), statuses));
        }

        List<Village> capturedVillages = List.copyOf(VillageManager.villages());

        // Capture registered houses
        List<HouseMapMarker> capturedHouses = new ArrayList<>();
        Set<HouseBlockPos> houseBeds = new HashSet<>();
        HouseManager.HouseIndexes houseIndexes = HouseManager.indexes(world);
        for (HouseData house : houseIndexes.housesById.values()) {
            if (house == null) continue;
            HouseBlockPos anchor = house.getAnchor();
            if (anchor == null) continue;

            Set<HouseBlockPos> beds = new HashSet<>();
            if (house.bedPos != null) beds.add(house.bedPos);
            if (house.beds != null) beds.addAll(house.beds);
            houseBeds.addAll(beds);

            List<String> names = new ArrayList<>();
            if (house.owners != null) {
                for (String ownerStr : house.owners) {
                    try {
                        UUID u = UUID.fromString(ownerStr);
                        SimNPCComponent resident = SimTale.findNpc(u);
                        if (resident != null && resident.name != null && !resident.name.isBlank()
                                && !names.contains(resident.name)) {
                            names.add(resident.name);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            /* The owners list is the source of truth for "occupied"; the name list is only what
            can be resolved right now. Previously occupancy was tied to the *loaded* owners, so an
            NPC in an unloaded chunk (or a house whose owner list was rebuilt empty while a
            resident still slept there) painted the house blue as "unoccupied". A resident whose
            bed is in this house also proves occupancy even if the owner list drifted.
            */
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.bedLocation == null) continue;
                HouseBlockPos bed = new HouseBlockPos(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z);
                if (beds.contains(bed) && npc.name != null && !npc.name.isBlank() && !names.contains(npc.name)) {
                    names.add(npc.name);
                }
            }

            boolean occupied = !names.isEmpty() || (house.owners != null && !house.owners.isEmpty());
            String houseId = house.houseId != null ? house.houseId : (anchor.x + "_" + anchor.y + "_" + anchor.z);
            capturedHouses.add(new HouseMapMarker(houseId, List.copyOf(names),
                    houseMarkerPosition(house, anchor), occupied));
        }
        List<HouseMapMarker> capturedHousesSnapshot = List.copyOf(capturedHouses);

        List<WorkPostMapMarker> capturedWorkPosts = new ArrayList<>();
        synchronized (FishingPostRegistry.POSTS) {
            for (FishingPostRegistry.FishingPost post : FishingPostRegistry.POSTS) {
                capturedWorkPosts.add(new WorkPostMapMarker(
                        "fishing:" + post.worldName() + ":" + post.postX() + ":" + post.postY() + ":" + post.postZ(),
                        "fishing", new Vector3d(post.postX() + 0.5, post.postY() + 0.5, post.postZ() + 0.5)));
            }
        }
        synchronized (LumberPostRegistry.POSTS) {
            for (LumberPostRegistry.LumberPost post : LumberPostRegistry.POSTS) {
                capturedWorkPosts.add(new WorkPostMapMarker(
                        "lumber:" + post.worldName() + ":" + post.postX() + ":" + post.postY() + ":" + post.postZ(),
                        "lumber", new Vector3d(post.postX() + 0.5, post.postY() + 0.5, post.postZ() + 0.5)));
            }
        }
        synchronized (FarmPostRegistry.POSTS) {
            for (FarmPostRegistry.FarmPost post : FarmPostRegistry.POSTS) {
                capturedWorkPosts.add(new WorkPostMapMarker(
                        "farm:" + post.worldName() + ":" + post.postX() + ":" + post.postY() + ":" + post.postZ(),
                        "farm", new Vector3d(post.postX() + 0.5, post.postY() + 0.5, post.postZ() + 0.5)));
            }
        }
        List<WorkPostMapMarker> capturedWorkPostsSnapshot = List.copyOf(capturedWorkPosts);

        /* A registered bed that belongs to no house is the "built but not claimed yet" state.
        Without these the map is silent exactly while the player is waiting for an NPC to take the
        room, which reads as recognition being broken. Once claimed, the same bed disappears from
        this list and the house marker (green, with owner names) takes over.
        */
        Set<HouseBlockPos> claimedBeds = new HashSet<>();
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.bedLocation == null) continue;
            claimedBeds.add(new HouseBlockPos(npc.bedLocation.x, npc.bedLocation.y, npc.bedLocation.z));
        }

        List<HouseBlockPos> unclaimed = new ArrayList<>();
        for (SimBedData.BedPos bed : BedRegistry.registeredBeds(world)) {
                HouseBlockPos pos = new HouseBlockPos(bed.x, bed.y, bed.z);
                if (houseBeds.contains(pos) || claimedBeds.contains(pos)) continue;
                unclaimed.add(pos);
            }
        WorldMarkerSnapshot capturedSnapshot = new WorldMarkerSnapshot(
                List.copyOf(captured), capturedVillages, capturedHousesSnapshot,
                capturedWorkPostsSnapshot, List.copyOf(unclaimed));
        Map<String, WorldMarkerSnapshot> updatedSnapshots = new HashMap<>(snapshots);
        updatedSnapshots.put(world.getName(), capturedSnapshot);
        snapshots = Map.copyOf(updatedSnapshots);
    }

    /**
     * Marker position for a house: the centre of its interior, not the first bed.
     *
     * <p>The old position was {@code HouseData.getAnchor()}, which is the original bed. In a
     * one-house village that is also exactly the village centre, so the Home and Spawn markers
     * landed on the same pixel and the house looked missing. It also meant that adding a wing with
     * a new bed left the marker sitting on the old bed, giving no visual hint that the extension
     * had been recognised. The interior centre follows the actual building instead.
     */
    private static Vector3d houseMarkerPosition(HouseData house, HouseBlockPos fallback) {
        Set<HouseBlockPos> interior = house.interior;
        if (interior != null && !interior.isEmpty()) {
            double sumX = 0, sumY = 0, sumZ = 0;
            for (HouseBlockPos pos : interior) {
                sumX += pos.x;
                sumY += pos.y;
                sumZ += pos.z;
            }
            int count = interior.size();
            return new Vector3d(sumX / count + 0.5, sumY / count + 0.5, sumZ / count + 0.5);
        }
        return new Vector3d(fallback.x + 0.5, fallback.y + 0.5, fallback.z + 0.5);
    }

    @Override
    @SuppressWarnings("removal")
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        PlayerRef playerRef = player.getPlayerRef();
        UUID viewerId = (playerRef != null) ? playerRef.getUuid() : player.getUuid();
        if (viewerId == null) return;

        WorldMarkerSnapshot worldSnapshot = snapshots.get(world.getName());
        if (worldSnapshot == null) return;
        List<NpcMarker> current = worldSnapshot.npcs();
        int emitted = 0;
        int outOfRange = 0;

        for (NpcMarker npc : current) {
            try {
                TintComponent tint = new TintComponent();
                tint.color = colorFor(npc, viewerId);

                Transform markerTransform = npc.transform();
                if (!collector.isInViewDistance(markerTransform.getPosition())) {
                    outOfRange++;
                }

                /* Position and icon go through the constructor, not through with* calls: the
                builder keeps id, image and transform as fixed state and only the optional parts
                are chainable.

                addIgnoreViewDistance, not add: `add` silently drops anything the collector
                considers out of view, and villagers are exactly the thing you want to find on a
                map without already being next to them. It is also what the vanilla
                OtherPlayersMarkerProvider uses, for the same reason — POIMarkerProvider is the
                one that opts into the distance filter.
                */
                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder(PROVIDER_ID + ":" + npc.id(), MARKER_IMAGE, markerTransform)
                                .withCustomName(npc.name())
                                .withComponent(new PlayerMarkerComponent(npc.id()))
                                .withComponent(tint)
                                .build());
                emitted++;
            } catch (RuntimeException e) {
                // One malformed NPC must not cost every other marker on the map.
                LOGGER.debug("[MAP] Failed to mark '{}': {}", npc.name(), e.toString());
            }
        }

        // Emit village center markers
        for (Village village : worldSnapshot.villages()) {
            try {
                Transform centerTransform = new Transform(new Vector3d(village.centerX(), 65.0, village.centerZ()));
                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder(PROVIDER_ID + ":village:" + (int) village.centerX() + ":" + (int) village.centerZ(),
                                MARKER_IMAGE_VILLAGE, centerTransform)
                                .withName(Message.translation("general.map.village")
                                        .param("houses", village.houses())
                                        .param("radius", (int) village.radius()))
                                .withComponent(new TintComponent(COLOR_VILLAGE_CENTER))
                                .build());
                emitted++;
            } catch (RuntimeException e) {
                LOGGER.debug("[MAP] Failed to mark village: {}", e.toString());
            }
        }

        // Emit house markers
        for (HouseMapMarker house : worldSnapshot.houses()) {
            try {
                Transform houseTransform = new Transform(house.position());
                TintComponent tint = new TintComponent(house.occupied() ? COLOR_HOUSE_OCCUPIED : COLOR_HOUSE_EMPTY);
                Message houseName;
                if (!house.occupied()) {
                    houseName = Message.translation("general.map.house.empty");
                } else if (house.ownerNames().isEmpty()) {
                    /* Owners exist but none of them is loaded right now. Still occupied — the
                    old code painted this blue because it only counted resolvable names.
                    */
                    houseName = Message.translation("general.map.house.occupied_unknown");
                } else {
                    houseName = Message.translation("general.map.house.occupied")
                            .param("owners", String.join(", ", house.ownerNames()));
                }
                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder(PROVIDER_ID + ":house:" + house.id(),
                                MARKER_IMAGE_HOUSE, houseTransform)
                                .withName(houseName)
                                .withComponent(tint)
                                .build());
                emitted++;
            } catch (RuntimeException e) {
                LOGGER.debug("[MAP] Failed to mark house '{}': {}", house.id(), e.toString());
            }
        }

        // Emit recognized profession work posts: fishing traps, lumbermills and scarecrows.
        for (WorkPostMapMarker post : worldSnapshot.workPosts()) {
            try {
                Color tint;
                Message name;
                switch (post.type()) {
                    case "fishing" -> {
                        tint = COLOR_FISHING_POST;
                        name = Message.translation("general.map.workpost.fishing");
                    }
                    case "lumber" -> {
                        tint = COLOR_LUMBER_POST;
                        name = Message.translation("general.map.workpost.lumber");
                    }
                    default -> {
                        tint = COLOR_FARM_POST;
                        name = Message.translation("general.map.workpost.farm");
                    }
                }
                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder(PROVIDER_ID + ":workpost:" + post.id(), MARKER_IMAGE_WORK,
                                new Transform(post.position()))
                                .withName(name)
                                .withComponent(new TintComponent(tint))
                                .build());
                emitted++;
            } catch (RuntimeException e) {
                LOGGER.debug("[MAP] Failed to mark work post '{}': {}", post.id(), e.toString());
            }
        }

        // Emit beds that are registered but not part of any house yet
        for (HouseBlockPos bed : worldSnapshot.unclaimedBeds()) {
            try {
                Transform bedTransform = new Transform(new Vector3d(bed.x + 0.5, bed.y + 0.5, bed.z + 0.5));
                collector.addIgnoreViewDistance(
                        new MapMarkerBuilder(PROVIDER_ID + ":bed:" + bed.x + ":" + bed.y + ":" + bed.z,
                                MARKER_IMAGE_HOUSE, bedTransform)
                                .withName(Message.translation("general.map.bed.unclaimed"))
                                .withComponent(new TintComponent(COLOR_BED_UNCLAIMED))
                                .build());
                emitted++;
            } catch (RuntimeException e) {
                LOGGER.debug("[MAP] Failed to mark unclaimed bed at {}: {}", bed, e.toString());
            }
        }

        /* Separates "we are not producing markers" from "we are producing them and the client is
        not drawing them" — the two looked identical through three rounds of guessing while the
        markers were broken. Now that they work it is pure noise on a provider that runs every
        frame, so it is down at debug with the rest.
        */
        if (LOG_PASSES.incrementAndGet() % 100 == 1) {
            LOGGER.debug("[MAP] {} NPC(s) in snapshot, {} marker(s) emitted, {} outside view distance",
                    current.size(), emitted, outOfRange);
        }
    }

    /** Counts update passes so the diagnostic above logs once in a while, not every frame. */
    private static final AtomicLong LOG_PASSES = new AtomicLong();

    /**
     * Marker colour, from this NPC's relationship with the player currently looking at the map.
     *
     * <p>Per-viewer on purpose: the same NPC is a friend to one player and an enemy to another, and
     * {@code update} is already called once per player, so the map costs nothing extra to
     * personalise.
     */
    private Color colorFor(NpcMarker npc, UUID viewerId) {
        if (viewerId == null) return COLOR_STRANGER;

        RelationshipStatus status = npc.statuses().get(viewerId);
        if (status == null) return COLOR_STRANGER;

        return switch (status) {
            case MARRIED, ENGAGED, PARTNER, DATING, CRUSH -> COLOR_ROMANCE;
            case ENEMIES -> COLOR_ENEMY;
            case BEST_FRIEND, GOOD_FRIEND -> COLOR_CLOSE;
            case FRIEND -> COLOR_FRIEND;
            case UNKNOWN, STRANGER, ACQUAINTANCE -> COLOR_STRANGER;
        };
    }
}
