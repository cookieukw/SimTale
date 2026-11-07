package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.Currency;
import com.cookieukw.SimTale.db.PiggyBankPersistence;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

/**
 * Bridges the inventory wallet and the piggy bank balance.
 *
 * <p>The piggy bank is a per-player account, not a second inventory: the item only opens the
 * deposit screen. That is what lets the merchant pay directly from savings when the player is
 * carrying no coins — the balance lives in Caskara, keyed by the player UUID.
 */
public final class PiggyBankService {
    /** Returned by {@link #withdraw} when the coins do not fit in the inventory. */
    public static final long INVENTORY_FULL = -1L;

    private PiggyBankService() {
    }

    public static long balance(World world, UUID playerId) {
        return PiggyBankPersistence.get(world, playerId);
    }

    /** Moves every coin in the inventory into the piggy bank and returns the deposited value. */
    public static long depositAll(CombinedItemContainer inventory, World world, UUID playerId) {
        long value = Currency.removeAllCoins(inventory);
        if (value > 0L) {
            PiggyBankPersistence.add(world, playerId, value);
        }
        return value;
    }

    /**
     * Withdraws up to {@code requested} copper units. A non-positive request withdraws everything.
     *
     * @return the value actually withdrawn, 0 when the bank is empty, or
     *         {@link #INVENTORY_FULL} when the coins would not fit
     */
    public static long withdraw(CombinedItemContainer inventory, World world, UUID playerId,
                                long requested) {
        long bank = PiggyBankPersistence.get(world, playerId);
        if (bank <= 0L) return 0L;
        long amount = requested > 0L ? Math.min(bank, requested) : bank;
        if (!Currency.canAdd(inventory, amount)) return INVENTORY_FULL;
        if (!Currency.add(inventory, amount)) return INVENTORY_FULL;
        PiggyBankPersistence.set(world, playerId, bank - amount);
        return amount;
    }

    /**
     * Pays a merchant from the inventory first and from the piggy bank for the remainder.
     *
     * @return false when inventory plus savings cannot cover the price; no money is moved then
     */
    public static boolean pay(CombinedItemContainer inventory, World world, UUID playerId, long cost) {
        if (cost <= 0L) return true;
        long wallet = Currency.inventoryValue(inventory);
        long bank = PiggyBankPersistence.get(world, playerId);
        if (wallet + bank < cost) return false;

        if (wallet >= cost) {
            long removed = Currency.removeAllCoins(inventory);
            long change = removed - cost;
            // The wallet was re-denominated; if the change does not fit (a nearly full inventory),
            // keep it in savings instead of silently losing it.
            if (change > 0L && !Currency.add(inventory, change)) {
                PiggyBankPersistence.add(world, playerId, change);
            }
            return true;
        }

        Currency.removeAllCoins(inventory);
        PiggyBankPersistence.set(world, playerId, bank - (cost - wallet));
        return true;
    }

    /** Credits sale proceeds to the inventory, falling back to savings when the inventory is full. */
    public static void credit(CombinedItemContainer inventory, World world, UUID playerId, long amount) {
        if (amount <= 0L) return;
        if (!Currency.add(inventory, amount)) {
            PiggyBankPersistence.add(world, playerId, amount);
        }
    }
}
