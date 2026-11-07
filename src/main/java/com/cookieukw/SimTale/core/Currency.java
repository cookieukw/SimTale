package com.cookieukw.SimTale.core;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * The three-tier currency used by travelling merchants.
 *
 * <p>All prices are stored in copper units. Copper is the base item, thorium is worth 100 copper
 * and gold is worth 100 thorium (10,000 copper). Keeping every price in one unit avoids rounding
 * differences between the shop screen and the wallet.
 */
public final class Currency {
    public static final String COPPER_ID = "SimTale_Coin";
    public static final String THORIUM_ID = "SimTale_Coin_Thorium";
    public static final String GOLD_ID = "SimTale_Coin_Gold";

    public static final long THORIUM_VALUE = 100L;
    public static final long GOLD_VALUE = 10_000L;
    public static final int MAX_STACK = 999;

    private Currency() {
    }

    public static long valueOf(String itemId) {
        if (COPPER_ID.equals(itemId)) return 1L;
        if (THORIUM_ID.equals(itemId)) return THORIUM_VALUE;
        if (GOLD_ID.equals(itemId)) return GOLD_VALUE;
        return 0L;
    }

    public static boolean isCoin(String itemId) {
        return valueOf(itemId) > 0L;
    }

    /** Total value of every coin stack in the container, in copper units. */
    public static long inventoryValue(CombinedItemContainer inventory) {
        if (inventory == null) return 0L;
        long total = 0L;
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack == null) continue;
            long value = valueOf(stack.getItemId());
            if (value <= 0L) continue;
            total += value * stack.getQuantity();
        }
        return total;
    }

    /**
     * Removes every coin stack and returns its total value. The caller usually spends part of it
     * and then calls {@link #add(CombinedItemContainer, long)} with the change, which re-denominates
     * the remaining wallet into the fewest possible stacks.
     */
    public static long removeAllCoins(CombinedItemContainer inventory) {
        if (inventory == null) return 0L;
        long total = 0L;
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack == null) continue;
            long value = valueOf(stack.getItemId());
            if (value <= 0L) continue;
            total += value * stack.getQuantity();
            inventory.removeItemStackFromSlot(slot, stack.getQuantity());
        }
        return total;
    }

    /** Coin stacks that represent exactly {@code copperValue}, largest denomination first. */
    public static List<ItemStack> stacksFor(long copperValue) {
        List<ItemStack> stacks = new ArrayList<>();
        if (copperValue <= 0L) return stacks;

        long gold = copperValue / GOLD_VALUE;
        long remainder = copperValue % GOLD_VALUE;
        long thorium = remainder / THORIUM_VALUE;
        long copper = remainder % THORIUM_VALUE;

        appendStacks(stacks, GOLD_ID, gold);
        appendStacks(stacks, THORIUM_ID, thorium);
        appendStacks(stacks, COPPER_ID, copper);
        return stacks;
    }

    private static void appendStacks(List<ItemStack> stacks, String itemId, long quantity) {
        while (quantity > 0L) {
            int amount = (int) Math.min(quantity, MAX_STACK);
            stacks.add(new ItemStack(itemId, amount));
            quantity -= amount;
        }
    }

    public static boolean canAdd(CombinedItemContainer inventory, long copperValue) {
        if (inventory == null) return false;
        List<ItemStack> stacks = stacksFor(copperValue);
        return stacks.isEmpty() || inventory.canAddItemStacks(stacks);
    }

    /**
     * Adds {@code copperValue} as an optimal set of coin stacks.
     *
     * @return false when the inventory cannot hold the full amount; nothing is added in that case
     */
    public static boolean add(CombinedItemContainer inventory, long copperValue) {
        if (inventory == null || copperValue <= 0L) return true;
        List<ItemStack> stacks = stacksFor(copperValue);
        if (!inventory.canAddItemStacks(stacks)) return false;
        inventory.addItemStacks(stacks);
        return true;
    }
}
