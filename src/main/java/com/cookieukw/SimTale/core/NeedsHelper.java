package com.cookieukw.SimTale.core;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Utility class to manage SimNPC needs using Hytale's native EntityStatMap.
 */
public class NeedsHelper {

    private static final SimLog LOGGER = SimLog.forClass(NeedsHelper.class);

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    /**
     * Stat ids, which are the asset file names under {@code Server/Entity/Stats} — no namespace.
     *
     * <p>The engine's own stats follow the same rule: {@code Stamina.json} registers as
     * {@code Stamina}. A {@code simtale:} prefix here matches nothing, and an unmatched lookup does
     * not return null — it returns {@code Integer.MIN_VALUE} as an index and blows up inside
     * {@link EntityStatMap}.
     */
    public static final String HUNGER_ID = "simtale_hunger";
    public static final String ENERGY_ID = "simtale_energy";
    public static final String SOCIAL_ID = "simtale_social";
    public static final String FUN_ID = "simtale_fun";
    public static final String HYGIENE_ID = "simtale_hygiene";

    /** Value returned when a stat cannot be read, matching the assets' InitialValue. */
    private static final float DEFAULT_VALUE = 100f;

    /**
     * Finds the store that actually owns {@code entity}.
     *
     * <p>The previous version returned the first world of the iteration and called it a day. With
     * one world that is right by accident; with two it reads an NPC's stats through a store that
     * has never heard of it, and {@code getComponent} answers null — so the NPC silently reports
     * full hunger and energy forever. Most call sites pass null for the store, so this ran on
     * nearly every read.
     */
    public static Store<EntityStore> getStore(Ref<EntityStore> entity) {
        if (entity == null || !entity.isValid()) return null;
        for (World w : Universe.get().getWorlds().values()) {
            Store<EntityStore> store = w.getEntityStore().getStore();
            if (store != null && store.getComponent(entity, EntityStatMap.getComponentType()) != null) {
                return store;
            }
        }
        return null;
    }

    /**
     * Reads a stat, or the default when it cannot be read.
     *
     * <p>{@code EntityStatMap.get(String)} does NOT return null for an unknown id — it resolves the
     * id to an index, gets {@code Integer.MIN_VALUE} back, and indexes the array with it. That
     * throws {@code ArrayIndexOutOfBoundsException} on the world thread and takes the tick loop
     * down with it, which is exactly what a missing stat asset caused.
     *
     * <p>An asset that fails to load should degrade the mod, not kill the server tick.
     */
    public static float getNeed(Store<EntityStore> store, Ref<EntityStore> entity, String statId) {
        if (entity == null || !entity.isValid()) return DEFAULT_VALUE;
        if (store == null) store = getStore(entity);
        if (store == null) return DEFAULT_VALUE;

        EntityStatMap map = store.getComponent(entity, EntityStatMap.getComponentType());
        if (map == null) return DEFAULT_VALUE;

        try {
            int statIndex = EntityStatType.getAssetMap().getIndex(statId);
            EntityStatValue val = map.get(statIndex);
            return val != null ? val.get() : DEFAULT_VALUE;
        } catch (RuntimeException e) {
            warnOnce(statId);
            return DEFAULT_VALUE;
        }
    }

    public static void setNeed(Store<EntityStore> store, Ref<EntityStore> entity, String statId, float value) {
        if (entity == null || !entity.isValid()) return;
        if (store == null) store = getStore(entity);
        if (store == null) return;

        EntityStatMap map = store.getComponent(entity, EntityStatMap.getComponentType());
        if (map == null) return;

        try {
            int statIndex = EntityStatType.getAssetMap().getIndex(statId);
            EntityStatValue val = map.get(statIndex);
            if (val != null) {
                map.setStatValue(val.getIndex(), Math.max(0f, Math.min(100f, value)));
            }
        } catch (RuntimeException e) {
            warnOnce(statId);
        }
    }

    /**
     * One warning per stat id, not one per tick.
     *
     * <p>These run on every NPC every tick, so an unguarded log would bury the server in identical
     * lines and hide whatever else went wrong.
     */
    private static void warnOnce(String statId) {
        if (WARNED.add(statId)) {
            LOGGER.warn("[SimTale] stat '{}' nao registrado — usando {} como padrao. "
                    + "Confira Server/Entity/Stats/{}.json", statId, DEFAULT_VALUE, statId);
        }
    }

    public static boolean isMiserable(Store<EntityStore> store, Ref<EntityStore> entity) {
        return getNeed(store, entity, HUNGER_ID) < 10 
            || getNeed(store, entity, ENERGY_ID) < 10 
            || getNeed(store, entity, SOCIAL_ID) < 10 
            || getNeed(store, entity, FUN_ID) < 10 
            || getNeed(store, entity, HYGIENE_ID) < 10;
    }

    public static void tickDecay(Store<EntityStore> store, Ref<EntityStore> entity, Set<Trait> traits) {
        boolean lazy = traits != null && traits.contains(Trait.LAZY);
        boolean funny = traits != null && traits.contains(Trait.FUNNY);
        float energyDecay = lazy ? 0.0004f : 0.0002f;
        float funDecay = funny ? 0.00005f : 0.0001f;
        
        setNeed(store, entity, HUNGER_ID, getNeed(store, entity, HUNGER_ID) - 0.0001f);
        setNeed(store, entity, ENERGY_ID, getNeed(store, entity, ENERGY_ID) - energyDecay);
        setNeed(store, entity, SOCIAL_ID, getNeed(store, entity, SOCIAL_ID) - 0.00015f);
        setNeed(store, entity, FUN_ID, getNeed(store, entity, FUN_ID) - funDecay);
        setNeed(store, entity, HYGIENE_ID, getNeed(store, entity, HYGIENE_ID) - 0.0002f);
    }
}
