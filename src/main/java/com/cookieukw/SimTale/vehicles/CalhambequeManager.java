package com.cookieukw.SimTale.vehicles;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles spawning, player mounting, and passenger logic for the 1930s Calhambeque vintage car.
 *
 * <p><b>How riding works.</b> The car is an ordinary NPC moved by {@link CalhambequePhysicsSystem}.
 * Getting in sends the client a {@code MountNPC} with the seat as anchor, which is what attaches
 * the player to the car on screen. The engine's own NPC-mount bookkeeping
 * ({@code NPCMountComponent}, {@code Player.mountEntityId}) is deliberately not used: it swaps the
 * NPC to {@code Empty_Role} and lets the client move it, which would fight the car physics.
 *
 * <p><b>Getting out.</b> When the player jumps or presses the dismount key while mounted, the
 * client sends {@code DismountNPC}. The engine's handler ({@code MountGamePacketHandler}) ignores
 * it, because {@code Player.mountEntityId} is 0 for our cars, so nothing on the server ever knew
 * the player had left: the physics kept pulling them back into the seat. {@link SimTale} now
 * watches that packet and calls {@link #onClientDismount}. Crouching (Shift), right-click/F on the
 * car again and {@code /simtale carexit} still work too.
 */
public class CalhambequeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Who is sitting in which car (driver or passenger), for the dismount packet. */
    private static final Map<UUID, Ref<EntityStore>> RIDING = new ConcurrentHashMap<>();

    /** The item a car is placed from (and given back by {@link #pickUp}). */
    public static final String ITEM_ID = "SimTale_Calhambeque";

    private static boolean isCrouching(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        MovementStatesComponent msc = store.getComponent(playerRef, MovementStatesComponent.getComponentType());
        return msc != null && msc.getMovementStates() != null && msc.getMovementStates().crouching;
    }

    /**
     * Spawns a new Calhambeque vintage car in the world.
     */
    public static Ref<EntityStore> spawnCalhambeque(Store<EntityStore> store, Vector3d position, float yaw) {
        if (store == null || position == null) return null;

        try {
            Rotation3f rotation = new Rotation3f();
            rotation.setYaw(yaw);

            // Spawn base entity with dedicated vehicle role and interaction instructions
            Pair<Ref<EntityStore>, ?> result = NPCPlugin.get().spawnNPC(
                    store,
                    "SimTale_Calhambeque",
                    null,
                    position,
                    rotation
            );

            if (result == null || result.left() == null) {
                LOGGER.atWarning().log("SimTale: Falha ao instanciar entidade para o Calhambeque");
                return null;
            }

            Ref<EntityStore> carRef = result.left();

            // Attach Calhambeque component
            CalhambequeComponent carComp = new CalhambequeComponent();
            carComp.requestedScale = CalhambequeGeometry.scale();
            carComp.appliedTuneVersion = CalhambequeGeometry.version();
            store.putComponent(carRef, SimTale.CALHAMBEQUE_COMPONENT_TYPE, carComp);

            // Apply the 3D model at the current car scale (hitbox and eye height scale with it)
            SimNPCFactory.applyModel(store, carRef, CalhambequeGeometry.MODEL_ID, CalhambequeGeometry.scale(), null);

            // Ensure Interactable so right-click is detected
            store.ensureComponent(carRef, Interactable.getComponentType());

            // No name tag over the car (the role gives NPCs one)
            hideNameplate(store, carRef);

            LOGGER.atInfo().log("SimTale: Calhambeque Vintage dos Anos 1930 instanciado em " + position);
            return carRef;
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Erro ao instanciar Calhambeque: " + e.getMessage());
            return null;
        }
    }

    /** The car's current model scale (what the player sees), or the tuned default. */
    public static float carScale(Store<EntityStore> store, Ref<EntityStore> carRef) {
        if (store != null && carRef != null && carRef.isValid()) {
            ModelComponent model = store.getComponent(carRef, ModelComponent.getComponentType());
            if (model != null && model.getModel() != null && model.getModel().getScale() > 0f) {
                return model.getModel().getScale();
            }
        }
        return CalhambequeGeometry.scale();
    }

    /**
     * Handles right-click interaction on the car: mounts the player as driver or passenger.
     */
    public static void handleInteract(Store<EntityStore> store, Ref<EntityStore> carRef,
                                      CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (store == null || carRef == null || car == null || playerRef == null || playerRefComp == null) return;

        UUID playerUuid = playerRefComp.getUuid();

        // Crouch + interact on a car you are not in: put it back in the inventory
        if (!playerUuid.equals(car.driverUuid) && !playerUuid.equals(car.passengerUuid)
                && !RIDING.containsKey(playerUuid) && isCrouching(store, playerRef)) {
            pickUp(store, carRef, car, playerRef, playerRefComp);
            return;
        }

        // Already inside: the same click gets you out
        if (playerUuid.equals(car.driverUuid)) {
            dismountDriver(store, carRef, car, playerRef, playerRefComp);
            return;
        }
        if (playerUuid.equals(car.passengerUuid)) {
            dismountPassenger(store, carRef, car, playerRef, playerRefComp);
            return;
        }

        // Sitting in another car: get out of that one first
        Ref<EntityStore> other = RIDING.get(playerUuid);
        if (other != null && other.isValid() && !other.equals(carRef)) {
            playerRefComp.sendMessage(Message.raw("§e[Calhambeque] §cDesça do outro carro primeiro."));
            return;
        }

        // If car has no driver, mount as driver
        if (!car.hasDriver()) {
            mountDriver(store, carRef, car, playerRefComp);
            return;
        }

        // Already has driver: mount as passenger if empty
        if (car.passengerUuid == null) {
            mountPassenger(store, carRef, car, playerRefComp);
            return;
        }

        playerRefComp.sendMessage(Message.raw("§e[Calhambeque] §cO veículo já está com lotação máxima!"));
    }

    /**
     * Mounts the player into the driver seat (left side, behind the steering wheel).
     */
    public static void mountDriver(Store<EntityStore> store, Ref<EntityStore> carRef,
                                   CalhambequeComponent car, PlayerRef playerRefComp) {
        int netId = networkId(store, carRef);
        if (netId == 0) return;

        car.driverUuid = playerRefComp.getUuid();
        car.engineRunning = true;
        RIDING.put(car.driverUuid, carRef);

        sendMount(playerRefComp, CalhambequeGeometry.driverSeat(carScale(store, carRef)), netId);

        playerRefComp.sendMessage(Message.raw("§6[Calhambeque 1930s] §aMotor ligado! Use §fW/S §apara acelerar e dar ré."
                + " Para descer: §fpule§a, §fShift §aou §f/simtale carexit§a."));
    }

    /**
     * Mounts the player into the passenger seat (right side of the bench).
     */
    public static void mountPassenger(Store<EntityStore> store, Ref<EntityStore> carRef,
                                      CalhambequeComponent car, PlayerRef playerRefComp) {
        int netId = networkId(store, carRef);
        if (netId == 0) return;

        car.passengerUuid = playerRefComp.getUuid();
        RIDING.put(car.passengerUuid, carRef);

        sendMount(playerRefComp, CalhambequeGeometry.passengerSeat(carScale(store, carRef)), netId);

        playerRefComp.sendMessage(Message.raw("§6[Calhambeque 1930s] §aVocê sentou no banco do passageiro!"
                + " Para descer: §fpule§a, §fShift §aou §f/simtale carexit§a."));
    }

    /**
     * Re-sends the seat anchors to whoever is in the car (after /simtale carscale or carseat).
     * Takes the scale explicitly: right after a resize the model component still holds the old one.
     */
    public static void refreshRiders(Store<EntityStore> store, Ref<EntityStore> carRef, CalhambequeComponent car,
                                     float scale) {
        if (car.driverUuid == null && car.passengerUuid == null) return;
        int netId = networkId(store, carRef);
        if (netId == 0) return;
        PlayerRef driver = playerRefOf(store, car.driverUuid);
        if (driver != null) sendMount(driver, CalhambequeGeometry.driverSeat(scale), netId);
        PlayerRef passenger = playerRefOf(store, car.passengerUuid);
        if (passenger != null) sendMount(passenger, CalhambequeGeometry.passengerSeat(scale), netId);
    }

    /**
     * Takes the driver out to the left (driver's) side of the car.
     * Safe to call from inside a system: the teleport is queued on the world thread.
     */
    public static void dismountDriver(Store<EntityStore> store, Ref<EntityStore> carRef,
                                      CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (car.driverUuid == null) return;
        UUID uuid = car.driverUuid;
        car.driverUuid = null;
        car.speed = 0f;
        leave(store, carRef, uuid, playerRef, playerRefComp, +1, "§6[Calhambeque 1930s] §7Você desceu do veículo.");
    }

    /** Takes the passenger out to the right-hand side. Safe to call from inside a system. */
    public static void dismountPassenger(Store<EntityStore> store, Ref<EntityStore> carRef,
                                         CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (car.passengerUuid == null) return;
        UUID uuid = car.passengerUuid;
        car.passengerUuid = null;
        leave(store, carRef, uuid, playerRef, playerRefComp, -1, "§6[Calhambeque 1930s] §7Você desceu do banco do passageiro.");
    }

    /**
     * The client sent {@code DismountNPC} (jump / dismount key). Runs on a network thread, so the
     * work is handed to the player's world thread, the same way the engine's own handler does it.
     */
    public static void onClientDismount(PlayerRef playerRefComp) {
        if (playerRefComp == null) return;
        UUID uuid = playerRefComp.getUuid();
        if (uuid == null || !RIDING.containsKey(uuid)) return;
        Ref<EntityStore> playerRef = playerRefComp.getReference();
        if (playerRef == null || !playerRef.isValid()) {
            RIDING.remove(uuid);
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> exitCar(store, playerRef, playerRefComp));
    }

    /**
     * Gets the player out of whatever car they are in. Must run on the world thread outside a
     * system (commands, world.execute). Returns false when they were not in a car.
     */
    public static boolean exitCar(Store<EntityStore> store, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (playerRefComp == null) return false;
        UUID uuid = playerRefComp.getUuid();
        Ref<EntityStore> carRef = RIDING.get(uuid);
        if (carRef == null || !carRef.isValid() || !playerRef.isValid()) {
            RIDING.remove(uuid);
            return false;
        }
        CalhambequeComponent car = store.getComponent(carRef, SimTale.CALHAMBEQUE_COMPONENT_TYPE);
        if (car == null) {
            RIDING.remove(uuid);
            return false;
        }
        if (uuid.equals(car.driverUuid)) {
            dismountDriver(store, carRef, car, playerRef, playerRefComp);
            return true;
        }
        if (uuid.equals(car.passengerUuid)) {
            dismountPassenger(store, carRef, car, playerRef, playerRefComp);
            return true;
        }
        RIDING.remove(uuid);
        return false;
    }

    /** Forget a rider whose entity went away (logout, world change). */
    public static void forgetRider(UUID uuid) {
        if (uuid != null) RIDING.remove(uuid);
    }

    /**
     * Shared exit: tell the client, then move the player next to the car on the chosen side
     * (-1 left/driver, +1 right/passenger), falling back to the other side, behind the car and
     * finally the roof when blocks are in the way.
     */
    private static void leave(Store<EntityStore> store, Ref<EntityStore> carRef, UUID uuid,
                              Ref<EntityStore> playerRef, PlayerRef playerRefComp, int side, String message) {
        RIDING.remove(uuid);
        int carNetId = networkId(store, carRef);
        if (playerRefComp != null) {
            playerRefComp.getPacketHandler().write(new DismountNPC(carNetId));
        }

        TransformComponent carTransform = store.getComponent(carRef, TransformComponent.getComponentType());
        if (carTransform != null && playerRef != null && playerRef.isValid()) {
            World world = store.getExternalData().getWorld();
            Vector3d exit = exitSpot(world, carTransform.getPosition(), carTransform.getRotation().yaw(),
                    carScale(store, carRef), side);
            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
            Rotation3f look = playerTransform != null ? new Rotation3f(playerTransform.getRotation()) : new Rotation3f();
            /* A Teleport, not TransformComponent.setPosition: player movement is client-side, so a
            bare server-side position change is overwritten by the next movement packet and the
            player stays on the seat. Queued because this can run inside a system (crouch check in
            the physics tick, F-key interaction) where adding a component is not allowed. */
            world.execute(() -> {
                if (playerRef.isValid()) {
                    store.putComponent(playerRef, Teleport.getComponentType(), Teleport.createForPlayer(exit, look));
                }
            });
        }

        if (playerRefComp != null) {
            playerRefComp.sendMessage(Message.raw(message));
        }
    }

    /** A free spot next to the car: two blocks of air above a floor-level position. */
    static Vector3d exitSpot(World world, Vector3d carPos, float yaw, float scale, int side) {
        double out = CalhambequeGeometry.halfWidth(scale) + 0.8;
        double back = CalhambequeGeometry.blocks(-CalhambequeGeometry.seatBack(), scale);
        double behind = -(CalhambequeGeometry.halfLength(scale) + 0.8);
        double[][] candidates = {
                {side * out, back},
                {-side * out, back},
                {0, behind},
        };
        for (double[] c : candidates) {
            double x = CalhambequeGeometry.toWorldX(carPos.x, yaw, c[0], c[1]);
            double z = CalhambequeGeometry.toWorldZ(carPos.z, yaw, c[0], c[1]);
            int bx = (int) Math.floor(x), by = (int) Math.floor(carPos.y + 0.1), bz = (int) Math.floor(z);
            if (!CalhambequePhysicsSystem.isObstacle(world, bx, by, bz)
                    && !CalhambequePhysicsSystem.isObstacle(world, bx, by + 1, bz)) {
                return new Vector3d(x, carPos.y + 0.1, z);
            }
        }
        // Boxed in: stand on the car
        return new Vector3d(carPos.x, carPos.y + CalhambequeGeometry.height(scale) + 0.1, carPos.z);
    }

    private static void sendMount(PlayerRef playerRefComp, Vector3f seat, int carNetId) {
        Vector3f anchor = CalhambequeGeometry.mountAnchor(seat);
        playerRefComp.getPacketHandler().write(new MountNPC(anchor.x, anchor.y, anchor.z, carNetId));
    }

    /** Whether this player is sitting in a car (the plumbob is hidden meanwhile). */
    public static boolean isRiding(UUID playerUuid) {
        return playerUuid != null && RIDING.containsKey(playerUuid);
    }

    /**
     * Removes the car's name tag. NPCs get one from their role (NameTranslationKey) and the spawn
     * used to set "Calhambeque 1930s" on top. Deferred: this also runs from the physics tick.
     */
    public static void hideNameplate(Store<EntityStore> store, Ref<EntityStore> carRef) {
        if (store == null || carRef == null || !carRef.isValid()) return;
        if (store.getComponent(carRef, Nameplate.getComponentType()) == null
                && store.getComponent(carRef, PersistentDisplayName.getComponentType()) == null) return;
        World world = store.getExternalData().getWorld();
        Runnable task = () -> {
            if (!carRef.isValid()) return;
            store.removeComponentIfExists(carRef, Nameplate.getComponentType());
            store.removeComponentIfExists(carRef, PersistentDisplayName.getComponentType());
        };
        if (world != null) world.execute(task); else task.run();
    }

    /**
     * Puts an empty car back in the player's inventory as the item it was placed from.
     * Crouch + interact, or /simtale carremove.
     *
     * @return false (with a message) when someone is inside or the inventory is full
     */
    public static boolean pickUp(Store<EntityStore> store, Ref<EntityStore> carRef, CalhambequeComponent car,
                                 Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (store == null || carRef == null || !carRef.isValid() || car == null || playerRef == null) return false;
        if (car.driverUuid != null || car.passengerUuid != null) {
            if (playerRefComp != null) {
                playerRefComp.sendMessage(Message.raw("§e[Calhambeque] §cTem alguém dentro do carro."));
            }
            return false;
        }
        ItemStack item = new ItemStack(ITEM_ID, 1);
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, playerRef, InventoryComponent.HOTBAR_FIRST);
        if (inventory == null || !inventory.canAddItemStack(item)) {
            if (playerRefComp != null) {
                playerRefComp.sendMessage(Message.raw("§e[Calhambeque] §cInventário cheio."));
            }
            return false;
        }
        inventory.addItemStack(item);
        World world = store.getExternalData().getWorld();
        Runnable remove = () -> {
            if (carRef.isValid()) store.removeEntity(carRef, RemoveReason.REMOVE);
        };
        if (world != null) world.execute(remove); else remove.run();
        if (playerRefComp != null) {
            playerRefComp.sendMessage(Message.raw("§6[Calhambeque] §7Carro guardado no inventário."));
        }
        return true;
    }

    /**
     * The nearest car within {@code radius} blocks of a point, or null.
     */
    public static Ref<EntityStore> nearestCar(Store<EntityStore> store, Vector3d at, double radius) {
        final Ref<EntityStore>[] best = new Ref[1];
        final double[] bestSq = {radius * radius};
        store.forEachChunk(SimTale.CALHAMBEQUE_COMPONENT_TYPE, (chunk, _) -> {
            for (int i = 0; i < chunk.size(); i++) {
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                if (t == null) continue;
                double d = t.getPosition().distanceSquared(at);
                if (d < bestSq[0]) {
                    bestSq[0] = d;
                    best[0] = chunk.getReferenceTo(i);
                }
            }
        });
        return best[0];
    }

    private static int networkId(Store<EntityStore> store, Ref<EntityStore> carRef) {
        NetworkId netId = store.getComponent(carRef, NetworkId.getComponentType());
        return netId != null ? netId.getId() : 0;
    }

    private static PlayerRef playerRefOf(Store<EntityStore> store, UUID uuid) {
        if (uuid == null) return null;
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        if (ref == null || !ref.isValid()) return null;
        return store.getComponent(ref, Universe.get().getPlayerRefComponentType());
    }

    /**
     * Spawns a Calhambeque from the held item.
     */
    public static boolean spawnFromHeldItem(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                            PlayerRef playerRefComp, ItemStack heldItem, Vector3d spawnPos) {
        if (store == null || playerRef == null || heldItem == null || spawnPos == null) return false;

        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        float yaw = playerTransform != null ? playerTransform.getRotation().yaw() : 0f;

        Ref<EntityStore> car = spawnCalhambeque(store, spawnPos, yaw);
        if (car == null) return false;

        // Consume 1 item
        try {
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(
                    playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
            combinedInventory.removeItemStack(new ItemStack(heldItem.getItemId(), 1));
        } catch (Exception ignored) {
        }

        if (playerRefComp != null) {
            playerRefComp.sendMessage(Message.raw("§6* Vrum! * Calhambeque Vintage 1930s colocado no mundo!"));
        }
        return true;
    }
}
