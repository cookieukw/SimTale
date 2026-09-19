package com.cookieukw.SimTale.vehicles;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.Ref;
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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Handles spawning, player mounting, and passenger logic for the 1930s Calhambeque vintage car.
 */
public class CalhambequeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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
            store.putComponent(carRef, SimTale.CALHAMBEQUE_COMPONENT_TYPE, carComp);

            // Apply Calhambeque 3D Model scaled to realistic 1930s car dimensions
            SimNPCFactory.applyModel(store, carRef, "SimTale_Calhambeque", 2.2f, null);

            // Ensure Interactable so right-click is detected
            store.ensureComponent(carRef, Interactable.getComponentType());

            // Clear person nameplate
            SimNPCFactory.refreshNameplate(store, carRef, "Calhambeque 1930s");

            LOGGER.atInfo().log("SimTale: Calhambeque Vintage dos Anos 1930 instanciado em " + position);
            return carRef;
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Erro ao instanciar Calhambeque: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles right-click interaction on the car: mounts the player as driver or passenger.
     */
    public static void handleInteract(Store<EntityStore> store, Ref<EntityStore> carRef,
                                       CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (store == null || carRef == null || car == null || playerRef == null || playerRefComp == null) return;

        UUID playerUuid = playerRefComp.getUuid();

        // If player is already the driver, dismount
        if (playerUuid.equals(car.driverUuid)) {
            dismountDriver(store, carRef, car, playerRef, playerRefComp);
            return;
        }

        // If car has no driver, mount as driver
        if (!car.hasDriver()) {
            mountDriver(store, carRef, car, playerRef, playerRefComp);
            return;
        }

        // Already has driver: mount as passenger if empty
        if (car.passengerUuid == null) {
            mountPassenger(store, carRef, car, playerRef, playerRefComp);
            return;
        }

        playerRefComp.sendMessage(Message.raw("§e[Calhambeque] §cO veículo já está com lotação máxima!"));
    }

    /**
     * Mounts the player into the driver seat (BenchCushion left side).
     */
    public static void mountDriver(Store<EntityStore> store, Ref<EntityStore> carRef,
                                    CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        NetworkId carNetId = store.getComponent(carRef, NetworkId.getComponentType());
        if (carNetId == null) return;

        int netId = carNetId.getId();
        car.driverUuid = playerRefComp.getUuid();
        car.engineRunning = true;

        // Send MountNPC packet to client to attach player camera & model
        MountNPC mountPacket = new MountNPC(
                car.driverSeatOffset.x,
                car.driverSeatOffset.y,
                car.driverSeatOffset.z,
                netId
        );
        if (playerRefComp.getPacketHandler() != null) {
            playerRefComp.getPacketHandler().write(mountPacket);
        }

        playerRefComp.sendMessage(Message.raw("§6[Calhambeque 1930s] §aMotor ligado! Use §fW/S §apara acelerar e dar ré, e §fShift §apara descer."));
    }

    /**
     * Mounts the player into the passenger seat (BenchCushion right side).
     */
    public static void mountPassenger(Store<EntityStore> store, Ref<EntityStore> carRef,
                                       CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        NetworkId carNetId = store.getComponent(carRef, NetworkId.getComponentType());
        if (carNetId == null) return;

        int netId = carNetId.getId();
        car.passengerUuid = playerRefComp.getUuid();

        MountNPC mountPacket = new MountNPC(
                car.passengerSeatOffset.x,
                car.passengerSeatOffset.y,
                car.passengerSeatOffset.z,
                netId
        );
        if (playerRefComp.getPacketHandler() != null) {
            playerRefComp.getPacketHandler().write(mountPacket);
        }

        playerRefComp.sendMessage(Message.raw("§6[Calhambeque 1930s] §aVocê sentou no banco do passageiro!"));
    }

    /**
     * Dismounts the driver safely to the left of the car.
     */
    public static void dismountDriver(Store<EntityStore> store, Ref<EntityStore> carRef,
                                       CalhambequeComponent car, Ref<EntityStore> playerRef, PlayerRef playerRefComp) {
        if (car.driverUuid == null) return;

        int carNetId = 0;
        NetworkId netId = store.getComponent(carRef, NetworkId.getComponentType());
        if (netId != null) carNetId = netId.getId();

        car.driverUuid = null;
        car.speed = 0f;

        if (playerRefComp != null && playerRefComp.getPacketHandler() != null) {
            playerRefComp.getPacketHandler().write(new DismountNPC(carNetId));
        }

        // Place player safely to the left side
        TransformComponent carTransform = store.getComponent(carRef, TransformComponent.getComponentType());
        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (carTransform != null && playerTransform != null) {
            Vector3d carPos = carTransform.getPosition();
            float yaw = carTransform.getRotation().yaw();
            double sideX = -Math.cos(yaw) * 2.0;
            double sideZ = Math.sin(yaw) * 2.0;
            playerTransform.setPosition(new Vector3d(carPos.x + sideX, carPos.y + 0.1, carPos.z + sideZ));
        }

        if (playerRefComp != null) {
            playerRefComp.sendMessage(Message.raw("§6[Calhambeque 1930s] §7Você desceu do veículo."));
        }
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
