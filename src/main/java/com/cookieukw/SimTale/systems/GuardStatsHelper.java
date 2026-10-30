package com.cookieukw.SimTale.systems;

import com.cookie.runecore.api.RuneAttributes;
import com.cookie.runecore.api.StatHelper;
import com.cookie.runecore.api.attribute.CoreAttributes;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Makes guards noticeably tougher and harder-hitting than an ordinary resident.
 *
 * <p>Two separate systems are involved:
 * <ul>
 *   <li>RuneCore's combat attributes handle armour, flat damage and damage reduction whenever a
 *       guard is actually hit or deals a real damage event.</li>
 *   <li>Hytale's own {@code EntityStatMap} holds the health bar, so the extra health is a
 *       {@link StaticModifier.CalculationType#MULTIPLICATIVE} modifier on the native Health
 *       stat.</li>
 * </ul>
 *
 * <p>Guards do their normal patrol fight through {@link NPCGuardHelper}'s scripted combat, which
 * removes the hostile directly; the shorter attack duration there is what makes their damage
 * felt. These attributes matter outside the village, where the safe zone does not cancel damage.
 */
public final class GuardStatsHelper {

    private static final String MODIFIER_PREFIX = "simtale_guard";

    /** Extra physical damage on top of RuneCore's base. Guards hit roughly twice as hard. */
    private static final float BONUS_PHYSICAL_DAMAGE = 10.0f;

    /** Flat armour, on the same scale as RuneCore's armour sets (roughly 5-25 per piece). */
    private static final float BONUS_ARMOR = 50.0f;

    /** 25% less damage from every non-true source, on top of armour. */
    private static final float BONUS_DAMAGE_REDUCTION = 0.25f;

    /** Doubles the native Health stat's maximum. */
    private static final float HEALTH_MULTIPLIER = 2.0f;

    private static final String HEALTH_MODIFIER = MODIFIER_PREFIX + "_health";

    private GuardStatsHelper() {
    }

    /**
     * Applies the guard buffs idempotently. Safe to call from the periodic tick, on spawn, and
     * when a profession is assigned.
     */
    public static void ensureGuardStats(Ref<EntityStore> ref, SimNPCComponent npc, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || npc == null || store == null) return;
        if (npc.profession != Profession.GUARD || npc.entityId == null) return;

        /* RuneCore keys combat stats by UUID. Tracking is what opts an entity into the damage
        pipeline; without it, armour and damage modifiers would be stored but never consulted.
        */
        RuneAttributes.track(npc.entityId);
        RuneAttributes.of(npc.entityId).ifPresent(attributes -> {
            attributes.addModifier(MODIFIER_PREFIX + "_damage", CoreAttributes.PHYSICAL_DAMAGE, BONUS_PHYSICAL_DAMAGE);
            attributes.addModifier(MODIFIER_PREFIX + "_armor", CoreAttributes.ARMOR, BONUS_ARMOR);
            attributes.addModifier(MODIFIER_PREFIX + "_reduction", CoreAttributes.DAMAGE_REDUCTION, BONUS_DAMAGE_REDUCTION);
        });

        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (stats.getModifier(healthIndex, HEALTH_MODIFIER) == null) {
            StatHelper.applyStatModifier(ref, "Health", HEALTH_MODIFIER, HEALTH_MULTIPLIER,
                    StaticModifier.CalculationType.MULTIPLICATIVE);
            /* The current value does not grow with the maximum. Top the guard up once so a newly
            promoted guard is not left at half health until the next meal.
            */
            StatHelper.addHealth(ref, 100000f);
        }
    }

    /** Removes the buffs if a guard is reassigned to another profession. */
    public static void removeGuardStats(SimNPCComponent npc, Store<EntityStore> store) {
        if (npc == null || npc.entityId == null) return;

        RuneAttributes.of(npc.entityId).ifPresent(attributes -> {
            attributes.removeModifier(MODIFIER_PREFIX + "_damage");
            attributes.removeModifier(MODIFIER_PREFIX + "_armor");
            attributes.removeModifier(MODIFIER_PREFIX + "_reduction");
        });

        if (store == null || npc.entityRef == null || !npc.entityRef.isValid()) return;
        StatHelper.removeStatModifier(npc.entityRef, "Health", HEALTH_MODIFIER);
    }
}
