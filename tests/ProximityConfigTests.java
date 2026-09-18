package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.config.SimTaleConfig;
import com.cookieukw.SimTale.config.SimTaleConfigManager;

/**
 * Tests for SimTaleConfig and SimTaleConfigManager.
 */
public final class ProximityConfigTests {

    private ProximityConfigTests() {
    }

    public static void run() {
        Assert.suite("SimTaleConfig default values", ProximityConfigTests::testDefaults);
        Assert.suite("SimTaleConfigManager get/set and persistence", ProximityConfigTests::testConfigPersistence);
    }

    private static void testDefaults() {
        SimTaleConfig cfg = new SimTaleConfig();
        Assert.isTrue(cfg.proximityEnabled, "proximityEnabled default should be true");
        Assert.isTrue(cfg.proximityChatEnabled, "proximityChatEnabled default should be true");
        Assert.equal(cfg.proximityNpcCooldownSeconds, 60, "proximityNpcCooldownSeconds default should be 60");
        Assert.equal(cfg.proximityPlayerCooldownSeconds, 15, "proximityPlayerCooldownSeconds default should be 15");
        Assert.floatEqual((float) cfg.proximityRadius, 4.5f, "proximityRadius default should be 4.5");
    }

    private static void testConfigPersistence() {
        SimTaleConfig initial = SimTaleConfigManager.getConfig();

        try {
            SimTaleConfig custom = new SimTaleConfig();
            custom.proximityEnabled = false;
            custom.proximityChatEnabled = false;
            custom.proximityNpcCooldownSeconds = 120;
            custom.proximityPlayerCooldownSeconds = 30;
            custom.proximityRadius = 6.0;

            SimTaleConfigManager.setConfig(custom);
            Assert.equal(SimTaleConfigManager.getConfig(), custom, "setConfig should update active config");
            Assert.isFalse(SimTaleConfigManager.getConfig().proximityEnabled, "proximityEnabled should be false");
            Assert.isFalse(SimTaleConfigManager.getConfig().proximityChatEnabled, "proximityChatEnabled should be false");
            Assert.equal(SimTaleConfigManager.getConfig().proximityNpcCooldownSeconds, 120, "proximityNpcCooldownSeconds should be 120");
            Assert.equal(SimTaleConfigManager.getConfig().proximityPlayerCooldownSeconds, 30, "proximityPlayerCooldownSeconds should be 30");

            // Save to disk and reload
            SimTaleConfigManager.save();
            SimTaleConfigManager.load();

            SimTaleConfig loaded = SimTaleConfigManager.getConfig();
            Assert.isFalse(loaded.proximityEnabled, "loaded proximityEnabled should match saved");
            Assert.isFalse(loaded.proximityChatEnabled, "loaded proximityChatEnabled should match saved");
            Assert.equal(loaded.proximityNpcCooldownSeconds, 120, "loaded proximityNpcCooldownSeconds should match saved");
            Assert.equal(loaded.proximityPlayerCooldownSeconds, 30, "loaded proximityPlayerCooldownSeconds should match saved");
            Assert.floatEqual((float) loaded.proximityRadius, 6.0f, "loaded proximityRadius should match saved");

            // Null safety
            SimTaleConfigManager.setConfig(null);
            Assert.equal(SimTaleConfigManager.getConfig(), loaded, "setConfig(null) should not replace config");
        } finally {
            // Restore initial config and save
            SimTaleConfigManager.setConfig(initial);
            SimTaleConfigManager.save();
        }
    }
}
