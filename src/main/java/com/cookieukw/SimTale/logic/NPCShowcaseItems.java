package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.NPCPreferences.Hobby;
import com.cookieukw.SimTale.core.Profession;

/**
 * Item ids used to represent an NPC visually in the interaction menu.
 *
 * <p>Every id here was checked against {@code Server/Item/Items} in the game assets. That check is
 * the whole point of this class existing: a wrong id renders nothing, silently, and there is no
 * error to notice — so the ids live in one place where they can be re-verified, rather than inline
 * in a switch nobody revisits.
 *
 * <p>Copper tier is used throughout for the tools and weapons: it exists for every one of them,
 * which keeps the row visually consistent instead of mixing crude wood with adamantite.
 */
public final class NPCShowcaseItems {

    private NPCShowcaseItems() {
    }

    /** Item that stands for the NPC's trade, or null when it has none to show. */
    public static String forProfession(Profession profession) {
        if (profession == null || profession == Profession.UNEMPLOYED) return null;
        return switch (profession) {
            case MINER -> "Tool_Pickaxe_Copper";
            case FARMER -> "Tool_Hoe_Copper";
            case FISHERMAN -> "Tool_Fishing_Trap";
            case LUMBERJACK -> "Tool_Hatchet_Copper";
            case GUARD -> "Weapon_Sword_Copper";
            case HUNTER -> "Weapon_Shortbow_Copper";
            case BUILDER -> "Tool_Hammer_Crude";
            case EXPLORER -> "Tool_Map";
            default -> null;
        };
    }

    /** Item that stands for the NPC's hobby, or null when unknown. */
    public static String forHobby(Hobby hobby) {
        if (hobby == null) return null;
        return switch (hobby) {
            case FISHING -> "Fish_Bluegill_Item";
            case MINING -> "Ore_Gold";
            case GARDENING -> "Plant_Crop_Carrot_Item";
            case READING -> "Deco_Book_Pile_Small";
            case SLEEPING -> "Deco_Lantern";
        };
    }
}
