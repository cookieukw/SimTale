package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookie.caskara.db.Shell;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.UUID;

/** Caskara persistence for piggy bank balances, isolated per Hytale world and player. */
public final class PiggyBankPersistence {
    private static final String SHELL_NAME = "simtale_piggy_bank";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PiggyBankPersistence() {
    }

    private static Shell shell(World world) {
        return world == null ? Caskara.shell(SHELL_NAME) : Caskara.shell(world, SHELL_NAME);
    }

    public static long get(World world, UUID playerId) {
        if (playerId == null) return 0L;
        try {
            PiggyBankData data = shell(world).core(PiggyBankData.class)
                    .extract(playerId.toString()).sync().orElse(null);
            return data == null ? 0L : Math.max(0L, data.copper);
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to load piggy bank: " + e.getMessage());
            return 0L;
        }
    }

    public static void set(World world, UUID playerId, long copper) {
        if (playerId == null) return;
        try {
            long safe = Math.max(0L, copper);
            shell(world).core(PiggyBankData.class)
                    .preserve(playerId.toString(), new PiggyBankData(playerId.toString(), safe));
        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: failed to save piggy bank: " + e.getMessage());
        }
    }

    public static void add(World world, UUID playerId, long copper) {
        if (playerId == null || copper == 0L) return;
        long current = get(world, playerId);
        set(world, playerId, current + copper);
    }
}
