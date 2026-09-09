package com.cookieukw.SimTale.core;

/**
 * Whether a weapon is meant to be used up close or from a distance.
 *
 * <p>Exists so a combat-capable NPC (today, just the Guard) is not hardcoded to one weapon family.
 * A mod that adds a new sword, axe or firearm does not need SimTale to know that item by name: as
 * long as it resolves to {@code MELEE} or {@code RANGED} (built-in keyword, or an entry in
 * {@code simtale-weapons.json}), the NPC using it behaves accordingly.
 *
 * @see WeaponCategoryRegistry#of(String)
 */
public enum WeaponCategory {
    MELEE,
    RANGED
}
