package com.cookieukw.SimTale.animals;

import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;

import java.util.Random;

/**
 * Manages spawning and ambient interactions for the SimTale White Cat entity.
 */
public class WhiteCatManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String[] CAT_NAMES = {
        "Mingau", "Snowball", "Pipoca", "Nuvem", "Algodão",
        "Floquinho", "Biscoito", "Mimi", "Pérola", "Chantilly"
    };
    private static final Random RANDOM = new Random();

    /**
     * Spawns a White Cat entity at the specified world position.
     */
    public static Ref<EntityStore> spawnWhiteCat(Store<EntityStore> store, Vector3d position, float yaw) {
        if (store == null || position == null) return null;

        try {
            Rotation3f rotation = new Rotation3f();
            rotation.setYaw(yaw);

            // Spawn using passive critter role
            Pair<Ref<EntityStore>, ?> result = NPCPlugin.get().spawnNPC(
                    store,
                    "SimTale_WhiteCat",
                    null,
                    position,
                    rotation
            );

            if (result == null || result.left() == null) {
                LOGGER.atWarning().log("SimTale: Falha ao invocar entidade SimTale_WhiteCat via NPCPlugin");
                return null;
            }

            Ref<EntityStore> catRef = result.left();

            // Ensure model is correctly applied
            SimNPCFactory.applyModel(store, catRef, "SimTale_WhiteCat", 1.0f, null);

            // Assign a cute random nameplate
            String name = CAT_NAMES[RANDOM.nextInt(CAT_NAMES.length)];
            SimNPCFactory.refreshNameplate(store, catRef, name);

            LOGGER.atInfo().log("SimTale: Gatinho Branco '" + name + "' invocado em " + position);
            return catRef;
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Erro ao instanciar Gatinho Branco: " + e.getMessage());
            return null;
        }
    }

    /**
     * Spawns a cat from a player holding the SimTale_WhiteCat item.
     */
    public static boolean spawnFromHeldItem(Store<EntityStore> store, Ref<EntityStore> playerRef,
                                            PlayerRef playerRefComp, ItemStack heldItem, Vector3d spawnPos) {
        if (store == null || playerRef == null || heldItem == null || spawnPos == null) return false;

        Ref<EntityStore> cat = spawnWhiteCat(store, spawnPos, 0f);
        if (cat == null) return false;

        // Consume 1 item from hand
        try {
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(
                    playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
            combinedInventory.removeItemStack(new ItemStack(heldItem.getItemId(), 1));
        } catch (Exception ignored) {
        }

        if (playerRefComp != null) {
            playerRefComp.sendMessage(Message.raw("§a* Miau! * Um lindo gatinho branco apareceu!"));
        }
        return true;
    }
}
