package com.cookieukw.SimTale.systems;

import com.cookie.runecore.api.CastContext;
import com.cookie.runecore.api.RuneEffect;
import com.cookieukw.SimTale.config.SimTaleConfig;
import com.cookieukw.SimTale.config.SimTaleConfigManager;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.SimLog;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants a regeneration status effect to players standing inside a village.
 *
 * <p>The effect is applied through RuneCore's {@link RuneEffect#withNativeEffect(String)} path, so
 * it uses the engine's own {@code EffectControllerComponent} and the vanilla Health Regeneration
 * status icon rather than a parallel SimTale buff system.
 *
 * <p>Villages are derived from houses, so the area follows the live village boundary. The effect is
 * refreshed while the player stays inside and is allowed to expire a few seconds after leaving.
 */
public final class VillageRegenSystem extends EntityTickingSystem<EntityStore> {

    private static final SimLog LOGGER = SimLog.forClass(VillageRegenSystem.class);

    /**
     * Vanilla effect asset (Server/Entity/Effects/Food/Regen/Food_Health_Regen_Medium.json).
     * Medium is deliberately chosen: the small one is barely noticeable, while the large/potion
     * variants are too strong for a permanent safe-zone buff.
     */
    private static final String EFFECT_ID = "Food_Health_Regen_Medium";

    /** Effect lifetime granted by each application; refreshed well before it expires. */
    private static final int EFFECT_DURATION_TICKS = 120; // 6 s
    private static final int REFRESH_INTERVAL_TICKS = 40;  // 2 s

    private static final RuneEffect VILLAGE_REGEN =
            new RuneEffect("simtale:village_regen", EFFECT_DURATION_TICKS).withNativeEffect(EFFECT_ID);

    /** Last tick each player received the effect, and whether they were inside last tick. */
    private static final Map<UUID, Long> LAST_APPLIED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_INSIDE = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRefComponent = player.getPlayerRef();
        if (playerRefComponent == null) return;

        SimTaleConfig config = SimTaleConfigManager.getConfig();
        if (config == null || !config.villageRegenEnabled) return;

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d pos = transform.getPosition();
        VillageManager.Village village = VillageManager.nearest(pos.x, pos.z);
        boolean insideVillage = village != null && village.contains(pos.x, pos.z);

        /* A house can sit just outside the computed village radius (a large wing, a second
        building added before it joined the village). Standing inside a registered house is still
        "inside the village" for this buff, so use the house interior index as a second check.
        */
        HouseBlockPos blockPos = new HouseBlockPos(
                (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
        boolean inside = insideVillage || HouseManager.BLOCK_TO_HOUSE_ID.containsKey(blockPos);

        UUID playerId = playerRefComponent.getUuid();
        if (playerId == null) return;

        Boolean wasInside = WAS_INSIDE.put(playerId, inside);
        if (!inside) {
            /* Let the current effect run out naturally; removing it immediately would also remove
            a regeneration effect the player might have received from food or a potion.
            */
            LAST_APPLIED.remove(playerId);
            return;
        }

        World world = store.getExternalData().getWorld();
        long tick = world.getTick();
        Long lastApplied = LAST_APPLIED.get(playerId);

        boolean isEntry = wasInside == null || !wasInside;
        if (!isEntry && lastApplied != null && tick - lastApplied < REFRESH_INTERVAL_TICKS) {
            return;
        }

        Ref<EntityStore> playerEntity = chunk.getReferenceTo(index);
        if (playerEntity == null || !playerEntity.isValid()) return;

        VILLAGE_REGEN.execute(new CastContext(playerRefComponent, playerEntity, world, 1.0));
        LAST_APPLIED.put(playerId, tick);

        if (isEntry) {
            LOGGER.debug("[SimTale] {} entered the village safe zone; regeneration applied",
                    playerRefComponent.getUsername());
            HytaleLogger.forEnclosingClass().atInfo().log("SimTale: " + playerRefComponent.getUsername()
                    + " entered the village at (" + String.format("%.1f", pos.x) + ", "
                    + String.format("%.1f", pos.z) + "); regeneration applied.");
        }
    }
}
