package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.NPCPreferences;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.Map;

/**
 * Decides what counts as food and how appealing it is to a given NPC.
 *
 * <p>Classification comes from the item asset, never from its name. The previous heuristic
 * matched {@code "food_"} in the id, which both accepted {@code Plant_Crop_Mushroom_Cap_Brown}
 * (not edible at all) and rejected {@code Ingredient_Dough} (edible, inherits the consume
 * interaction from {@code Template_Food}).
 */
public final class NPCFoodHelper {

    private NPCFoodHelper() {
    }

    public static final int NOT_FOOD = 0;

    /**
     * Hytale grades edible items as {@code Root_Secondary_Consume_Food_T1..T3}. Raw meat,
     * ingredients and harvested crops all land on T1 because they inherit it from their template,
     * while anything cooked or assembled declares T2 or T3 of its own. That makes the tier a
     * ready-made "prepared vs raw" signal.
     */
    private static final String CONSUME_PREFIX = "Root_Secondary_Consume_Food_T";

    private static final float[] HUNGER_BY_TIER = {0f, 25f, 45f, 65f};

    public static int tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NOT_FOOD;

        Item item = stack.getItem();
        if (item == null) return NOT_FOOD;

        String consume = consumeInteraction(item);
        if (consume == null) {
            return NOT_FOOD;
        }

        char tier = consume.charAt(CONSUME_PREFIX.length());
        if (tier < '1' || tier > '3') return NOT_FOOD;
        return tier - '0';
    }

    public static boolean isFood(ItemStack stack) {
        return tierOf(stack) != NOT_FOOD;
    }

    public static float hungerRestored(int tier) {
        return tier >= 1 && tier < HUNGER_BY_TIER.length ? HUNGER_BY_TIER[tier] : 0f;
    }

    /**
     * Ranks a stack for a specific NPC. Higher is better; {@link #NOT_FOOD} means inedible.
     *
     * <p>Tier dominates preference on purpose: a disliked pie still beats a beloved slab of raw
     * beef, which is the behaviour a village sim wants.
     */
    public static int scoreFor(ItemStack stack, NPCPreferences preferences) {
        int tier = tierOf(stack);
        if (tier == NOT_FOOD) return NOT_FOOD;

        int taste = 2;
        if (preferences != null) {
            String id = stack.getItemId();
            if (preferences.getHatedFoods() != null && preferences.getHatedFoods().contains(id)) {
                taste = 1;
            } else if (preferences.getFavoriteFoods() != null && preferences.getFavoriteFoods().contains(id)) {
                taste = 3;
            }
        }
        return tier * 10 + taste;
    }

    public static boolean isHated(ItemStack stack, NPCPreferences preferences) {
        return preferences != null && preferences.getHatedFoods() != null
                && stack != null && preferences.getHatedFoods().contains(stack.getItemId());
    }

    public static boolean isFavorite(ItemStack stack, NPCPreferences preferences) {
        return preferences != null && preferences.getFavoriteFoods() != null
                && stack != null && preferences.getFavoriteFoods().contains(stack.getItemId());
    }

    /**
     * Returns the consume interaction, or null when the item is not edible.
     *
     * <p>{@code isConsumable()} alone is not enough: it also covers potions. The interaction id is
     * what separates food from the rest, and it arrives already resolved through the item's
     * {@code Parent} chain.
     */
    private static String consumeInteraction(Item item) {
        Map<InteractionType, String> interactions = item.getInteractions();
        if (interactions == null) return null;

        String id = interactions.get(InteractionType.Secondary);
        if (id == null) return null;

        return id.startsWith(CONSUME_PREFIX) && id.length() > CONSUME_PREFIX.length() ? id : null;
    }
}
