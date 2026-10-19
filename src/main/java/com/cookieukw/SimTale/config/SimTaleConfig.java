package com.cookieukw.SimTale.config;

/**
 * General configuration for the SimTale mod.
 * Persisted in simtale-config.json.
 */
public class SimTaleConfig {

    /**
     * Master debug switch. If true, full diagnostics and routine logs are emitted.
     * If false, all console logs from SimTale are completely silenced.
     */
    public volatile boolean debugMode = false;

    /**
     * Master switch for experimental seasonal event costumes (Christmas, Halloween).
     * Disabled by default.
     */
    public boolean seasonalCostumesEnabled = false;

    /**
     * Master switch for proximity greetings. If false, NPCs will not react
     * when a player approaches.
     */
    public boolean proximityEnabled = true;

    /**
     * Whether proximity greetings send spoken text to the player's chat.
     * If false, NPCs will still wave/turn friendly without cluttering the chat.
     */
    public boolean proximityChatEnabled = true;

    /**
     * Cooldown (in seconds) before the same NPC can greet a player again.
     */
    public int proximityNpcCooldownSeconds = 60;

    /**
     * Anti-spam interval (in seconds) per player.
     * When any NPC greets a player, other NPCs will not greet that player
     * until this cooldown expires, preventing multi-NPC chat flood.
     */
    public int proximityPlayerCooldownSeconds = 15;

    /**
     * Detection radius in blocks for player proximity.
     */
    public double proximityRadius = 4.5;

    /**
     * Multiplier applied to SimTale's hunger decay.
     *
     * <p>1.0 is the original pacing (about 4h10 from 100 to 70, the first "go find food"
     * threshold). Raise it to make hunger visible sooner, e.g. 10.0 reaches that threshold in
     * roughly 25 minutes. 0 freezes hunger entirely, which is useful for screenshots/trailers.
     */
    public double hungerDecayMultiplier = 1.0;

    /**
     * Chance (0..1) that a worker takes surplus to a village/global chest even when her own home
     * chest still has room. 0 makes village chests a last resort; 1 always uses them.
     */
    public double villageChestDepositChance = 0.30;
}
